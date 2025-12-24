package com.minikano.f50_sms.modules

import com.minikano.f50_sms.utils.KanoLog
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.httpMethod
import io.ktor.server.request.receiveText
import io.ktor.server.request.uri
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import java.net.HttpURLConnection
import java.net.URL

const val TAG = "[$BASE_TAG]_reverseProxyModule"

// Reverse proxy to official backend
fun Route.reverseProxyModule(targetServerIP:String) {
    // Forward to vendor web backend
    route("/api/goform/{...}") {
        KanoLog.d(TAG,"Starting reverse proxy...")
        handle {
            val targetServer = "http://${targetServerIP}" // Replace with your target server

            val originalPath = call.request.uri.removePrefix("/api")
            val queryString = call.request.queryParameters.entries()
                .joinToString("&") { (k, v) -> v.joinToString("&") { "$k=$it" } }

            val fullUrl = if (queryString.isBlank()) {
                "$targetServer$originalPath"
            } else {
                "$targetServer$originalPath?$queryString"
            }

            val method = call.request.httpMethod.value

            // Handle OPTIONS request
            if (method == "OPTIONS") {
                call.response.headers.append("Access-Control-Allow-Origin", "*")
                call.response.headers.append("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
                call.response.headers.append("Access-Control-Allow-Headers", "Content-Type, X-Requested-With")
                call.respond(HttpStatusCode.OK)
                return@handle
            }

            try {
                val url = URL(fullUrl)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = method
                    doInput = true
                    setRequestProperty("Referer", null)
                    setRequestProperty("Referer", targetServer)

                    call.request.headers.forEach { key, values ->
                        // Ignore client Referer host
                        if (!key.equals("host", ignoreCase = true) && !key.equals("referer", ignoreCase = true)) {
                            setRequestProperty(key, values.joinToString(","))
                        }
                    }

                    if (method == "POST" || method == "PUT") {
                        val body = call.receiveText()
                        doOutput = true
                        setRequestProperty("Content-Length", body.toByteArray().size.toString())
                        outputStream.use { it.write(body.toByteArray()) }
                    }
                }

                val responseCode = conn.responseCode
                val responseStream = if (responseCode in 200..299) conn.inputStream else conn.errorStream
                val responseBytes = responseStream.readBytes()
                val responseContentType = conn.contentType ?: "text/plain"

                conn.headerFields.forEach { (key, values) ->
                    if (key != null && key.equals("Set-Cookie", ignoreCase = true)) {
                        values?.forEach { cookie ->
                            call.response.headers.append("kano-cookie", cookie)
                        }
                    }
                }

                call.response.headers.append("Access-Control-Allow-Origin", "*")
                call.response.headers.append("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
                call.response.headers.append("Access-Control-Allow-Headers", "Content-Type, X-Requested-With")

                call.respondBytes(responseBytes, ContentType.parse(responseContentType), HttpStatusCode.fromValue(responseCode))
            } catch (e: Exception) {
                KanoLog.e(TAG,"Proxy forward error",e)
                call.respond(HttpStatusCode.InternalServerError, "Proxy error: ${e.message}")
            }
        }
    }
}