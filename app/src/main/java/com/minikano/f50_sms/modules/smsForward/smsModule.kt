package com.minikano.f50_sms.modules.smsForward

import android.content.Context
import androidx.core.content.edit
import com.minikano.f50_sms.utils.KanoLog
import com.minikano.f50_sms.utils.SmsInfo
import com.minikano.f50_sms.utils.SmsPoll
import com.minikano.f50_sms.modules.BASE_TAG
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import org.json.JSONObject

fun Route.smsModule(context: Context) {
    val TAG = "[$BASE_TAG]_smsModule"

    // Get SMS forwarding method
    get("/api/sms_forward_method") {
        val sharedPrefs =
            context.getSharedPreferences("kano_ZTE_store", Context.MODE_PRIVATE)
        val sms_forward_method = sharedPrefs.getString("kano_sms_forward_method", "") ?: ""
        val json = """
        {
            "sms_forward_method": "${sms_forward_method.replace("\"", "\\\"")}"
        }
    """.trimIndent()

        call.response.headers.append("Access-Control-Allow-Origin", "*")
        call.respondText(json, ContentType.Application.Json, HttpStatusCode.OK)
    }

    // Save SMS forwarding settings: email
    post("/api/sms_forward_mail") {
        try {
            val body = call.receiveText()
            val json = JSONObject(body)

            val smtpHost = json.optString("smtp_host", "").trim()
            val smtpPort = json.optString("smtp_port", "465").trim()
            val smtpTo = json.optString("smtp_to", "").trim()
            val smtpUsername = json.optString("smtp_username", "").trim()
            val smtpPassword = json.optString("smtp_password", "").trim()

            if (smtpTo.isEmpty() || smtpHost.isEmpty() || smtpUsername.isEmpty() || smtpPassword.isEmpty()) {
                throw Exception("Missing required parameters")
            }

            val sharedPrefs =
                context.getSharedPreferences("kano_ZTE_store", Context.MODE_PRIVATE)
            sharedPrefs.edit(commit = true) {
                putString("kano_sms_forward_method", "SMTP")
                putString("kano_smtp_host", smtpHost)
                putString("kano_smtp_port", smtpPort)
                putString("kano_smtp_to", smtpTo)
                putString("kano_smtp_username", smtpUsername)
                putString("kano_smtp_password", smtpPassword)
            }

            KanoLog.d(TAG, "SMTP config saved: $smtpHost:$smtpPort [$smtpUsername]")

            val test_msg = SmsInfo("1016263950", "UFI-TOOLS TEST message", 0)
            SmsPoll.forwardByEmail(test_msg, context)

            call.response.headers.append("Access-Control-Allow-Origin", "*")
            call.respondText(
                """{"result":"success"}""",
                ContentType.Application.Json,
                HttpStatusCode.OK
            )

        } catch (e: Exception) {
            KanoLog.d(TAG, "SMTP config error: ${e.message}")
            call.response.headers.append("Access-Control-Allow-Origin", "*")
            call.respondText(
                """{"error":"SMTP config error"}""",
                ContentType.Application.Json,
                HttpStatusCode.InternalServerError
            )
        }
    }

    // Read SMTP config
    get("/api/sms_forward_mail") {
        val sharedPrefs =
            context.getSharedPreferences("kano_ZTE_store", Context.MODE_PRIVATE)

        val smtpHost = sharedPrefs.getString("kano_smtp_host", "") ?: ""
        val smtpPort = sharedPrefs.getString("kano_smtp_port", "") ?: ""
        val smtpTo = sharedPrefs.getString("kano_smtp_to", "") ?: ""
        val username = sharedPrefs.getString("kano_smtp_username", "") ?: ""
        val password = sharedPrefs.getString("kano_smtp_password", "") ?: ""

        val json = """
        {
            "smtp_host": "$smtpHost",
            "smtp_port": "$smtpPort",
            "smtp_to": "$smtpTo",
            "smtp_username": "$username",
            "smtp_password": "$password"
        }
    """.trimIndent()

        call.respondText(json, ContentType.Application.Json, HttpStatusCode.OK)
    }

    // Save SMS forwarding settings: curl
    post("/api/sms_forward_curl") {
        try {
            val body = call.receiveText()
            val json = JSONObject(body)

            val originalCurl = json.getString("curl_text")

            KanoLog.d(TAG, "Contains {{sms}}: ${originalCurl.contains("{{sms}}")}")
            KanoLog.d(TAG, "curl config: $originalCurl")

            if (!originalCurl.contains("{{sms-body}}")) throw Exception("Missing placeholder: {{sms-body}}")
            if (!originalCurl.contains("{{sms-time}}")) throw Exception("Missing placeholder: {{sms-time}}")
            if (!originalCurl.contains("{{sms-from}}")) throw Exception("Missing placeholder: {{sms-from}}")

            // Store to SharedPreferences
            val sharedPrefs =
                context.getSharedPreferences("kano_ZTE_store", Context.MODE_PRIVATE)
            sharedPrefs.edit(commit = true) {
                putString("kano_sms_forward_method", "CURL")
                putString("kano_sms_curl", originalCurl)
            }

            // Send test message
            val test_msg =
                SmsInfo("11451419198", "UFI-TOOLS TEST message", System.currentTimeMillis())
            SmsPoll.forwardSmsByCurl(test_msg, context)

            json.put("curl_text", originalCurl)

            call.response.headers.append("Access-Control-Allow-Origin", "*")
            call.respondText(
                """{"result":"success"}""",
                ContentType.Application.Json,
                HttpStatusCode.OK
            )
        } catch (e: Exception) {
            KanoLog.d(TAG, "curl config error: ${e.message}")
            call.response.headers.append("Access-Control-Allow-Origin", "*")
            call.respondText(
                """{"error":"curl config error: ${e.message}"}""",
                ContentType.Application.Json,
                HttpStatusCode.InternalServerError
            )
        }
    }

    // Read SMS forwarding curl config
    get("/api/sms_forward_curl") {
        val sharedPrefs =
            context.getSharedPreferences("kano_ZTE_store", Context.MODE_PRIVATE)

        val curlText = sharedPrefs.getString("kano_sms_curl", "") ?: ""

        val json = JSONObject(mapOf("curl_text" to curlText)).toString()

        call.respondText(
            json,
            ContentType.Application.Json,
            HttpStatusCode.OK
        )
    }

    // SMS forwarding master switch
    post("/api/sms_forward_enabled") {
        try {
            val enable = call.request.queryParameters["enable"]
                ?: throw Exception("Missing query parameter: enable")
            KanoLog.d(TAG, "SMS forward enable param: $enable")

            val sharedPrefs =
                context.getSharedPreferences("kano_ZTE_store", Context.MODE_PRIVATE)
            sharedPrefs.edit(commit = true) {
                putString("kano_sms_forward_enabled", enable)
            }

            call.response.headers.append("Access-Control-Allow-Origin", "*")
            call.respondText(
                """{"result":"success"}""",
                ContentType.Application.Json,
                HttpStatusCode.OK
            )
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

    // Get SMS forwarding status
    get("/api/sms_forward_enabled") {
        try {
            val sharedPrefs =
                context.getSharedPreferences("kano_ZTE_store", Context.MODE_PRIVATE)
            val str = sharedPrefs.getString("kano_sms_forward_enabled", "0") ?: "0"

            call.response.headers.append("Access-Control-Allow-Origin", "*")
            call.respondText(
                """{"enabled":"$str"}""",
                ContentType.Application.Json,
                HttpStatusCode.OK
            )
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

    // Save SMS forwarding settings: DingTalk webhook
    post("/api/sms_forward_dingtalk") {
        try {
            val body = call.receiveText()
            val json = JSONObject(body)

            val webhookUrl = json.optString("webhook_url", "").trim()
            val secret = json.optString("secret", "").trim()

            if (webhookUrl.isEmpty()) {
                throw Exception("Missing required parameter: webhook_url")
            }

            // Store to SharedPreferences
            val sharedPrefs =
                context.getSharedPreferences("kano_ZTE_store", Context.MODE_PRIVATE)
            sharedPrefs.edit(commit = true) {
                putString("kano_sms_forward_method", "DINGTALK")
                putString("kano_dingtalk_webhook", webhookUrl)
                putString("kano_dingtalk_secret", secret)
            }

            KanoLog.d(TAG, "DingTalk config saved: $webhookUrl")

            // Send test message
            val test_msg =
                SmsInfo("1010721", "UFI-TOOLS TEST message", System.currentTimeMillis())
            SmsPoll.forwardSmsByDingTalk(test_msg, context)

            call.response.headers.append("Access-Control-Allow-Origin", "*")
            call.respondText(
                """{"result":"success"}""",
                ContentType.Application.Json,
                HttpStatusCode.OK
            )
        } catch (e: Exception) {
            KanoLog.d(TAG, "DingTalk config error: ${e.message}")
            call.response.headers.append("Access-Control-Allow-Origin", "*")
            call.respondText(
                """{"error":"DingTalk config error: ${e.message}"}""",
                ContentType.Application.Json,
                HttpStatusCode.InternalServerError
            )
        }
    }

    // Read SMS forwarding DingTalk config
    get("/api/sms_forward_dingtalk") {
        val sharedPrefs =
            context.getSharedPreferences("kano_ZTE_store", Context.MODE_PRIVATE)

        val webhookUrl = sharedPrefs.getString("kano_dingtalk_webhook", "") ?: ""
        val secret = sharedPrefs.getString("kano_dingtalk_secret", "") ?: ""

        val json = """
        {
            "webhook_url": "$webhookUrl",
            "secret": "$secret"
        }
    """.trimIndent()

        call.response.headers.append("Access-Control-Allow-Origin", "*")
        call.respondText(json, ContentType.Application.Json, HttpStatusCode.OK)
    }

}