package com.margelo.nitro.blesdk.model

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import androidx.annotation.RequiresPermission
import com.margelo.nitro.blesdk.BleDevice

data class BluetoothDeviceModel(
  val name: String?,
  val address: String,
  val type: Int,
  val bondState: Int,
  val connectionState: Int = BluetoothProfile.STATE_DISCONNECTED,
  val rssi: Int = -1,
  val isBle: Boolean = (
    type == BluetoothDevice.DEVICE_TYPE_LE ||
      type == BluetoothDevice.DEVICE_TYPE_DUAL
  ),
  val serviceUuids: List<String> = emptyList(),
  val characterSticsUuid: List<String> = emptyList(),
) {
  companion object {
    fun isBleType(type: Int): Boolean {
      return type == BluetoothDevice.DEVICE_TYPE_LE ||
        type == BluetoothDevice.DEVICE_TYPE_DUAL
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun fromBluetoothDevice(
      device: BluetoothDevice,
      rssi: Int = -1,
      serviceUuids: List<String>? = null,
      characterSticksUuid: List<String>?=null,
      bondState: Int? = null,
      connectionState: Int = BluetoothProfile.STATE_DISCONNECTED,
    ): BluetoothDeviceModel {
      val resolvedType = device.type
//      val characterSticksUuid = null
      return BluetoothDeviceModel(
        name = device.name,
        address = device.address,
        type = resolvedType,
        bondState = bondState ?: device.bondState,
        connectionState = connectionState,
        rssi = rssi,
        isBle = isBleType(resolvedType),
        serviceUuids = serviceUuids ?: device.uuids?.map { uuid -> uuid.uuid.toString() } ?: emptyList(),
        characterSticsUuid = characterSticksUuid ?: device.uuids?.map { uuid -> uuid.uuid.toString() } ?: emptyList(),
      )
    }
  }

  fun displayName(): String {
    return name ?: "unknown ($address)"
  }

  fun bondStateString(): String {
    return when (bondState) {
      BluetoothDevice.BOND_NONE -> "Not Paired"
      BluetoothDevice.BOND_BONDING -> "Pairing..."
      BluetoothDevice.BOND_BONDED -> "Paired"
      else -> "UNKNOWN"
    }
  }

  fun typeString(): String {
    return when (type) {
      BluetoothDevice.DEVICE_TYPE_CLASSIC -> "Classic"
      BluetoothDevice.DEVICE_TYPE_LE -> "BLE"
      BluetoothDevice.DEVICE_TYPE_DUAL -> "Dual(classic + BLE)"
      else -> "UNKNOWN"
    }
  }

  fun connectionStateString(): String {
    return when (connectionState) {
      BluetoothProfile.STATE_DISCONNECTED -> "Disconnected"
      BluetoothProfile.STATE_CONNECTING -> "Connecting..."
      BluetoothProfile.STATE_CONNECTED -> "Connected"
      BluetoothProfile.STATE_DISCONNECTING -> "Disconnecting..."
      else -> "UNKNOWN"
    }
  }

  fun toBleDevice(): BleDevice {
    return BleDevice(
      name = name,
      address = address,
      type = type.toDouble(),
      typeLabel = typeString(),
      bondState = bondState.toDouble(),
      bondStateLabel = bondStateString(),
      connectionState = connectionState.toDouble(),
      connectionStateLabel = connectionStateString(),
      rssi = rssi.toDouble(),
      isBle = isBle,
      serviceUuids = serviceUuids.toTypedArray(),
    )
  }

  fun mergeWith(next: BluetoothDeviceModel): BluetoothDeviceModel {
    val mergedType =
      if (next.type != BluetoothDevice.DEVICE_TYPE_UNKNOWN) next.type else type
    val mergedName = next.name ?: name
    val mergedRssi = when {
      rssi == -1 -> next.rssi
      next.rssi == -1 -> rssi
      else -> ((rssi * 3) + next.rssi) / 4
    }
    val mergedServiceUuids = linkedSetOf<String>().apply {
      addAll(serviceUuids)
      addAll(next.serviceUuids)
    }.toList()

    return copy(
      name = mergedName,
      type = mergedType,
      bondState = next.bondState,
      connectionState = next.connectionState,
      rssi = mergedRssi,
      isBle = isBleType(mergedType),
      serviceUuids = mergedServiceUuids,
    )
  }
}
