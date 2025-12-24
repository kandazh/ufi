package com.minikano.f50_sms.utils

import android.content.Context
import android.os.Build
import android.util.Log
import com.minikano.f50_sms.utils.KanoReport.Companion.reportToServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.core.content.edit

object DeviceModelChecker {
    private var isUnSupportDevice = false
    private val devicesBlackList = listOf(
        "MU5352"
    )
    private val PREFS_NAME = "kano_ZTE_store"
    private val frimwareWhiteList = listOf(
        "MU5352_DSV1.0.0B07",
        "MU5352_DSV1.0.0B05",
        "MU5352_DSV1.0.0B03",
        "MU300",
        "F50",
        "U30Air",
    )

    suspend fun checkBlackList(context:Context): Boolean {
        Log.d("kano_ZTE_LOG_devcheck", "Checking blacklisted devices...")
        val model = Build.MODEL.trim()
        val firmwareVersion = Build.DISPLAY

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isDeviceWhiteList = prefs.getString("is_device_white_list", null)
        if (!isDeviceWhiteList.isNullOrEmpty()) {
            if(isDeviceWhiteList == "kano") {
                Log.d("kano_ZTE_LOG_devcheck", "Remote whitelist (persisted): always allow")
                return false
            } else {
                Log.d("kano_ZTE_LOG_devcheck", "Invalid whitelist marker; clearing and continuing")
                prefs.edit(commit = true) { remove("is_device_white_list") }
            }
        }

        val uuid = UniqueDeviceIDManager.getUUID()
        Log.d("kano_ZTE_LOG_devcheck", "Current device UUID: $uuid")

        try {
            if (uuid != null) {
                val res = KanoReport.getRemoteDeviceRegisterItem(uuid)
                Log.d("kano_ZTE_LOG_devcheck", "Remote response: $res")
                if (res != null && res.isWhiteList) {
                    Log.d("kano_ZTE_LOG_devcheck", "Remote whitelist: always allow")
                    prefs.edit(commit = true) { putString("is_device_white_list", "kano") }
                    return false
                }
                // Report info
                try{
                    CoroutineScope(Dispatchers.Main).launch {
                        reportToServer()
                    }
                } catch (_:Exception){}
            }
        } catch (e: Exception) {
            Log.e("kano_ZTE_LOG_devcheck", "Failed to fetch remote device registration info", e)
        }

        devicesBlackList.forEach {
            Log.d("kano_ZTE_LOG_devcheck", "$it == $model ?")
            if (it.trim().contains(model)) {
                isUnSupportDevice = true
            }
        }
        frimwareWhiteList.forEach {
            if (firmwareVersion.contains(it.trim())) {
                Log.d("kano_ZTE_LOG_devcheck", "Whitelisted firmware detected; allowing")
                isUnSupportDevice = false
            }
        }
        return isUnSupportDevice
    }

    fun checkIsNotUFI(context: Context):Boolean{
        val isUFI_0 = KanoUtils.isAppInstalled(context,"com.zte.web")
        val isUFI = ShellKano.runShellCommand("pm list package")
        Log.d("kano_ZTE_LOG_devcheck", "isUFI_0: ${isUFI_0}, has com.zte.web?: ${isUFI?.contains("com.zte.web")} ")
        return !(isUFI != null && isUFI.contains("com.zte.web")) || !isUFI_0
    }
}