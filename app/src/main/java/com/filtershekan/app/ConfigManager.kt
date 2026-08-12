package com.filtershekan.app

import android.content.Context
import android.util.Base64
import org.json.JSONObject

/**
 * این کلاس مسئول تبدیل لینک‌های اشتراکی (vmess://, vless://, trojan://)
 * به یک JSON قابل فهم برای هسته Xray است.
 */
object ConfigManager {

    private const val PREFS_NAME = "filtershekan_prefs"
    private const val KEY_RAW_INPUT = "raw_input"
    private const val KEY_AUTO_FETCH = "auto_fetch_enabled"

    fun saveRawInput(context: Context, raw: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_RAW_INPUT, raw).apply()
    }

    fun getSavedRawInput(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_RAW_INPUT, "") ?: ""
    }

    fun getAutoFetchEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTO_FETCH, true)
    }

    fun setAutoFetchEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_AUTO_FETCH, enabled).apply()
    }

    /**
     * ورودی خام (یک لینک تکی) را به کانفیگ JSON قابل استفاده در Xray تبدیل می‌کند.
     */
    fun buildXrayConfigJson(rawLink: String): String {
        val link = rawLink.trim()
        return when {
            link.startsWith("vmess://") -> buildFromVmess(link)
            link.startsWith("vless://") -> buildFromVless(link)
            link.startsWith("trojan://") -> buildFromTrojan(link)
            link.startsWith("ss://") -> throw NotImplementedError("پارسر Shadowsocks هنوز اضافه نشده - باید تکمیل شود")
            else -> throw IllegalArgumentException("فرمت کانفیگ ناشناخته است")
        }
    }

    // ---------------------------------------------------------------------
    // VMess
    // ---------------------------------------------------------------------
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

        return wrapOutboundAsFullConfig(outbound)
    }

    // ---------------------------------------------------------------------
    // VLESS
    // ---------------------------------------------------------------------
    private fun buildFromVless(link: String): String {
        val uri = java.net.URI(link)
        val userId = uri.userInfo
        val address = uri.host
        val port = uri.port
        val params = parseQueryParams(uri.query ?: "")

        val network = params["type"] ?: "tcp"
        val security = params["security"] ?: "none"
        val path = params["path"] ?: ""
        val host = params["host"] ?: ""
        val sni = params["sni"] ?: ""
        val flow = params["flow"] ?: ""

        val outbound = JSONObject().apply {
            put("protocol", "vless")
            put("settings", JSONObject().apply {
                put("vnext", org.json.JSONArray().put(JSONObject().apply {
                    put("address", address)
                    put("port", port)
                    put("users", org.json.JSONArray().put(JSONObject().apply {
                        put("id", userId)
                        put("encryption", "none")
                        if (flow.isNotEmpty()) put("flow", flow)
                    }))
                }))
            })
            put("streamSettings", JSONObject().apply {
                put("network", network)
                if (security == "tls") {
                    put("security", "tls")
                    put("tlsSettings", JSONObject().apply {
                        if (sni.isNotEmpty()) put("serverName", sni)
                    })
                }
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

        return wrapOutboundAsFullConfig(outbound)
    }

    // ---------------------------------------------------------------------
    // Trojan
    // ---------------------------------------------------------------------
    private fun buildFromTrojan(link: String): String {
        val uri = java.net.URI(link)
        val password = uri.userInfo
        val address = uri.host
        val port = uri.port
        val params = parseQueryParams(uri.query ?: "")
        val sni = params["sni"] ?: address

        val outbound = JSONObject().apply {
            put("protocol", "trojan")
            put("settings", JSONObject().apply {
                put("servers", org.json.JSONArray().put(JSONObject().apply {
                    put("address", address)
                    put("port", port)
                    put("password", password)
                }))
            })
            put("streamSettings", JSONObject().apply {
                put("network", "tcp")
                put("security", "tls")
                put("tlsSettings", JSONObject().apply {
                    put("serverName", sni)
                })
            })
        }

        return wrapOutboundAsFullConfig(outbound)
    }

    // ---------------------------------------------------------------------
    // ابزارهای کمکی مشترک
    // ---------------------------------------------------------------------
    private fun parseQueryParams(query: String): Map<String, String> {
        if (query.isEmpty()) return emptyMap()
        return query.split("&").mapNotNull {
            val parts = it.split("=", limit = 2)
            if (parts.size == 2) {
                java.net.URLDecoder.decode(parts[0], "UTF-8") to java.net.URLDecoder.decode(parts[1], "UTF-8")
            } else null
        }.toMap()
    }

    private fun wrapOutboundAsFullConfig(outbound: JSONObject): String {
        val fullConfig = JSONObject().apply {
            put("log", JSONObject().apply { put("loglevel", "warning") })
            put("inbounds", org.json.JSONArray().put(JSONObject().apply {
                put("port", 10808)
                put("listen", "127.0.0.1")
                put("protocol", "socks")
                put("settings", JSONObject().apply { put("udp", true) })
            }))
            put("outbounds", org.json.JSONArray().put(outbound))
        }
        return fullConfig.toString()
    }
}
