package com.margelo.nitro.blesdk.bluetooth.facade

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import com.margelo.nitro.blesdk.bluetooth.manager.BleConnectionManager
import com.margelo.nitro.blesdk.bluetooth.scanner.BleScanner
import com.margelo.nitro.blesdk.model.BluetoothDeviceModel
import com.margelo.nitro.blesdk.permissions.BluetoothPermissionsHelper
import java.util.UUID

class BleFacade(private val context: Context) {
  companion object {
    private const val TAG = "BleFacade"
  }

  private val bluetoothManager: BluetoothManager =
    context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager

  private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
  private val scanner = BleScanner(context)
  private val connectionManager = BleConnectionManager(context)
  private val discoveredDevices = linkedMapOf<String, BluetoothDeviceModel>()
  private var isInitialized = false
  private var isBondStateReceiverRegistered = false
  private val bondStateReceiver = object : BroadcastReceiver() {
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onReceive(receiverContext: Context?, intent: Intent?) {
      if (intent?.action != BluetoothDevice.ACTION_BOND_STATE_CHANGED) {
        return
      }

      if (!BluetoothPermissionsHelper.hasConnectPermission(context)) {
        Log.w(TAG, "Skipping bond-state update because BLUETOOTH_CONNECT is not granted")
        return
      }

      try {
        val device = intent.getBluetoothDeviceExtra() ?: return
        val bondState = intent.getIntExtra(
          BluetoothDevice.EXTRA_BOND_STATE,
          device.bondState,
        )

        Log.d(TAG, "Bond state changed for ${device.address}: $bondState")
        upsertDevice(
          device = device,
          rssi = discoveredDevices[device.address]?.rssi ?: -1,
          serviceUuids = discoveredDevices[device.address]?.serviceUuids ?: emptyList(),
          characterSticksUuid= discoveredDevices[device.address]?.characterSticsUuid ?: emptyList(),
          bondState = bondState,
          connectionState = connectionManager.getConnectionState(device.address),
        )
      } catch (error: SecurityException) {
        Log.w(TAG, "Failed to process bond-state update because BLUETOOTH_CONNECT was revoked", error)
      }
    }
  }

  init {
    initialize()
  }

  private fun initialize() {
    if (isInitialized) {
      return
    }

    Log.d(TAG, "Initializing BLE facade")
    registerBondStateReceiver()

    connectionManager.onConnectionStateChanged = connectionStateChanged@ { device, connectionState, status ->
      if (!BluetoothPermissionsHelper.hasConnectPermission(context)) {
        Log.w(TAG, "Skipping connection-state update because BLUETOOTH_CONNECT is not granted")
        return@connectionStateChanged
      }

      try {
        Log.d(
          TAG,
          "Connection state changed for ${device.address}: state=$connectionState status=$status",
        )
        upsertDevice(
          device = device,
          rssi = discoveredDevices[device.address]?.rssi ?: -1,
          serviceUuids = discoveredDevices[device.address]?.serviceUuids ?: emptyList(),
          characterSticksUuid= discoveredDevices[device.address]?.characterSticsUuid ?: emptyList(),
          bondState = discoveredDevices[device.address]?.bondState ?: device.bondState,
          connectionState = connectionState,
        )
      } catch (error: SecurityException) {
        Log.w(TAG, "Failed to process connection-state update because BLUETOOTH_CONNECT was revoked", error)
      }
    }

    scanner.onDeviceFound = { model, _ ->
      // Preserve insertion order so the UI stays stable while scan packets keep updating RSSI.
      upsertDevice(
        model.copy(
          connectionState = connectionManager.getConnectionState(model.address),
        )
      )
    }

    scanner.onScanError = { errorCode ->
      Log.w(TAG, "BLE scan failed with error code $errorCode")
    }

    scanner.onScanningStateChanged = { scanning ->
      Log.d(TAG, "BLE scan state changed: $scanning")
    }

    isInitialized = true
  }

  fun isBluetoothSupported(): Boolean {
    return bluetoothAdapter != null
  }

