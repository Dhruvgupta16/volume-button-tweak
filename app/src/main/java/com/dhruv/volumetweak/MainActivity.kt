package com.dhruv.volumetweak

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var btnEnableService: MaterialButton
    private lateinit var tvRamUsage: TextView
    private lateinit var tvCpuUsage: TextView
    private lateinit var switchGlyph: MaterialSwitch
    private lateinit var switchTestMode: MaterialSwitch
    private lateinit var tvWhitelistApp: TextView
    private lateinit var tvLogs: TextView
    private lateinit var scrollLogs: ScrollView
    private lateinit var btnClearLogs: ImageView

    private val monitorHandler = Handler(Looper.getMainLooper())
    private var monitorRunnable: Runnable? = null

    private val whitelistOptions = listOf(
        "All Media Apps" to "ALL",
        "YouTube Music" to "com.google.android.apps.youtube.music",
        "Spotify" to "com.spotify.music"
    )
    private var whitelistIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        btnEnableService = findViewById(R.id.btnEnableService)
        tvRamUsage = findViewById(R.id.tvRamUsage)
        tvCpuUsage = findViewById(R.id.tvCpuUsage)
        switchGlyph = findViewById(R.id.switchGlyph)
        switchTestMode = findViewById(R.id.switchTestMode)
        tvWhitelistApp = findViewById(R.id.tvWhitelistApp)
        tvLogs = findViewById(R.id.tvLogs)
        scrollLogs = findViewById(R.id.scrollLogs)
        btnClearLogs = findViewById(R.id.btnClearLogs)

        val prefs = getSharedPreferences("prefs", Context.MODE_PRIVATE)

        // Test Mode
        val isTestMode = prefs.getBoolean("test_mode", false)
        switchTestMode.isChecked = isTestMode
        VolumeTweakService.testModeEnabled = isTestMode

        switchTestMode.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("test_mode", isChecked).apply()
            VolumeTweakService.testModeEnabled = isChecked
            LogBuffer.log("Test mode: ${if (isChecked) "ON (Music bypass enabled)" else "OFF (Music active required)"}")
        }

        // Glyph Switch
        val isGlyph = prefs.getBoolean("glyph_reaction", false)
        switchGlyph.isChecked = isGlyph
        VolumeTweakService.glyphReactionEnabled = isGlyph

        switchGlyph.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("glyph_reaction", isChecked).apply()
            VolumeTweakService.glyphReactionEnabled = isChecked
            LogBuffer.log("Glyph reaction: ${if (isChecked) "ENABLED" else "DISABLED"}")
        }

        // App Whitelist
        val savedPkg = prefs.getString("whitelist_pkg", "ALL") ?: "ALL"
        whitelistIndex = whitelistOptions.indexOfFirst { it.second == savedPkg }.coerceAtLeast(0)
        updateWhitelistUI()

        tvWhitelistApp.setOnClickListener {
            whitelistIndex = (whitelistIndex + 1) % whitelistOptions.size
            val selected = whitelistOptions[whitelistIndex]
            prefs.edit().putString("whitelist_pkg", selected.second).apply()
            VolumeTweakService.targetAppPackage = selected.second
            updateWhitelistUI()
            LogBuffer.log("Target filter: ${selected.first}")
        }

        btnEnableService.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        }

        btnClearLogs.setOnClickListener {
            LogBuffer.clear()
        }
    }

    private fun updateWhitelistUI() {
        val selected = whitelistOptions[whitelistIndex]
        tvWhitelistApp.text = selected.first
    }

    override fun onResume() {
        super.onResume()
        updateServiceStatus()
        startPerformanceMonitoring()

        LogBuffer.setListener { logList ->
            if (logList.isEmpty()) {
                tvLogs.text = "Awaiting events..."
            } else {
                tvLogs.text = logList.joinToString("\n")
                scrollLogs.post {
                    scrollLogs.fullScroll(ScrollView.FOCUS_DOWN)
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        stopPerformanceMonitoring()
        LogBuffer.setListener(null)
    }

    private fun startPerformanceMonitoring() {
        monitorRunnable = object : Runnable {
            override fun run() {
                val ramMb = PerformanceMonitor.getMemoryUsageMB(this@MainActivity)
                val cpuStr = PerformanceMonitor.getCpuUsagePercent()
                tvRamUsage.text = String.format("RAM: %.1f MB", ramMb)
                tvCpuUsage.text = "CPU: $cpuStr"
                monitorHandler.postDelayed(this, 1800)
            }
        }
        monitorHandler.post(monitorRunnable!!)
    }

    private fun stopPerformanceMonitoring() {
        monitorRunnable?.let { monitorHandler.removeCallbacks(it) }
        monitorRunnable = null
    }

    private fun updateServiceStatus() {
        val isEnabled = isAccessibilityServiceEnabled(this, VolumeTweakService::class.java)
        if (isEnabled) {
            tvStatus.text = "ACTIVE"
            tvStatus.setTextColor(Color.parseColor("#4CAF50"))
            btnEnableService.text = "Accessibility Active"
            btnEnableService.isEnabled = true
        } else {
            tvStatus.text = "DISABLED"
            tvStatus.setTextColor(Color.parseColor("#E50914"))
            btnEnableService.text = "Enable Accessibility Service"
            btnEnableService.isEnabled = true
        }
    }

    private fun isAccessibilityServiceEnabled(context: Context, serviceClass: Class<*>): Boolean {
        val expectedComponentName = ComponentName(context, serviceClass)
        val enabledServicesSetting = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val stringSplitter = TextUtils.SimpleStringSplitter(':')
        stringSplitter.setString(enabledServicesSetting)

        while (stringSplitter.hasNext()) {
            val componentNameString = stringSplitter.next()
            val enabledComponentName = ComponentName.unflattenFromString(componentNameString)
            if (enabledComponentName != null && enabledComponentName == expectedComponentName) {
                return true
            }
        }
        return false
    }
}
