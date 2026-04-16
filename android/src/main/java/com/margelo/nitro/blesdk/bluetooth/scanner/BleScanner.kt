package com.margelo.nitro.blesdk.bluetooth.scanner

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import com.margelo.nitro.blesdk.model.BluetoothDeviceModel
import com.margelo.nitro.blesdk.permissions.BluetoothPermissionsHelper
import java.util.UUID

class BleScanner(private val  context: Context) {
  companion object{
    private const val TAG = "BleScanner";
  }

  private val bluetoothManager: BluetoothManager =
    context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager

  private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

  private val bleScanner: BluetoothLeScanner?
    get() = bluetoothAdapter?.bluetoothLeScanner

  //--------------- Scanning State ------------------------- //
  var isScanning: Boolean = false

  //----------- callback ----------------------------------//
  var onDeviceFound: ((BluetoothDeviceModel, ScanResult)-> Unit)? =null
  var  onScanningStateChanged:((Boolean)-> Unit)?=  null;
  var onScanError : ((Int)-> Unit)?= null;

  private val scanCallback = object : ScanCallback(){
    override fun onScanResult(callbackType: Int, result: ScanResult?) {
      val scanResult = result ?: return

      if (!BluetoothPermissionsHelper.hasConnectPermission(context)) {
        Log.w(TAG, "Skipping BLE scan result because BLUETOOTH_CONNECT is not granted")
        return
      }

      try {
        // What is ScanResult?
        // A rich data object containing:
        // .device -> BluetoothDevice (name, address, type)
        // .rssi -> Signal strength in dBm
        // .scanRecord -> Raw advertisement data
        // .timeStamp -> when the advertisement was received
        val device = scanResult.device
        val deviceName: String? = device.name ?: scanResult.scanRecord?.deviceName
        val serviceUuids = scanResult.scanRecord?.serviceUuids
          ?.map { uuid -> uuid.uuid.toString() }
          ?: emptyList()

        val model = BluetoothDeviceModel(
          name = deviceName,
          address = device.address,
          type = device.type,
          bondState = device.bondState,
          rssi = scanResult.rssi,
          serviceUuids = serviceUuids,
        )

        Log.d(TAG, "BLE Device:${model.displayName()} RSSI:${model.rssi} Bond:${model.bondStateString()}")

        scanResult.scanRecord?.let { record ->
          record.serviceUuids?.forEach { uuid -> Log.d(TAG, "Service UUID: $uuid") }
          Log.d(TAG, "Advertised Data: ${record.bytes}")
          record.deviceName?.let { name -> Log.d(TAG, "Device Name: $name") }
        }
        onDeviceFound?.invoke(model, scanResult)
      } catch (error: SecurityException) {
        Log.w(TAG, "Ignoring BLE scan result because Bluetooth permission was revoked", error)
      }
    }

    override fun onBatchScanResults(results: MutableList<ScanResult>){
      Log.d(TAG,"Batch scan result: ${results.size} devices");
      results.forEach { result-> onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES,result) }
    }