  fun isBluetoothEnabled(): Boolean {
    val adapter = bluetoothAdapter ?: return false
    if (!BluetoothPermissionsHelper.hasConnectPermission(context)) {
      Log.d(TAG, "BLUETOOTH_CONNECT is not granted; reporting Bluetooth as unavailable")
      return false
    }

    return try {
      adapter.isEnabled
    } catch (error: SecurityException) {
      Log.w(TAG, "Failed to read Bluetooth adapter state because BLUETOOTH_CONNECT was revoked", error)
      false
    }
  }

  fun isScanning(): Boolean {
    return scanner.isScanning
  }

  fun getRequiredPermissions(): Array<String> {
    return BluetoothPermissionsHelper.getRequiredPermissions()
  }

  fun hasRequiredPermissions(): Boolean {
    return BluetoothPermissionsHelper.allPermissionsGranted(context)
  }

  fun startScan(
    serviceUuid: String? = null,
    deviceName: String? = null,
    scanMode: Double? = null,
  ) {
    ensureBluetoothSupported()
    ensurePermissions()
    ensureBluetoothEnabled()

    val normalizedServiceUuid = serviceUuid
      ?.trim()
      ?.takeIf { uuid -> uuid.isNotEmpty() }
      ?.let { uuid -> UUID.fromString(uuid) }
    val normalizedDeviceName = deviceName
      ?.trim()
      ?.takeIf { name -> name.isNotEmpty() }
    val resolvedScanMode = resolveScanMode(scanMode)

    if (
      normalizedServiceUuid != null ||
      normalizedDeviceName != null ||
      scanMode != null
    ) {
      scanner.startScanWithFilters(
        serviceUuid = normalizedServiceUuid,
        deviceName = normalizedDeviceName,
        scanMode = resolvedScanMode,
      )
    } else {
      scanner.startScan()
    }
  }

  fun stopScan() {
    scanner.stopScan()
  }

  @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
  fun pairDevice(address: String): Boolean {
    ensureBluetoothSupported()
    ensurePermissions()
    ensureBluetoothEnabled()

    require(BluetoothAdapter.checkBluetoothAddress(address)) {
      "Invalid Bluetooth device address: $address"
    }

    val adapter = bluetoothAdapter
      ?: throw IllegalStateException("Bluetooth adapter is not available.")
    val device = adapter.getRemoteDevice(address)
    val currentBondState = discoveredDevices[address]?.bondState ?: device.bondState
    val currentConnectionState = connectionManager.getConnectionState(address)

    if (currentBondState == BluetoothDevice.BOND_BONDED) {
      upsertDevice(
        device = device,
        rssi = discoveredDevices[address]?.rssi ?: -1,
        serviceUuids = discoveredDevices[address]?.serviceUuids ?: emptyList(),
        characterSticksUuid= discoveredDevices[device.address]?.characterSticsUuid ?: emptyList(),
        bondState = BluetoothDevice.BOND_BONDED,
        connectionState = currentConnectionState,
      )
      return true
    }

    val pairingStarted = device.createBond()
    if (pairingStarted) {
      upsertDevice(
        device = device,
        rssi = discoveredDevices[address]?.rssi ?: -1,
        serviceUuids = discoveredDevices[address]?.serviceUuids ?: emptyList(),
        characterSticksUuid= discoveredDevices[device.address]?.characterSticsUuid ?: emptyList(),
        bondState = BluetoothDevice.BOND_BONDING,
        connectionState = currentConnectionState,
      )
    }

    return pairingStarted
  }

