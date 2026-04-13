package com.margelo.nitro.blesdk.model

data class BluetoothDeviceModel(
  val name: String?,
  val address: String,
  val type: Int,
  val bondState: Int,
  val rssi: Int = -1,
  val isBle: Boolean = (type == 2 || type == 3)
) {
  //---------- Display name with fallback ------------------//
  fun displayName(): String {
    return name ?: "unknown ($address)"
  }
  //------- Human readable band state string  for ui display --------------------//
  fun bandStateString(): String {
    return when (bondState) {
      1 -> "Not Paired"
      2 -> "Pairing..."
      3 -> "Paired"
      else -> "UNKNOWN"
    }
  }

  //-------- Human redable device type string ---------------------------//
  fun typeString(): String{
    return when(type){
      1 -> "Classic"
      2 -> "BLE"
      3 -> "Dual(classic + BLE)"
      else -> "UNKNOWN"
    }
  }
}
