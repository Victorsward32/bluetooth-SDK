package com.margelo.nitro.blesdk

import com.facebook.proguard.annotations.DoNotStrip
import com.margelo.nitro.NitroModules
import com.margelo.nitro.blesdk.bluetooth.facade.BleFacade

@DoNotStrip
class BleSdk : HybridBleSdkSpec() {
  private var bleFacade: BleFacade? = null

  private fun getBleFacade(): BleFacade {
    bleFacade?.let { facade ->
      return facade
    }

    val reactContext = NitroModules.applicationContext
      ?: throw IllegalStateException(
        "NitroModules.applicationContext is not available. Make sure Nitro is installed before using BleSdk."
      )

    return BleFacade(reactContext.applicationContext).also { facade ->
      bleFacade = facade
    }
  }

  override fun isBluetoothSupported(): Boolean {
    return getBleFacade().isBluetoothSupported()
  }

  override fun isBluetoothEnabled(): Boolean {
    return getBleFacade().isBluetoothEnabled()
  }

  override fun isScanning(): Boolean {
    return getBleFacade().isScanning()
  }

  override fun getRequiredPermissions(): Array<String> {
    return getBleFacade().getRequiredPermissions()
  }

  override fun hasRequiredPermissions(): Boolean {
    return getBleFacade().hasRequiredPermissions()
  }

  override fun startScan(serviceUuid: String?, deviceName: String?, scanMode: Double?) {
    getBleFacade().startScan(serviceUuid, deviceName, scanMode)
  }

  override fun stopScan() {
    getBleFacade().stopScan()
  }

  override fun pairDevice(address: String): Boolean {
    return getBleFacade().pairDevice(address)
  }

  override fun unpairDevice(address: String): Boolean {
    return getBleFacade().unpairDevice(address)
  }

  override fun connectDevice(address: String): Boolean {
    return getBleFacade().connectDevice(address)
  }

  override fun disconnectDevice(address: String): Boolean {
    return getBleFacade().disconnectDevice(address)
  }

  override fun clearDiscoveredDevices() {
    getBleFacade().clearDiscoveredDevices()
  }

  override fun getDiscoveredDevices(): Array<BleDevice> {
    return getBleFacade()
      .getDiscoveredDevices()
      .map { device -> device.toBleDevice() }
      .toTypedArray()
  }

  override fun dispose() {
    bleFacade?.dispose()
    bleFacade = null
    super.dispose()
  }
}
