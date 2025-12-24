package com.minikano.f50_sms.utils

import android.os.Build
import com.minikano.f50_sms.configs.AppMeta
import com.minikano.f50_sms.configs.AppMeta.isDeviceRooted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class KanoReport {
    companion object {
        private const val BASE_URL = "https://api.kanokano.cn/ufi_tools_report"
        private const val REPORT_PATH = "/report"
        private const val TOKEN = "minikano1234"

        suspend fun reportToServer() {
            try {
                val uuid = UniqueDeviceIDManager.getUUID()?.trim()
                if (uuid.isNullOrEmpty()) {
                    KanoLog.d("kano_ZTE_LOG_report_service","UUID is empty; skipping report")
                    return
                }

                val model = Build.MODEL.trim()
                val firmwareVersion = Build.DISPLAY
                val appVer = "${AppMeta.versionName} (${AppMeta.versionCode})"

                val json = JSONObject().apply {
                    put("uuid", uuid)
                    put("device_name", model)
                    put("app_ver", appVer)
                    put("firmware_ver", firmwareVersion)
                    put("is_root", isDeviceRooted)
                }.toString()

                val client = OkHttpClient()

                val url = BASE_URL + REPORT_PATH
                val mediaType = "application/json; charset=utf-8".toMediaType()
                val body = json.toRequestBody(mediaType)

                val request = Request.Builder()
                    .url(url)
                    .header("token",TOKEN)
                    .post(body)
                    .build()

                // Run network request on IO dispatcher
                withContext(Dispatchers.IO) {
                    client.newCall(request).execute().use { resp ->
                        if (resp.isSuccessful) {
                            KanoLog.d("kano_ZTE_LOG_report_service","Report succeeded: ${resp.code}")
                        } else {
                            KanoLog.e("kano_ZTE_LOG_report_service","Report failed: ${resp.code} - ${resp.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                KanoLog.e("kano_ZTE_LOG_report_service","Report failed:",e)
                e.printStackTrace()
            }
        }

        data class Report(
            val id: Long?,
            val uuid: String,
            val deviceName: String?,
            val appVer: String?,
            val firmwareVer: String?,
            val requestTime: String?,
            val isRoot: Boolean,
            val isWhiteList: Boolean
        )

        suspend fun getRemoteDeviceRegisterItem(uuid: String): Report? = withContext(Dispatchers.IO) {
            val client = OkHttpClient.Builder()
                .connectTimeout(1, TimeUnit.SECONDS)  // connect timeout
                .readTimeout(1, TimeUnit.SECONDS)     // read timeout
                .writeTimeout(1, TimeUnit.SECONDS)    // write timeout
                .retryOnConnectionFailure(false)  // disable retries
                .build()
            val url = "$BASE_URL/report/$uuid"
            val request = Request.Builder()
                .url(url)
                .header("token", TOKEN)
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    KanoLog.e("kano_ZTE_LOG_devcheck", "Request failed, code=${response.code}")
                    return@withContext null
                }
                val bodyStr = response.body?.string() ?: return@withContext null
                val json = JSONObject(bodyStr)
                return@withContext Report(
                    id = json.optLong("id"),
                    uuid = json.getString("uuid"),
                    deviceName = json.optString("device_name", null),
                    appVer = json.optString("app_ver", null),
                    firmwareVer = json.optString("firmware_ver", null),
                    requestTime = json.optString("request_time", null),
                    isRoot = json.optBoolean("is_root", false),
                    isWhiteList = json.optBoolean("is_white_list", true)
                )
            }
        }
    }
}