  @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
  fun unpairDevice(address: String): Boolean {
    ensureBluetoothSupported()
    ensurePermissions()
    ensureBluetoothEnabled()

    require(BluetoothAdapter.checkBluetoothAddress(address)) {
      "Invalid Bluetooth device address: $address"
    }

    val adapter = bluetoothAdapter
      ?: throw IllegalStateException("Bluetooth adapter is not available.")
    val device = adapter.getRemoteDevice(address)
    val currentBondState = discoveredDevices[address]?.bondState ?: device.bondState
    val currentConnectionState = connectionManager.getConnectionState(address)

    check(currentConnectionState == BluetoothProfile.STATE_DISCONNECTED) {
      "Disconnect the device before unpairing."
    }

    if (currentBondState == BluetoothDevice.BOND_NONE) {
      upsertDevice(
        device = device,
        rssi = discoveredDevices[address]?.rssi ?: -1,
        serviceUuids = discoveredDevices[address]?.serviceUuids ?: emptyList(),
        characterSticksUuid= discoveredDevices[device.address]?.characterSticsUuid ?: emptyList(),
        bondState = BluetoothDevice.BOND_NONE,
        connectionState = BluetoothProfile.STATE_DISCONNECTED,
      )
      return true
    }

    val unpairStarted = when (currentBondState) {
      BluetoothDevice.BOND_BONDING -> device.cancelBondProcessCompat()
      BluetoothDevice.BOND_BONDED -> device.removeBondCompat()
      else -> false
    }

    if (unpairStarted) {
      upsertDevice(
        device = device,
        rssi = discoveredDevices[address]?.rssi ?: -1,
        serviceUuids = discoveredDevices[address]?.serviceUuids ?: emptyList(),
        characterSticksUuid= discoveredDevices[device.address]?.characterSticsUuid ?: emptyList(),
        bondState = BluetoothDevice.BOND_NONE,
        connectionState = BluetoothProfile.STATE_DISCONNECTED,
      )
    }

    return unpairStarted
  }

  @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
  fun connectDevice(address: String): Boolean {
    ensureBluetoothSupported()
    ensurePermissions()
    ensureBluetoothEnabled()

    require(BluetoothAdapter.checkBluetoothAddress(address)) {
      "Invalid Bluetooth device address: $address"
    }

    val adapter = bluetoothAdapter
      ?: throw IllegalStateException("Bluetooth adapter is not available.")
    val device = adapter.getRemoteDevice(address)
    val currentBondState = discoveredDevices[address]?.bondState ?: device.bondState
    val currentConnectionState = connectionManager.getConnectionState(address)
    val isBleDevice =
      discoveredDevices[address]?.isBle ?: BluetoothDeviceModel.isBleType(device.type)

    check(isBleDevice) {
      "GATT connections are only supported for BLE devices."
    }

    if (currentConnectionState == BluetoothProfile.STATE_CONNECTED) {
      upsertDevice(
        device = device,
        rssi = discoveredDevices[address]?.rssi ?: -1,
        characterSticksUuid= discoveredDevices[device.address]?.characterSticsUuid ?: emptyList(),
        serviceUuids = discoveredDevices[address]?.serviceUuids ?: emptyList(),
        bondState = currentBondState,
        connectionState = BluetoothProfile.STATE_CONNECTED,
      )
      return true
    }

    val connectStarted = connectionManager.connect(device)
    if (connectStarted) {
      upsertDevice(
        device = device,
        rssi = discoveredDevices[address]?.rssi ?: -1,
        serviceUuids = discoveredDevices[address]?.serviceUuids ?: emptyList(),
        characterSticksUuid= discoveredDevices[device.address]?.characterSticsUuid ?: emptyList(),
        bondState = currentBondState,
        connectionState = connectionManager.getConnectionState(address),
      )
    }

    return connectStarted
  }

  @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
  fun disconnectDevice(address: String): Boolean {
    ensureBluetoothSupported()
    ensurePermissions()
    ensureBluetoothEnabled()

    require(BluetoothAdapter.checkBluetoothAddress(address)) {
      "Invalid Bluetooth device address: $address"
    }

    val adapter = bluetoothAdapter
      ?: throw IllegalStateException("Bluetooth adapter is not available.")
    val device = adapter.getRemoteDevice(address)
    val currentBondState = discoveredDevices[address]?.bondState ?: device.bondState
    val currentConnectionState = connectionManager.getConnectionState(address)

    if (currentConnectionState == BluetoothProfile.STATE_DISCONNECTED) {
      upsertDevice(
        device = device,
        rssi = discoveredDevices[address]?.rssi ?: -1,
        serviceUuids = discoveredDevices[address]?.serviceUuids ?: emptyList(),
        characterSticksUuid= discoveredDevices[device.address]?.characterSticsUuid ?: emptyList(),

        bondState = currentBondState,
        connectionState = BluetoothProfile.STATE_DISCONNECTED,
      )
      return true
    }

    val disconnectStarted = connectionManager.disconnect(address)
    if (disconnectStarted) {
      upsertDevice(
        device = device,
        rssi = discoveredDevices[address]?.rssi ?: -1,
        serviceUuids = discoveredDevices[address]?.serviceUuids ?: emptyList(),
        characterSticksUuid= discoveredDevices[device.address]?.characterSticsUuid ?: emptyList(),
        bondState = currentBondState,
        connectionState = BluetoothProfile.STATE_DISCONNECTING,
      )
    }

    return disconnectStarted
  }

