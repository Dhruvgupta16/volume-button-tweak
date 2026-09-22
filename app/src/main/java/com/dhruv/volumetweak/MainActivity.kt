package com.dhruv.volumetweak

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var btnEnableService: MaterialButton
    private lateinit var switchTestMode: MaterialSwitch
    private lateinit var tvLogs: TextView
    private lateinit var scrollLogs: ScrollView
    private lateinit var btnClearLogs: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        btnEnableService = findViewById(R.id.btnEnableService)
        switchTestMode = findViewById(R.id.switchTestMode)
        tvLogs = findViewById(R.id.tvLogs)
        scrollLogs = findViewById(R.id.scrollLogs)
        btnClearLogs = findViewById(R.id.btnClearLogs)

        val prefs = getSharedPreferences("prefs", Context.MODE_PRIVATE)
        val isTestMode = prefs.getBoolean("test_mode", false)
        switchTestMode.isChecked = isTestMode
        VolumeTweakService.testModeEnabled = isTestMode

        switchTestMode.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("test_mode", isChecked).apply()
            VolumeTweakService.testModeEnabled = isChecked
            LogBuffer.log("Test mode: ${if (isChecked) "ON (music check bypassed)" else "OFF (music required)"}")
        }

        btnEnableService.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        }

        btnClearLogs.setOnClickListener {
            LogBuffer.clear()
        }
    }

    override fun onResume() {
        super.onResume()
        updateServiceStatus()
        LogBuffer.setListener { logList ->
            if (logList.isEmpty()) {
                tvLogs.text = "Waiting for button events..."
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
        LogBuffer.setListener(null)
    }

    private fun updateServiceStatus() {
        val isEnabled = isAccessibilityServiceEnabled(this, VolumeTweakService::class.java)
        if (isEnabled) {
            tvStatus.text = getString(R.string.status_enabled)
            tvStatus.setTextColor(Color.parseColor("#4CAF50"))
            btnEnableService.text = "Accessibility Permission Granted"
            btnEnableService.isEnabled = true
        } else {
            tvStatus.text = getString(R.string.status_disabled)
            tvStatus.setTextColor(Color.parseColor("#E50914"))
            btnEnableService.text = getString(R.string.enable_service_button)
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
