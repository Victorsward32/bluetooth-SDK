package com.margelo.nitro.blesdk.bluetooth.scanner

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.content.Context

class BleScanner(private val  context: Context) {
  companion object{
    private const val TAG = "BleScanner";
  }

  private val bluetoothManager: BluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager;

  private val bluetoothAdapter: BluetoothAdapter = bluetoothManager.adapter

  private val bleScanner: BluetoothLeScanner ?= bluetoothAdapter?.bluetoothLeScanner

  //--------------- Scanning State ------------------------- //
  var isScanning: Boolean = false

  //----------- callback ----------------------------------//
//  var onDeviceFound: ((BluetoothDeviceModel,))?/
}
