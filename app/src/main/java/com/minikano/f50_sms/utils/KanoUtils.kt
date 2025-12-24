package com.minikano.f50_sms.utils

import android.app.usage.NetworkStatsManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.BatteryManager
import android.os.StatFs
import android.util.Log
import android.widget.Toast
import com.minikano.f50_sms.ADBService.Companion.isExecutingDisabledFOTA
import com.minikano.f50_sms.modules.deviceInfo.MyStorageInfo
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Calendar
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import androidx.core.content.edit

class KanoUtils {
    companion object {
        fun HmacSignature(secret: String, data: String): String {
            val hmacMd5Bytes = hmac("HmacMD5", secret, data)
            val mid = hmacMd5Bytes.size / 2
            val part1 = hmacMd5Bytes.sliceArray(0 until mid)
            val part2 = hmacMd5Bytes.sliceArray(mid until hmacMd5Bytes.size)
            val sha1 = sha256(part1)
            val sha2 = sha256(part2)
            val combined = sha1 + sha2
            val finalHash = sha256(combined)
            return finalHash.joinToString("") { "%02x".format(it) }
        }

        fun hmac(algorithm: String, key: String, data: String): ByteArray {
            val mac = Mac.getInstance(algorithm)
            val secretKeySpec = SecretKeySpec(key.toByteArray(), algorithm)
            mac.init(secretKeySpec)
            return mac.doFinal(data.toByteArray())
        }

        fun sha256(data: ByteArray): ByteArray {
            val digest = MessageDigest.getInstance("SHA-256")
            return digest.digest(data)
        }

        fun sha256Hex(input: String): String {
            val bytes = input.toByteArray(Charsets.UTF_8)
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
            return digest.joinToString("") { "%02x".format(it) }
        }

        // Get battery percentage
        fun getBatteryPercentage(context: Context): Int {
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus = context.registerReceiver(null, filter) ?: return -1

            val level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1)

            return ((level / scale.toFloat()) * 100).toInt()
        }

        private var lastStorageInfo: MyStorageInfo? = null
        private var lastStorageUpdateTime: Long = 0

        fun getCachedRemovableStorageInfo(context: Context): MyStorageInfo? {
            val now = System.currentTimeMillis()
            if (lastStorageInfo == null || now - lastStorageUpdateTime > 10_000) {
                val dirs = context.getExternalFilesDirs(null)
                for (file in dirs) {
                    val path = file?.absolutePath ?: continue
                    if (!path.contains("/storage/emulated/0")) {
                        val statFs = StatFs(path)
                        val total = statFs.blockSizeLong * statFs.blockCountLong
                        val available = statFs.blockSizeLong * statFs.availableBlocksLong

                        lastStorageInfo = MyStorageInfo(path, total, available)
                        lastStorageUpdateTime = now
                        break
                    }
                }
            }
            return lastStorageInfo
        }

        fun getStartOfDayMillis(): Long {
            val cal = Calendar.getInstance()
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            return cal.timeInMillis
        }

        fun getTodayDataUsage(
            context: Context,
        ): Long {
            val networkStatsManager =
                context.getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager

            val startTime = getStartOfDayMillis()
            val endTime = System.currentTimeMillis()

            var totalBytes = 0L

            try {
                val summary = networkStatsManager.querySummaryForDevice(
                    ConnectivityManager.TYPE_MOBILE, null, startTime, endTime
                )
                totalBytes = summary.rxBytes + summary.txBytes
            } catch (e: Exception) {
                e.printStackTrace()
            }

            return totalBytes
        }

        // Parse URL-encoded request body
        fun parseUrlEncoded(data: String): Map<String, String> {
            val params = mutableMapOf<String, String>()
            val pairs = data.split("&")

            for (pair in pairs) {
                val keyValue = pair.split("=")
                if (keyValue.size == 2) {
                    val key = keyValue[0]
                    val value = keyValue[1]
                    params[key] = java.net.URLDecoder.decode(value, Charsets.UTF_8.name())  // Decode
                }
            }

            return params
        }


    // Parse /proc/meminfo
        fun parseMeminfo(meminfo: String): Float {
            val memMap = mutableMapOf<String, Long>()

            meminfo.lines().forEach { line ->
                val parts = line.split(Regex("\\s+"))
                if (parts.size >= 2) {
                    val key = parts[0].removeSuffix(":")
                    val value = parts[1].toLongOrNull() ?: return@forEach
                    memMap[key] = value
                }
            }

            val total = memMap["MemTotal"] ?: return 0f
            val free = memMap["MemFree"] ?: 0
            val cached = memMap["Cached"] ?: 0
            val buffers = memMap["Buffers"] ?: 0

            val used = total - free - cached - buffers
            return used.toFloat() / total
        }

