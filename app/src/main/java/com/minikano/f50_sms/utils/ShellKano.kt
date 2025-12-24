package com.minikano.f50_sms.utils

import android.content.Context
import android.util.Log
import com.minikano.f50_sms.ADBService.Companion.adbIsReady
import com.minikano.f50_sms.modules.TAG
import com.minikano.f50_sms.utils.KanoUtils.Companion.sendShellCmd
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.w3c.dom.Document
import org.w3c.dom.NodeList
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.net.InetAddress
import java.util.concurrent.TimeUnit
import javax.xml.parsers.DocumentBuilderFactory

class ShellKano {
    companion object {
        const val PREFS_NAME = "kano_ZTE_store"

        fun runShellCommand(command: String?, escaped: Boolean = false): String? {
            val output = StringBuilder()
            try {
                var process = Runtime.getRuntime().exec(command)
                if (escaped) {
                    process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
                }
                val reader = BufferedReader(
                    InputStreamReader(process.inputStream)
                )

                var line: String?
                while ((reader.readLine().also { line = it }) != null) {
                    output.append(line).append("\n")
                }

                reader.close()
                process.waitFor()
            } catch (e: IOException) {
                e.printStackTrace()
                return null
            } catch (e: InterruptedException) {
                e.printStackTrace()
                return null
            }

            return output.toString().trim { it <= ' ' }
        }

        fun runShellCommand(command: String?, context: Context): String? {
            val output = StringBuilder()
            try {
                // Set HOME environment variable
                val env = arrayOf("HOME=${context.filesDir.absolutePath}")

                // Start process (with environment variables)
                val process = Runtime.getRuntime().exec(command, env)

                val reader = BufferedReader(
                    InputStreamReader(process.inputStream)
                )
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    output.append(line).append("\n")
                }

                reader.close()
                process.waitFor()
            } catch (e: IOException) {
                e.printStackTrace()
                return null
            } catch (e: InterruptedException) {
                e.printStackTrace()
                return null
            }

            return output.toString().trim { it <= ' ' }
        }

        /**
         * Find a UI element via adb and tap it.
         * @return 1 0 -1
         * 1 means we're already on the AT send page
         * 0 means the tap was executed successfully
         * -1 means execution failed (no matching text found)
         */
        fun parseUiDumpAndClick(targetText: String, adbPath: String, context: Context): Number {
            val cacheFile = getUiDoc(adbPath, context)

            val doc = cacheFile
            KanoLog.d("kano_ZTE_LOG", "doc read result: ${doc.getElementsByTagName("node")}")

            // Tap logic
            val nodes = doc.getElementsByTagName("node")
            for (i in 0 until nodes.length) {
                val node = nodes.item(i)
                val attrs = node.attributes
                val text = attrs.getNamedItem("text")?.nodeValue ?: ""
                KanoLog.d("kano_ZTE_LOG", "Node text: '$text'")
                if (text.contains(targetText)) {
                    val bounds = attrs.getNamedItem("bounds")?.nodeValue ?: continue
                    val regex = Regex("""\[(\d+),(\d+)\]\[(\d+),(\d+)\]""")
                    val match = regex.find(bounds) ?: continue
                    val (x1, y1, x2, y2) = match.destructured
                    val tapX = (x1.toInt() + x2.toInt()) / 2
                    val tapY = (y1.toInt() + y2.toInt()) / 2
                    val result = runShellCommand(
                        "$adbPath -s localhost shell input tap $tapX $tapY",
                        context
                    )
                        ?: throw Exception("Failed to execute input tap")
                    KanoLog.d("kano_ZTE_LOG", "input tap at: $tapX,$tapY result: ${result} ")
                    return 0
                } else if (text.contains("AT Command:")) {
                    // Already on the AT page
                    return 1
                }
            }
            return -1
        }

