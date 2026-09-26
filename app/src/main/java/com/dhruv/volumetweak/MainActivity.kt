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
import android.os.SystemClock
import android.provider.Settings
import android.text.TextUtils
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.SeekBar
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
    private lateinit var imgActionIcon1: ImageView
    private lateinit var rowGesture2: View
    private lateinit var tvAction2: TextView
    private lateinit var imgActionIcon2: ImageView
    private lateinit var rowGesture3: View
    private lateinit var tvAction3: TextView
    private lateinit var imgActionIcon3: ImageView
    private lateinit var rowGesture4: View
    private lateinit var tvAction4: TextView
    private lateinit var imgActionIcon4: ImageView
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
    private var currentVersionName = "1.9.6"

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
        imgActionIcon1 = findViewById(R.id.imgActionIcon1)
        rowGesture2 = findViewById(R.id.rowGesture2)
        tvAction2 = findViewById(R.id.tvAction2)
        imgActionIcon2 = findViewById(R.id.imgActionIcon2)
        rowGesture3 = findViewById(R.id.rowGesture3)
        tvAction3 = findViewById(R.id.tvAction3)
        imgActionIcon3 = findViewById(R.id.imgActionIcon3)
        rowGesture4 = findViewById(R.id.rowGesture4)
        tvAction4 = findViewById(R.id.tvAction4)
        imgActionIcon4 = findViewById(R.id.imgActionIcon4)
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
        updateGestureRowUI(1, action1)
        rowGesture1.setOnClickListener {
            handleActionSelection("1 Click Action", VolumeTweakService.action1Click) { selected ->
                prefs.edit().putString("action_1_click", selected).apply()
                VolumeTweakService.action1Click = selected
                updateGestureRowUI(1, selected)
                LogBuffer.log("Config: 1 Click -> $selected")
            }
        }

        val action2 = prefs.getString("action_2_clicks", "NEXT") ?: "NEXT"
        VolumeTweakService.action2Clicks = action2
        updateGestureRowUI(2, action2)
        rowGesture2.setOnClickListener {
            handleActionSelection("2 Clicks Action", VolumeTweakService.action2Clicks) { selected ->
                prefs.edit().putString("action_2_clicks", selected).apply()
                VolumeTweakService.action2Clicks = selected
                updateGestureRowUI(2, selected)
                LogBuffer.log("Config: 2 Clicks -> $selected")
            }
        }

        val action3 = prefs.getString("action_3_clicks", "PREV") ?: "PREV"
        VolumeTweakService.action3Clicks = action3
        updateGestureRowUI(3, action3)
        rowGesture3.setOnClickListener {
            handleActionSelection("3 Clicks Action", VolumeTweakService.action3Clicks) { selected ->
                prefs.edit().putString("action_3_clicks", selected).apply()
                VolumeTweakService.action3Clicks = selected
                updateGestureRowUI(3, selected)
                LogBuffer.log("Config: 3 Clicks -> $selected")
            }
        }

        val action4 = prefs.getString("action_4_clicks", "SKIP_FWD_15") ?: "SKIP_FWD_15"
        VolumeTweakService.action4Clicks = action4
        updateGestureRowUI(4, action4)
        rowGesture4.setOnClickListener {
            handleActionSelection("4 Clicks Action", VolumeTweakService.action4Clicks) { selected ->
                prefs.edit().putString("action_4_clicks", selected).apply()
                VolumeTweakService.action4Clicks = selected
                updateGestureRowUI(4, selected)
                LogBuffer.log("Config: 4 Clicks -> $selected")
            }
        }

        // Sensitivity (Dual-Press Window) Customizer with Live Test Pad
        val windowMs = prefs.getLong("dual_press_window", 140L)
        VolumeTweakService.dualPressWindowMs = windowMs
        tvSensitivity.text = formatSensitivityLabel(windowMs)
        rowSensitivity.setOnClickListener {
            showSensitivityDialog(VolumeTweakService.dualPressWindowMs) { selectedMs ->
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
            val imgIcon = itemView.findViewById<ImageView>(R.id.imgComboIcon)
            val switchItem = itemView.findViewById<MaterialSwitch>(R.id.switchComboItem)
            val btnDelete = itemView.findViewById<ImageView>(R.id.btnDeleteCombo)
            val layoutRow = itemView.findViewById<View>(R.id.layoutComboRow)

            tvSequence.text = combo.getDisplaySequence()
            tvAction.text = ActionRegistry.getTitle(combo.action)
            imgIcon.setImageResource(ActionRegistry.getIcon(combo.action))
            switchItem.isChecked = combo.isEnabled

            switchItem.setOnCheckedChangeListener { _, isChecked ->
                combo.isEnabled = isChecked
                saveCustomCombosToPrefs()
            }

            layoutRow.setOnClickListener {
                handleActionSelection("Action for ${combo.getDisplaySequence()}", combo.action) { newAction ->
                    combo.action = newAction
                    tvAction.text = ActionRegistry.getTitle(newAction)
                    imgIcon.setImageResource(ActionRegistry.getIcon(newAction))
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
        var chosenAction = "SKIP_FORWARD:15"

        val tvDisplay = dialogView.findViewById<TextView>(R.id.tvBuilderSequenceDisplay)
        val tvActionTitle = dialogView.findViewById<TextView>(R.id.tvBuilderActionTitle)
        val imgActionIcon = dialogView.findViewById<ImageView>(R.id.imgBuilderActionIcon)
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
            imgActionIcon.setImageResource(ActionRegistry.getIcon(chosenAction))
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
            handleActionSelection("Select Sequence Action", chosenAction) { selected ->
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
        val desc = when {
            ms <= 110L -> "Tight"
            ms <= 155L -> "Balanced"
            ms <= 210L -> "Relaxed"
            else -> "Forgiving"
        }
        return "$desc (${ms}ms)"
    }

    private fun updateGestureRowUI(index: Int, actionId: String) {
        val title = ActionRegistry.getTitle(actionId)
        val iconRes = ActionRegistry.getIcon(actionId)
        when (index) {
            1 -> {
                tvAction1.text = title
                imgActionIcon1.setImageResource(iconRes)
            }
            2 -> {
                tvAction2.text = title
                imgActionIcon2.setImageResource(iconRes)
            }
            3 -> {
                tvAction3.text = title
                imgActionIcon3.setImageResource(iconRes)
            }
            4 -> {
                tvAction4.text = title
                imgActionIcon4.setImageResource(iconRes)
            }
        }
    }

    private fun handleActionSelection(title: String, currentAction: String, onFinalAction: (String) -> Unit) {
        showActionPicker(title, currentAction) { chosenActionId ->
            val actionDef = ActionRegistry.getAction(chosenActionId)
            if (actionDef != null && actionDef.parameterType != ParameterType.NONE) {
                showParameterDialog(chosenActionId, currentAction, onFinalAction)
            } else {
                onFinalAction(chosenActionId)
            }
        }
    }

    private fun showActionPicker(title: String, currentActionId: String, onSelect: (String) -> Unit) {
        val actions = ActionRegistry.ALL_ACTIONS
        val baseCurrent = ActionRegistry.getBaseId(currentActionId)

        val adapter = object : ArrayAdapter<TweakAction>(this, 0, actions) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_action_picker, parent, false)
                val item = getItem(position)!!
                val img = view.findViewById<ImageView>(R.id.imgPickerIcon)
                val tvTitle = view.findViewById<TextView>(R.id.tvPickerTitle)
                val tvDesc = view.findViewById<TextView>(R.id.tvPickerDesc)

                img.setImageResource(item.iconRes)
                tvTitle.text = item.title
                tvDesc.text = item.description

                if (item.id == baseCurrent) {
                    tvTitle.setTextColor(Color.parseColor("#D71921"))
                } else {
                    tvTitle.setTextColor(Color.WHITE)
                }

                return view
            }
        }

        var dialog: AlertDialog? = null
        dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setAdapter(adapter) { _, which ->
                onSelect(actions[which].id)
                dialog?.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .create()

        dialog.show()
    }

    private fun showParameterDialog(chosenActionId: String, currentActionStr: String, onComplete: (String) -> Unit) {
        val baseId = ActionRegistry.getBaseId(chosenActionId)
        val actionDef = ActionRegistry.getAction(baseId) ?: return onComplete(chosenActionId)
        val currentParam = ActionRegistry.getParam(currentActionStr) ?: actionDef.defaultParam

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_action_parameter, null)
        val imgIcon = dialogView.findViewById<ImageView>(R.id.imgParamActionIcon)
        val tvTitle = dialogView.findViewById<TextView>(R.id.tvParamDialogTitle)
        val tvSubtitle = dialogView.findViewById<TextView>(R.id.tvParamDialogSubtitle)
        val tvValueDisplay = dialogView.findViewById<TextView>(R.id.tvParamValueDisplay)
        val seekParam = dialogView.findViewById<SeekBar>(R.id.seekParam)
        val tvMin = dialogView.findViewById<TextView>(R.id.tvParamMin)
        val tvMax = dialogView.findViewById<TextView>(R.id.tvParamMax)
        val containerChips = dialogView.findViewById<LinearLayout>(R.id.containerParamChips)
        val btnCancel = dialogView.findViewById<MaterialButton>(R.id.btnParamCancel)
        val btnSave = dialogView.findViewById<MaterialButton>(R.id.btnParamSave)

        imgIcon.setImageResource(actionDef.iconRes)
        tvTitle.text = actionDef.title.uppercase()

        var selectedParamValue = currentParam

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        when (actionDef.parameterType) {
            ParameterType.SECONDS -> {
                tvSubtitle.text = "Set how many seconds to skip on each trigger."
                tvMin.text = "5s"
                tvMax.text = "120s"
                seekParam.max = 115
                seekParam.progress = (selectedParamValue - 5).coerceIn(0, 115)

                fun updateSec(sec: Int) {
                    selectedParamValue = sec
                    tvValueDisplay.text = "$sec seconds"
                }
                updateSec(selectedParamValue)

                seekParam.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                        if (fromUser) updateSec(p + 5)
                    }
                    override fun onStartTrackingTouch(s: SeekBar?) {}
                    override fun onStopTrackingTouch(s: SeekBar?) {}
                })

                val presets = listOf(5, 10, 15, 30, 45, 60)
                for (p in presets) {
                    val btn = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                        text = "${p}s"
                        textSize = 11f
                        setTextColor(Color.WHITE)
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                            marginEnd = 4
                        }
                        setOnClickListener {
                            seekParam.progress = (p - 5).coerceIn(0, 115)
                            updateSec(p)
                        }
                    }
                    containerChips.addView(btn)
                }
            }

            ParameterType.PERCENTAGE -> {
                tvSubtitle.text = "Set exact audio playback volume percentage."
                tvMin.text = "0%"
                tvMax.text = "100%"
                seekParam.max = 100
                seekParam.progress = selectedParamValue.coerceIn(0, 100)

                fun updatePct(pct: Int) {
                    selectedParamValue = pct
                    tvValueDisplay.text = "$pct%"
                }
                updatePct(selectedParamValue)

                seekParam.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                        if (fromUser) updatePct(p)
                    }
                    override fun onStartTrackingTouch(s: SeekBar?) {}
                    override fun onStopTrackingTouch(s: SeekBar?) {}
                })

                val presets = listOf(15, 35, 50, 75, 100)
                for (p in presets) {
                    val btn = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                        text = "${p}%"
                        textSize = 11f
                        setTextColor(Color.WHITE)
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                            marginEnd = 4
                        }
                        setOnClickListener {
                            seekParam.progress = p
                            updatePct(p)
                        }
                    }
                    containerChips.addView(btn)
                }
            }

            ParameterType.STEP_PERCENT -> {
                tvSubtitle.text = "Set percentage to increment or decrement volume."
                tvMin.text = "-30%"
                tvMax.text = "+30%"
                seekParam.max = 60
                seekParam.progress = (selectedParamValue + 30).coerceIn(0, 60)

                fun updateStep(step: Int) {
                    selectedParamValue = step
                    tvValueDisplay.text = if (step >= 0) "+$step%" else "$step%"
                }
                updateStep(selectedParamValue)

                seekParam.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                        if (fromUser) updateStep(p - 30)
                    }
                    override fun onStartTrackingTouch(s: SeekBar?) {}
                    override fun onStopTrackingTouch(s: SeekBar?) {}
                })

                val presets = listOf(5, 10, 20, -5, -10, -20)
                for (p in presets) {
                    val btn = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                        text = if (p >= 0) "+$p%" else "$p%"
                        textSize = 10f
                        setTextColor(Color.WHITE)
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                            marginEnd = 2
                        }
                        setOnClickListener {
                            seekParam.progress = (p + 30).coerceIn(0, 60)
                            updateStep(p)
                        }
                    }
                    containerChips.addView(btn)
                }
            }

            ParameterType.NONE -> {
                dialog.dismiss()
                onComplete(chosenActionId)
                return
            }
        }

        btnCancel.setOnClickListener { dialog.dismiss() }
        btnSave.setOnClickListener {
            dialog.dismiss()
            onComplete("${baseId}:${selectedParamValue}")
        }

        dialog.show()
    }

    private fun showSensitivityDialog(currentMs: Long, onSelect: (Long) -> Unit) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_sensitivity_test, null)

        val tvValue = dialogView.findViewById<TextView>(R.id.tvSensitivityMsValue)
        val tvDescriptor = dialogView.findViewById<TextView>(R.id.tvSensitivityDescriptor)
        val seekBar = dialogView.findViewById<SeekBar>(R.id.seekSensitivity)

        val btnPresetTight = dialogView.findViewById<MaterialButton>(R.id.btnPresetTight)
        val btnPresetBalanced = dialogView.findViewById<MaterialButton>(R.id.btnPresetBalanced)
        val btnPresetRelaxed = dialogView.findViewById<MaterialButton>(R.id.btnPresetRelaxed)
        val btnPresetWide = dialogView.findViewById<MaterialButton>(R.id.btnPresetWide)

        val btnPadUp = dialogView.findViewById<MaterialButton>(R.id.btnPadVolUp)
        val btnPadDown = dialogView.findViewById<MaterialButton>(R.id.btnPadVolDown)
        val tvDelta = dialogView.findViewById<TextView>(R.id.tvTestPadDelta)
        val tvVerdict = dialogView.findViewById<TextView>(R.id.tvTestPadVerdict)
        val btnApply = dialogView.findViewById<MaterialButton>(R.id.btnTestPadApply)

        val btnCancel = dialogView.findViewById<MaterialButton>(R.id.btnSensitivityCancel)
        val btnSave = dialogView.findViewById<MaterialButton>(R.id.btnSensitivitySave)

        var selectedMs = currentMs.coerceIn(60L, 320L)

        fun updateUI(ms: Long) {
            selectedMs = ms.coerceIn(60L, 320L)
            tvValue.text = "${selectedMs} ms"
            seekBar.progress = (selectedMs - 60L).toInt()

            val desc = when {
                selectedMs <= 110L -> "TIGHT / RAPID"
                selectedMs <= 155L -> "BALANCED"
                selectedMs <= 210L -> "RELAXED"
                else -> "ULTRA-FORGIVING"
            }
            tvDescriptor.text = desc
        }

        updateUI(selectedMs)

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    updateUI((progress + 60).toLong())
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        btnPresetTight.setOnClickListener { updateUI(100L) }
        btnPresetBalanced.setOnClickListener { updateUI(140L) }
        btnPresetRelaxed.setOnClickListener { updateUI(180L) }
        btnPresetWide.setOnClickListener { updateUI(240L) }

        var testUpTime = 0L
        var testDownTime = 0L

        fun evaluateTestPress() {
            if (testUpTime > 0 && testDownTime > 0) {
                val diff = Math.abs(testUpTime - testDownTime)
                if (diff < 1500) {
                    tvDelta.text = "Measured Time Gap: ${diff} ms"
                    if (diff <= selectedMs) {
                        tvVerdict.text = "SUCCESS: Within window! Registered as Dual Press."
                        tvVerdict.setTextColor(Color.parseColor("#4CAF50"))
                        HapticFeedbackController.vibrateTick(this@MainActivity)
                    } else {
                        val lateBy = diff - selectedMs
                        tvVerdict.text = "MISSED: Second key was ${lateBy}ms too late for current slider."
                        tvVerdict.setTextColor(Color.parseColor("#E50914"))
                    }

                    val recommended = (diff + 20L).coerceIn(60L, 320L)
                    btnApply.visibility = View.VISIBLE
                    btnApply.text = "SET SLIDER TO ${recommended}ms (+20ms BUFFER)"
                    btnApply.setOnClickListener {
                        updateUI(recommended)
                        Toast.makeText(this@MainActivity, "Slider updated to ${recommended}ms", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        btnPadUp.setOnClickListener {
            testUpTime = SystemClock.uptimeMillis()
            evaluateTestPress()
        }

        btnPadDown.setOnClickListener {
            testDownTime = SystemClock.uptimeMillis()
            evaluateTestPress()
        }

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialog.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                    testUpTime = SystemClock.uptimeMillis()
                    evaluateTestPress()
                    return@setOnKeyListener true
                } else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                    testDownTime = SystemClock.uptimeMillis()
                    evaluateTestPress()
                    return@setOnKeyListener true
                }
            }
            false
        }

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnSave.setOnClickListener {
            onSelect(selectedMs)
            dialog.dismiss()
        }

        dialog.show()
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
