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
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
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
    private lateinit var rowGesture4: View
    private lateinit var tvAction4: TextView
    private lateinit var rowSensitivity: View
    private lateinit var tvSensitivity: TextView

    private lateinit var switchCombo: MaterialSwitch
    private lateinit var layoutComboOptions: View
    private lateinit var containerCustomCombos: LinearLayout
    private lateinit var btnAddCustomCombo: MaterialButton

    private lateinit var switchHaptic: MaterialSwitch
    private lateinit var switchProximity: MaterialSwitch
    private lateinit var switchGlyph: MaterialSwitch
    private lateinit var switchTestMode: MaterialSwitch
    private lateinit var layoutWhitelistSelector: View
    private lateinit var tvWhitelistSummary: TextView

    private lateinit var tvLogs: TextView
    private lateinit var scrollLogs: ScrollView
    private lateinit var btnClearLogs: View
    private lateinit var btnCopyLogs: View

    private val monitorHandler = Handler(Looper.getMainLooper())
    private var monitorRunnable: Runnable? = null
    private var currentVersionName: String = "1.9.4"

    private val customCombosList = mutableListOf<CustomCombo>()

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
        rowGesture4 = findViewById(R.id.rowGesture4)
        tvAction4 = findViewById(R.id.tvAction4)
        rowSensitivity = findViewById(R.id.rowSensitivity)
        tvSensitivity = findViewById(R.id.tvSensitivity)

        switchCombo = findViewById(R.id.switchCombo)
        layoutComboOptions = findViewById(R.id.layoutComboOptions)
        containerCustomCombos = findViewById(R.id.containerCustomCombos)
        btnAddCustomCombo = findViewById(R.id.btnAddCustomCombo)

        switchHaptic = findViewById(R.id.switchHaptic)
        switchProximity = findViewById(R.id.switchProximity)
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
            currentVersionName = pInfo.versionName ?: "1.9.2"
            val vCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION") pInfo.versionCode.toLong()
            }
            tvAppVersion.text = "v$currentVersionName (Build $vCode)"
        } catch (e: Exception) {
            tvAppVersion.text = "v1.9.2 (Build 12)"
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
        val isSuspended = prefs.getBoolean("service_suspended", false)
        switchMasterKill.isChecked = !isSuspended
        VolumeTweakService.isServiceSuspended = isSuspended
        updateMasterStatusUI(!isSuspended)

        switchMasterKill.setOnCheckedChangeListener { _, isChecked ->
            val suspended = !isChecked
            prefs.edit().putBoolean("service_suspended", suspended).apply()
            VolumeTweakService.isServiceSuspended = suspended
            updateMasterStatusUI(isChecked)
            LogBuffer.log("Master switch: ${if (isChecked) "ACTIVE" else "SUSPENDED"}")
        }

        // Standard Dual-Press Gestures
        val action1 = prefs.getString("action_1_click", "PLAY_PAUSE") ?: "PLAY_PAUSE"
        VolumeTweakService.action1Click = action1
        tvAction1.text = ActionRegistry.getTitle(action1)
        rowGesture1.setOnClickListener {
            showActionPicker("1 Click Action", action1) { selected ->
                prefs.edit().putString("action_1_click", selected).apply()
                VolumeTweakService.action1Click = selected
                tvAction1.text = ActionRegistry.getTitle(selected)
                LogBuffer.log("Config: 1 Click -> $selected")
            }
        }

        val action2 = prefs.getString("action_2_clicks", "NEXT") ?: "NEXT"
        VolumeTweakService.action2Clicks = action2
        tvAction2.text = ActionRegistry.getTitle(action2)
        rowGesture2.setOnClickListener {
            showActionPicker("2 Clicks Action", action2) { selected ->
                prefs.edit().putString("action_2_clicks", selected).apply()
                VolumeTweakService.action2Clicks = selected
                tvAction2.text = ActionRegistry.getTitle(selected)
                LogBuffer.log("Config: 2 Clicks -> $selected")
            }
        }

        val action3 = prefs.getString("action_3_clicks", "PREV") ?: "PREV"
        VolumeTweakService.action3Clicks = action3
        tvAction3.text = ActionRegistry.getTitle(action3)
        rowGesture3.setOnClickListener {
            showActionPicker("3 Clicks Action", action3) { selected ->
                prefs.edit().putString("action_3_clicks", selected).apply()
                VolumeTweakService.action3Clicks = selected
                tvAction3.text = ActionRegistry.getTitle(selected)
                LogBuffer.log("Config: 3 Clicks -> $selected")
            }
        }

        val action4 = prefs.getString("action_4_clicks", "SKIP_FWD_15") ?: "SKIP_FWD_15"
        VolumeTweakService.action4Clicks = action4
        tvAction4.text = ActionRegistry.getTitle(action4)
        rowGesture4.setOnClickListener {
            showActionPicker("4 Clicks Action", action4) { selected ->
                prefs.edit().putString("action_4_clicks", selected).apply()
                VolumeTweakService.action4Clicks = selected
                tvAction4.text = ActionRegistry.getTitle(selected)
                LogBuffer.log("Config: 4 Clicks -> $selected")
            }
        }

        // Sensitivity (Dual-Press Window) Customizer
        val windowMs = prefs.getLong("dual_press_window", 140L)
        VolumeTweakService.dualPressWindowMs = windowMs
        tvSensitivity.text = formatSensitivityLabel(windowMs)
        rowSensitivity.setOnClickListener {
            showSensitivityPicker(windowMs) { selectedMs ->
                prefs.edit().putLong("dual_press_window", selectedMs).apply()
                VolumeTweakService.dualPressWindowMs = selectedMs
                tvSensitivity.text = formatSensitivityLabel(selectedMs)
                LogBuffer.log("Config: Window -> ${selectedMs}ms")
            }
        }

        // Combo Sequences Setup
        val isCombo = prefs.getBoolean("combo_sequences_enabled", true)
        switchCombo.isChecked = isCombo
        VolumeTweakService.comboSequencesEnabled = isCombo
        layoutComboOptions.visibility = if (isCombo) View.VISIBLE else View.GONE

        switchCombo.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("combo_sequences_enabled", isChecked).apply()
            VolumeTweakService.comboSequencesEnabled = isChecked
            layoutComboOptions.visibility = if (isChecked) View.VISIBLE else View.GONE
            LogBuffer.log("Combo Sequences: ${if (isChecked) "ENABLED" else "DISABLED"}")
        }

        // Load & Render Dynamic Custom Combos
        val savedCombosJson = prefs.getString("custom_combos_json", null)
        customCombosList.clear()
        customCombosList.addAll(CustomCombo.parseList(savedCombosJson))
        VolumeTweakService.customCombos = customCombosList
        renderCustomCombos()

        btnAddCustomCombo.setOnClickListener {
            showSequenceBuilderDialog()
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

        // Proximity Sensor Guard Switch
        val isProximity = prefs.getBoolean("proximity_pocket_guard", false)
        switchProximity.isChecked = isProximity
        VolumeTweakService.proximitySensorEnabled = isProximity
        switchProximity.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("proximity_pocket_guard", isChecked).apply()
            VolumeTweakService.proximitySensorEnabled = isChecked
            LogBuffer.log("Proximity Guard: ${if (isChecked) "ENABLED" else "DISABLED"}")
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
        val savedWhitelist = prefs.getStringSet("target_apps", emptySet()) ?: emptySet()
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

    private fun renderCustomCombos() {
        containerCustomCombos.removeAllViews()
        val inflater = LayoutInflater.from(this)

        if (customCombosList.isEmpty()) {
            val emptyTv = TextView(this).apply {
                text = "No combos added yet. Tap '+ ADD CUSTOM SEQUENCE' below."
                setTextColor(Color.parseColor("#666666"))
                textSize = 12f
                setPadding(0, 16, 0, 16)
            }
            containerCustomCombos.addView(emptyTv)
            return
        }

        for (combo in customCombosList) {
            val itemView = inflater.inflate(R.layout.item_custom_combo, containerCustomCombos, false)

            val tvSequence = itemView.findViewById<TextView>(R.id.tvComboSequence)
            val tvAction = itemView.findViewById<TextView>(R.id.tvComboAction)
            val switchItem = itemView.findViewById<MaterialSwitch>(R.id.switchComboItem)
            val btnDelete = itemView.findViewById<ImageView>(R.id.btnDeleteCombo)
            val layoutRow = itemView.findViewById<View>(R.id.layoutComboRow)

            tvSequence.text = combo.getDisplaySequence()
            tvAction.text = ActionRegistry.getTitle(combo.action)
            switchItem.isChecked = combo.isEnabled

            switchItem.setOnCheckedChangeListener { _, isChecked ->
                combo.isEnabled = isChecked
                saveCustomCombosToPrefs()
            }

            layoutRow.setOnClickListener {
                showActionPicker("Select Action for ${combo.getDisplaySequence()}", combo.action) { newAction ->
                    combo.action = newAction
                    tvAction.text = ActionRegistry.getTitle(newAction)
                    saveCustomCombosToPrefs()
                }
            }

            btnDelete.setOnClickListener {
                AlertDialog.Builder(this)
                    .setTitle("Delete Sequence")
                    .setMessage("Remove sequence '${combo.getDisplaySequence()}'?")
                    .setPositiveButton("Delete") { _, _ ->
                        customCombosList.remove(combo)
                        saveCustomCombosToPrefs()
                        renderCustomCombos()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }

            containerCustomCombos.addView(itemView)
        }
    }

    private fun saveCustomCombosToPrefs() {
        val prefs = getSharedPreferences("prefs", Context.MODE_PRIVATE)
        val jsonStr = CustomCombo.toJsonList(customCombosList)
        prefs.edit().putString("custom_combos_json", jsonStr).apply()
        VolumeTweakService.customCombos = customCombosList.toList()
        LogBuffer.log("[COMBO CONFIG] Saved ${customCombosList.size} sequences")
    }

    private fun showSequenceBuilderDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_sequence_builder, null)
        val builderTokens = mutableListOf("DUAL")
        var chosenAction = "SKIP_FWD_15"

        val tvDisplay = dialogView.findViewById<TextView>(R.id.tvBuilderSequenceDisplay)
        val tvActionTitle = dialogView.findViewById<TextView>(R.id.tvBuilderActionTitle)
        val rowSelectAction = dialogView.findViewById<View>(R.id.rowBuilderSelectAction)

        val btnDual = dialogView.findViewById<MaterialButton>(R.id.btnTokenDual)
        val btnDualHold = dialogView.findViewById<MaterialButton>(R.id.btnTokenDualHold)
        val btnUp = dialogView.findViewById<MaterialButton>(R.id.btnTokenUp)
        val btnUpHold = dialogView.findViewById<MaterialButton>(R.id.btnTokenUpHold)
        val btnDown = dialogView.findViewById<MaterialButton>(R.id.btnTokenDown)
        val btnDownHold = dialogView.findViewById<MaterialButton>(R.id.btnTokenDownHold)
        val btnBackspace = dialogView.findViewById<MaterialButton>(R.id.btnTokenBackspace)
        val btnClear = dialogView.findViewById<MaterialButton>(R.id.btnTokenClear)

        val btnCancel = dialogView.findViewById<MaterialButton>(R.id.btnBuilderCancel)
        val btnSave = dialogView.findViewById<MaterialButton>(R.id.btnBuilderSave)

        fun updateUI() {
            if (builderTokens.isEmpty()) {
                tvDisplay.text = "No inputs added yet"
                tvDisplay.setTextColor(Color.parseColor("#7A7A7A"))
            } else {
                tvDisplay.text = builderTokens.joinToString(" → ") { CustomCombo.formatToken(it) }
                tvDisplay.setTextColor(Color.WHITE)
            }
            tvActionTitle.text = ActionRegistry.getTitle(chosenAction)
        }

        updateUI()

        btnDual.setOnClickListener { builderTokens.add("DUAL"); updateUI() }
        btnDualHold.setOnClickListener { builderTokens.add("DUAL_HOLD"); updateUI() }
        btnUp.setOnClickListener { builderTokens.add("UP"); updateUI() }
        btnUpHold.setOnClickListener { builderTokens.add("UP_HOLD"); updateUI() }
        btnDown.setOnClickListener { builderTokens.add("DOWN"); updateUI() }
        btnDownHold.setOnClickListener { builderTokens.add("DOWN_HOLD"); updateUI() }

        btnBackspace.setOnClickListener {
            if (builderTokens.isNotEmpty()) {
                builderTokens.removeAt(builderTokens.size - 1)
                updateUI()
            }
        }

        btnClear.setOnClickListener {
            builderTokens.clear()
            updateUI()
        }

        rowSelectAction.setOnClickListener {
            showActionPicker("Select Sequence Action", chosenAction) { selected ->
                chosenAction = selected
                updateUI()
            }
        }

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnSave.setOnClickListener {
            if (builderTokens.isEmpty()) {
                Toast.makeText(this, "Please add at least one button step.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val newCombo = CustomCombo(
                id = "combo_${System.currentTimeMillis()}",
                tokens = builderTokens.toList(),
                action = chosenAction,
                isEnabled = true
            )
            customCombosList.add(newCombo)
            saveCustomCombosToPrefs()
            renderCustomCombos()
            dialog.dismiss()
            Toast.makeText(this, "Custom sequence added!", Toast.LENGTH_SHORT).show()
        }

        dialog.show()
    }

    private fun updateMasterStatusUI(isActive: Boolean) {
        if (isActive) {
            tvMasterStatusTitle.text = "TWEAK ACTIVE"
            tvMasterStatusTitle.setTextColor(Color.WHITE)
            tvMasterStatusSubtitle.text = "Intercepting volume button gestures"
        } else {
            tvMasterStatusTitle.text = "TWEAK SUSPENDED"
            tvMasterStatusTitle.setTextColor(Color.parseColor("#E50914"))
            tvMasterStatusSubtitle.text = "Volume buttons 100% normal (tweak sleeping)"
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

    private fun showActionPicker(title: String, currentActionId: String, onSelect: (String) -> Unit) {
        val actions = ActionRegistry.ALL_ACTIONS
        val labels = actions.map { "${it.title}  •  ${it.category}" }.toTypedArray()
        val currentIndex = actions.indexOfFirst { it.id == currentActionId }.coerceAtLeast(0)

        AlertDialog.Builder(this)
            .setTitle(title)
            .setSingleChoiceItems(labels, currentIndex) { dialog, which ->
                onSelect(actions[which].id)
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
        val apkUrl = result.downloadUrl
        if (apkUrl.isNullOrBlank()) {
            Toast.makeText(this, "No download URL available for this update.", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Update Available: ${result.latestVersion}")
            .setMessage("A new version is ready to install!\n\nRelease info:\n${result.releaseNotes ?: "Performance improvements and bug fixes."}")
            .setPositiveButton("Update Now") { _, _ ->
                startInAppUpdate(apkUrl)
            }
            .setNegativeButton("Later", null)
            .show()
    }

    private fun startInAppUpdate(downloadUrl: String) {
        val dp = resources.displayMetrics.density
        val padding = (20 * dp).toInt()
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
        }

        val tvProgress = TextView(this).apply {
            text = "Downloading update... 0%"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 14f
            setPadding(0, 0, 0, (12 * dp).toInt())
        }

        val progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = false
            max = 100
            progress = 0
        }

        layout.addView(tvProgress)
        layout.addView(progressBar)

        val progressDialog = AlertDialog.Builder(this)
            .setTitle("Installing Update")
            .setView(layout)
            .setCancelable(false)
            .create()

        progressDialog.show()

        UpdateChecker.downloadAndInstallApk(
            activity = this,
            downloadUrl = downloadUrl,
            onProgress = { percent ->
                progressBar.progress = percent
                tvProgress.text = "Downloading update... $percent%"
            },
            onComplete = {
                progressDialog.dismiss()
            },
            onError = { errMsg ->
                progressDialog.dismiss()
                AlertDialog.Builder(this)
                    .setTitle("Update Notice")
                    .setMessage(errMsg)
                    .setPositiveButton("OK", null)
                    .show()
            }
        )
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
                prefs.edit().putStringSet("target_apps", currentSelected).apply()
                VolumeTweakService.targetAppPackages = currentSelected
                updateWhitelistSummaryUI(currentSelected)
                LogBuffer.log("Whitelist: ${currentSelected.size} apps selected")
            }
            .setNeutralButton("Clear (All Apps)") { _, _ ->
                val prefs = getSharedPreferences("prefs", Context.MODE_PRIVATE)
                prefs.edit().putStringSet("target_apps", emptySet()).apply()
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

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_UI_HIDDEN) {
            System.gc()
        }
    }

    private fun startPerformanceMonitoring() {
        monitorRunnable = object : Runnable {
            override fun run() {
                val stats = PerformanceMonitor.getMemoryStats()
                val cpuStr = PerformanceMonitor.getCpuUsagePercent()
                tvRamUsage.text = String.format("Heap: %.1f MB (PSS: %.0fM)", stats.appHeapMb, stats.pssMb)
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
