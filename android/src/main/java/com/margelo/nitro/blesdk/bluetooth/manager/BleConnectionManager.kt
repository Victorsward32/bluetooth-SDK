package com.margelo.nitro.blesdk.bluetooth.manager

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import android.util.Log
import java.util.concurrent.ConcurrentHashMap

class BleConnectionManager(private val context: Context) {
  companion object {
    private const val TAG = "BleConnectionManager"
  }

  private val gattConnections = ConcurrentHashMap<String, BluetoothGatt>()
  private val connectionStates = ConcurrentHashMap<String, Int>()

  var onConnectionStateChanged: ((BluetoothDevice, Int, Int) -> Unit)? = null

  @SuppressLint("MissingPermission")
  fun connect(device: BluetoothDevice): Boolean {
    val address = device.address
    when (getConnectionState(address)) {
      BluetoothProfile.STATE_CONNECTED,
      BluetoothProfile.STATE_CONNECTING -> return true

      BluetoothProfile.STATE_DISCONNECTING -> return false
    }

    closeConnection(address)
    updateConnectionState(
      device = device,
      connectionState = BluetoothProfile.STATE_CONNECTING,
      status = BluetoothGatt.GATT_SUCCESS,
    )

    return runCatching {
      val gatt =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
          device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } else {
          @Suppress("DEPRECATION")
          device.connectGatt(context, false, gattCallback)
        }

      if (gatt == null) {
        updateConnectionState(
          device = device,
          connectionState = BluetoothProfile.STATE_DISCONNECTED,
          status = BluetoothGatt.GATT_FAILURE,
        )
        false
      } else {
        gattConnections[address] = gatt
        true
      }
    }
      .onFailure { error ->
        Log.w(TAG, "Failed to start GATT connection for $address", error)
        updateConnectionState(
          device = device,
          connectionState = BluetoothProfile.STATE_DISCONNECTED,
          status = BluetoothGatt.GATT_FAILURE,
        )
      }
      .getOrDefault(false)
  }

  @SuppressLint("MissingPermission")
  fun disconnect(address: String): Boolean {
    val currentState = getConnectionState(address)
    if (
      currentState == BluetoothProfile.STATE_DISCONNECTED ||
      currentState == BluetoothProfile.STATE_DISCONNECTING
    ) {
      return true
    }

    val gatt = gattConnections[address] ?: return false
    updateConnectionState(
      device = gatt.device,
      connectionState = BluetoothProfile.STATE_DISCONNECTING,
      status = BluetoothGatt.GATT_SUCCESS,
    )

    return runCatching {
      gatt.disconnect()
      true
    }
      .onFailure { error ->
        Log.w(TAG, "Failed to disconnect GATT for $address", error)
        updateConnectionState(
          device = gatt.device,
          connectionState = BluetoothProfile.STATE_DISCONNECTED,
          status = BluetoothGatt.GATT_FAILURE,
        )
        closeConnection(address, gatt)
      }
      .getOrDefault(false)
  }

  fun getConnectionState(address: String): Int {
    return connectionStates[address] ?: BluetoothProfile.STATE_DISCONNECTED
  }

  fun dispose() {
    gattConnections.entries.forEach { (address, gatt) ->
      runCatching {
        gatt.close()
      }.onFailure { error ->
        Log.w(TAG, "Failed to close GATT for $address during dispose", error)
      }
    }

    gattConnections.clear()
    connectionStates.clear()
  }

  private val gattCallback = object : BluetoothGattCallback() {
    override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
      val address = gatt.device.address
      val trackedGatt = gattConnections[address]
      if (trackedGatt == null || trackedGatt != gatt) {
        Log.d(TAG, "Ignoring stale GATT callback for $address")
        gatt.close()
        return
      }

      Log.d(TAG, "GATT connection changed for $address: state=$newState status=$status")

      if (status != BluetoothGatt.GATT_SUCCESS && newState != BluetoothProfile.STATE_CONNECTED) {
        updateConnectionState(
          device = gatt.device,
          connectionState = BluetoothProfile.STATE_DISCONNECTED,
          status = status,
        )
        closeConnection(address, gatt)
        return
      }

      updateConnectionState(
        device = gatt.device,
        connectionState = newState,
        status = status,
      )

      if (newState == BluetoothProfile.STATE_DISCONNECTED) {
        closeConnection(address, gatt)
      }
    }
  }

  private fun updateConnectionState(
    device: BluetoothDevice,
    connectionState: Int,
    status: Int,
  ) {
    if (connectionState == BluetoothProfile.STATE_DISCONNECTED) {
      connectionStates.remove(device.address)
    } else {
      connectionStates[device.address] = connectionState
    }
    onConnectionStateChanged?.invoke(device, connectionState, status)
  }

  private fun closeConnection(address: String, expectedGatt: BluetoothGatt? = null) {
    val gatt =
      if (expectedGatt == null) {
        gattConnections.remove(address)
      } else {
        val removed = gattConnections.remove(address, expectedGatt)
        if (removed) expectedGatt else null
      }

    runCatching {
      gatt?.close()
    }.onFailure { error ->
      Log.w(TAG, "Failed to close GATT for $address", error)
    }
  }
}
