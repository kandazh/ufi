package com.minikano.f50_sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.minikano.f50_sms.configs.AppMeta
import com.minikano.f50_sms.utils.DeviceModelChecker
import com.minikano.f50_sms.utils.ShellKano
import com.minikano.f50_sms.utils.UniqueDeviceIDManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.system.exitProcess

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("kano_ZTE_LOG", "Boot completed broadcast received; starting services")
            AppMeta.init(context)
            UniqueDeviceIDManager.init(context)

            //check
            val isNotUFI = DeviceModelChecker.checkIsNotUFI(context)
            if (isNotUFI){
                Log.d("kano_ZTE_LOG", "Device is not a UFI/MIFI device; terminating")
                exitProcess(-999)
            }

            // Launch coroutine to call suspend functions asynchronously
            CoroutineScope(Dispatchers.Default).launch {
                UniqueDeviceIDManager.init(context)
                val isUnSupportDevice = DeviceModelChecker.checkBlackList(context)
                Log.d("kano_ZTE_LOG", "Blacklist check result: $isUnSupportDevice")

                withContext(Dispatchers.Main) {
                    if (isUnSupportDevice) {
                        // Handle unsupported device
                        Log.d("kano_ZTE_LOG", "Unsupported device detected; terminating")
                        exitProcess(-999)
                    }
                }
            }

            val startIntent = Intent(context, WebService::class.java)
            startIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startForegroundService(startIntent)
            Log.d("kano_ZTE_LOG", "Starting WebService")

            val startIntent_ADB = Intent(context, ADBService::class.java)
            startIntent_ADB.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startForegroundService(startIntent_ADB)
            Log.d("kano_ZTE_LOG", "Starting ADBService")

            // Enable network ADB, etc.
            ShellKano.runADB(context)
            Log.d("kano_ZTE_LOG", "Enabling network ADB")
        }
    }
}