import Foundation

/// Single-flight queue for CoreBluetooth operations.
///
/// CoreBluetooth is callback-driven and operation order matters. This queue keeps at most
/// one operation active, with timeout + bounded retries for transient failures.
final class BleOperationQueue {
    enum Kind: String {
        case discoverServices
        case discoverCharacteristics
        case readCharacteristic
        case writeCharacteristic
        case setNotify
        case requestMtu
    }

    struct OperationError: Error {
        let kind: Kind
        let reason: String
        let retriable: Bool
    }

    private struct Item {
        let id: UUID
        let kind: Kind
        let timeoutMs: Int
        let maxRetries: Int
        let execute: () -> Void
        let onSuccess: () -> Void
        let onFailure: (OperationError) -> Void
        var attempt: Int
        var timeoutWorkItem: DispatchWorkItem?
    }

    private var queue: [Item] = []
    private var inFlight: Item?
    private let lock = NSLock()
    private let dispatchQueue = DispatchQueue(label: "BleOperationQueue")

    func enqueue(
        kind: Kind,
        timeoutMs: Int = 8000,
        maxRetries: Int = 1,
        execute: @escaping () -> Void,
        onSuccess: @escaping () -> Void,
        onFailure: @escaping (OperationError) -> Void
    ) -> UUID {
        let id = UUID()
        let item = Item(
            id: id,
            kind: kind,
            timeoutMs: max(timeoutMs, 250),
            maxRetries: max(maxRetries, 0),
            execute: execute,
            onSuccess: onSuccess,
            onFailure: onFailure,
            attempt: 0,
            timeoutWorkItem: nil
        )

        lock.lock()
        queue.append(item)
        lock.unlock()

        pump()
        return id
    }

    func complete(id: UUID) {
        lock.lock()
        guard var current = inFlight, current.id == id else {
            lock.unlock()
            return
        }
        current.timeoutWorkItem?.cancel()
        inFlight = nil
        lock.unlock()

        current.onSuccess()
        pump()
    }

    func fail(id: UUID, reason: String, retriable: Bool) {
        lock.lock()
        guard var current = inFlight, current.id == id else {
            lock.unlock()
            return
        }
        current.timeoutWorkItem?.cancel()

        let shouldRetry = retriable && current.attempt < current.maxRetries
        if shouldRetry {
            current.attempt += 1
            inFlight = nil
            queue.insert(current, at: 0)
            lock.unlock()

            let backoffMs = min(250 * current.attempt, 2000)
            dispatchQueue.asyncAfter(deadline: .now() + .milliseconds(backoffMs)) { [weak self] in
                self?.pump()
            }
            return
        }

        inFlight = nil
        lock.unlock()

        current.onFailure(OperationError(kind: current.kind, reason: reason, retriable: retriable))
        pump()
    }

    func clear(reason: String = "Queue cleared") {
        lock.lock()
        let pending = queue
        let active = inFlight
        queue.removeAll()
        inFlight = nil
        lock.unlock()

        active?.timeoutWorkItem?.cancel()
        if let active {
            active.onFailure(OperationError(kind: active.kind, reason: reason, retriable: false))
        }

        for item in pending {
            item.onFailure(OperationError(kind: item.kind, reason: reason, retriable: false))
        }
    }

    private func pump() {
        lock.lock()
        if inFlight != nil || queue.isEmpty {
            lock.unlock()
            return
        }

        var item = queue.removeFirst()
        let timeout = DispatchWorkItem { [weak self] in
            self?.fail(id: item.id, reason: "Timed out", retriable: true)
        }
        item.timeoutWorkItem = timeout
        inFlight = item
        lock.unlock()

        dispatchQueue.asyncAfter(deadline: .now() + .milliseconds(item.timeoutMs), execute: timeout)

        item.execute()
    }
}
