package com.minikano.f50_sms

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.minikano.f50_sms.utils.KanoLog
import com.minikano.f50_sms.utils.KanoReport
import com.minikano.f50_sms.utils.KanoReport.Companion.reportToServer
import com.minikano.f50_sms.utils.KanoUtils
import com.minikano.f50_sms.utils.RootShell
import com.minikano.f50_sms.utils.ShellKano
import com.minikano.f50_sms.utils.ShellKano.Companion.executeShellFromAssetsSubfolderWithArgs
import com.minikano.f50_sms.utils.ShellKano.Companion.killProcessByName
import com.minikano.f50_sms.utils.SmbThrottledRunner
import com.minikano.f50_sms.utils.SmsPoll
import com.minikano.f50_sms.utils.TaskSchedulerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.Executors
import kotlin.concurrent.thread

class ADBService : Service() {
    private lateinit var runnable: Runnable
    private lateinit var handlerThread: HandlerThread
    private lateinit var handler: Handler
    private val adbExecutor = Executors.newSingleThreadExecutor()
    private val iperfExecutor = Executors.newSingleThreadExecutor()
    private var disableFOTATimes = 3

    companion object {
        @Volatile
        var adbIsReady: Boolean = false
        var isExecutedDisabledFOTA = false
        var isExecutingDisabledFOTA = false
        var isExecutedSambaMount = false
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(1234599, createNotification())

        handlerThread = HandlerThread("KanoBackgroundHandler")
        handlerThread.start()
        handler = Handler(handlerThread.looper)
        // Run tasks sequentially
        handler.post {
            resetFilesFromAssets(applicationContext)

            // Continue after file copy completes
            startAdbKeepAliveTask(applicationContext)
            startIperfTask(applicationContext)
            val executor = Executors.newFixedThreadPool(2)
            executor.execute(runnableSMS)
            executor.execute(runnableSMB)
        }

        // Start scheduled tasks
        TaskSchedulerManager.init(applicationContext)

        // Report status
        try{
            CoroutineScope(Dispatchers.Main).launch {
                reportToServer()
            }
        } catch (_:Exception){}

        return START_STICKY
    }

    private fun resetFilesFromAssets(context: Context) {
        val filesDir = context.filesDir

        // Delete all files
        filesDir.listFiles()?.forEach { file ->
            if (file.isFile) {
                file.delete()
            }
        }

        // Copy all files from assets
        try {
            KanoUtils.copyAssetsRecursively(context, "shell", context.filesDir)
            Log.d("kano_ZTE_LOG", "Initialized files directory")
        } catch (e: Exception) {
            Log.e("kano_ZTE_LOG", "Failed to initialize files directory: ${e.message}")
        }
    }

    private val runnableSMS = object : Runnable {
        override fun run() {
            val sharedPrefs = getSharedPreferences("kano_ZTE_store", Context.MODE_PRIVATE)
            if (sharedPrefs.getString("kano_sms_forward_enabled", "0") == "1") {
                try {
                    SmsPoll.checkNewSmsAndSend(applicationContext)
                } catch (e: Exception) {
                    KanoLog.e("kano_ZTE_LOG", "Error while reading SMS", e)
                }
            }
            handler.postDelayed(this, 5000)
        }
    }

    private val runnableSMB = object : Runnable {
        override fun run() {
            try {
                KanoLog.d("kano_ZTE_LOG", "Activating built-in SMB script...")
                SmbThrottledRunner.runOnceInThread(applicationContext)
            } catch (e: Exception) {
                KanoLog.e("kano_ZTE_LOG", "Failed to activate built-in SMB script")
            }
            handler.postDelayed(this, 20_000)
        }
    }

    private fun startIperfTask(context: Context){
        iperfExecutor.execute {
            try{
                KanoLog.d("kano_ZTE_LOG", "Starting iperf3...")
                killProcessByName("iperf3")
                val result =
                    executeShellFromAssetsSubfolderWithArgs(
                        applicationContext,
                        "shell/iperf3",
                        "-s",
                        "-D",
                    )
                if (result != null) {
                    KanoLog.d("kano_ZTE_LOG", "iperf3 started")
                } else {
                    KanoLog.e("kano_ZTE_LOG", "iperf3 failed to start (user mode)")
                }
            }catch (e:Exception){
                KanoLog.e("kano_ZTE_LOG", "iperf3 command failed",e)
            }
        }
    }

    private fun startAdbKeepAliveTask(context: Context) {
        adbExecutor.execute {
            try {
                val adbPath = "shell/adb"

                while (!Thread.currentThread().isInterrupted) {
                    KanoLog.d("kano_ZTE_LOG", "Keeping ADB service alive...")

                    var result =
                        executeShellFromAssetsSubfolderWithArgs(context, adbPath, "devices") {
                            ShellKano.killProcessByName("adb")
                        }

                    if (result?.contains("localhost:5555\tdevice") == true) {
                        KanoLog.d("kano_ZTE_LOG", "ADB is alive; no need to start")
                        adbIsReady = true
                        if(!isExecutedDisabledFOTA) {
                            disableFOTATimes --
                            if(disableFOTATimes <= 0){
                                KanoLog.d("kano_ZTE_LOG", "Tried 3 times to disable FOTA via adb; forcing isExecutingDisabledFOTA = true")
                                isExecutingDisabledFOTA = true
                            }
                            val res = KanoUtils.disableFota(applicationContext)
                            if(res){
                                KanoLog.d("kano_ZTE_LOG", "Disabled FOTA via adb")
                            }
                            isExecutedDisabledFOTA = true
                        }
                    } else {
                        KanoLog.w("kano_ZTE_LOG", "ADB has no device or exited; trying to start")
                        adbIsReady = false

                        ShellKano.killProcessByName("adb")
                        Thread.sleep(1000)

                        executeShellFromAssetsSubfolderWithArgs(
                            context,
                            adbPath,
                            "connect",
                            "localhost"
                        ) {
                            ShellKano.killProcessByName("adb")
                        }

                        val maxWaitMs = 5_000
                        val interval = 500
                        var waited = 0

                        while (waited < maxWaitMs) {
                            result = executeShellFromAssetsSubfolderWithArgs(
                                context,
                                adbPath,
                                "devices"
                            ) {
                                ShellKano.killProcessByName("adb")
                            }

                            if (result?.contains("localhost:5555\tdevice") == true) {
                                KanoLog.d("kano_ZTE_LOG", "ADB connected successfully: $result")
                                adbIsReady = true
                                break
                            } else {
                                KanoLog.d("kano_ZTE_LOG", "ADB not connected: $result")
                            }

                            Thread.sleep(interval.toLong())
                            waited += interval
                        }
                    }
                    // Poll every 11 seconds
                    Thread.sleep(11_000)
                }
            } catch (e: Exception) {
                KanoLog.e("kano_ZTE_LOG", "ADB keep-alive thread error", e)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handlerThread.quitSafely()
        handler.removeCallbacks(runnable)
        TaskSchedulerManager.scheduler?.stop()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotification(): Notification {
        val channelId = "kano_adb_service"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "adb_service background service",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("adb_service running in background")
            .setContentText("Running adb_service scheduled tasks")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()
    }
}