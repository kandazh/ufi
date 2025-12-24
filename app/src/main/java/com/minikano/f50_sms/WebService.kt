package com.minikano.f50_sms

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.minikano.f50_sms.configs.AppMeta
import com.minikano.f50_sms.utils.KanoLog
import com.minikano.f50_sms.utils.KanoUtils
import com.minikano.f50_sms.utils.UniqueDeviceIDManager
import com.minikano.f50_sms.utils.WakeLock
import kotlin.concurrent.thread

class WebService : Service() {
    private var webServer: KanoWebServer? = null
    private val port = 2333
    private val SERVER_INTENT = "com.minikano.f50_sms.SERVER_STATUS_CHANGED"
    private val UI_INTENT = "com.minikano.f50_sms.UI_STATUS_CHANGED"

    @Volatile
    private var allowAutoStart = true
    private var allowAutoReStart = true

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action
            Log.d("kano_ZTE_LOG", "WebService received Intent")
            if (action == UI_INTENT) {
                val shouldStart = intent.getBooleanExtra("status", false)
                if (shouldStart) {
                    startWebServer()
                } else {
                    stopWebServer()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        AppMeta.init(this)
        // Initialize once at application/activity startup:
        UniqueDeviceIDManager.init(this)
        startForegroundNotification()

        // Detect IP changes to adapt to user's subnet updates
        KanoUtils.adaptIPChange(applicationContext)

        val prefs = getSharedPreferences("kano_ZTE_store", Context.MODE_PRIVATE)
        val needWakeLock = prefs.getString("wakeLock", "lock") ?: "lock"
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if(needWakeLock != "lock") {
            KanoLog.d("kano_ZTE_LOG","Wake lock not needed; releasing...")
            WakeLock.releaseWakeLock()
        } else {
            KanoLog.d("kano_ZTE_LOG","Wake lock needed; acquiring...")
            WakeLock.execWakeLock(pm)
        }

        // Register broadcast receiver
        registerReceiver(statusReceiver, IntentFilter(UI_INTENT), Context.RECEIVER_EXPORTED)
        startForeground(114514, createNotification())

        allowAutoReStart = true
        startWebServer()

        Log.d("kano_ZTE_LOG", "WebService Init Success!")
    }

    private fun startWebServer() {
        val prefs = getSharedPreferences("kano_ZTE_store", Context.MODE_PRIVATE)
        Thread {
            val currentIp = prefs.getString("gateway_ip", "192.168.0.1:8080") ?: "192.168.0.1:8080"
            allowAutoStart = true
            try {
                Log.d("kano_ZTE_LOG", "Starting web service, bind address: http://0.0.0.0:$port")
                webServer = KanoWebServer(applicationContext, 2333, currentIp)
                webServer?.start()
                sendStickyBroadcast(Intent(SERVER_INTENT).putExtra("status", true))
                Log.d("kano_ZTE_LOG", "Service started, address: http://0.0.0.0:$port")
            } catch (fallbackEx: Exception) {
                Log.e("kano_ZTE_LOG", "Service failed to start: ${fallbackEx.message}")
                sendStickyBroadcast(Intent(SERVER_INTENT).putExtra("status", false))
            }
        }.start()
    }

    private fun stopWebServer() {
        allowAutoStart = false  // Disable auto-retry
        allowAutoReStart = false  // Disable auto-restart

        thread { webServer?.stop() }
        sendStickyBroadcast(Intent(SERVER_INTENT).putExtra("status", false))
        Log.d("kano_ZTE_LOG", "Web server stopped")
    }

    override fun onDestroy() {
        super.onDestroy()
        stopWebServer()
    }

    private fun createNotification(): Notification {
        val channelId = "web_server_channel"
        val channel = NotificationChannel(
            channelId, "Web Server", NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

        val builder =
            NotificationCompat.Builder(this, channelId).setContentTitle("ZTE Tools Web Server")
                .setContentText("Service is running in the background")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setOngoing(true)

        return builder.build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundNotification() {
        val channelId = "running_service"
        val channelName = "Server status"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan =
                NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_HIGH)
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(chan)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground).setOngoing(true).build()

        startForeground(1, notification)
        Log.d("kano_ZTE_LOG", "Notification created")
    }
}