        /**
         * Fill the input field and send an AT command.
         */
        fun fillInputAndSend(
            inputText: String,
            adbPath: String,
            context: Context,
            resId: String,
            btnName: List<String>,
            needBack: Boolean = true,
            useClipBoard: Boolean = false
        ): String {
            val doc = getUiDoc(adbPath, context)
            val nodes = doc.getElementsByTagName("node")
            val escapedInput = inputText.replace(" ", "%s")
            // Copy text to clipboard
            KanoUtils.copyToClipboard(context, "sambaCommand", inputText)

            // Find the input field
            var inputClicked = false
            for (i in 0 until nodes.length) {
                val node = nodes.item(i)
                val attrs = node.attributes
                val clazz = attrs.getNamedItem("class")?.nodeValue ?: ""
                val bounds = attrs.getNamedItem("bounds")?.nodeValue ?: continue

                if (clazz == "android.widget.EditText") {
                    val regex = Regex("""\[(\d+),(\d+)\]\[(\d+),(\d+)\]""")
                    val match = regex.find(bounds) ?: continue
                    val (x1, y1, x2, y2) = match.destructured
                    val tapX = (x1.toInt() + x2.toInt()) / 2
                    val tapY = (y1.toInt() + y2.toInt()) / 2

                    repeat(3) {
                        runShellCommand(
                            "$adbPath -s localhost shell input tap $tapX $tapY",
                            context
                        )
                        KanoLog.d("kano_ZTE_LOG", "Tap input field at: $tapX,$tapY")
                    }

                    // Enter text
                    if (!useClipBoard) {
                        Thread.sleep(200) // Wait for the soft keyboard to appear
                        runShellCommand(
                            "$adbPath -s localhost shell input text \"$escapedInput\"",
                            context
                        )
                        KanoLog.d("kano_ZTE_LOG", "Entered text: $inputText")
                        inputClicked = true
                        if (escapedInput.length > 20) {
                            Thread.sleep(500) // Wait for input to finish
                        }
                        break
                    } else {
                        runShellCommand(
                            "$adbPath -s localhost shell input keyevent KEYCODE_PASTE",
                            context
                        )
                        KanoLog.d("kano_ZTE_LOG", "Pasted from clipboard, text: $inputText")
                        inputClicked = true
                        Thread.sleep(666) // Wait for input to finish
                        break
                    }
                }
            }

            if (!inputClicked) throw Exception("EditText input not found")

            fun getBtnAndClick(nodes_after: NodeList): String? {
                // Find the button and tap it
                for (i in 0 until nodes_after.length) {
                    val node = nodes_after.item(i)
                    val attrs = node.attributes
                    val text = attrs.getNamedItem("text")?.nodeValue ?: ""
                    val bounds = attrs.getNamedItem("bounds")?.nodeValue ?: continue

                    if (btnName.any { it.equals(text, ignoreCase = true) }) {
                        val regex = Regex("""\[(\d+),(\d+)\]\[(\d+),(\d+)\]""")
                        val match = regex.find(bounds) ?: continue
                        val (x1, y1, x2, y2) = match.destructured
                        val tapX = (x1.toInt() + x2.toInt()) / 2
                        val tapY = (y1.toInt() + y2.toInt()) / 2
                        runShellCommand(
                            "$adbPath -s localhost shell input tap $tapX $tapY",
                            context
                        )
                        KanoLog.d(
                            "kano_ZTE_LOG",
                            "Tap ${btnName.joinToString(", ")} at: $tapX,$tapY"
                        )
                        // Continue checking result
                        if (resId != "") {
                            val res = getTextFromUIByResourceId(resId, adbPath, context)
                            if (needBack) {
                                // Go back and finish
                                Thread.sleep(800) // Wait for input to finish
                                repeat(10) {
                                    runShellCommand(
                                        "$adbPath -s localhost shell input keyevent KEYCODE_BACK",
                                        context
                                    )
                                }
                            }
                            return res[0]
                        } else {
                            if (needBack) {
                                // Go back and finish
                                Thread.sleep(800) // Wait for input to finish
                                repeat(10) {
                                    runShellCommand(
                                        "$adbPath -s localhost shell input keyevent KEYCODE_BACK",
                                        context
                                    )
                                }
                            }
                            return ""
                        }
                    }
                }
                return null
            }

            var res: String? = null

            for (i in 0 until 10) {
                val nodes_after = getUiDoc(adbPath, context).getElementsByTagName("node")
                var temp = getBtnAndClick(nodes_after)
                if (temp != null) {
                    res = temp
                    break
                }
            }

            if (res != null) {
                return res as String
            }

            throw Exception("Button not found: ${btnName.joinToString(", ")}")
        }

