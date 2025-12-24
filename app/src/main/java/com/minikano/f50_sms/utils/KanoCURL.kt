package com.minikano.f50_sms.utils

import android.content.Context
import java.util.concurrent.atomic.AtomicBoolean
class KanoCURL(private val context: Context) {
    // Prevent duplicate requests
    private val isSending = AtomicBoolean(false)

    fun send(command:String) {
        // If already running, return immediately
        if (!isSending.compareAndSet(false, true)) {
            KanoLog.w("kano_ZTE_LOG_Curl", "curl request is in progress; ignoring duplicate")
            return
        }
        Thread {
            try {
                KanoLog.w("kano_ZTE_LOG_Curl", "Executing curl command: $command")
                val args = KanoUtils.parseShellArgs(command.replaceFirst("curl", ""))
                val result = ShellKano.executeShellFromAssetsSubfolderWithArgs(
                    context,
                    "shell/curl",
                    *args.toTypedArray(),
                    timeoutMs = 10000
                ) ?: throw Exception("runShellCommand returned null")
                KanoLog.w("kano_ZTE_LOG_Curl", "curl command result: $result")
            } catch (e: Exception) {
                KanoLog.e("kano_ZTE_LOG_Curl", "curl request failed: ${e.message}", e)
            } finally {
                isSending.set(false)
            }
        }.start()
    }
}