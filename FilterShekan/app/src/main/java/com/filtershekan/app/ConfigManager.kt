package com.filtershekan.app

import android.content.Context
import android.util.Base64
import org.json.JSONObject

object ConfigManager {

    private const val PREFS_NAME = "filtershekan_prefs"
    private const val KEY_RAW_INPUT = "raw_input"

    fun saveRawInput(context: Context, raw: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_RAW_INPUT, raw).apply()
    }

    fun getSavedRawInput(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_RAW_INPUT, "") ?: ""
    }

    fun buildXrayConfigJson(rawLink: String): String {
        val link = rawLink.trim()
        return when {
            link.startsWith("vmess://") -> buildFromVmess(link)
            link.startsWith("vless://") -> throw NotImplementedError("پارسر vless هنوز اضافه نشده - باید تکمیل شود")
            link.startsWith("ss://") -> throw NotImplementedError("پارسر Shadowsocks هنوز اضافه نشده - باید تکمیل شود")
            link.startsWith("trojan://") -> throw NotImplementedError("پارسر Trojan هنوز اضافه نشده - باید تکمیل شود")
            else -> throw IllegalArgumentException("فرمت کانفیگ ناشناخته است")
        }
    }

    private fun buildFromVmess(link: String): String {
        val encoded = link.removePrefix("vmess://")
        val decodedBytes = Base64.decode(encoded, Base64.DEFAULT)
        val json = JSONObject(String(decodedBytes, Charsets.UTF_8))

        val address = json.getString("add")
        val port = json.getInt("port")
        val userId = json.getString("id")
        val alterId = json.optInt("aid", 0)
        val network = json.optString("net", "tcp")
        val path = json.optString("path", "")
        val host = json.optString("host", "")
        val tls = json.optString("tls", "")

        val outbound = JSONObject().apply {
            put("protocol", "vmess")
            put("settings", JSONObject().apply {
                put("vnext", org.json.JSONArray().put(JSONObject().apply {
                    put("address", address)
                    put("port", port)
                    put("users", org.json.JSONArray().put(JSONObject().apply {
                        put("id", userId)
                        put("alterId", alterId)
                        put("security", "auto")
                    }))
                }))
            })
            put("streamSettings", JSONObject().apply {
                put("network", network)
                if (tls == "tls") put("security", "tls")
                if (network == "ws") {
                    put("wsSettings", JSONObject().apply {
                        put("path", path)
                        put("headers", JSONObject().apply {
                            if (host.isNotEmpty()) put("Host", host)
                        })
                    })
                }
            })
        }

        val fullConfig = JSONObject().apply {
            put("log", JSONObject().apply { put("loglevel", "warning") })
            put("inbounds", org.json.JSONArray().put(JSONObject().apply {
                put("port", 10808)
                put("listen", "127.0.0.1")
                put("protocol", "socks")
                put("settings", JSONObject().apply {
                    put("udp", true)
                })
            }))
            put("outbounds", org.json.JSONArray().put(outbound))
        }

        return fullConfig.toString()
    }
}
