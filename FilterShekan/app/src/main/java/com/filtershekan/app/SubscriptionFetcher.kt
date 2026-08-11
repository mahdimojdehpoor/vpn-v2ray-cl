package com.filtershekan.app

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

object SubscriptionFetcher {

    suspend fun fetchAndDecode(subscriptionUrl: String): List<String> = withContext(Dispatchers.IO) {
        val connection = URL(subscriptionUrl).openConnection() as HttpURLConnection
        connection.connectTimeout = 10000
        connection.readTimeout = 10000
        connection.requestMethod = "GET"

        try {
            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw Exception("کد پاسخ سرور: $responseCode")
            }
            val rawBody = connection.inputStream.bufferedReader().use { it.readText() }.trim()

            val decodedText = try {
                String(Base64.decode(rawBody, Base64.DEFAULT), Charsets.UTF_8)
            } catch (e: Exception) {
                rawBody
            }

            decodedText.lines()
                .map { it.trim() }
                .filter { it.startsWith("vmess://") || it.startsWith("vless://") || it.startsWith("ss://") || it.startsWith("trojan://") }
        } finally {
            connection.disconnect()
        }
    }
}
