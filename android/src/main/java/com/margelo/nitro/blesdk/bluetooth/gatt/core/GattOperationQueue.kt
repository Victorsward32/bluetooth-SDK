package com.margelo.nitro.blesdk.bluetooth.gatt.core

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import java.util.ArrayDeque
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Single-flight BLE operation queue.
 *
 * Why this exists:
 * - Android BLE stack behaves as a single threaded state machine per GATT connection.
 * - Running multiple GATT commands in parallel frequently causes GATT_BUSY / dropped callbacks.
 *
 * Guarantees:
 * - At most one in-flight operation at a time.
 * - Per operation timeout.
 * - Bounded retry with simple linear backoff.
 */
class GattOperationQueue(
  private val tag: String,
  private val mainHandler: Handler = Handler(Looper.getMainLooper()),
  private val clockMs: () -> Long = { SystemClock.elapsedRealtime() },
) {
  private val queue = ArrayDeque<QueuedOperation>()
  private var inFlight: QueuedOperation? = null
  private val disposed = AtomicBoolean(false)

  /**
   * Enqueue an operation. Caller gets operationId and should complete/fail by that id.
   */
  fun enqueue(
    kind: OperationKind,
    timeoutMs: Long,
    maxRetries: Int,
    execute: () -> Unit,
    onSuccess: () -> Unit,
    onFailure: (GattOperationError) -> Unit,
  ): UUID {
    check(!disposed.get()) { "Queue is disposed" }
    val item = QueuedOperation(
      id = UUID.randomUUID(),
      kind = kind,
      timeoutMs = timeoutMs.coerceAtLeast(MIN_TIMEOUT_MS),
      maxRetries = maxRetries.coerceAtLeast(0),
      execute = execute,
      onSuccess = onSuccess,
      onFailure = onFailure,
    )
    queue.addLast(item)
    pump()
    return item.id
  }

  /**
   * Called by BLE callbacks when an op is finished successfully.
   */
  fun complete(operationId: UUID) {
    val current = inFlight ?: return
    if (current.id != operationId) return

    cancelTimeout(current)
    inFlight = null
    Log.d(tag, "Operation completed: ${current.kind} (${current.id})")
    current.onSuccess.invoke()
    pump()
  }

  /**
   * Called by BLE callbacks when an op failed.
   */
  fun fail(operationId: UUID, reason: String, status: Int? = null, retryable: Boolean = true) {
    val current = inFlight ?: return
    if (current.id != operationId) return

    cancelTimeout(current)

    val canRetry = retryable && current.attempt < current.maxRetries
    if (canRetry) {
      current.attempt += 1
      inFlight = null
      queue.addFirst(current)
      val backoffMs = (BASE_RETRY_DELAY_MS * current.attempt).coerceAtMost(MAX_RETRY_DELAY_MS)
      Log.w(
        tag,
        "Operation failed; retrying ${current.kind} (${current.id}) attempt=${current.attempt}/${current.maxRetries} backoffMs=$backoffMs status=$status reason=$reason",
      )
      mainHandler.postDelayed({ pump() }, backoffMs)
      return
    }

    inFlight = null
    val error = GattOperationError(
      kind = current.kind,
      reason = reason,
      status = status,
      retriable = retryable,
    )
    Log.w(tag, "Operation failed permanently: ${current.kind} (${current.id}) status=$status reason=$reason")
    current.onFailure.invoke(error)
    pump()
  }

  /**
   * Flush queue and fail all pending/in-flight operations.
   */
  fun clear(reason: String) {
    val toFail = mutableListOf<QueuedOperation>()
    inFlight?.let { toFail.add(it) }
    toFail.addAll(queue)
    queue.clear()
    inFlight = null

    toFail.forEach { op ->
      cancelTimeout(op)
      op.onFailure.invoke(
        GattOperationError(
          kind = op.kind,
          reason = reason,
          status = null,
          retriable = false,
        )
      )
    }
  }

  fun dispose() {
    if (!disposed.compareAndSet(false, true)) return
    clear("Queue disposed")
  }

  private fun pump() {
    if (disposed.get()) return
    if (inFlight != null) return
    val next = queue.removeFirstOrNull() ?: return

    inFlight = next
    next.startedAtMs = clockMs()
    scheduleTimeout(next)

    try {
      Log.d(tag, "Executing operation: ${next.kind} (${next.id}) attempt=${next.attempt}")
      next.execute.invoke()
    } catch (t: Throwable) {
      fail(
        operationId = next.id,
        reason = t.message ?: "Execution threw",
        status = null,
        retryable = false,
      )
    }
  }

  private fun scheduleTimeout(op: QueuedOperation) {
    val timeoutRunnable = Runnable {
      val current = inFlight
      if (current == null || current.id != op.id) return@Runnable

      val elapsed = clockMs() - op.startedAtMs
      fail(
        operationId = op.id,
        reason = "Timed out after ${elapsed}ms",
        status = null,
        retryable = true,
      )
    }

    op.timeoutRunnable = timeoutRunnable
    mainHandler.postDelayed(timeoutRunnable, op.timeoutMs)
  }

  private fun cancelTimeout(op: QueuedOperation) {
    op.timeoutRunnable?.let(mainHandler::removeCallbacks)
    op.timeoutRunnable = null
  }

  private data class QueuedOperation(
    val id: UUID,
    val kind: OperationKind,
    val timeoutMs: Long,
    val maxRetries: Int,
    val execute: () -> Unit,
    val onSuccess: () -> Unit,
    val onFailure: (GattOperationError) -> Unit,
    var attempt: Int = 0,
    var startedAtMs: Long = 0,
    var timeoutRunnable: Runnable? = null,
  )

  companion object {
    private const val BASE_RETRY_DELAY_MS = 250L
    private const val MAX_RETRY_DELAY_MS = 2_000L
    private const val MIN_TIMEOUT_MS = 250L
  }
}

enum class OperationKind {
  DISCOVER_SERVICES,
  REQUEST_MTU,
  READ_CHARACTERISTIC,
  WRITE_CHARACTERISTIC,
  WRITE_DESCRIPTOR,
  ENABLE_NOTIFICATION,
  DISABLE_NOTIFICATION,
  REQUEST_CONNECTION_PRIORITY,
  SET_PREFERRED_PHY,
}

data class GattOperationError(
  val kind: OperationKind,
  val reason: String,
  val status: Int?,
  val retriable: Boolean,
)
