package com.minikano.f50_sms.utils

import android.content.Context
import android.net.Uri
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class SmsInfo(val address: String, val body: String, val timestamp: Long)

object SmsPoll {
    private var lastSms: SmsInfo? = null

    //store
    private val PREFS_NAME = "kano_ZTE_store"

    fun checkNewSmsAndSend(context: Context) {
        val sms = getLatestSms(context) ?: return

        val now = System.currentTimeMillis()
        val minute = 2
        val withinMin = now - sms.timestamp <= minute * 60 * 1000
        val isNew = lastSms == null || sms != lastSms

        if (withinMin && isNew) {
            KanoLog.d("kano_ZTE_LOG", "Received new SMS: ${sms.address} - ${sms.body}")
            lastSms = sms
            // Forward processing
            val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val sms_forward_method = sharedPrefs.getString("kano_sms_forward_method", "") ?: ""
            if(sms_forward_method =="SMTP") {
                forwardByEmail(lastSms, context)
            }
            else if(sms_forward_method == "CURL"){
                forwardSmsByCurl(lastSms,context)
            }
            else if(sms_forward_method == "DINGTALK"){
                forwardSmsByDingTalk(lastSms,context)
            }
        } else {
            KanoLog.d(
                "kano_ZTE_LOG",
                "No new SMS. Within ${minute} minutes: $withinMin, is new: $isNew"
            )
        }
    }

    // Forward via curl
    fun forwardSmsByCurl(sms_data: SmsInfo?, context: Context) {
        if (sms_data == null) return
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val originalCurl = sharedPrefs.getString("kano_sms_curl", null)
        if (originalCurl.isNullOrEmpty()) {
            KanoLog.e("kano_ZTE_LOG", "curl config error: kano_sms_curl is empty")
            return
        }

        KanoLog.d("kano_ZTE_LOG", "Forwarding SMS... (CURL)")
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault())
        val smsText = sms_data.body.trimStart()
        val smsFrom = sms_data.address
        val smsTime = formatter.format(Instant.ofEpochMilli(sms_data.timestamp))

        // Replace placeholders and send
        val replacedCurl = originalCurl
            .replace("\n","")
            .replace("{{sms-body}}", smsText)
            .replace("{{sms-time}}", smsTime)
            .replace("{{sms-from}}", smsFrom).trimIndent()

        KanoCURL(context).send(replacedCurl)
    }

    // Forward via SMTP email
    fun forwardByEmail(sms_data: SmsInfo?, context: Context) {
        if (sms_data == null) return
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val smtpHost = sharedPrefs.getString("kano_smtp_host", null)
        if (smtpHost.isNullOrEmpty()) {
            KanoLog.e("kano_ZTE_LOG", "SMTP config error: kano_smtp_host is empty")
            return
        }

        val smtpTo = sharedPrefs.getString("kano_smtp_to", null)
        if (smtpTo.isNullOrEmpty()) {
            KanoLog.e("kano_ZTE_LOG", "SMTP config error: kano_smtp_to is empty")
            return
        }

        val smtpPort = sharedPrefs.getString("kano_smtp_port", null)
        if (smtpPort.isNullOrEmpty()) {
            KanoLog.e("kano_ZTE_LOG", "SMTP config error: kano_smtp_port is empty")
            return
        }

        val username = sharedPrefs.getString("kano_smtp_username", null)
        if (username.isNullOrEmpty()) {
            KanoLog.e("kano_ZTE_LOG", "SMTP config error: kano_smtp_username is empty")
            return
        }

        val password = sharedPrefs.getString("kano_smtp_password", null)
        if (password.isNullOrEmpty()) {
            KanoLog.e("kano_ZTE_LOG", "SMTP config error: kano_smtp_password is empty")
            return
        }

        val smtpClient = KanoSMTP(smtpHost, smtpPort, username, password)

        KanoLog.d("kano_ZTE_LOG", "Forwarding SMS... (SMTP)")

        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault())
        val previewText = sms_data.body.trimStart().let {
            if (it.length > 37) it.take(37) + "…" else it
        }
        smtpClient.sendEmail(
            to = smtpTo,
            subject = previewText,
            body = """
                <div>
                    <p>${sms_data!!.body.trimStart()}</p>
                    <p>📩 <b>From:</b> ${sms_data.address}</p>
                    <p>⏰ <b>Time:</b> ${formatter.format(Instant.ofEpochMilli(sms_data.timestamp))}</p>
                    <div style="text-align: center;">
                        <i>Powered by <a href="https://github.com/kanoqwq/UFI-TOOLS" target="_blank">UFI-TOOLS</a></i>
                    </div>
                </div>
            """.trimIndent()
        )
    }

    // Forward via DingTalk webhook
    fun forwardSmsByDingTalk(sms_data: SmsInfo?, context: Context) {
        if (sms_data == null) return
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val webhookUrl = sharedPrefs.getString("kano_dingtalk_webhook", null)
        if (webhookUrl.isNullOrEmpty()) {
            KanoLog.e("kano_ZTE_LOG", "DingTalk config error: kano_dingtalk_webhook is empty")
            return
        }

        val secret = sharedPrefs.getString("kano_dingtalk_secret", null)

        KanoLog.d("kano_ZTE_LOG", "Forwarding SMS... (DingTalk)")
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault())
        val smsText = sms_data.body.trimStart()
        val smsFrom = sms_data.address
        val smsTime = formatter.format(Instant.ofEpochMilli(sms_data.timestamp))

        // Build DingTalk message content
        val messageContent = """
            📱 New SMS Notification

            📄 Content: $smsText
            📞 From: $smsFrom
            ⏰ Time: $smsTime

            Powered by UFI-TOOLS
        """.trimIndent()

        val dingTalkClient = KanoDingTalk(webhookUrl, secret)
        dingTalkClient.sendMessage(messageContent)
    }

    fun getLatestSms(context: Context): SmsInfo? {
        val uri = Uri.parse("content://sms/inbox")
        val projection = arrayOf("address", "body", "date")
        val sortOrder = "date DESC"

        return try {
            val cursor = context.contentResolver.query(uri, projection, null, null, sortOrder)
            cursor?.use {
                if (it.moveToFirst()) {
                    val address = it.getString(it.getColumnIndexOrThrow("address"))
                    val body = it.getString(it.getColumnIndexOrThrow("body"))
                    val date = it.getLong(it.getColumnIndexOrThrow("date"))
                    SmsInfo(address, body, date)
                } else null
            }
        } catch (e: Exception) {
            KanoLog.e("kano_ZTE_LOG", "Missing SMS permission; cannot read SMS", e)
            null
        }
    }
}