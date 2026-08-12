package com.filtershekan.app

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.filtershekan.app.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var isConnected = false

    // لینک همون سابسکریپشن رایگانی که تست کردیم؛ هر ۱۵ دقیقه آپدیت می‌شه
    private val defaultSubscriptionUrl =
        "https://raw.githubusercontent.com/ebrasha/free-v2ray-public-list/refs/heads/main/vmess_configs.txt"

    private val vpnPermissionLauncher =
        registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                startVpnService()
            } else {
                Toast.makeText(this, "دسترسی VPN رد شد", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val autoFetchEnabled = ConfigManager.getAutoFetchEnabled(this)
        binding.checkboxAutoFetch.isChecked = autoFetchEnabled
        updateManualInputVisibility(autoFetchEnabled)

        val saved = ConfigManager.getSavedRawInput(this)
        if (saved.isNotEmpty()) {
            binding.editConfigInput.setText(saved)
        }

        // اگه چک‌باکس فعاله، همین موقع باز شدن اپ یه سرور تازه می‌گیریم
        if (autoFetchEnabled) {
            fetchSubscription(defaultSubscriptionUrl, silent = true)
        }

        binding.checkboxAutoFetch.setOnCheckedChangeListener { _, isChecked ->
            ConfigManager.setAutoFetchEnabled(this, isChecked)
            updateManualInputVisibility(isChecked)
            if (isChecked) {
                fetchSubscription(defaultSubscriptionUrl, silent = true)
            }
        }

        binding.btnFetchSubscription.setOnClickListener {
            val subUrl = binding.editSubscriptionUrl.text.toString().trim()
            if (subUrl.isEmpty()) {
                Toast.makeText(this, "لینک سابسکریپشن/سرور را وارد کنید", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            fetchSubscription(subUrl, silent = false)
        }

        binding.btnConnect.setOnClickListener {
            if (isConnected) {
                stopVpnService()
                return@setOnClickListener
            }

            if (binding.checkboxAutoFetch.isChecked) {
                // قبل از اتصال، یه بار دیگه تازه‌ترین سرور رو می‌گیریم تا مطمئن باشیم کار می‌کنه
                lifecycleScope.launch {
                    try {
                        val configs = SubscriptionFetcher.fetchAndDecode(defaultSubscriptionUrl)
                        if (configs.isNotEmpty()) {
                            val chosen = configs.first()
                            binding.editConfigInput.setText(chosen)
                            ConfigManager.saveRawInput(this@MainActivity, chosen)
                            requestVpnPermissionAndStart()
                        } else {
                            Toast.makeText(this@MainActivity, "سروری پیدا نشد، دوباره امتحان کن", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(this@MainActivity, "خطا در دریافت سرور: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            } else {
                val rawInput = binding.editConfigInput.text.toString().trim()
                if (rawInput.isEmpty()) {
                    Toast.makeText(this, "یک کانفیگ (vmess/vless/ss) یا سابسکریپشن وارد کنید", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                ConfigManager.saveRawInput(this, rawInput)
                requestVpnPermissionAndStart()
            }
        }
    }

    private fun updateManualInputVisibility(autoFetchEnabled: Boolean) {
        binding.manualInputContainer.visibility =
            if (autoFetchEnabled) android.view.View.GONE else android.view.View.VISIBLE
    }

    private fun fetchSubscription(url: String, silent: Boolean) {
        if (!silent) binding.btnFetchSubscription.isEnabled = false
        lifecycleScope.launch {
            try {
                val configs = SubscriptionFetcher.fetchAndDecode(url)
                if (configs.isEmpty()) {
                    if (!silent) Toast.makeText(this@MainActivity, "کانفیگی در این لینک پیدا نشد", Toast.LENGTH_SHORT).show()
                } else {
                    val chosen = configs.first()
                    binding.editConfigInput.setText(chosen)
                    ConfigManager.saveRawInput(this@MainActivity, chosen)
                    if (!silent) Toast.makeText(this@MainActivity, "${configs.size} سرور پیدا شد. اولین سرور انتخاب شد", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                if (!silent) Toast.makeText(this@MainActivity, "خطا در دریافت سابسکریپشن: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                if (!silent) binding.btnFetchSubscription.isEnabled = true
            }
        }
    }

    private fun requestVpnPermissionAndStart() {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            vpnPermissionLauncher.launch(intent)
        } else {
            startVpnService()
        }
    }

    private fun startVpnService() {
        val intent = Intent(this, VpnConnectionService::class.java)
        intent.action = VpnConnectionService.ACTION_CONNECT
        startService(intent)
        setConnectedUi(true)
    }

    private fun stopVpnService() {
        val intent = Intent(this, VpnConnectionService::class.java)
        intent.action = VpnConnectionService.ACTION_DISCONNECT
        startService(intent)
        setConnectedUi(false)
    }

    private fun setConnectedUi(connected: Boolean) {
        isConnected = connected
        binding.btnConnect.text = if (connected) "قطع اتصال" else "اتصال"
        binding.tvStatus.text = if (connected) "وضعیت: متصل" else "وضعیت: قطع"
    }
}
