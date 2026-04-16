package com.margelo.nitro.blesdk.permissions

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

object  BluetoothPermissionsHelper {

  //------------- TAG FOR HELPER ----------------//
  private const val TAG = "PermissionHelper"

  // - request code : a unique numbner so we can identify our permissions request
  // back in onRequestPermissionsResult()
  // you can use any integer but pick soemthing uniuque per feature

  const val REQUEST_CODE_BLUETOOTH = 1001

/**
 * // Get all required permissions for the current android version
 * */

fun getRequiredPermissions() : Array<String> {
  return if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S){
    arrayOf(
      Manifest.permission.BLUETOOTH_SCAN,  // To search for devices
      Manifest.permission.BLUETOOTH_CONNECT, // TO connect./Pair to devices
    )
  }
  else{
    arrayOf(
      Manifest.permission.ACCESS_FINE_LOCATION, // To get location required for scanning
      Manifest.permission.ACCESS_COARSE_LOCATION // To get fallback location
    )
  }
}

  /**
   * CHECK if All required bluetooth permissions are granted or not
   *
   * HOW ContextCompat.checkSelfPermissions() WORKS:
   * it returns one of two values
   *   - package.PERMISSIOND_GRANTEED (=0) -<- if permission is granted
   *   - package.PERMISSION_DENIED (=1) -<- if permission is not granted
   * */

  fun allPermissionsGranted(context: Context): Boolean{
    val allGranted = getRequiredPermissions().all { permission -> ContextCompat.checkSelfPermission(context,permission) == PackageManager.PERMISSION_GRANTED }
// ------- add log here -----------------//
    Log.d(TAG,"All permissions granted : $allGranted")
    return allGranted
  }

  /**
   *  check a single specific permissions is granted or not
   *  used when only need to check onr permissions before specific operation is performed
   * */

  fun hasPermission(context: Context, permission: String): Boolean {
    val granted = ContextCompat.checkSelfPermission(context,permission) == PackageManager.PERMISSION_GRANTED
    return granted
  }

  fun hasScanPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      hasPermission(context, Manifest.permission.BLUETOOTH_SCAN)
    } else {
      allPermissionsGranted(context)
    }
  }

  fun hasConnectPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      hasPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
      true
    }
  }

  /**
   * REQUEST ALL required Bluetooth permissions from the user
   * */

  fun requestAllPermissions(activity: Activity){
    val permissionsNeeded = getRequiredPermissions().filter { permission ->
      ContextCompat.checkSelfPermission(activity,permission) != PackageManager.PERMISSION_GRANTED }.toTypedArray()

    if (permissionsNeeded.isNotEmpty()){
      Log.d(TAG,"Requesting permissions : ${permissionsNeeded.size} permissions....")
      ActivityCompat.requestPermissions(activity,permissionsNeeded,REQUEST_CODE_BLUETOOTH)
    } else{
      Log.d( TAG,"All permissions are granted")
    }
  }

  /***
   * Process the permissions result from  onRequestPermissionsResult()
   *
   * */

  fun handlePermissionResult(requestCode: Int,grantResults: IntArray): Boolean{
    if(requestCode != REQUEST_CODE_BLUETOOTH) return  false

    // check if every (permissions) in the result was granted
    val allGranted = grantResults.isNotEmpty() && grantResults.all{ it == PackageManager.PERMISSION_GRANTED }

    if(allGranted){
      Log.d(TAG,"All permissions granted")

    } else{
      Log.d(TAG,"Not all permissions granted")
    }
    return allGranted
  }

  /**
   * Check if we should show a rational (explanation) to the  user.
   *
   * */

  fun shouldShowRational(activity: Activity): Boolean{
    return  getRequiredPermissions().any{permission -> ActivityCompat.shouldShowRequestPermissionRationale(activity,permission) }
  }


  }