        fun createShellScript(context: Context, fileName: String, scriptContent: String): File {
            val scriptFile = File(context.getExternalFilesDir(null), fileName)

            try {
                // If the file exists, delete the old file
                if (scriptFile.exists()) {
                    scriptFile.delete()
                }
            } catch (e: Exception) {
                KanoLog.d("kano_ZTE_LOG", "Failed to delete script: ${e.message}")
            }

            // Write content (writeText overwrites)
            scriptFile.writeText(scriptContent)

            // Set executable permission (some devices require re-setting it after deletion)
            scriptFile.setExecutable(true)

            return scriptFile
        }

        private fun getTextFromUIByResourceId(
            resId: String,
            adbPath: String,
            context: Context
        ): List<String> {
            val doc = getUiDoc(adbPath, context)
            val nodes = doc.getElementsByTagName("node")

            val resultTexts = mutableListOf<String>()

            for (i in 0 until nodes.length) {
                val node = nodes.item(i)
                val attrs = node.attributes

                val resourceId = attrs.getNamedItem("resource-id")?.nodeValue ?: continue
                if (resourceId == resId) {
                    val text = attrs.getNamedItem("text")?.nodeValue ?: ""
                    resultTexts.add(text)
                }
            }

            KanoLog.d(
                "kano_ZTE_LOG",
                "For: $resId, found ${resultTexts.size} result_text values: $resultTexts"
            )
            return resultTexts
        }

        // Get UI
        private fun getUiDoc(adbPath: String, context: Context, maxRetry: Int = 3): Document {
            if (adbPath.isEmpty()) throw Exception("adbPath is required")

            repeat(maxRetry) { attempt ->
                try {

                    // Clear old XML
                    runShellCommand("$adbPath -s localhost shell rm /sdcard/kano_ui.xml", context)
                    Thread.sleep(200)

                    // Dump current UI
                    runShellCommand(
                        "$adbPath -s localhost shell uiautomator dump /sdcard/kano_ui.xml",
                        context
                    )
                        ?: throw Exception("uiautomator dump failed")

                    Thread.sleep(300)

                    // Read XML content via cat
                    val xmlContent = runShellCommand(
                        "$adbPath -s localhost shell cat /sdcard/kano_ui.xml",
                        context
                    )
                        ?: throw Exception("Failed to cat kano_ui.xml")

                    if (!xmlContent.trim().endsWith("</hierarchy>")) {
                        KanoLog.w("kano_ZTE_LOG", "UI XML is incomplete, attempt ${attempt + 1}")
                        Thread.sleep(200)
                        return@repeat
                    }

                    // Convert to Document
                    val factory = DocumentBuilderFactory.newInstance()
                    val builder = factory.newDocumentBuilder()
                    val inputStream = xmlContent.byteInputStream()
                    return builder.parse(inputStream)
                } catch (e: Exception) {
                    KanoLog.e("kano_ZTE_LOG", "Failed to parse UI XML, attempt ${attempt + 1}: ${e.message}")
                    Thread.sleep(200)
                }
            }

            throw Exception("Unable to get a complete UI dump after multiple attempts")
        }


