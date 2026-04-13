package com.margelo.nitro.blesdk.model;
import java.util.UUID


/**
 * Ble communication is built on a protocol called GATT (Generic Access Profile)
 * **/
data class BleCharactersticData( val uuid: UUID, val properties: Int,val value: ByteArray? = null ,val serviceUUID: UUID){
  fun valueAsHex(): String? {
    return value?.joinToString (""){
      byte -> "%02x".format(byte) ?:""
    }


  }

  fun valueAsString(): String {
    return try {
      value?.toString(Charsets.UTF_8) ?: "No Value"
    } catch (e: Exception) {
      "No Value"
    }
  }

  fun propertiesString(): String{
    val props = mutableListOf<String>()
    if(properties and 0x01 == 1) props.add("Broadcast");
    if(properties and 0x02 == 2) props.add("Read");
    if(properties and 0x04 == 4) props.add("Write No Response");
    if(properties and 0x08 == 8) props.add("Write");
    if(properties and 0x10 == 16) props.add("Notify");
    if(properties and 0x20 == 32) props.add("Indicate");
    if(properties and 0x40 == 64) props.add("Authenticated Signed");
    if(properties and 0x80 == 128) props.add("Extended Prop");


    return if(props.isEmpty()) "No Properties" else props.joinToString (",")
  }



}

data class BleServiceData(val uuid: UUID,val characteristics: List<BleCharactersticData>){

  fun shortUuid(): String = uuid.toString().uppercase().take(8)
}
