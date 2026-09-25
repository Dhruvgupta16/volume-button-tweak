package com.dhruv.volumetweak

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.view.View
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var tvAppVersion: TextView
    private lateinit var btnCheckUpdates: View
    private lateinit var tvUpdateStatus: TextView
    private lateinit var btnEnableService: MaterialButton

    private lateinit var switchMasterKill: MaterialSwitch
    private lateinit var tvMasterStatusTitle: TextView
    private lateinit var tvMasterStatusSubtitle: TextView

    private lateinit var tvRamUsage: TextView
    private lateinit var tvCpuUsage: TextView

    private lateinit var rowGesture1: View
    private lateinit var tvAction1: TextView
    private lateinit var rowGesture2: View
    private lateinit var tvAction2: TextView
    private lateinit var rowGesture3: View
    private lateinit var tvAction3: TextView
    private lateinit var rowSensitivity: View
    private lateinit var tvSensitivity: TextView

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
    private var currentVersionName: String = "1.6"

    data class AppItem(val label: String, val packageName: String)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Bind Views
        tvStatus = findViewById(R.id.tvStatus)
        tvAppVersion = findViewById(R.id.tvAppVersion)
        btnCheckUpdates = findViewById(R.id.btnCheckUpdates)
        tvUpdateStatus = findViewById(R.id.tvUpdateStatus)
        btnEnableService = findViewById(R.id.btnEnableService)

        switchMasterKill = findViewById(R.id.switchMasterKill)
        tvMasterStatusTitle = findViewById(R.id.tvMasterStatusTitle)
        tvMasterStatusSubtitle = findViewById(R.id.tvMasterStatusSubtitle)

        tvRamUsage = findViewById(R.id.tvRamUsage)
        tvCpuUsage = findViewById(R.id.tvCpuUsage)

        rowGesture1 = findViewById(R.id.rowGesture1)
        tvAction1 = findViewById(R.id.tvAction1)
        rowGesture2 = findViewById(R.id.rowGesture2)
        tvAction2 = findViewById(R.id.tvAction2)
        rowGesture3 = findViewById(R.id.rowGesture3)
        tvAction3 = findViewById(R.id.tvAction3)
        rowSensitivity = findViewById(R.id.rowSensitivity)
        tvSensitivity = findViewById(R.id.tvSensitivity)

        switchHaptic = findViewById(R.id.switchHaptic)
        switchGlyph = findViewById(R.id.switchGlyph)
        switchTestMode = findViewById(R.id.switchTestMode)
        layoutWhitelistSelector = findViewById(R.id.layoutWhitelistSelector)
        tvWhitelistSummary = findViewById(R.id.tvWhitelistSummary)

        tvLogs = findViewById(R.id.tvLogs)
        scrollLogs = findViewById(R.id.scrollLogs)
        btnClearLogs = findViewById(R.id.btnClearLogs)
        btnCopyLogs = findViewById(R.id.btnCopyLogs)

        val prefs = getSharedPreferences("prefs", Context.MODE_PRIVATE)

        // Dynamic Version Display
        try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            currentVersionName = pInfo.versionName ?: "1.8"
            val vCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION") pInfo.versionCode.toLong()
            }
            tvAppVersion.text = "v$currentVersionName (Build $vCode)"
        } catch (e: Exception) {
            tvAppVersion.text = "v1.8 (Build 9)"
        }

        // Check for Updates
        btnCheckUpdates.setOnClickListener {
            tvUpdateStatus.text = "Checking..."
            UpdateChecker.checkForUpdate(currentVersionName) { result ->
                if (result.hasUpdate) {
                    tvUpdateStatus.text = "Update!"
                    showUpdateAvailableDialog(result)
                } else {
                    tvUpdateStatus.text = "Up to date"
                    val msg = if (result.errorMessage != null) {
                        "Checked: ${result.errorMessage}"
                    } else {
                        "You are on the latest version (${result.latestVersion})"
                    }
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Master Kill Switch
        val isSuspended = prefs.getBoolean("master_service_suspended", false)
        switchMasterKill.isChecked = !isSuspended
        VolumeTweakService.isServiceSuspended = isSuspended
        updateMasterStatusUI(!isSuspended)

        switchMasterKill.setOnCheckedChangeListener { _, isActive ->
            val suspended = !isActive
            prefs.edit().putBoolean("master_service_suspended", suspended).apply()
            VolumeTweakService.isServiceSuspended = suspended
            updateMasterStatusUI(isActive)
            LogBuffer.log("[MASTER] Service ${if (isActive) "ACTIVATED" else "SUSPENDED (Pass-through mode)"}")
        }

        // Gesture Action 1 Customizer
        val action1 = prefs.getString("action_1_click", "PLAY_PAUSE") ?: "PLAY_PAUSE"
        VolumeTweakService.action1Click = action1
        tvAction1.text = formatActionLabel(action1)
        rowGesture1.setOnClickListener {
            showActionPicker("1 Click Action", listOf("PLAY_PAUSE", "NEXT", "PREV", "MUTE", "FLASHLIGHT"), action1) { selected ->
                prefs.edit().putString("action_1_click", selected).apply()
                VolumeTweakService.action1Click = selected
                tvAction1.text = formatActionLabel(selected)
                LogBuffer.log("Config: 1 Click -> $selected")
            }
        }

        // Gesture Action 2 Customizer
        val action2 = prefs.getString("action_2_clicks", "NEXT") ?: "NEXT"
        VolumeTweakService.action2Clicks = action2
        tvAction2.text = formatActionLabel(action2)
        rowGesture2.setOnClickListener {
            showActionPicker("2 Clicks Action", listOf("NEXT", "PLAY_PAUSE", "PREV"), action2) { selected ->
                prefs.edit().putString("action_2_clicks", selected).apply()
                VolumeTweakService.action2Clicks = selected
                tvAction2.text = formatActionLabel(selected)
                LogBuffer.log("Config: 2 Clicks -> $selected")
            }
        }

        // Gesture Action 3 Customizer
        val action3 = prefs.getString("action_3_clicks", "PREV") ?: "PREV"
        VolumeTweakService.action3Clicks = action3
        tvAction3.text = formatActionLabel(action3)
        rowGesture3.setOnClickListener {
            showActionPicker("3 Clicks Action", listOf("PREV", "NEXT", "PLAY_PAUSE"), action3) { selected ->
                prefs.edit().putString("action_3_clicks", selected).apply()
                VolumeTweakService.action3Clicks = selected
                tvAction3.text = formatActionLabel(selected)
                LogBuffer.log("Config: 3 Clicks -> $selected")
            }
        }

        // Sensitivity (Dual-Press Window) Customizer
        val windowMs = prefs.getLong("dual_press_window_ms", 140L)
        VolumeTweakService.dualPressWindowMs = windowMs
        tvSensitivity.text = formatSensitivityLabel(windowMs)
        rowSensitivity.setOnClickListener {
            showSensitivityPicker(windowMs) { selectedMs ->
                prefs.edit().putLong("dual_press_window_ms", selectedMs).apply()
                VolumeTweakService.dualPressWindowMs = selectedMs
                tvSensitivity.text = formatSensitivityLabel(selectedMs)
                LogBuffer.log("Config: Window -> ${selectedMs}ms")
            }
        }

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
            LogBuffer.log("Haptic feedback: ${if (isChecked) "ENABLED" else "DISABLED"}")
        }

        // Test Mode Switch
        val isTestMode = prefs.getBoolean("test_mode", false)
        switchTestMode.isChecked = isTestMode
        VolumeTweakService.testModeEnabled = isTestMode
        switchTestMode.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("test_mode", isChecked).apply()
            VolumeTweakService.testModeEnabled = isChecked
            LogBuffer.log("Test mode: ${if (isChecked) "ON (Music bypass active)" else "OFF (Music required)"}")
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

        btnCopyLogs.setOnClickListener {
            val text = LogBuffer.getAllLogsText()
            if (text.isNotBlank()) {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("VolumeTweakLogs", text)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "Logs copied to clipboard", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "No logs to copy", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateMasterStatusUI(isActive: Boolean) {
        if (isActive) {
            tvMasterStatusTitle.text = "Service Status: ACTIVE"
            tvMasterStatusTitle.setTextColor(Color.WHITE)
            tvMasterStatusSubtitle.text = "Intercepting volume button gestures"
        } else {
            tvMasterStatusTitle.text = "Service Status: SUSPENDED"
            tvMasterStatusTitle.setTextColor(Color.parseColor("#E50914"))
            tvMasterStatusSubtitle.text = "Volume buttons 100% normal (tweak sleeping)"
        }
    }

    private fun formatActionLabel(action: String): String {
        return when (action) {
            "PLAY_PAUSE" -> "Play / Pause"
            "NEXT" -> "Next Track"
            "PREV" -> "Previous Track"
            "MUTE" -> "Toggle Mute"
            "FLASHLIGHT" -> "Flashlight Pulse"
            else -> action
        }
    }

    private fun formatSensitivityLabel(ms: Long): String {
        return when (ms) {
            100L -> "Tight (100ms)"
            140L -> "Balanced (140ms)"
            180L -> "Relaxed (180ms)"
            else -> "${ms}ms"
        }
    }

    private fun showActionPicker(title: String, options: List<String>, current: String, onSelect: (String) -> Unit) {
        val labels = options.map { formatActionLabel(it) }.toTypedArray()
        val currentIndex = options.indexOf(current).coerceAtLeast(0)

        AlertDialog.Builder(this)
            .setTitle(title)
            .setSingleChoiceItems(labels, currentIndex) { dialog, which ->
                onSelect(options[which])
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showSensitivityPicker(currentMs: Long, onSelect: (Long) -> Unit) {
        val options = listOf(100L, 140L, 180L)
        val labels = options.map { formatSensitivityLabel(it) }.toTypedArray()
        val currentIndex = options.indexOf(currentMs).coerceAtLeast(1)

        AlertDialog.Builder(this)
            .setTitle("Dual-Press Sensitivity Window")
            .setSingleChoiceItems(labels, currentIndex) { dialog, which ->
                onSelect(options[which])
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showUpdateAvailableDialog(result: UpdateChecker.CheckResult) {
        AlertDialog.Builder(this)
            .setTitle("New Update: ${result.latestVersion}")
            .setMessage("A new version is available on GitHub!\n\nRelease info:\n${result.releaseNotes ?: "Bug fixes and performance improvements."}")
            .setPositiveButton("Download & Install") { _, _ ->
                UpdateChecker.openDownloadUrl(this, result.downloadUrl)
            }
            .setNegativeButton("Later", null)
            .show()
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
                LogBuffer.log("Whitelist: ${currentSelected.size} apps selected")
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
            btnEnableService.visibility = View.GONE
        } else {
            tvStatus.text = "DISABLED"
            tvStatus.setTextColor(Color.parseColor("#E50914"))
            btnEnableService.visibility = View.VISIBLE
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