        fun killProcessByName(processKeyword: String) {
            try {
                val psProcess = ProcessBuilder("ps").start()
                val output = psProcess.inputStream.bufferedReader().readText()

                val lines = output.lines()
                for (line in lines) {
                    if (line.contains(processKeyword)) {
                        val tokens = line.trim().split(Regex("\\s+"))
                        if (tokens.size > 1) {
                            val pid = tokens[1]
                            KanoLog.w("kano_ZTE_LOG", "Matched process: $line, preparing to kill -9 $pid")
                            try {
                                ProcessBuilder("kill", "-9", pid).start().waitFor()
                                KanoLog.w("kano_ZTE_LOG", "Killed -9 $pid")
                            } catch (e: Exception) {
                                KanoLog.e("kano_ZTE_LOG", "kill -9 $pid failed: ${e.message}")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                KanoLog.e("kano_ZTE_LOG", "killProcessByName failed: ${e.message}")
            }
        }

        fun executeShellFromAssetsSubfolderWithArgs(
            context: Context,
            assetSubPath: String,
            vararg args: String,
            timeoutMs: Long = 20000,  // Default: wait up to 20 seconds
            onTimeout: (() -> Unit)? = null  // Timeout callback
        ): String? {
            return try {
                val assetManager = context.assets
                val inputStream = assetManager.open(assetSubPath)
                val fileName = File(assetSubPath).name
                val outFile = File(context.filesDir, fileName)

                if (!outFile.exists()) {
                    inputStream.use { input ->
                        FileOutputStream(outFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    KanoLog.d("kano_ZTE_LOG", "${outFile} file copy completed")
                } else {
                    KanoLog.d("kano_ZTE_LOG", "${outFile} already exists, no need to copy")
                }

                outFile.setExecutable(true)

                val command = ArrayList<String>().apply {
                    add(outFile.absolutePath)
                    addAll(args)
                }

                KanoLog.d("kano_ZTE_LOG", "Executing command: ${command.joinToString(" ")}")

                val process = ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .apply {
                        environment()["HOME"] = context.filesDir.absolutePath
                    }
                    .start()

                // Start a thread to read output
                val outputBuilder = StringBuilder()
                val readerThread = Thread {
                    try {
                        process.inputStream.bufferedReader().forEachLine {
                            outputBuilder.appendLine(it)
                        }
                    } catch (e: Exception) {
                        KanoLog.w("kano_ZTE_LOG", "Error reading process output: ${e.message}")
                    }
                }
                readerThread.start()

                // Wait up to timeoutMs milliseconds
                val finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)

                if (!finished) {
                    KanoLog.w("kano_ZTE_LOG", "Execution timed out; force-destroying process")
                    process.destroy()

                    // Invoke callback
                    onTimeout?.invoke()
                }

                readerThread.join(100) // Wait up to 100ms for output reading to finish
                outputBuilder.toString().trim()

            } catch (e: Exception) {
                KanoLog.e("kano_ZTE_LOG", "Execution error: ${e.message}")
                null
            }
        }

        // Ensure ADB is alive
        fun ensureAdbAlive(context: Context): Boolean {
            try {
                val adbPath = "shell/adb"

                // First check
                var result = executeShellFromAssetsSubfolderWithArgs(context, adbPath, "devices")
                KanoLog.d("kano_ZTE_LOG", "adb devices output: $result")

                if (result?.contains("localhost:5555\tdevice") == true) {
                    KanoLog.d("kano_ZTE_LOG", "ADB is alive; no need to start")
                    return true
                }

                KanoLog.w("kano_ZTE_LOG", "No ADB device or ADB has exited; trying to start")

                // Restart ADB server
                executeShellFromAssetsSubfolderWithArgs(context, adbPath, "kill-server")
                Thread.sleep(1000)
                executeShellFromAssetsSubfolderWithArgs(context, adbPath, "connect", "localhost")

                // Wait up to 10 seconds for the device to become "device"
                val maxWaitMs = 10_000
                val interval = 500
                var waited = 0

                while (waited < maxWaitMs) {
                    result = executeShellFromAssetsSubfolderWithArgs(context, adbPath, "devices")
                    KanoLog.d("kano_ZTE_LOG", "Waiting for ADB to start: $result")
                    if (result?.contains("localhost:5555\tdevice") == true) {
                        KanoLog.d("kano_ZTE_LOG", "ADB connected")
                        return true
                    }
                    Thread.sleep(interval.toLong())
                    waited += interval
                }

                KanoLog.e("kano_ZTE_LOG", "Timed out waiting for ADB device")
                return false
            } catch (e: Exception) {
                KanoLog.e("kano_ZTE_LOG", "Failed to check/start ADB: ${e.message}")
                return false
            }
        }

        fun executeShellFromAssetsSubfolder(
            context: Context,
            assetSubPath: String,
            outFileName: String = "tmp_script.sh"
        ): String? {
            try {
                val assetManager = context.assets

                val inputStream = assetManager.open(assetSubPath)
                val outFile = File(context.filesDir, outFileName)

                if (!outFile.exists()) {
                    inputStream.use { input ->
                        FileOutputStream(outFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }

                outFile.setExecutable(true)

                val process = Runtime.getRuntime().exec(outFile.absolutePath)

                val reader = process.inputStream.bufferedReader()
                val output = reader.readText()

                process.waitFor()

                return output
            } catch (e: Exception) {
                KanoLog.d("kano_ZTE_LOG", "Execution error: ${e.message}")
                e.printStackTrace()
            }

            return null
        }


        fun runADB(context: Context) {
            // Network ADB
            //adb setprop service.adb.tcp.port 5555
            Thread {
                try {
                    runShellCommand("/system/bin/setprop persist.service.adb.tcp.port 5555")
                    runShellCommand("/system/bin/setprop service.adb.tcp.port 5555")
                    Log.d("kano_ZTE_LOG", "Network ADB debug props set successfully")
                } catch (e: Exception) {
                    try {
                        runShellCommand("/system/bin/setprop service.adb.tcp.port 5555")
                        runShellCommand("/system/bin/setprop persist.service.adb.tcp.port 5555")
                        Log.d("kano_ZTE_LOG", "Network ADB debug props set successfully")
                    } catch (e: Exception) {
                        Log.d("kano_ZTE_LOG", "Failed to set network ADB debug props: ${e.message}")
                    }
                }
                Thread.sleep(500)
                try {
                    Log.d("kano_ZTE_LOG", "Starting adb_ip activation flow")

                    val sharedPrefs =
                        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

                    val ADB_IP_ENABLED = sharedPrefs.getString("ADB_IP_ENABLED", "") ?: null

                    Log.d("kano_ZTE_LOG", "ADB_IP_ENABLED:${ADB_IP_ENABLED}")

                    if (ADB_IP_ENABLED == "true") {
                        val ADB_IP =
                            sharedPrefs.getString("gateway_ip", "")?.substringBefore(":")
                                ?: throw Exception("Missing ADMIN_IP")
                        val ADMIN_PWD =
                            sharedPrefs.getString("ADMIN_PWD","Wa@9w+YWRtaW4=") ?: "Wa@9w+YWRtaW4="

                        Log.d(
                            "kano_ZTE_LOG", "Loaded network ADB config: ADB_IP:${
                                ADB_IP
                            } ADMIN_PWD:${
                                ADMIN_PWD.take(2)
                            }"
                        )

                        suspend fun waitUntilReachable(ip: String, timeoutSeconds: Int = 30): Boolean {
                            val intervalMillis = 300L
                            val maxAttempts = timeoutSeconds * 1000 / intervalMillis
                            val req = KanoGoformRequest("http://$ip:8080")

                            repeat(maxAttempts.toInt()) {
                                try {
                                    val result = req.getData(
                                        mapOf(
                                            "multi_data" to "1",
                                            "cmd" to "loginfo"
                                        )
                                    )
                                    KanoLog.d("kano_ZTE_LOG", "Attempting connection: $result")
                                    if (result != null) {
                                        KanoLog.d("kano_ZTE_LOG", "http://$ip:8080 is reachable")
                                        return true
                                    }
                                } catch (e: Exception) {
                                    KanoLog.d("kano_ZTE_LOG", "Connection error: ${e.message}")
                                }
                                delay(intervalMillis)
                            }

                            KanoLog.e("kano_ZTE_LOG", "http://$ip:8080 not reachable within $timeoutSeconds seconds")
                            return false
                        }

                        try {
                            runBlocking {
                                val reachable = waitUntilReachable(ADB_IP, 30)
                                if (!reachable) {
                                    KanoLog.e("kano_ZTE_LOG", "Official web service unreachable; aborting ADB auto-start")
                                    return@runBlocking
                                }

                                val req = KanoGoformRequest("http://$ADB_IP:8080")
                                val cookie = req.login(ADMIN_PWD)
                                if (cookie != null) {
                                    val result1 = req.postData(
                                        cookie, mapOf(
                                            "goformId" to "USB_PORT_SETTING",
                                            "usb_port_switch" to "0"
                                        )
                                    )
                                    KanoLog.d("kano_ZTE_LOG", "Disable ADBD result: $result1")
                                    delay(500)
                                    val result2 = req.postData(
                                        cookie, mapOf(
                                            "goformId" to "USB_PORT_SETTING",
                                            "usb_port_switch" to "1"
                                        )
                                    )
                                    KanoLog.d("kano_ZTE_LOG", "Enable ADBD result: $result2")

                                    val result3 = req.postData(
                                        cookie, mapOf(
                                            "goformId" to "SetUpgAutoSetting",
                                            "UpgMode" to "0",
                                            "UpgIntervalDay" to "114514",
                                            "UpgRoamPermission" to "0"
                                        )
                                    )

                                    KanoLog.d("kano_ZTE_LOG", "Disable FOTA result: $result3")

                                    val samba_result = sendShellCmd("cat /data/samba/etc/smb.conf | grep internal_storage")

                                    if(samba_result.done && samba_result.content.contains("internal_storage")){
                                        val result = req.postData(
                                            cookie, mapOf(
                                                "goformId" to "SAMBA_SETTING",
                                                "samba_switch" to "1",
                                            )
                                        )
                                        KanoLog.d("kano_ZTE_LOG", "Enable samba result: $result")
                                    }

                                    req.logout(cookie)
                                    if (result1?.getString("result") == "success" && result2?.getString("result") == "success") {
                                        KanoLog.d("kano_ZTE_LOG", "ADB_WIFI auto-start succeeded")
                                    }
                                }
                            }

                        } catch (e: Exception) {
                            KanoLog.e("kano_ZTE_LOG", "ADB_WIFI error: ${e.message}")
                        }
                    } else {
                        Log.d("kano_ZTE_LOG", "ADB_WIFI auto-start not needed")
                    }
                } catch (e: Exception) {
                    Log.d("kano_ZTE_LOG", "ADB_WIFI auto-start error: ${e.message}")
                    e.printStackTrace()
                }

                Thread.sleep(5000)
                executeShellFromAssetsSubfolderWithArgs(
                    context, "shell/adb", "start-server"
                )
                ensureAdbAlive(context)
            }.start()
        }

        fun openSMB (context: Context){
            // If the samba switch is off, bring it up immediately
            try {
                val open_command = "settings put global samba_enable 1"
                val socketPath = File(context.filesDir, "kano_root_shell.sock")

                if (adbIsReady) {
                    val outFile_adb = KanoUtils.copyFileToFilesDir(context, "shell/adb")
                    if(outFile_adb != null){
                        outFile_adb.setExecutable(true)
                        KanoLog.d("kano_ZTE_LOG", "Samba is off; trying to enable (via adb)...")
                        val res = runShellCommand(
                            "${outFile_adb.absolutePath} -s localhost shell $open_command",
                            context
                        )
                        KanoLog.d("kano_ZTE_LOG", "Enable samba via adb result: $res")
                    }
                }

                if (socketPath.exists()) {
                    KanoLog.d("kano_ZTE_LOG", "Samba is off; trying to enable (via root)...")
                    val res =  RootShell.sendCommandToSocket(open_command.trimIndent(),
                        socketPath.absolutePath,
                        2000
                    )
                    KanoLog.d("kano_ZTE_LOG", "Enable samba via root result: $res")
                }
            } catch (e: Exception) {
                KanoLog.e("kano_ZTE_LOG", "Failed to enable SMB", e)
            }
        }
    }
}