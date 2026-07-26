package com.example.myapplication.translation

import android.os.Handler
import android.os.Looper
import com.example.myapplication.BuildConfig
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import org.json.JSONObject

class JapaneseKoreanTranslator {
    private val mainHandler = Handler(Looper.getMainLooper())

    fun translate(
        text: String,
        onSuccess: (String) -> Unit,
        onFailure: (Throwable) -> Unit
    ) {
        if (BuildConfig.PAPAGO_CLIENT_ID.isBlank() || BuildConfig.PAPAGO_CLIENT_SECRET.isBlank()) {
            onFailure(IllegalStateException("Papago credentials are missing in local.properties"))
            return
        }

        Thread {
            try {
                val translatedText = requestPapagoTranslation(text)
                mainHandler.post {
                    onSuccess(translatedText)
                }
            } catch (error: Throwable) {
                mainHandler.post {
                    onFailure(error)
                }
            }
        }.start()
    }

    fun close() = Unit

    private fun requestPapagoTranslation(text: String): String {
        val connection = (URL(API_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = TIMEOUT_MILLIS
            readTimeout = TIMEOUT_MILLIS
            doOutput = true
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            setRequestProperty("X-NCP-APIGW-API-KEY-ID", BuildConfig.PAPAGO_CLIENT_ID)
            setRequestProperty("X-NCP-APIGW-API-KEY", BuildConfig.PAPAGO_CLIENT_SECRET)
        }

        val body = buildFormBody(text)
        OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
            writer.write(body)
        }

        val responseCode = connection.responseCode
        val responseBody = readResponse(connection, responseCode)
        connection.disconnect()

        if (responseCode !in 200..299) {
            throw IllegalStateException("Papago failed: HTTP $responseCode $responseBody")
        }

        return JSONObject(responseBody)
            .getJSONObject("message")
            .getJSONObject("result")
            .getString("translatedText")
            .trim()
    }

    private fun buildFormBody(text: String): String {
        return listOf(
            "source" to SOURCE_LANGUAGE,
            "target" to TARGET_LANGUAGE,
            "text" to text
        ).joinToString(separator = "&") { (key, value) ->
            "${key}=${URLEncoder.encode(value, Charsets.UTF_8.name())}"
        }
    }

    private fun readResponse(connection: HttpURLConnection, responseCode: Int): String {
        val stream = if (responseCode in 200..299) {
            connection.inputStream
        } else {
            connection.errorStream ?: connection.inputStream
        }

        return BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
            reader.readText()
        }
    }

    private companion object {
        const val API_URL = "https://papago.apigw.ntruss.com/nmt/v1/translation"
        const val SOURCE_LANGUAGE = "ja"
        const val TARGET_LANGUAGE = "ko"
        const val TIMEOUT_MILLIS = 10_000
    }
}
