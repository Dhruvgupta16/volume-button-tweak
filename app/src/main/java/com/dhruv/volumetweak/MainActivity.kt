package com.dhruv.volumetweak

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.view.View
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var btnEnableService: MaterialButton
    private lateinit var tvRamUsage: TextView
    private lateinit var tvCpuUsage: TextView
    private lateinit var switchHaptic: MaterialSwitch
    private lateinit var switchGlyph: MaterialSwitch
    private lateinit var switchTestMode: MaterialSwitch
    private lateinit var layoutWhitelistSelector: View
    private lateinit var tvWhitelistSummary: TextView
    private lateinit var tvLogs: TextView
    private lateinit var scrollLogs: ScrollView
    private lateinit var btnClearLogs: ImageView
    private lateinit var btnCopyLogs: ImageView

    private val monitorHandler = Handler(Looper.getMainLooper())
    private var monitorRunnable: Runnable? = null

    data class AppItem(val label: String, val packageName: String)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        btnEnableService = findViewById(R.id.btnEnableService)
        tvRamUsage = findViewById(R.id.tvRamUsage)
        tvCpuUsage = findViewById(R.id.tvCpuUsage)
        switchHaptic = findViewById(R.id.switchHaptic)
        switchGlyph = findViewById(R.id.switchGlyph)
        switchTestMode = findViewById(R.id.switchTestMode)
        layoutWhitelistSelector = findViewById(R.id.layoutWhitelistSelector)
        tvWhitelistSummary = findViewById(R.id.tvWhitelistSummary)
        tvLogs = findViewById(R.id.tvLogs)
        scrollLogs = findViewById(R.id.scrollLogs)
        btnClearLogs = findViewById(R.id.btnClearLogs)
        btnCopyLogs = findViewById(R.id.btnCopyLogs)

        btnCopyLogs.setOnClickListener {
            val text = LogBuffer.getAllLogsText()
            if (text.isNotBlank()) {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("VolumeTweakLogs", text)
                clipboard.setPrimaryClip(clip)
                android.widget.Toast.makeText(this, "Logs copied to clipboard", android.widget.Toast.LENGTH_SHORT).show()
            } else {
                android.widget.Toast.makeText(this, "No logs to copy", android.widget.Toast.LENGTH_SHORT).show()
            }
        }

        val prefs = getSharedPreferences("prefs", Context.MODE_PRIVATE)

        // Haptic Feedback Switch
        val isHaptic = prefs.getBoolean("haptic_feedback", true)
        switchHaptic.isChecked = isHaptic
        VolumeTweakService.hapticReactionEnabled = isHaptic
        switchHaptic.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("haptic_feedback", isChecked).apply()
            VolumeTweakService.hapticReactionEnabled = isChecked
            if (isChecked) {
                HapticFeedbackController.vibrate(this, 1)
            }
            LogBuffer.log("Haptic feedback: ${if (isChecked) "ENABLED (tested 1 pulse)" else "DISABLED"}")
        }

        // Test Mode Switch
        val isTestMode = prefs.getBoolean("test_mode", false)
        switchTestMode.isChecked = isTestMode
        VolumeTweakService.testModeEnabled = isTestMode
        switchTestMode.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("test_mode", isChecked).apply()
            VolumeTweakService.testModeEnabled = isChecked
            LogBuffer.log("Test mode: ${if (isChecked) "ON (Bypass active)" else "OFF (Music required)"}")
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

        // Whitelist Multi-App Configuration
        val savedWhitelist = prefs.getStringSet("whitelist_packages", emptySet()) ?: emptySet()
        VolumeTweakService.targetAppPackages = savedWhitelist
        updateWhitelistSummaryUI(savedWhitelist)

        layoutWhitelistSelector.setOnClickListener {
            showAppPickerDialog()
        }

        btnEnableService.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        }

        btnClearLogs.setOnClickListener {
            LogBuffer.clear()
        }
    }

    private fun updateWhitelistSummaryUI(packages: Set<String>) {
        if (packages.isEmpty()) {
            tvWhitelistSummary.text = "All Media Apps (No Filter)"
        } else {
            val pm = packageManager
            val names = packages.map { pkg ->
                try {
                    val info = pm.getApplicationInfo(pkg, 0)
                    pm.getApplicationLabel(info).toString()
                } catch (e: Exception) {
                    pkg
                }
            }
            tvWhitelistSummary.text = if (names.size == 1) {
                names.first()
            } else {
                "${names.take(2).joinToString(", ")} (+${names.size - 2} more)"
            }
        }
    }

    private fun showAppPickerDialog() {
        val pm = packageManager
        val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)

        val appList = mutableListOf<AppItem>()
        for (app in installedApps) {
            val pkg = app.packageName
            if (pkg == packageName) continue
            val launchIntent = pm.getLaunchIntentForPackage(pkg)
            if (launchIntent != null) {
                val label = pm.getApplicationLabel(app).toString()
                appList.add(AppItem(label, pkg))
            }
        }

        // Sort alphabetically
        appList.sortBy { it.label.lowercase() }

        val appNames = appList.map { it.label }.toTypedArray()
        val currentSelected = VolumeTweakService.targetAppPackages.toMutableSet()
        val checkedItems = BooleanArray(appList.size) { i ->
            currentSelected.contains(appList[i].packageName)
        }

        AlertDialog.Builder(this)
            .setTitle("Select Target Media Apps")
            .setMultiChoiceItems(appNames, checkedItems) { _, which, isChecked ->
                val pkg = appList[which].packageName
                if (isChecked) {
                    currentSelected.add(pkg)
                } else {
                    currentSelected.remove(pkg)
                }
            }
            .setPositiveButton("Save") { _, _ ->
                val prefs = getSharedPreferences("prefs", Context.MODE_PRIVATE)
                prefs.edit().putStringSet("whitelist_packages", currentSelected).apply()
                VolumeTweakService.targetAppPackages = currentSelected
                updateWhitelistSummaryUI(currentSelected)
                LogBuffer.log("Whitelist updated: ${currentSelected.size} apps selected")
            }
            .setNeutralButton("Clear (All Apps)") { _, _ ->
                val prefs = getSharedPreferences("prefs", Context.MODE_PRIVATE)
                prefs.edit().putStringSet("whitelist_packages", emptySet()).apply()
                VolumeTweakService.targetAppPackages = emptySet()
                updateWhitelistSummaryUI(emptySet())
                LogBuffer.log("Whitelist cleared: All media apps enabled")
            }
            .setNegativeButton("Cancel", null)
            .show()
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
                val stats = PerformanceMonitor.getMemoryStats(this@MainActivity)
                val cpuStr = PerformanceMonitor.getCpuUsagePercent()
                tvRamUsage.text = String.format("App: %.1f MB (PSS: %.1f MB)", stats.privateDirtyMb, stats.pssMb)
                tvCpuUsage.text = "CPU: $cpuStr"
                monitorHandler.postDelayed(this, 3500)
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
