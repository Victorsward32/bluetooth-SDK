package com.margelo.nitro.blesdk.bluetooth.facade

import  android.annotation.SuppressLint;
import  android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.ScanResult;
import  android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.util.Log
import com.margelo.nitro.blesdk.bluetooth.scanner.BleScanner
import com.margelo.nitro.blesdk.bluetooth.advertiser.BleAdvertiser
import com.margelo.nitro.blesdk.bluetooth.manager.BleConnectionManager

import java.util.*;



class BleFacade (private val  context: Context) {

  companion object{
    private const val TAG = "BleFacade";
  }

  //------------ Get the Bluetooth Adapter --------------//

  private  val bluetoothManager: BluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager

  private  val bluetoothAdapter: BluetoothAdapter = bluetoothManager.adapter

  //------------- Sub - component ---------------------//
  private  val bleScanner = BleScanner(context);
  private val connectionManager: BleConnectionManager = BleConnectionManager(context);
//  private val advertiser : BleAdvertiser = BleAdvertiser(context)


  //----- Device tracking  -------------------------------------------//
  private val _discoveredDevices = mutableListOf<BluetoothDevice>()
  val discoveredDevices: List<BluetoothDevice> get() = _discoveredDevices

  private val _scanResuls = mutableMapOf<String, ScanResult>();

  fun initialize(){
    Log.d(TAG,"==========================================");
    Log.d(TAG,"Initializing BLE Facade........");
    Log.d(TAG,"==========================================");

    if(bluetoothAdapter == null){
      Log.d(TAG,"Bluetooth adapter not found");
      return
    }

    //--------------- wire up Scanner callback -----------------//
    bleScanner.onDeviceFound = {}

  }

}
