package com.margelo.nitro.blesdk
  
import com.facebook.proguard.annotations.DoNotStrip

@DoNotStrip
class BleSdk : HybridBleSdkSpec() {
  override fun multiply(a: Double, b: Double): Double {
    return a * b
  }
}
