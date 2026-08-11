package com.filtershekan.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class VpnConnectionService : VpnService() {

    companion object {
        const val ACTION_CONNECT = "com.filtershekan.app.CONNECT"
        const val ACTION_DISCONNECT = "com.filtershekan.app.DISCONNECT"
        private const val NOTIFICATION_CHANNEL_ID = "filtershekan_vpn_channel"
        private const val NOTIFICATION_ID = 1
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> connect()
            ACTION_DISCONNECT -> disconnect()
        }
        return START_STICKY
    }

    private fun connect() {
        startForeground(NOTIFICATION_ID, buildNotification())

        serviceScope.launch {
            try {
                val rawInput = ConfigManager.getSavedRawInput(this@VpnConnectionService)
                val xrayConfigJson = ConfigManager.buildXrayConfigJson(rawInput)

                XrayCoreBridge.start(xrayConfigJson) { protectSocketFd ->
                    protect(protectSocketFd)
                }

                establishTunInterface()
            } catch (e: Exception) {
                e.printStackTrace()
                stopSelf()
            }
        }
    }

    private fun establishTunInterface() {
        val builder = Builder()
            .setSession("FilterShekan")
            .addAddress("10.10.10.1", 32)
            .addDnsServer("1.1.1.1")
            .addDnsServer("8.8.8.8")
            .addRoute("0.0.0.0", 0)
            .setMtu(1500)

        vpnInterface = builder.establish()

        // TODO: هدایت ترافیک TUN به SOCKS محلی (127.0.0.1:10808) از طریق tun2socks
    }

    private fun disconnect() {
        XrayCoreBridge.stop()
        vpnInterface?.close()
        vpnInterface = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "اتصال فیلترشکن",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("فیلترشکن فعال است")
            .setContentText("در حال محافظت از اتصال اینترنت شما")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        disconnect()
    }
}

object XrayCoreBridge {
    fun start(configJson: String, protectCallback: (Int) -> Unit) {
        // TODO: Libv2ray.runV2Ray(configJson) طبق نسخه کتابخانه
    }

    fun stop() {
        // TODO: Libv2ray.stopV2Ray()
    }
}
