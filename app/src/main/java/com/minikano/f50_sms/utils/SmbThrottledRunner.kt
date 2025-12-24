package com.minikano.f50_sms.utils

import android.content.Context
import android.os.Build
import com.minikano.f50_sms.ADBService.Companion.adbIsReady
import com.minikano.f50_sms.ADBService.Companion.isExecutedSambaMount
import com.minikano.f50_sms.utils.KanoUtils.Companion.sendShellCmd
import com.minikano.f50_sms.utils.ShellKano.Companion.executeShellFromAssetsSubfolderWithArgs
import com.minikano.f50_sms.utils.ShellKano.Companion.openSMB
import java.util.concurrent.atomic.AtomicBoolean
import jcifs.smb.SmbFile
import jcifs.context.SingletonContext
import java.io.File

object SmbThrottledRunner {
    private val running = AtomicBoolean(false)
    private val PREF_GATEWAY_IP = "gateway_ip"
    private val PREFS_NAME = "kano_ZTE_store"

    fun runOnceInThread(context: Context) {
        if (running.get()) {
            KanoLog.d("kano_ZTE_LOG", "SMB command is already running, skipping")
            return
        }
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val gatewayIP = sharedPrefs.getString(PREF_GATEWAY_IP, "192.168.0.1:445")

        KanoLog.d("kano_ZTE_LOG", "SMB command is starting, IP:${gatewayIP}")

        val host = gatewayIP?.substringBefore(":")

        running.set(true)

        Thread {
            val samba_result = sendShellCmd("cat /data/samba/etc/smb.conf | grep internal_storage")
            val advancedIsEnable =
                samba_result.done && samba_result.content.contains("internal_storage")
            var needOpenSMB = false
            if(advancedIsEnable) {
                try {
                    KanoLog.d(
                        "kano_ZTE_LOG",
                        "Starting SMB command, connecting to: \"smb://$host/internal_storage/\""
                    )

                    val ctx = SingletonContext.getInstance()
                    val smbFile = SmbFile("smb://$host/internal_storage/", ctx)

                    if (smbFile.exists()) {
                        KanoLog.d("kano_ZTE_LOG", "SMB path exists")
                        if (!isExecutedSambaMount) {
                            try {
                                val socketPath = File(context.filesDir, "kano_root_shell.sock")
                                if (!socketPath.exists()) {
                                    throw Exception("Command failed: socat-created sock not found (is Advanced feature enabled?)")
                                }
                                val result =
                                    RootShell.sendCommandToSocket(
                                        """
SRC_LIST="/sdcard/DCIM /mnt/media_rw /storage/sdcard0"
TGT_LIST="/data/SAMBA_SHARE/internal_storage /data/SAMBA_SHARE/external_storage /data/SAMBA_SHARE/sd_card"

i=1
for src in ${'$'}SRC_LIST; do
  tgt=${'$'}(echo ${'$'}TGT_LIST | cut -d' ' -f${'$'}i)
  i=${'$'}((i + 1))

  [ ! -d "${'$'}tgt" ] && mkdir -p "${'$'}tgt"

  mount | grep " ${'$'}tgt " >/dev/null 2>&1
  if [ ${'$'}? -ne 0 ]; then
      mount --bind "${'$'}src" "${'$'}tgt"
      echo "Mounted ${'$'}src -> ${'$'}tgt"
  else
      echo "${'$'}tgt already mounted"
  fi
done
                        """.trimIndent(),
                                        socketPath.absolutePath
                                    )
                                        ?: throw Exception("Please check the command input format")

                                KanoLog.d("kano_ZTE_LOG", "SMB bind-mount result: $result")
                                isExecutedSambaMount = true
                            } catch (e: Exception) {
                                KanoLog.e("kano_ZTE_LOG", "SMB bind-mount failed", e)
                            }
                        }
                    } else {
                        KanoLog.d("kano_ZTE_LOG", "SMB path does not exist")
                        needOpenSMB = true
                    }
                } catch (e: Exception) {
                    KanoLog.e("kano_ZTE_LOG", "SMB command error: ${e.message}")
                    needOpenSMB = true
                } finally {
                    running.set(false)
                    KanoLog.d("kano_ZTE_LOG", "SMB command finished")
                }
                if (needOpenSMB) {
                    openSMB(context)
                }
            } else {
                KanoLog.d("kano_ZTE_LOG", "No SMB config change detected; Advanced feature is not enabled; skipping")
                running.set(false)
            }
        }.start()
    }
}