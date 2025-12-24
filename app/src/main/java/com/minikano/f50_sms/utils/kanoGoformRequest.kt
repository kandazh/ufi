package com.minikano.f50_sms.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.*
import okhttp3.Headers.Companion.toHeaders
import org.json.JSONObject
import java.security.MessageDigest

class KanoGoformRequest(private val baseUrl: String) {
    private val client = OkHttpClient()
    private val commonHeaders = mapOf(
        "Referer" to "$baseUrl/index.html",
        "Host" to baseUrl.replace("https://", "").replace("http://", ""),
        "Origin" to baseUrl
    )

    suspend fun login(password: String): String? = withContext(Dispatchers.IO) {
        val ld = getLD()?.optString("LD") ?: return@withContext null
        val pwdHash = sha256(sha256(password) + ld)
        KanoLog.d("kano_ZTE_LOG", "Login hash: $pwdHash")

        val body = FormBody.Builder()
            .add("goformId", "LOGIN")
            .add("isTest", "false")
            .add("user", "admin")
            .add("password", pwdHash)
            .build()

        val req = Request.Builder()
            .url("$baseUrl/goform/goform_set_cmd_process")
            .headers(commonHeaders.toHeaders())
            .addHeader("Content-Type", "application/x-www-form-urlencoded")
            .post(body)
            .build()

        client.newCall(req).execute().use { res ->
            if (!res.isSuccessful) return@withContext null
            val bdy = res.body?.string()?.let { JSONObject(it) }
            KanoLog.d("kano_ZTE_LOG", "Login response: $bdy")
            val resData = bdy ?: return@withContext null
            if (resData.optString("result") == "3") return@withContext null
            val header = res.header("set-cookie")?.split(";")?.firstOrNull()
            KanoLog.d("kano_ZTE_LOG", "Login cookie: $header")
            return@withContext res.header("set-cookie")
        }
    }

    suspend fun logout(cookie: String): String? = withContext(Dispatchers.IO) {
        val ad = processAD(cookie)
        val body = FormBody.Builder()
            .add("goformId", "LOGOUT")
            .add("isTest", "false")
            .add("AD", ad)
            .build()

        val req = Request.Builder()
            .url("$baseUrl/goform/goform_set_cmd_process")
            .headers((commonHeaders + mapOf("Cookie" to cookie)).toHeaders())
            .addHeader("Content-Type", "application/x-www-form-urlencoded")
            .post(body)
            .build()

        client.newCall(req).execute().use { res ->
            return@withContext res.body?.string()
        }
    }

    private suspend fun getLD(): JSONObject? = getData(mapOf("cmd" to "LD"))

    private suspend fun getRD(cookie: String): JSONObject? = getData(
        mapOf("cmd" to "RD"), cookie
    )

    private suspend fun getUFIInfo(): JSONObject? = getData(
        mapOf("cmd" to "Language,cr_version,wa_inner_version", "multi_data" to "1")
    )

    private suspend fun processAD(cookie: String): String = withContext(Dispatchers.IO) {
        val ufiInfo = getUFIInfo() ?: throw Exception("Unable to fetch version info")
        val wa = ufiInfo.optString("wa_inner_version")
        val cr = ufiInfo.optString("cr_version")
        if (wa.isEmpty() || cr.isEmpty()) throw Exception("Missing version fields")
        val parsed = sha256(wa + cr)

        val rd = getRD(cookie)?.optString("RD") ?: throw Exception("Failed to get RD")
        return@withContext sha256(parsed + rd)
    }

    suspend fun postData(cookie: String, data: Map<String, String>): JSONObject? = withContext(Dispatchers.IO) {
        val ad = processAD(cookie)
        val allParams = data.toMutableMap().apply {
            put("isTest", "false")
            put("AD", ad)
        }

        val formBody = FormBody.Builder().apply {
            allParams.forEach { (k, v) -> add(k, v) }
        }.build()

        val req = Request.Builder()
            .url("$baseUrl/goform/goform_set_cmd_process")
            .headers((commonHeaders + mapOf("Cookie" to cookie)).toHeaders())
            .addHeader("Content-Type", "application/x-www-form-urlencoded")
            .post(formBody)
            .build()

        client.newCall(req).execute().use { res ->
            if (!res.isSuccessful) return@withContext null
            return@withContext JSONObject(res.body?.string() ?: return@withContext null)
        }
    }

    suspend fun getData(
        params: Map<String, String>,
        cookie: String? = null,
        timeoutMillis: Long = 2000 // default timeout: 2 seconds
    ): JSONObject? = withContext(Dispatchers.IO) {
        try {
            withTimeout(timeoutMillis) {
                val allParams = params.toMutableMap().apply {
                    put("isTest", "false")
                    put("_", System.currentTimeMillis().toString())
                }

                val query = allParams.map { "${it.key}=${it.value}" }.joinToString("&")
                val url = "$baseUrl/goform/goform_get_cmd_process?$query"

                val headers = if (cookie != null)
                    (commonHeaders + mapOf("Cookie" to cookie)).toHeaders()
                else
                    commonHeaders.toHeaders()

                val req = Request.Builder()
                    .url(url)
                    .headers(headers)
                    .get()
                    .build()

                client.newCall(req).execute().use { res ->
                    if (!res.isSuccessful) return@withTimeout null
                    return@withTimeout JSONObject(res.body?.string() ?: return@withTimeout null)
                }
            }
        } catch (e: Exception) {
            // Logging the exception helps with debugging
            KanoLog.e("kano_ZTE_LOG", "getData exception: ${e.message}")
            return@withContext null
        }
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }.uppercase()
    }
}