        fun parseCpuStat(raw: String): Pair<Long, Long>? {
            val line = raw.lines().firstOrNull { it.startsWith("cpu ") } ?: return null
            val parts = line.trim().split(Regex("\\s+"))
            if (parts.size < 8) return null

            val user = parts[1].toLongOrNull() ?: return null
            val nice = parts[2].toLongOrNull() ?: return null
            val system = parts[3].toLongOrNull() ?: return null
            val idle = parts[4].toLongOrNull() ?: return null
            val iowait = parts[5].toLongOrNull() ?: 0
            val irq = parts[6].toLongOrNull() ?: 0
            val softirq = parts[7].toLongOrNull() ?: 0

            val total = user + nice + system + idle + iowait + irq + softirq
            val idleAll = idle + iowait
            return Pair(total, idleAll)
        }

        fun getChunkCount(param: String?): Int {
            val default = 4
            val max = 1024

            return param?.toIntOrNull()?.let {
                when {
                    it <= 0 -> default
                    it > max -> max
                    else -> it
                }
            } ?: default
        }

        fun copyToClipboard(context: Context, label: String, text: String) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText(label, text)
            clipboard.setPrimaryClip(clip)
        }

        fun copyFileToFilesDir(
            context: Context,
            path: String,
            skipIfExists: Boolean = true
        ): File? {
            val assetManager = context.assets
            val fileName = File(path).name
            val outFile = File(context.filesDir, fileName)

            // If skipIfExists is enabled and the file already exists, return it to avoid interfering with executable usage.
            if (skipIfExists && outFile.exists()) {
                KanoLog.d("kano_ZTE_LOG", "File already exists, skipping copy: ${outFile.absolutePath}")
                return outFile
            }

            val input = try {
                assetManager.open(path)
            } catch (e: Exception) {
                KanoLog.e("kano_ZTE_LOG", "Asset file not found: $path")
                return null
            }

            return try {
                KanoLog.d(
                    "kano_ZTE_LOG",
                    "Copying $fileName to ${context.filesDir} (skipIfExists: $skipIfExists)"
                )
                input.use { ins ->
                    FileOutputStream(outFile, skipIfExists).use { out ->
                        ins.copyTo(out)
                    }
                }
                KanoLog.d("kano_ZTE_LOG", "Copied $fileName successfully -> ${outFile.absolutePath}")
                outFile
            } catch (e: Exception) {
                KanoLog.e("kano_ZTE_LOG", "Failed to copy $fileName: ${e.message}")
                null
            }
        }

        fun parseShellArgs(command: String): List<String> {
            val matcher = Regex("""(["'])(.*?)(?<!\\)\1|(\S+)""") // Handles single-quoted/double-quoted/unquoted args
            return matcher.findAll(command).map {
                val quoted = it.groups[2]?.value
                val plain = it.groups[3]?.value
                when {
                    quoted != null -> quoted
                    plain != null -> plain.replace("\\", "")
                    else -> ""
                }
            }.toList()
        }

        fun isAppInstalled(context: Context, packageName: String): Boolean {
            return try {
                context.packageManager.getPackageInfo(packageName, 0)
                true
            } catch (e: PackageManager.NameNotFoundException) {
                false
            }
        }

        fun adaptIPChange(
            context: Context,
            userTouched: Boolean = false,
            onIpChanged: ((String) -> Unit)? = null
        ) {
            val prefs = context.getSharedPreferences("kano_ZTE_store", Context.MODE_PRIVATE)
            val ip_add = prefs.getString("gateway_ip", null)
            val need_auto_ip = prefs.getString("auto_ip_enabled", true.toString())
            val currentIp = IPManager.getHotspotGatewayIp("8080")

            if ((ip_add != null && need_auto_ip == "true") || userTouched) {
                KanoLog.d("kano_ZTE_LOG", "Auto-detected gateway IP: $currentIp")
                if (currentIp == null) {
                    KanoLog.d("kano_ZTE_LOG", "Failed to auto-detect gateway IP")
                    Toast.makeText(context, "Failed to auto-detect gateway IP...", Toast.LENGTH_SHORT).show()
                    return
                }
                if ((currentIp != ip_add) || userTouched) {
                    if (userTouched) {
                        KanoLog.d("kano_ZTE_LOG", "User triggered gateway IP auto-detection")
                        Toast.makeText(context, "Auto-detecting gateway IP...", Toast.LENGTH_SHORT).show()
                    } else {
                        KanoLog.d(
                            "kano_ZTE_LOG",
                            "Local gateway IP changed; updating gateway IP to: $currentIp"
                        )
                        Toast.makeText(
                            context,
                            "Local gateway IP changed; updated gateway IP to: $currentIp",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    prefs.edit(commit = true) { putString("gateway_ip", currentIp) }
                    if (currentIp != null) {
                        onIpChanged?.invoke(currentIp)
                    } // Notify Compose to update UI
                }
            } else if (need_auto_ip == "true") {
                // Likely first launch
                prefs.edit(commit = true) { putString("gateway_ip", currentIp) }
                KanoLog.d("kano_ZTE_LOG", "Likely first launch; setting gateway IP to: $currentIp")
            }
        }

        private fun isADBEnabled(context: Context): Boolean {
            return try {
                runBlocking {
                    val sharedPrefs =
                        context.getSharedPreferences("kano_ZTE_store", Context.MODE_PRIVATE)
                    val ADB_IP =
                        sharedPrefs.getString("gateway_ip", "")?.substringBefore(":")
                            ?: throw Exception("Missing ADMIN_IP")

                    val req = KanoGoformRequest("http://$ADB_IP:8080")
                    val result = req.getData(mapOf("cmd" to "usb_port_switch"))
                    val adb_enabled = result?.getString("usb_port_switch")
                    Log.d("kano_ZTE_LOG", "ADB enabled status: $adb_enabled")
                    adb_enabled == "1"
                }
            } catch (e: Exception) {
                Log.e("kano_ZTE_LOG", "Failed to query ADB enabled status: ${e.message}")
                false
            }
        }

        fun copyAssetToExternalStorage(
            context: Context,
            assetPath: String,
            skipIfExists: Boolean = false
        ): File? {
            val fileName = File(assetPath).name
            val outFile = File(context.getExternalFilesDir(null), fileName)

            // If skipIfExists is enabled and the file already exists, return it to avoid interfering with executable usage.
            if (skipIfExists && outFile.exists()) {
                KanoLog.d("kano_ZTE_LOG", "External file already exists, skipping copy: ${outFile.absolutePath}")
                return outFile
            }

            val input = try {
                context.assets.open(assetPath)
            } catch (e: Exception) {
                KanoLog.e("kano_ZTE_LOG", "Asset file not found: $assetPath")
                return null
            }

            return try {
                KanoLog.d(
                    "kano_ZTE_LOG",
                    "Copying $fileName to external storage directory (skipIfExists: $skipIfExists)"
                )
                input.use { ins ->
                    FileOutputStream(outFile, skipIfExists).use { out ->
                        ins.copyTo(out)
                    }
                }
                KanoLog.d("kano_ZTE_LOG", "Copied successfully -> ${outFile.absolutePath}")
                outFile
            } catch (e: Exception) {
                KanoLog.e("kano_ZTE_LOG", "Copy failed: ${e.message}")
                null
            }
        }

        // Recursively copy all assets (dirs/files) into filesDir
        fun copyAssetsRecursively(
            context: Context,
            assetPath: String = "",
            destDir: File = context.filesDir
        ) {
            val assetManager = context.assets
            val fileList = assetManager.list(assetPath) ?: return

            for (fileName in fileList) {
                val fullAssetPath = if (assetPath.isEmpty()) fileName else "$assetPath/$fileName"
                val outFile = File(destDir, fileName)

                if ((assetManager.list(fullAssetPath)?.isNotEmpty() == true)) {
                    // Directory: copy recursively
                    outFile.mkdirs()
                    copyAssetsRecursively(context, fullAssetPath, outFile)
                } else {
                    // File: copy
                    assetManager.open(fullAssetPath).use { input ->
                        FileOutputStream(outFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    outFile.setExecutable(true)
                    outFile.setReadable(true)
                }
            }
        }

        fun getStatusCode(urlStr: String): Int {
            val url = URL(urlStr)
            val connection = url.openConnection() as HttpURLConnection
            return try {
                connection.requestMethod = "GET"
                connection.connect()
                connection.responseCode // Status code
            } catch (e: Exception) {
                e.printStackTrace()
                -1 // Request failed
            } finally {
                connection.disconnect()
            }
        }


        private var cachedTotal = 0L
        private var lastUpdate = 0L
        fun getCachedTodayUsage(context: Context): Long {
            val now = System.currentTimeMillis()
            if (now - lastUpdate > 10_000) { // Update every 10 seconds
                cachedTotal = getTodayDataUsage(context)
                lastUpdate = now
            }
            return cachedTotal
        }

        fun getSELinuxStatus(): String {
            try {
                val process = Runtime.getRuntime().exec("getenforce")
                val reader = process.inputStream.bufferedReader()
                return reader.readLine().trim()
            } catch (e: Exception) {
                e.printStackTrace()
                return "Unknown"
            }
        }

        @Serializable
        data class ShellResult(
            val done: Boolean,   // true: normal output; false: error or timeout
            val content: String  // output content or error message
        )

        fun sendShellCmd(cmd: String, timeoutSeconds: Long = 300): ShellResult {
            if (cmd.isEmpty()) return ShellResult(done = false, content = "Error: empty command")

            return try {
                val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd))

                val output = StringBuilder()
                val error = StringBuilder()

                val reader = process.inputStream.bufferedReader()
                val errorReader = process.errorStream.bufferedReader()

                // Start two threads to read output to avoid blocking
                val outThread = Thread {
                    reader.useLines { lines ->
                        lines.forEach { line -> output.appendLine(line) }
                    }
                }
                val errThread = Thread {
                    errorReader.useLines { lines ->
                        lines.forEach { line -> error.appendLine(line) }
                    }
                }

                outThread.start()
                errThread.start()

                // Wait up to timeoutSeconds seconds
                val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)

                if (!finished) {
                    process.destroyForcibly() // Kill process on timeout
                    return ShellResult(done = false, content = "Error: Command timed out after $timeoutSeconds seconds")
                }

                // Ensure output threads have finished
                outThread.join()
                errThread.join()

                return if (error.isNotEmpty()) {
                    ShellResult(done = false, content = error.toString().trim())
                } else {
                    ShellResult(done = true, content = output.toString().trim())
                }
            } catch (e: Exception) {
                e.printStackTrace()
                ShellResult(done = false, content = "Exception: ${e.message}")
            }
        }

        fun disableFota(context: Context):Boolean{
            if(isExecutingDisabledFOTA){
                KanoLog.w("kano_ZTE_LOG", "Disable FOTA is already running; skipping duplicate execution")
                return false
            }
            try {
                isExecutingDisabledFOTA = true
                // Copy dependency file
                val outFileAdb = copyFileToFilesDir(context, "shell/adb")
                    ?: throw Exception("Failed to copy adb to filesDir")

                // Set executable permissions
                outFileAdb.setExecutable(true)

                val cmds = listOf(
                    "${outFileAdb.absolutePath} -s localhost shell pm disable-user --user 0 com.zte.zdm",
                    "${outFileAdb.absolutePath} -s localhost shell pm uninstall -k --user 0 com.zte.zdm",
                    "${outFileAdb.absolutePath} -s localhost shell pm uninstall -k --user 0 cn.zte.aftersale",
                    "${outFileAdb.absolutePath} -s localhost shell pm uninstall -k --user 0 com.zte.zdmdaemon",
                    "${outFileAdb.absolutePath} -s localhost shell pm uninstall -k --user 0 com.zte.zdmdaemon.install",
                    "${outFileAdb.absolutePath} -s localhost shell pm uninstall -k --user 0 com.zte.analytics",
                    "${outFileAdb.absolutePath} -s localhost shell pm uninstall -k --user 0 com.zte.neopush"
                )

                cmds.forEach{item->ShellKano.runShellCommand(item, context = context)}
                return true
            } catch (e:Exception){
                return false
            } finally {
                isExecutingDisabledFOTA = false
            }
        }

        fun isWeakToken(token: String): Boolean {
            val t = token.ifBlank { "admin" }

            val rules: List<(String) -> Boolean> = listOf(
                { it == "admin" },           // Default weak token
                { it.length < 8 },           // Minimum length
                { !it.any { c -> c.isDigit() } }, // No digits
                { !it.any { c -> c.isLetter() } } // No letters
            )

            return rules.any { rule -> rule(t) }
        }
    }
}