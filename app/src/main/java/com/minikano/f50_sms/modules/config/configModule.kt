package com.minikano.f50_sms.modules.config

import android.content.Context
import com.minikano.f50_sms.modules.BASE_TAG
import com.minikano.f50_sms.utils.KanoLog
import com.minikano.f50_sms.utils.KanoRequest
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import org.json.JSONObject
import androidx.core.content.edit
import com.minikano.f50_sms.configs.AppMeta
import com.minikano.f50_sms.utils.KanoUtils

fun Route.configModule(context: Context) {
    val TAG = "[$BASE_TAG]_configModule"
    val PREFS_NAME = "kano_ZTE_store"
    val PREF_LOGIN_TOKEN = "login_token"

    // Check whether token is default/weak
    get("/api/is_weak_token") {
        try {
            val jsonResult = """{"is_weak_token":${AppMeta.isDefaultOrWeakToken}}""".trimIndent()

            call.response.headers.append("Access-Control-Allow-Origin", "*")
            call.respondText(jsonResult, ContentType.Application.Json)
        } catch (e: Exception) {
            KanoLog.d("kano_ZTE_LOG", "Failed to get is_weak_token: ${e.message}")
            call.response.headers.append("Access-Control-Allow-Origin", "*")
            call.respondText(
                """{"error":"Failed to get is_weak_token"}""",
                ContentType.Application.Json,
                HttpStatusCode.InternalServerError
            )
        }
    }

    // Set token
    post("/api/set_token") {
        try {
            val body = call.receiveText()
            val json = JSONObject(body)

            val token = json.optString("token", "").trim()
            if (token.isEmpty() || token.isBlank()) {
                throw IllegalArgumentException("Please provide token")
            }

            val regex = Regex("^(?=.*[a-zA-Z])(?=.*\\d).{8,128}$")
            if(token.length < 8) {
                throw IllegalArgumentException("Token must be at least 8 characters")
            }
            else if(!regex.matches(token)) {
                throw IllegalArgumentException("Token must contain at least letters and digits")
            }

            KanoLog.d(TAG, "Received token=$token")

            val pref = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            pref.edit(commit = true) {
                putString(PREF_LOGIN_TOKEN, token)
            }
            AppMeta.updateIsDefaultOrWeakToken(KanoUtils.isWeakToken(token = token))

            call.response.headers.append("Access-Control-Allow-Origin", "*")
            call.respondText(
                """{"result":"success"}""",
                ContentType.Application.Json,
                HttpStatusCode.OK
            )
        } catch (e: Exception) {
            KanoLog.d(TAG, "Failed to set token: ${e.message}")
            call.response.headers.append("Access-Control-Allow-Origin", "*")
            call.respondText(
                """{"error":"${e.message ?: "Unknown error"}"}""",
                ContentType.Application.Json,
                HttpStatusCode.InternalServerError
            )
        }
    }

    // Get global resource server URL
    get("/api/get_res_server") {
        try {
            // Build JSON response
            val resultJson = """{
                "res_server": "${AppMeta.GLOBAL_SERVER_URL}"
            }""".trimIndent()

            call.response.headers.append("Access-Control-Allow-Origin", "*")
            call.respondText(resultJson, ContentType.Application.Json, HttpStatusCode.OK)
        } catch (e: Exception) {
            KanoLog.d(TAG, "Request failed: ${e.message}")
            call.response.headers.append("Access-Control-Allow-Origin", "*")
            call.respondText(
                """{"error":"Request failed"}""",
                ContentType.Application.Json,
                HttpStatusCode.InternalServerError
            )
        }
    }

    // Set resource server URL
    post("/api/set_res_server") {
        try {
            val body = call.receiveText()
            val json = JSONObject(body)

            val resServerUrl = json.optString("res_server", "").trim()
            if (resServerUrl.isEmpty() || resServerUrl.isBlank()) {
                throw IllegalArgumentException("Please provide res_server")
            }

            KanoLog.d(TAG, "Received res_server=$resServerUrl")

            AppMeta.setGlobalServerUrl(context, resServerUrl)

            call.response.headers.append("Access-Control-Allow-Origin", "*")
            call.respondText(
                """{"result":"success"}""",
                ContentType.Application.Json,
                HttpStatusCode.OK
            )
        } catch (e: Exception) {
            KanoLog.d(TAG, "Failed to set resServerUrl: ${e.message}")
            call.response.headers.append("Access-Control-Allow-Origin", "*")
            call.respondText(
                """{"error":"${e.message ?: "Unknown error"}"}""",
                ContentType.Application.Json,
                HttpStatusCode.InternalServerError
            )
        }
    }

    // Get debug log switch status
    get("/api/get_log_status") {
        try {
            // Build JSON response
            val resultJson = """{
                "debug_log_enabled": "${AppMeta.isEnableLog}"
            }""".trimIndent()

            call.response.headers.append("Access-Control-Allow-Origin", "*")
            call.respondText(resultJson, ContentType.Application.Json, HttpStatusCode.OK)
        } catch (e: Exception) {
            KanoLog.d(TAG, "Request failed: ${e.message}")
            call.response.headers.append("Access-Control-Allow-Origin", "*")
            call.respondText(
                """{"error":"Request failed"}""",
                ContentType.Application.Json,
                HttpStatusCode.InternalServerError
            )
        }
    }

    // Set debug log switch status
    post("/api/set_log_status") {
        try {
            val body = call.receiveText()
            val json = JSONObject(body)

            val debugEnabled = json.optBoolean("debug_log_enabled", false)

            KanoLog.d(TAG, "Received debug_log_enabled=$debugEnabled")

            AppMeta.setIsEnableLog(context, debugEnabled)

            call.response.headers.append("Access-Control-Allow-Origin", "*")
            call.respondText(
                """{"result":"success"}""",
                ContentType.Application.Json,
                HttpStatusCode.OK
            )
        } catch (e: Exception) {
            KanoLog.d(TAG, "Failed to set debug_log_enabled: ${e.message}")
            call.response.headers.append("Access-Control-Allow-Origin", "*")
            call.respondText(
                """{"error":"${e.message ?: "Unknown error"}"}""",
                ContentType.Application.Json,
                HttpStatusCode.InternalServerError
            )
        }
    }
}