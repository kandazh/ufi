package com.minikano.f50_sms.modules.deviceInfo

import android.content.Context
import android.os.StatFs
import com.minikano.f50_sms.configs.AppMeta
import com.minikano.f50_sms.modules.BASE_TAG
import com.minikano.f50_sms.utils.KanoLog
import com.minikano.f50_sms.utils.KanoUtils
import com.minikano.f50_sms.utils.UniqueDeviceIDManager
import com.minikano.f50_sms.utils.calculateCpuUsage
import com.minikano.f50_sms.utils.getCpuFreqJson
import com.minikano.f50_sms.utils.getMemoryUsage
import com.minikano.f50_sms.utils.readBatteryStatus
import com.minikano.f50_sms.utils.readThermalZones
import com.minikano.f50_sms.utils.readUsbDevices
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.plugins.origin
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class MyStorageInfo(
    val path: String, val totalBytes: Long, val availableBytes: Long
)

fun Route.baseDeviceInfoModule(context: Context) {
    val TAG = "[$BASE_TAG]_baseDeviceInfoModule"

    get("/api/baseDeviceInfo") {
        // Client IP
        var ipRes: String? = null
        try {
            val headers = call.request.headers

            val ip = headers["http-client-ip"]
                ?: headers["x-forwarded-for"]
                ?: headers["remote-addr"]
                ?: call.request.origin.remoteAddress

            KanoLog.d(TAG, "Got client IP: $ip")
            ipRes = ip
        } catch (e: Exception) {
            KanoLog.e(TAG, "Failed to get client IP: ${e.message}")
            ipRes = null
        }

        // CPU temperature
        var cpuTempRes: String? = null
        var cpuTempMax:String? = null
        try {
            val (maxTemp,temp) = readThermalZones()
            cpuTempMax = maxTemp.toString()
            KanoLog.d(TAG, "Got CPU temperature: $temp")
            cpuTempRes = temp
            cpuTempRes = cpuTempRes.replace("\n", "")

        } catch (e: Exception) {
            KanoLog.d(TAG, "Failed to get CPU temperature: ${e.message}")
            cpuTempRes = null
        }

        // CPU and memory info
        var cpuFreqInfo: String? = null
        var cpuUsageInfo: String? = null
        var memInfo: String? = null
        var cpuUsageRes: Double? = null
        var memUsageRes: Double? = null

        try {
            val usage = calculateCpuUsage()
            val freq = getCpuFreqJson()
            val mem = getMemoryUsage()

            KanoLog.d(TAG, "CPU frequency data: ${freq}")
            KanoLog.d(TAG, "CPU usage data: ${usage}")
            KanoLog.d(TAG, "Memory usage data: ${mem}")
            cpuUsageRes = Json.parseToJsonElement(usage)
                .jsonObject["cpu"]
                ?.jsonPrimitive
                ?.double
            memUsageRes = Json.parseToJsonElement(mem)
                .jsonObject["mem_usage_percent"]
                ?.jsonPrimitive
                ?.double
            cpuFreqInfo = freq
            cpuUsageInfo = usage
            memInfo = mem
        } catch (e: Exception) {
            cpuFreqInfo = null
            cpuUsageInfo = null
            memInfo = null
            KanoLog.d(TAG, "Failed to get CPU/memory info: ${e.message}")
        }

        // Storage and daily data usage
        var dailyDataRes: Long? = null
        var availableSizeRes: Long? = null
        var usedSizeRes: Long? = null
        var totalSizeRes: Long? = null
        var externalTotalRes: Long? = null
        var externalUsedRes: Long? = null
        var externalAvailableRes: Long? = null
        try {
            // Internal storage
            val internalStorage = context.filesDir
            val statFs = StatFs(internalStorage.absolutePath)
            val totalSize = statFs.blockSizeLong * statFs.blockCountLong
            val availableSize = statFs.blockSizeLong * statFs.availableBlocksLong
            val usedSize = totalSize - availableSize

            // Daily data usage
            val dailyData = KanoUtils.getCachedTodayUsage(context)

            // External storage (removable)
            val exStorageInfo = KanoUtils.getCachedRemovableStorageInfo(context)
            val externalTotal = exStorageInfo?.totalBytes ?: 0
            val externalAvailable = exStorageInfo?.availableBytes ?: 0
            val externalUsed = externalTotal - externalAvailable

            KanoLog.d(TAG, "Daily data usage: $dailyData")
            KanoLog.d(TAG, "Internal storage: $usedSize/$totalSize")
            KanoLog.d(TAG, "External storage: $externalAvailable/$externalTotal")

            dailyDataRes = dailyData
            availableSizeRes = availableSize
            usedSizeRes = usedSize
            totalSizeRes = totalSize
            externalTotalRes = externalTotal
            externalUsedRes = externalUsed
            externalAvailableRes = externalAvailable

        } catch (e: Exception) {
            KanoLog.d(TAG, "Failed to get storage/daily data info: ${e.message}")
            dailyDataRes = null
            availableSizeRes = null
            usedSizeRes = null
            totalSizeRes = null
            externalTotalRes = null
            externalUsedRes = null
            externalAvailableRes = null
        }


        // Model and battery
        var versionNameRes: String? = null
        var versionCodeRes: Int? = null
        var modelRes: String? = null
        var batteryLevelRes: Int? = null
        var currentNow :Int? = null
        var votageNow :Int? = null
        try {
            val batteryLevel: Int = KanoUtils.getBatteryPercentage(context)
            val batteryStatus = readBatteryStatus()
            currentNow = batteryStatus.current_uA
            votageNow = batteryStatus.voltage_uV

            KanoLog.d(TAG, "Model and battery: ${AppMeta.model} $batteryLevel")

            versionNameRes = AppMeta.versionName
            versionCodeRes = AppMeta.versionCode
            modelRes = AppMeta.model
            batteryLevelRes = batteryLevel

        } catch (e: Exception) {
            KanoLog.d(TAG, "Failed to get model/battery info: ${e.message}")
            versionNameRes = null
            versionCodeRes = null
            modelRes = null
            batteryLevelRes = null
        }

        val jsonResult = """
            {
                "app_ver": "$versionNameRes",
                "app_ver_code": "$versionCodeRes",
                "model": "$modelRes",
                "battery": "$batteryLevelRes",
                "daily_data": $dailyDataRes,
                "internal_available_storage": $availableSizeRes,
                "internal_used_storage": $usedSizeRes,
                "internal_total_storage": $totalSizeRes,
                "external_total_storage": $externalTotalRes,
                "external_used_storage": $externalUsedRes,
                "external_available_storage": $externalAvailableRes,
                "cpu_temp_list":$cpuTempRes,
                "cpu_temp":$cpuTempMax,
                "client_ip":"$ipRes",
                "cpu_usage":$cpuUsageRes,
                "mem_usage":$memUsageRes,
                "cpuFreqInfo":$cpuFreqInfo,
                "cpuUsageInfo":$cpuUsageInfo,
                "memInfo":$memInfo,
                "current_now":$currentNow,
                "voltage_now":$votageNow
            }
        """.trimIndent()
        call.response.headers.append("Access-Control-Allow-Origin", "*")
        call.respondText(jsonResult, ContentType.Application.Json)
    }

    // Version info
    get("/api/version_info") {
        try {
            val jsonResult = """
            {
                "app_ver": "${AppMeta.versionName}",
                "app_ver_code": "${AppMeta.versionCode}",
                "model":"${AppMeta.model}"
            }
        """.trimIndent()

            call.response.headers.append("Access-Control-Allow-Origin", "*")
            call.respondText(jsonResult, ContentType.Application.Json)
        } catch (e: Exception) {
            KanoLog.d("kano_ZTE_LOG", "Failed to get version info: ${e.message}")
            call.response.headers.append("Access-Control-Allow-Origin", "*")
            call.respondText(
                """{"error":"Failed to get version info"}""",
                ContentType.Application.Json,
                HttpStatusCode.InternalServerError
            )
        }
    }

    get("/api/device_id") {
        try {
            val jsonResult = """{"device_id": "${UniqueDeviceIDManager.getUUID()}"}""".trimIndent()

            call.response.headers.append("Access-Control-Allow-Origin", "*")
            call.respondText(jsonResult, ContentType.Application.Json)
        } catch (e: Exception) {
            KanoLog.d("kano_ZTE_LOG", "Failed to get device id: ${e.message}")
            call.response.headers.append("Access-Control-Allow-Origin", "*")
            call.respondText(
                """{"error":"Failed to get device id"}""",
                ContentType.Application.Json,
                HttpStatusCode.InternalServerError
            )
        }
    }

    // SELinux status
    get("/api/SELinux"){
        try {
            val res = KanoUtils.getSELinuxStatus()
            val jsonResult = """
            {
                "selinux": "$res"
            }
        """.trimIndent()

            call.response.headers.append("Access-Control-Allow-Origin", "*")
            call.respondText(jsonResult, ContentType.Application.Json)
        } catch (e: Exception) {
            KanoLog.d("kano_ZTE_LOG", "Failed to get SELinux status: ${e.message}")
            call.response.headers.append("Access-Control-Allow-Origin", "*")
            call.respondText(
                """{"error":"Failed to get SELinux status"}""",
                ContentType.Application.Json,
                HttpStatusCode.InternalServerError
            )
        }
    }

    // Whether a token is required
    get("/api/need_token") {
        try {
            val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val needToken = sharedPrefs.getString("login_token_enabled", true.toString())

            val jsonResult = """
            {
                "need_token": $needToken
            }
        """.trimIndent()

            call.response.headers.append("Access-Control-Allow-Origin", "*")
            call.respondText(jsonResult, ContentType.Application.Json)
        } catch (e: Exception) {
            KanoLog.d("kano_ZTE_LOG", "Failed to get token requirement: ${e.message}")
            call.response.headers.append("Access-Control-Allow-Origin", "*")
            call.respondText(
                """{"error":"Failed to get token requirement"}""",
                ContentType.Application.Json,
                HttpStatusCode.InternalServerError
            )
        }
    }

    // USB device tree and port status
    get("/api/usb_status") {
        try {
            val (maxSpeed,details) = readUsbDevices()
            val jsonResult = """
            {
                "maxSpeed":$maxSpeed,
                "details":$details
            }
        """.trimIndent()

            call.response.headers.append("Access-Control-Allow-Origin", "*")
            call.respondText(jsonResult, ContentType.Application.Json)
        } catch (e: Exception) {
            KanoLog.d("kano_ZTE_LOG", "Failed to get UsbDevices info: ${e.message}")
            call.response.headers.append("Access-Control-Allow-Origin", "*")
            call.respondText(
                """{"error":"Failed to get UsbDevices info"}""",
                ContentType.Application.Json,
                HttpStatusCode.InternalServerError
            )
        }
    }
}