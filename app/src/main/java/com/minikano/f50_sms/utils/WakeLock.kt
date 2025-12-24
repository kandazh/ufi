package com.minikano.f50_sms.utils

import android.os.PowerManager
import android.util.Log

class WakeLock {

    companion object {
        private var wakeLock: PowerManager.WakeLock? = null
        private var wakeLock2: PowerManager.WakeLock? = null
        private var wakeLock3: PowerManager.WakeLock? = null

        fun execWakeLock (pm: PowerManager){
            // Prevent holding the wake lock multiple times
            releaseWakeLock()
            wakeLock = pm.newWakeLock(
                PowerManager.SCREEN_DIM_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "ZTE-UFI-TOOLS::WakeLock"
            )
            wakeLock?.acquire()
            Log.d("kano_ZTE_LOG", "Wake lock acquired to prevent screen from turning off")

            wakeLock2 = pm.newWakeLock(
                PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "ZTE-UFI-TOOLS::FULL_WAKE_LOCK"
            )
            wakeLock2?.acquire()
            Log.d("kano_ZTE_LOG", "Full wake lock acquired: keep screen on and wake up")

            wakeLock3 = pm.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "ZTE-UFI-TOOLS::BrightWakeLock"
            )
            wakeLock3?.acquire()
            Log.d("kano_ZTE_LOG", "Bright wake lock acquired: keep screen bright and wake up")
        }

        fun releaseWakeLock() {
            wakeLock?.let {
                if (it.isHeld) it.release()
                Log.d("kano_ZTE_LOG", "Wake lock released")
            }
            wakeLock2?.let {
                if (it.isHeld) it.release()
                Log.d("kano_ZTE_LOG", "FULL_WAKE_LOCK released")
            }
            wakeLock3?.let {
                if (it.isHeld) it.release()
                Log.d("kano_ZTE_LOG", "BrightWakeLock released")
            }
        }
    }
}