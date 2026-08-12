package com.filtershekan.app

import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray

/**
 * لایه واسط واقعی بین اپ و هسته‌ی Xray.
 * توجه: نام دقیق متدهای تولیدشده از گو (StartLoop/StopLoop یا startLoop/stopLoop)
 * ممکنه بسته به نسخه‌ی AAR کمی فرق کنه؛ اگه موقع بیلد ارور «Unresolved reference»
 * گرفتی، کافیه حرف اول اسم متد رو بین بزرگ/کوچیک عوض کنی.
 */
object XrayCoreBridge : CoreCallbackHandler {

    private var coreController: CoreController? = null
    private var statusCallback: ((String) -> Unit)? = null

    fun start(configJson: String, tunFd: Int, onStatus: (String) -> Unit): Boolean {
        statusCallback = onStatus
        return try {
            if (coreController == null) {
                coreController = CoreController(this)
            }
            coreController?.startLoop(configJson, tunFd)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            onStatus("خطا در اتصال به هسته: ${e.message}")
            false
        }
    }

    fun stop() {
        try {
            coreController?.stopLoop()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun startup(): Long {
        statusCallback?.invoke("هسته Xray روشن شد")
        return 0
    }

    override fun shutdown(): Long {
        statusCallback?.invoke("هسته Xray خاموش شد")
        return 0
    }

    override fun onEmitStatus(code: Long, message: String): Long {
        statusCallback?.invoke(message)
        return 0
    }
}