  fun clearDiscoveredDevices() {
    discoveredDevices.clear()
  }

  fun getDiscoveredDevices(): List<BluetoothDeviceModel> {
    return discoveredDevices.values.toList()
  }

  fun dispose() {
    scanner.stopScan()
    connectionManager.dispose()
    unregisterBondStateReceiver()
    discoveredDevices.clear()
    isInitialized = false
  }

  private fun ensureBluetoothSupported() {
    check(isBluetoothSupported()) { "Bluetooth is not supported on this device." }
  }

  private fun ensureBluetoothEnabled() {
    check(isBluetoothEnabled()) {
      "Bluetooth is disabled. Enable Bluetooth before scanning."
    }
  }

  private fun ensurePermissions() {
    check(hasRequiredPermissions()) {
      "Bluetooth permissions are missing. Request the required Android permissions before using BLE features."
    }
  }

  private fun resolveScanMode(scanMode: Double?): Int {
    val resolvedScanMode = scanMode?.toInt() ?: ScanSettings.SCAN_MODE_BALANCED
    require(
      resolvedScanMode == ScanSettings.SCAN_MODE_LOW_POWER ||
        resolvedScanMode == ScanSettings.SCAN_MODE_BALANCED ||
        resolvedScanMode == ScanSettings.SCAN_MODE_LOW_LATENCY
    ) {
      "Unsupported scan mode: $resolvedScanMode"
    }
    return resolvedScanMode
  }

  private fun upsertDevice(model: BluetoothDeviceModel) {
    val existing = discoveredDevices[model.address]
    discoveredDevices[model.address] = existing?.mergeWith(model) ?: model
  }

  @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
  private fun upsertDevice(
    device: BluetoothDevice,
    rssi: Int = -1,
    serviceUuids: List<String> = emptyList(),
    characterSticksUuid: List<String> = emptyList(),
    bondState: Int? = null,
    connectionState: Int? = null,
  ) {
    val resolvedConnectionState = connectionState ?: connectionManager.getConnectionState(device.address)
    upsertDevice(
      BluetoothDeviceModel.fromBluetoothDevice(
        device = device,
        rssi = rssi,
        serviceUuids = serviceUuids,
        characterSticksUuid= characterSticksUuid,
        bondState = bondState,
        connectionState = resolvedConnectionState,
      )
    )
  }

  private fun registerBondStateReceiver() {
    if (isBondStateReceiverRegistered) {
      return
    }

    val filter = IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      registerBondStateReceiverApi33(filter)
    } else {
      @Suppress("DEPRECATION")
      context.registerReceiver(bondStateReceiver, filter)
    }
    isBondStateReceiverRegistered = true
  }

  @RequiresApi(Build.VERSION_CODES.TIRAMISU)
  private fun registerBondStateReceiverApi33(filter: IntentFilter) {
    context.registerReceiver(
      bondStateReceiver,
      filter,
      Context.RECEIVER_NOT_EXPORTED,
    )
  }

  private fun unregisterBondStateReceiver() {
    if (!isBondStateReceiverRegistered) {
      return
    }

    context.unregisterReceiver(bondStateReceiver)
    isBondStateReceiverRegistered = false
  }

  private fun Intent.getBluetoothDeviceExtra(): BluetoothDevice? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
    } else {
      @Suppress("DEPRECATION")
      getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
    }
  }

  private fun BluetoothDevice.cancelBondProcessCompat(): Boolean {
    return invokeBondMethod("cancelBondProcess")
  }

  private fun BluetoothDevice.removeBondCompat(): Boolean {
    return invokeBondMethod("removeBond")
  }

  private fun BluetoothDevice.invokeBondMethod(methodName: String): Boolean {
    return runCatching {
      javaClass
        .getMethod(methodName)
        .invoke(this) as? Boolean ?: false
    }
      .onFailure { error ->
        Log.w(TAG, "BluetoothDevice.$methodName failed for $address", error)
      }
      .getOrDefault(false)
  }
}