    override fun onScanFailed(errorCode: Int) {
      isScanning = false
      val erroMsg = when (errorCode) {
        1 -> "ALREADY_STARTED"
        2 -> "SCANNING_TOO_FREQUENTLY"
        3 -> "OUT_OF_HARDWARE_RESOURCES"
        4 -> "OUT_OF_HARDWARE_RESOURCES"
        5 -> "SCAN_WINDOW_TOO_LONG"
        6 -> "CALLBACK_ALREADY_REGISTERED"
        7 -> "SCAN_FAILED_ALREADY_STARTED"
        8 -> "SCAN_FAILED_APPLICATION_REGISTRATION_FAILED"
        9 -> "SCAN_FAILED_FEATURE_UNSUPPORTED"
        10 -> "SCAN_FAILED_INTERNAL_ERROR"
        11 -> "SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES"
        12 -> "SCAN_FAILED_SCANNING_TOO_FREQUENTLY"
        else -> "Unknown Error ${errorCode}"
      }
      Log.d(TAG, "Scan failed : $erroMsg");
      onScanError?.invoke(errorCode);
      onScanningStateChanged?.invoke(false)


    }
  }
  /**
   * START BLE Scanning with Default settings
   * use this for a general find all Ble devices scan
   * **/
  fun startScan(){
    if (!BluetoothPermissionsHelper.hasScanPermission(context)) {
      throw SecurityException("BLUETOOTH_SCAN permission is missing.")
    }

    try {
      val scanner = bleScanner
      if(scanner == null){
        Log.d(TAG,"BLE Scanner not available");
        return
      }
      if(isScanning){
        Log.d(TAG,"Already Scanning");
        return
      }
      Log.d(TAG,"starting BLE scan")
      val settings = ScanSettings.Builder()
        .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
        .setReportDelay(0)
        .build()
      scanner.startScan(emptyList(), settings, scanCallback)
      isScanning=true
      onScanningStateChanged?.invoke(true)
    } catch (error: SecurityException) {
      isScanning = false
      onScanningStateChanged?.invoke(false)
      Log.w(TAG, "Failed to start BLE scan because BLUETOOTH_SCAN was revoked", error)
      throw error
    }
  }

  /**
   * START BLE Scannning with custom filters and settings
   *
   * **/
  fun startScanWithFilters(
    serviceUuid: UUID? = null,
    deviceName: String? = null,
    scanMode: Int = ScanSettings.SCAN_MODE_BALANCED
  ){
    if (!BluetoothPermissionsHelper.hasScanPermission(context)) {
      throw SecurityException("BLUETOOTH_SCAN permission is missing.")
    }

    try {
      val scanner = bleScanner
      if(scanner == null){
        Log.d(TAG,"BLE Scanner not available");
        return
      }

      if(isScanning){
        Log.d(TAG,"Already Scanning");
        return
      }
      //----------- Build scan filters ---------------------//
      val filters = mutableListOf<ScanFilter>()
      val filterBuilder = ScanFilter.Builder()

      serviceUuid?.let{
        filterBuilder.setServiceUuid(ParcelUuid(it))
        Log.d(TAG,"Filter: Service UUID: $it")
      }

      deviceName?.let{
        filterBuilder.setDeviceName(it)
        Log.d(TAG,"Filter: Device name= $it")
      }

      // only add the filter if we actually set something
      if(serviceUuid != null || deviceName!=null){
        filters.add(filterBuilder.build())
      }

      // scan settings control how the scan operates
      val settings= ScanSettings.Builder().setScanMode(scanMode).setReportDelay(0).build()
      val modeStr = when (scanMode){
        ScanSettings.SCAN_MODE_LOW_POWER->"LOW_POWER (slow, battery-friendly)"
        ScanSettings.SCAN_MODE_BALANCED -> "BALANCED (default)"
        ScanSettings.SCAN_MODE_LOW_LATENCY->"LOW_LATENCY (fast, high power)"
        else -> "UNKNOWN"
      }
      Log.d(TAG,"Starting BLE scan -Mode: $modeStr Filters: ${filters.size}")

      scanner.startScan(filters,settings,scanCallback)
      isScanning=true
      onScanningStateChanged?.invoke(true)
    } catch (error: SecurityException) {
      isScanning = false
      onScanningStateChanged?.invoke(false)
      Log.w(TAG, "Failed to start filtered BLE scan because BLUETOOTH_SCAN was revoked", error)
      throw error
    }

  }

  // stopScanning
  fun stopScan(){
    if(!isScanning){
      Log.d(TAG,"Not Scanning");
      return
    }

    if (!BluetoothPermissionsHelper.hasScanPermission(context)) {
      Log.w(TAG, "Stopping BLE scan locally because BLUETOOTH_SCAN is not granted")
      isScanning=false
      onScanningStateChanged?.invoke(false)
      return
    }

    try {
      bleScanner?.stopScan(scanCallback)
    } catch (error: SecurityException) {
      Log.w(TAG, "Failed to stop BLE scan cleanly because BLUETOOTH_SCAN was revoked", error)
    }
    isScanning=false
    onScanningStateChanged?.invoke(false)
    Log.d(TAG,"BLE scan Stop")
  }
}
