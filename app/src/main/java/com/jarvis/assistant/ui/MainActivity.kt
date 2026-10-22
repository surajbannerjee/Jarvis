package com.jarvis.assistant.ui

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.ContactsContract
import android.provider.Settings
import android.speech.RecognizerIntent
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.materialswitch.MaterialSwitch
import com.jarvis.assistant.JarvisApp
import com.jarvis.assistant.R
import com.jarvis.assistant.audio.AudioSlot
import com.jarvis.assistant.audio.CustomAudioPlayer
import com.jarvis.assistant.audio.CustomVoiceStorage
import com.jarvis.assistant.contacts.ContactHelper
import com.jarvis.assistant.data.entity.AliasEntity
import com.jarvis.assistant.nlp.CommandParser
import com.jarvis.assistant.nlp.JarvisIntent
import com.jarvis.assistant.nlp.SupportedLanguage
import com.jarvis.assistant.persona.PersonaGender
import com.jarvis.assistant.persona.PersonaManager
import com.jarvis.assistant.persona.StarkPersona
import com.jarvis.assistant.service.FridayWakeWordService
import com.jarvis.assistant.speech.JarvisSpeechManager
import com.jarvis.assistant.ai.JarvisAiBrain
import com.jarvis.assistant.telephony.CallRecordingManager
import com.jarvis.assistant.telephony.JarvisAccessibilityService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private val tag = "MainActivity"

    // Header & Persona Views (Dedicated F.R.I.D.A.Y.)
    private lateinit var tvAppTitle: TextView
    private lateinit var tvPersonaDisplayName: TextView
    private lateinit var tvPersonaCodeName: TextView
    private lateinit var tvPersonaDescription: TextView
    private lateinit var tvPersonaGenderBadge: TextView
    private lateinit var btnTuneVoice: Button
    private lateinit var btnPreviewVoice: Button
    private lateinit var switchWakeWord: MaterialSwitch
    private lateinit var switchOwnerVoiceLock: MaterialSwitch

    // Voice Assistant Views
    private lateinit var btnMic: ImageButton
    private lateinit var tvVoiceStatus: TextView
    private lateinit var tvRecognizedSpeech: TextView
    private lateinit var btnStopSpeech: Button

    // Quick Voice Test Chips
    private var chipVoiceBouCall: Button? = null
    private var chipVoiceMaaCall: Button? = null
    private var chipVoicePriyanka: Button? = null
    private var chipVoiceBossCall: Button? = null

    // Free AI Assistant Views
    private lateinit var tvAiEngineBadge: TextView
    private lateinit var tvAiUserPrompt: TextView
    private lateinit var tvAiAssistantReply: TextView
    private lateinit var btnAiGeminiConfig: Button
    private lateinit var btnPlayFridaySpeech: Button

    private var chipAskHowAreYou: Button? = null
    private var chipAskWhoAreYou: Button? = null
    private var chipAskCapabilities: Button? = null
    private var chipAskBatteryStatus: Button? = null
    private var chipAskTellJoke: Button? = null

    // Custom Voice Audio Views
    private lateinit var tvGreetingStatus: TextView
    private lateinit var btnSelectGreeting: Button
    private lateinit var btnTestGreeting: Button
    private lateinit var btnRemoveGreeting: Button

    private lateinit var tvVoicemailStatus: TextView
    private lateinit var btnSelectVoicemail: Button
    private lateinit var btnTestVoicemail: Button
    private lateinit var btnRemoveVoicemail: Button

    private lateinit var tvCallerStatus: TextView
    private lateinit var btnSelectCallerAudio: Button
    private lateinit var btnTestCallerAudio: Button
    private lateinit var btnRemoveCallerAudio: Button

    // Permissions Views
    private lateinit var btnGrantCorePerms: Button
    private lateinit var btnGrantNotification: Button
    private lateinit var btnGrantAccessibility: Button
    private lateinit var btnGrantBattery: Button

    // Alias Views
    private lateinit var btnAddAlias: Button
    private lateinit var tvEmptyAliases: TextView
    private lateinit var rvAliases: RecyclerView

    // Answering Machine Views
    private lateinit var switchAnsweringMachine: MaterialSwitch
    private lateinit var etRingThreshold: EditText
    private var chipRing10s: Button? = null
    private var chipRing12s: Button? = null
    private var chipRing15s: Button? = null
    private lateinit var switchRecordCalls: MaterialSwitch
    private lateinit var btnRefreshRecordings: Button
    private lateinit var tvEmptyRecordings: TextView
    private lateinit var layoutRecordingsContainer: LinearLayout

    private lateinit var aliasAdapter: AliasAdapter
    private val speechManager: JarvisSpeechManager by lazy { JarvisApp.instance.speechManager }

    private var isListening = false
    private var pulseAnimatorX: ObjectAnimator? = null
    private var pulseAnimatorY: ObjectAnimator? = null

    // For Train Relationship Dialog Contact Picking
    private var activeDialogEtName: EditText? = null
    private var activeDialogEtPhone: EditText? = null

    // For Custom Voice Audio Picking
    private var pendingAudioSlot: AudioSlot? = null

    private val corePermissions = mutableListOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.CALL_PHONE,
        Manifest.permission.READ_CONTACTS
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            add(Manifest.permission.ANSWER_PHONE_CALLS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        checkAndUpdatePermissionStatuses()
        val allGranted = results.values.all { it }
        if (allGranted) {
            Toast.makeText(this, "All core permissions granted!", Toast.LENGTH_SHORT).show()
        }
    }

    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startVoiceAssistant()
        } else {
            Toast.makeText(this, "Microphone permission is required for voice commands", Toast.LENGTH_SHORT).show()
        }
    }

    // Contact Picker Launcher for Train Relationship Dialog
    private val contactPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val contactUri = result.data?.data
            if (contactUri != null) {
                val (name, phone) = ContactHelper.getContactDetailsFromUri(this, contactUri)
                if (!name.isNullOrBlank()) {
                    activeDialogEtName?.setText(name)
                }
                if (!phone.isNullOrBlank()) {
                    activeDialogEtPhone?.setText(phone)
                }
                Toast.makeText(this, "Selected: ${name ?: "Contact"}", Toast.LENGTH_SHORT).show()
            }
        }
    }



    // Audio File Picker for Custom Voice Clips
    private val audioPickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        val slot = pendingAudioSlot ?: return@registerForActivityResult
        if (uri != null) {
            val success = CustomVoiceStorage.saveAudioFromUri(this, uri, slot)
            if (success) {
                Toast.makeText(this, "✓ Custom voice uploaded for ${slot.title}", Toast.LENGTH_SHORT).show()
                updateCustomAudioUi()
            } else {
                Toast.makeText(this, "Failed to import selected audio file", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupRecyclerView()
        setupListeners()
        observeAliases()
        observePersona()
        updateCustomAudioUi()

        if (JarvisApp.instance.isWakeWordEnabled()) {
            FridayWakeWordService.start(this)
        }
        renderRecordedCalls()
        handleWakeIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleWakeIntent(intent)
    }

    private fun handleWakeIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(FridayWakeWordService.EXTRA_TRIGGER_VOICE, false) == true) {
            if (!isListening) {
                startVoiceAssistant()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        FridayWakeWordService.setForegroundActive(true)
        checkAndUpdatePermissionStatuses()
        updateCustomAudioUi()
    }

    override fun onPause() {
        super.onPause()
        FridayWakeWordService.setForegroundActive(false)
        stopVoiceAssistant()
    }

    override fun onDestroy() {
        super.onDestroy()
        FridayWakeWordService.setForegroundActive(false)
        stopPulseAnimation()
        CustomAudioPlayer.stopAudio()
    }

    private fun initViews() {
        tvAppTitle = findViewById(R.id.tvAppTitle)
        tvPersonaDisplayName = findViewById(R.id.tvPersonaDisplayName)
        tvPersonaCodeName = findViewById(R.id.tvPersonaCodeName)
        tvPersonaDescription = findViewById(R.id.tvPersonaDescription)
        tvPersonaGenderBadge = findViewById(R.id.tvPersonaGenderBadge)
        btnTuneVoice = findViewById(R.id.btnTuneVoice)
        btnPreviewVoice = findViewById(R.id.btnPreviewVoice)
        switchWakeWord = findViewById(R.id.switchWakeWord)
        switchWakeWord.isChecked = JarvisApp.instance.isWakeWordEnabled()
        switchOwnerVoiceLock = findViewById(R.id.switchOwnerVoiceLock)
        switchOwnerVoiceLock.isChecked = JarvisApp.instance.isOwnerVoiceLockEnabled()

        btnMic = findViewById(R.id.btnMic)
        tvVoiceStatus = findViewById(R.id.tvVoiceStatus)
        tvRecognizedSpeech = findViewById(R.id.tvRecognizedSpeech)
        btnStopSpeech = findViewById(R.id.btnStopSpeech)

        chipVoiceBouCall = findViewById(R.id.chipVoiceBouCall)
        chipVoiceMaaCall = findViewById(R.id.chipVoiceMaaCall)
        chipVoicePriyanka = findViewById(R.id.chipVoicePriyanka)
        chipVoiceBossCall = findViewById(R.id.chipVoiceBossCall)

        // Free AI Assistant Views
        tvAiEngineBadge = findViewById(R.id.tvAiEngineBadge)
        tvAiUserPrompt = findViewById(R.id.tvAiUserPrompt)
        tvAiAssistantReply = findViewById(R.id.tvAiAssistantReply)
        btnAiGeminiConfig = findViewById(R.id.btnAiGeminiConfig)
        btnPlayFridaySpeech = findViewById(R.id.btnPlayFridaySpeech)

        chipAskHowAreYou = findViewById(R.id.chipAskHowAreYou)
        chipAskWhoAreYou = findViewById(R.id.chipAskWhoAreYou)
        chipAskCapabilities = findViewById(R.id.chipAskCapabilities)
        chipAskBatteryStatus = findViewById(R.id.chipAskBatteryStatus)
        chipAskTellJoke = findViewById(R.id.chipAskTellJoke)

        updateAiBadge()

        // Custom Voice Slots
        tvGreetingStatus = findViewById(R.id.tvGreetingStatus)
        btnSelectGreeting = findViewById(R.id.btnSelectGreeting)
        btnTestGreeting = findViewById(R.id.btnTestGreeting)
        btnRemoveGreeting = findViewById(R.id.btnRemoveGreeting)

        tvVoicemailStatus = findViewById(R.id.tvVoicemailStatus)
        btnSelectVoicemail = findViewById(R.id.btnSelectVoicemail)
        btnTestVoicemail = findViewById(R.id.btnTestVoicemail)
        btnRemoveVoicemail = findViewById(R.id.btnRemoveVoicemail)

        tvCallerStatus = findViewById(R.id.tvCallerStatus)
        btnSelectCallerAudio = findViewById(R.id.btnSelectCallerAudio)
        btnTestCallerAudio = findViewById(R.id.btnTestCallerAudio)
        btnRemoveCallerAudio = findViewById(R.id.btnRemoveCallerAudio)

        btnGrantCorePerms = findViewById(R.id.btnGrantCorePerms)
        btnGrantNotification = findViewById(R.id.btnGrantNotification)
        btnGrantAccessibility = findViewById(R.id.btnGrantAccessibility)
        btnGrantBattery = findViewById(R.id.btnGrantBattery)
        btnAddAlias = findViewById(R.id.btnAddAlias)
        tvEmptyAliases = findViewById(R.id.tvEmptyAliases)
        rvAliases = findViewById(R.id.rvAliases)
        switchAnsweringMachine = findViewById(R.id.switchAnsweringMachine)
        etRingThreshold = findViewById(R.id.etRingThreshold)

        switchAnsweringMachine.isChecked = JarvisApp.instance.isAnsweringMachineEnabled()
        etRingThreshold.setText(JarvisApp.instance.getRingThresholdSeconds().toString())

        switchRecordCalls = findViewById(R.id.switchRecordCalls)
        switchRecordCalls.isChecked = JarvisApp.instance.isCallRecordingEnabled()

        chipRing10s = findViewById(R.id.chipRing10s)
        chipRing12s = findViewById(R.id.chipRing12s)
        chipRing15s = findViewById(R.id.chipRing15s)

        btnRefreshRecordings = findViewById(R.id.btnRefreshRecordings)
        tvEmptyRecordings = findViewById(R.id.tvEmptyRecordings)
        layoutRecordingsContainer = findViewById(R.id.layoutRecordingsContainer)
    }

    private fun setupRecyclerView() {
        aliasAdapter = AliasAdapter { aliasToDelete ->
            lifecycleScope.launch(Dispatchers.IO) {
                JarvisApp.instance.database.aliasDao().delete(aliasToDelete)
            }
        }
        rvAliases.layoutManager = LinearLayoutManager(this)
        rvAliases.adapter = aliasAdapter
    }

    private fun setupListeners() {
        btnTuneVoice.setOnClickListener {
            showVoiceTuningDialog()
        }

        switchWakeWord.setOnCheckedChangeListener { _, isChecked ->
            JarvisApp.instance.setWakeWordEnabled(isChecked)
            if (isChecked) {
                FridayWakeWordService.start(this)
                Toast.makeText(this, "Friday wake-word activated! Say 'Hi Friday' anytime.", Toast.LENGTH_SHORT).show()
            } else {
                FridayWakeWordService.stop(this)
                Toast.makeText(this, "Friday wake-word disabled.", Toast.LENGTH_SHORT).show()
            }
        }

        switchOwnerVoiceLock.setOnCheckedChangeListener { _, isChecked ->
            JarvisApp.instance.setOwnerVoiceLockEnabled(isChecked)
            val msg = if (isChecked) {
                "🔒 Owner Voice Lock active: Friday will only obey Suraj Boss"
            } else {
                "Owner Voice Lock disabled"
            }
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }

        btnPreviewVoice.setOnClickListener {
            val persona = PersonaManager.getActivePersona()
            speechManager.previewPersona(persona, inBengali = true)
            Toast.makeText(this, "Playing ${persona.displayName} signature preview...", Toast.LENGTH_SHORT).show()
        }

        btnMic.setOnClickListener {
            toggleVoiceAssistant()
        }

        btnStopSpeech.setOnClickListener {
            stopVoiceAssistant()
        }

        // Quick Directive Voice Chips
        chipVoiceBouCall?.setOnClickListener {
            runVoiceChipDirective("Amar bou ke call koro")
        }
        chipVoiceMaaCall?.setOnClickListener {
            runVoiceChipDirective("Mummy ko call lagao")
        }
        chipVoicePriyanka?.setOnClickListener {
            runVoiceChipDirective("Jarvis, amar bou er nam Priyanka")
        }
        chipVoiceBossCall?.setOnClickListener {
            runVoiceChipDirective("Call my boss")
        }

        // Quick AI Conversation Chips
        chipAskHowAreYou?.setOnClickListener {
            runVoiceChipDirective("Kemon acho?")
        }
        chipAskWhoAreYou?.setOnClickListener {
            runVoiceChipDirective("Tumi ke?")
        }
        chipAskCapabilities?.setOnClickListener {
            runVoiceChipDirective("Ki korte paro?")
        }
        chipAskBatteryStatus?.setOnClickListener {
            runVoiceChipDirective("Battery status?")
        }
        chipAskTellJoke?.setOnClickListener {
            runVoiceChipDirective("Ekta joke shonao")
        }

        btnPlayFridaySpeech.setOnClickListener {
            PersonaManager.setActivePersona(StarkPersona.FRIDAY)
            CustomAudioPlayer.playCustomAudio(this, "friday_greeting")
            tvVoiceStatus.text = "Playing F.R.I.D.A.Y. Built-in Voice..."
            Toast.makeText(this, "Playing F.R.I.D.A.Y. Speech Audio...", Toast.LENGTH_SHORT).show()
        }

        btnAiGeminiConfig.setOnClickListener {
            showGeminiApiKeyDialog()
        }

        // Custom Audio Uploader Listeners
        btnSelectGreeting.setOnClickListener {
            pendingAudioSlot = AudioSlot.GREETING
            audioPickerLauncher.launch("audio/*")
        }
        btnTestGreeting.setOnClickListener {
            togglePlayAudioSlot(AudioSlot.GREETING, btnTestGreeting)
        }
        btnRemoveGreeting.setOnClickListener {
            CustomVoiceStorage.clearAudio(this, AudioSlot.GREETING)
            updateCustomAudioUi()
            Toast.makeText(this, "Assistant Greeting voice reset to TTS", Toast.LENGTH_SHORT).show()
        }

        btnSelectVoicemail.setOnClickListener {
            pendingAudioSlot = AudioSlot.VOICEMAIL
            audioPickerLauncher.launch("audio/*")
        }
        btnTestVoicemail.setOnClickListener {
            togglePlayAudioSlot(AudioSlot.VOICEMAIL, btnTestVoicemail)
        }
        btnRemoveVoicemail.setOnClickListener {
            CustomVoiceStorage.clearAudio(this, AudioSlot.VOICEMAIL)
            updateCustomAudioUi()
            Toast.makeText(this, "Answering Machine voicemail reset to TTS", Toast.LENGTH_SHORT).show()
        }

        btnSelectCallerAudio.setOnClickListener {
            pendingAudioSlot = AudioSlot.CALLER_ALERT
            audioPickerLauncher.launch("audio/*")
        }
        btnTestCallerAudio.setOnClickListener {
            togglePlayAudioSlot(AudioSlot.CALLER_ALERT, btnTestCallerAudio)
        }
        btnRemoveCallerAudio.setOnClickListener {
            CustomVoiceStorage.clearAudio(this, AudioSlot.CALLER_ALERT)
            updateCustomAudioUi()
            Toast.makeText(this, "Caller Alert audio reset to TTS", Toast.LENGTH_SHORT).show()
        }

        // Permission buttons
        btnGrantCorePerms.setOnClickListener {
            permissionLauncher.launch(corePermissions.toTypedArray())
        }

        btnGrantNotification.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        btnGrantAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        btnGrantBattery.setOnClickListener {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            try {
                startActivity(intent)
            } catch (e: Exception) {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }

        btnAddAlias.setOnClickListener {
            showAddAliasDialog()
        }

        switchAnsweringMachine.setOnCheckedChangeListener { _, isChecked ->
            JarvisApp.instance.setAnsweringMachineEnabled(isChecked)
        }

        etRingThreshold.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val seconds = s?.toString()?.toIntOrNull()
                if (seconds != null && seconds in 3..60) {
                    JarvisApp.instance.setRingThresholdSeconds(seconds)
                }
            }
        })

        switchRecordCalls.setOnCheckedChangeListener { _, isChecked ->
            JarvisApp.instance.setCallRecordingEnabled(isChecked)
            val msg = if (isChecked) "Call recording enabled" else "Call recording disabled"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }

        chipRing10s?.setOnClickListener {
            etRingThreshold.setText("10")
            JarvisApp.instance.setRingThresholdSeconds(10)
            Toast.makeText(this, "Auto-answer threshold: 10s", Toast.LENGTH_SHORT).show()
        }
        chipRing12s?.setOnClickListener {
            etRingThreshold.setText("12")
            JarvisApp.instance.setRingThresholdSeconds(12)
            Toast.makeText(this, "Auto-answer threshold: 12s", Toast.LENGTH_SHORT).show()
        }
        chipRing15s?.setOnClickListener {
            etRingThreshold.setText("15")
            JarvisApp.instance.setRingThresholdSeconds(15)
            Toast.makeText(this, "Auto-answer threshold: 15s", Toast.LENGTH_SHORT).show()
        }

        btnRefreshRecordings.setOnClickListener {
            renderRecordedCalls()
        }
    }

    private fun runVoiceChipDirective(directive: String) {
        tvVoiceStatus.text = "Directive: \"$directive\""
        tvRecognizedSpeech.text = "You said: \"$directive\""
        tvRecognizedSpeech.visibility = View.VISIBLE
        processVoiceCommand(directive)
    }

    private fun updateCustomAudioUi() {
        val greetingFile = CustomVoiceStorage.getAudioFile(this, AudioSlot.GREETING)
        if (greetingFile != null) {
            val sizeKb = greetingFile.length() / 1024
            tvGreetingStatus.text = "✓ Custom Voice Active (${sizeKb} KB)"
            tvGreetingStatus.setTextColor(ContextCompat.getColor(this, R.color.jarvis_success))
            btnRemoveGreeting.visibility = View.VISIBLE
        } else {
            tvGreetingStatus.text = "Default TTS (${PersonaManager.getActivePersona().displayName})"
            tvGreetingStatus.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            btnRemoveGreeting.visibility = View.GONE
        }

        val voicemailFile = CustomVoiceStorage.getAudioFile(this, AudioSlot.VOICEMAIL)
        if (voicemailFile != null) {
            val sizeKb = voicemailFile.length() / 1024
            tvVoicemailStatus.text = "✓ Custom Audio Active (${sizeKb} KB)"
            tvVoicemailStatus.setTextColor(ContextCompat.getColor(this, R.color.jarvis_success))
            btnRemoveVoicemail.visibility = View.VISIBLE
        } else {
            tvVoicemailStatus.text = "Default TTS (${PersonaManager.getActivePersona().displayName})"
            tvVoicemailStatus.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            btnRemoveVoicemail.visibility = View.GONE
        }

        val callerFile = CustomVoiceStorage.getAudioFile(this, AudioSlot.CALLER_ALERT)
        if (callerFile != null) {
            val sizeKb = callerFile.length() / 1024
            tvCallerStatus.text = "✓ Custom Audio Active (${sizeKb} KB)"
            tvCallerStatus.setTextColor(ContextCompat.getColor(this, R.color.jarvis_success))
            btnRemoveCallerAudio.visibility = View.VISIBLE
        } else {
            tvCallerStatus.text = "Default TTS (${PersonaManager.getActivePersona().displayName})"
            tvCallerStatus.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            btnRemoveCallerAudio.visibility = View.GONE
        }
    }

    private fun togglePlayAudioSlot(slot: AudioSlot, testButton: Button) {
        if (CustomAudioPlayer.isPlaying()) {
            CustomAudioPlayer.stopAudio()
            testButton.text = "▶ Test"
            return
        }

        val hasCustom = CustomVoiceStorage.hasCustomAudio(this, slot)
        if (hasCustom) {
            testButton.text = "⏹ Stop"
            val played = CustomAudioPlayer.playAudioSlot(this, slot) {
                runOnUiThread { testButton.text = "▶ Test" }
            }
            if (!played) {
                testButton.text = "▶ Test"
                Toast.makeText(this, "Failed to play custom audio file", Toast.LENGTH_SHORT).show()
            }
        } else {
            val persona = PersonaManager.getActivePersona()
            when (slot) {
                AudioSlot.GREETING -> speechManager.speak(persona.signatureGreetingBengali, SupportedLanguage.BENGALI)
                AudioSlot.VOICEMAIL -> speechManager.speak(persona.formatVoicemailMessage(isBengali = true), SupportedLanguage.BENGALI)
                AudioSlot.CALLER_ALERT -> speechManager.speak(persona.formatCallerAnnouncement("Priyanka", isBengali = true), SupportedLanguage.BENGALI)
            }
            Toast.makeText(this, "Playing ${persona.displayName} TTS preview", Toast.LENGTH_SHORT).show()
        }
    }

    private fun observePersona() {
        lifecycleScope.launch {
            PersonaManager.activePersonaFlow.collectLatest { persona ->
                tvAppTitle.text = persona.displayName
                tvPersonaDisplayName.text = persona.displayName
                tvPersonaCodeName.text = persona.codeName
                tvPersonaDescription.text = persona.description

                val badgeText = when (persona.gender) {
                    PersonaGender.FEMALE -> "${persona.preferredLocale.displayCountry.ifBlank { "TACTICAL" }} FEMALE"
                    PersonaGender.MALE -> "${persona.preferredLocale.displayCountry.ifBlank { "TACTICAL" }} MALE"
                    PersonaGender.ROBOTIC -> "ROBOTIC RESONANCE"
                }
                tvPersonaGenderBadge.text = badgeText
                updateCustomAudioUi()
            }
        }
    }

    private fun showVoiceTuningDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_voice_tuning, null)
        val tvPitchVal = dialogView.findViewById<TextView>(R.id.tvPitchValue)
        val seekPitch = dialogView.findViewById<SeekBar>(R.id.seekPitch)
        val tvSpeedVal = dialogView.findViewById<TextView>(R.id.tvSpeedValue)
        val seekSpeed = dialogView.findViewById<SeekBar>(R.id.seekSpeed)
        val btnTest = dialogView.findViewById<Button>(R.id.btnTestTunedVoice)

        val currentPitch = JarvisApp.instance.getFridayPitch()
        val pitchProgress = (((currentPitch - 0.7f) / 1.0f) * 100f).toInt().coerceIn(0, 100)
        seekPitch.progress = pitchProgress
        tvPitchVal.text = String.format(java.util.Locale.US, "%.2fx", currentPitch)

        val currentSpeed = JarvisApp.instance.getFridaySpeed()
        val speedProgress = (((currentSpeed - 0.7f) / 0.8f) * 100f).toInt().coerceIn(0, 100)
        seekSpeed.progress = speedProgress
        tvSpeedVal.text = String.format(java.util.Locale.US, "%.2fx", currentSpeed)

        var tunedPitch = currentPitch
        var tunedSpeed = currentSpeed

        seekPitch.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                tunedPitch = 0.7f + (progress / 100f) * 1.0f
                tvPitchVal.text = String.format(java.util.Locale.US, "%.2fx", tunedPitch)
                speechManager.setFridayPitchAndSpeed(tunedPitch, tunedSpeed)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        seekSpeed.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                tunedSpeed = 0.7f + (progress / 100f) * 0.8f
                tvSpeedVal.text = String.format(java.util.Locale.US, "%.2fx", tunedSpeed)
                speechManager.setFridayPitchAndSpeed(tunedPitch, tunedSpeed)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        btnTest.setOnClickListener {
            speechManager.speak("হ্যালো স্যার, আমি ফ্রাইডে। এই মিষ্টি কণ্ঠটি আপনার কেমন লাগছে?", SupportedLanguage.BENGALI)
        }

        AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("Save Voice") { _, _ ->
                JarvisApp.instance.setFridayPitchAndSpeed(tunedPitch, tunedSpeed)
                speechManager.setFridayPitchAndSpeed(tunedPitch, tunedSpeed)
                Toast.makeText(this, "✓ F.R.I.D.A.Y. voice tuned & saved!", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel") { _, _ ->
                speechManager.setFridayPitchAndSpeed(currentPitch, currentSpeed)
            }
            .show()
    }

    private fun renderRecordedCalls() {
        val recordings = CallRecordingManager.getRecordedCalls(this)
        layoutRecordingsContainer.removeAllViews()

        if (recordings.isEmpty()) {
            tvEmptyRecordings.visibility = View.VISIBLE
            return
        }
        tvEmptyRecordings.visibility = View.GONE

        recordings.forEach { item ->
            val itemCard = layoutInflater.inflate(R.layout.item_recorded_call, layoutRecordingsContainer, false)
            val tvFileName = itemCard.findViewById<TextView>(R.id.tvRecordingName)
            val tvDetails = itemCard.findViewById<TextView>(R.id.tvRecordingDetails)
            val btnPlay = itemCard.findViewById<Button>(R.id.btnPlayRecording)
            val btnDelete = itemCard.findViewById<Button>(R.id.btnDeleteRecording)

            tvFileName.text = "📞 ${item.caller}"
            val durationText = if (item.durationSec > 0) "${item.durationSec}s • " else ""
            tvDetails.text = "$durationText${item.formattedDate} • ${item.fileSizeKb} KB"

            var isThisPlaying = false
            btnPlay.setOnClickListener {
                if (isThisPlaying) {
                    CallRecordingManager.stopPlayback()
                    btnPlay.text = "▶ Play"
                    isThisPlaying = false
                } else {
                    val ok = CallRecordingManager.playRecording(this, item.filePath) {
                        runOnUiThread {
                            btnPlay.text = "▶ Play"
                            isThisPlaying = false
                        }
                    }
                    if (ok) {
                        btnPlay.text = "⏹ Stop"
                        isThisPlaying = true
                    } else {
                        Toast.makeText(this, "Failed to play recording", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            btnDelete.setOnClickListener {
                AlertDialog.Builder(this)
                    .setTitle("Delete Recording")
                    .setMessage("Are you sure you want to delete this recorded call?")
                    .setPositiveButton("Delete") { _, _ ->
                        CallRecordingManager.deleteRecording(this, item.id)
                        renderRecordedCalls()
                        Toast.makeText(this, "Recording deleted", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }

            layoutRecordingsContainer.addView(itemCard)
        }
    }

    private fun observeAliases() {
        lifecycleScope.launch {
            JarvisApp.instance.database.aliasDao().getAllAliasesFlow().collectLatest { list ->
                aliasAdapter.submitList(list)
                if (list.isEmpty()) {
                    tvEmptyAliases.visibility = View.VISIBLE
                    rvAliases.visibility = View.GONE
                } else {
                    tvEmptyAliases.visibility = View.GONE
                    rvAliases.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun toggleVoiceAssistant() {
        if (isListening) {
            stopVoiceAssistant()
            return
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Microphone permission required for voice directives.", Toast.LENGTH_SHORT).show()
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }

        startVoiceAssistant()
    }

    private fun startVoiceAssistant() {
        speechManager.stopSpeaking()
        CustomAudioPlayer.stopAudio()
        isListening = true
        setListeningUi(true)

        speechManager.startListening(
            preferredLanguage = SupportedLanguage.BENGALI,
            onReady = {
                runOnUiThread {
                    tvVoiceStatus.text = "🔴 Listening… Speak now"
                }
            },
            onResults = { recognizedText ->
                runOnUiThread {
                    setListeningUi(false)
                    tvVoiceStatus.text = getString(R.string.tap_to_speak)
                    tvRecognizedSpeech.text = "You said: \"$recognizedText\""
                    tvRecognizedSpeech.visibility = View.VISIBLE
                    processVoiceCommand(recognizedText)
                }
            },
            onError = { error ->
                Log.w(tag, "Native SpeechRecognizer error: $error")
                runOnUiThread {
                    setListeningUi(false)
                    tvVoiceStatus.text = getString(R.string.tap_to_speak)
                }
            }
        )
    }

    private fun stopVoiceAssistant() {
        isListening = false
        speechManager.stopListening()
        speechManager.stopSpeaking()
        setListeningUi(false)
        tvVoiceStatus.text = getString(R.string.tap_to_speak)
    }

    private fun setListeningUi(listening: Boolean) {
        isListening = listening
        if (listening) {
            btnMic.setBackgroundResource(R.drawable.bg_arc_reactor_listening)
            btnStopSpeech.visibility = View.VISIBLE
            tvVoiceStatus.text = "🔴 Initializing Arc Core..."
            startPulseAnimation()
        } else {
            btnMic.setBackgroundResource(R.drawable.bg_arc_reactor)
            btnStopSpeech.visibility = View.GONE
            stopPulseAnimation()
        }
    }

    private fun startPulseAnimation() {
        stopPulseAnimation()
        pulseAnimatorX = ObjectAnimator.ofFloat(btnMic, View.SCALE_X, 1.0f, 1.15f, 1.0f).apply {
            duration = 1000
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            start()
        }
        pulseAnimatorY = ObjectAnimator.ofFloat(btnMic, View.SCALE_Y, 1.0f, 1.15f, 1.0f).apply {
            duration = 1000
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            start()
        }
    }

    private fun stopPulseAnimation() {
        pulseAnimatorX?.cancel()
        pulseAnimatorY?.cancel()
        pulseAnimatorX = null
        pulseAnimatorY = null
        btnMic.scaleX = 1.0f
        btnMic.scaleY = 1.0f
    }

    private fun processVoiceCommand(text: String) {
        val intent = CommandParser.parse(text)
        lifecycleScope.launch(Dispatchers.IO) {
            when (intent) {
                is JarvisIntent.TrainRelation -> {
                    val relation = intent.relation.lowercase().trim()
                    val contactName = intent.contactName.trim()
                    val phone = ContactHelper.getPhoneNumberForContact(this@MainActivity, contactName)

                    val entity = AliasEntity(
                        relationshipTag = relation,
                        actualContactName = contactName,
                        phoneNumber = phone
                    )
                    JarvisApp.instance.database.aliasDao().insert(entity)

                    val reply = when (intent.language) {
                        SupportedLanguage.BENGALI -> "Mone rakhlam, tomar $relation holo $contactName."
                        SupportedLanguage.HINDI -> "Yaad rakha, aapka $relation $contactName hai."
                        SupportedLanguage.ENGLISH -> "I have remembered that your $relation is $contactName."
                    }
                    withContext(Dispatchers.Main) {
                        speechManager.speak(reply, intent.language)
                        Toast.makeText(this@MainActivity, reply, Toast.LENGTH_SHORT).show()
                    }
                }

                is JarvisIntent.CallTarget -> {
                    val target = intent.target.trim()
                    val targetLower = target.lowercase(Locale.ROOT)
                    val alias = JarvisApp.instance.database.aliasDao().findByTag(targetLower)

                    val contactToCall = alias?.actualContactName ?: target
                    var phoneToCall: String? = null

                    if (alias != null) {
                        phoneToCall = alias.phoneNumber ?: ContactHelper.getPhoneNumberForContact(this@MainActivity, contactToCall)
                    } else {
                        val byContact = JarvisApp.instance.database.aliasDao().findByContactName(contactToCall)
                        if (byContact != null) {
                            phoneToCall = byContact.phoneNumber ?: ContactHelper.getPhoneNumberForContact(this@MainActivity, contactToCall)
                        }
                    }

                    if (phoneToCall.isNullOrBlank()) {
                        phoneToCall = ContactHelper.getPhoneNumberForContact(this@MainActivity, contactToCall)
                    }
                    if (phoneToCall.isNullOrBlank()) {
                        phoneToCall = ContactHelper.getPhoneNumberForContact(this@MainActivity, target)
                    }

                    if (phoneToCall != null) {
                        val reply = when (intent.language) {
                            SupportedLanguage.BENGALI -> "$contactToCall-কে কল করছি..."
                            SupportedLanguage.HINDI -> "$contactToCall को कॉल लगा रही हूँ..."
                            SupportedLanguage.ENGLISH -> "Calling $contactToCall now..."
                        }
                        withContext(Dispatchers.Main) {
                            speechManager.speak(reply, intent.language)
                            Toast.makeText(this@MainActivity, reply, Toast.LENGTH_SHORT).show()
                            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                val callStarted = ContactHelper.makePhoneCall(this@MainActivity, phoneToCall)
                                if (!callStarted) {
                                    val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(phoneToCall)}"))
                                    startActivity(dialIntent)
                                }
                            }, 700)
                        }
                    } else {
                        val reply = when (intent.language) {
                            SupportedLanguage.BENGALI -> "$contactToCall-এর নম্বর কনট্যাক্টে পাওয়া যায়নি। ডায়ালারে খুলছি..."
                            SupportedLanguage.HINDI -> "$contactToCall का नंबर नहीं मिला। डायलर खोल रही हूँ..."
                            SupportedLanguage.ENGLISH -> "Could not find a phone number for $contactToCall. Opening dialer..."
                        }
                        withContext(Dispatchers.Main) {
                            speechManager.speak(reply, intent.language)
                            Toast.makeText(this@MainActivity, reply, Toast.LENGTH_SHORT).show()
                            val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(target)}"))
                            try {
                                startActivity(dialIntent)
                            } catch (e: Exception) {}
                        }
                    }
                }

                is JarvisIntent.Unauthorized -> {
                    val reply = when (intent.language) {
                        SupportedLanguage.BENGALI -> "Access restricted. সিকিউরিটি লক সক্রিয় রয়েছে, আমি শুধুমাত্র সুরজ স্যারের ভয়েস কমান্ড মানতে বাধ্য।"
                        SupportedLanguage.HINDI -> "Access restricted. सुरक्षा लॉक सक्रिय है, मैं केवल सूरज सर के वॉइस कमांड मानती हूँ।"
                        SupportedLanguage.ENGLISH -> "Access restricted. Owner voice protection is active. I only respond to Suraj Boss."
                    }
                    withContext(Dispatchers.Main) {
                        tvAiAssistantReply.text = "F.R.I.D.A.Y.: '$reply'"
                        speechManager.speak(reply, intent.language)
                        Toast.makeText(this@MainActivity, reply, Toast.LENGTH_SHORT).show()
                    }
                }

                is JarvisIntent.WhatsAppQuery -> {
                    val reply = when (intent.language) {
                        SupportedLanguage.BENGALI -> "WhatsApp notification monitoring active ache."
                        SupportedLanguage.HINDI -> "WhatsApp notification monitoring sakriya hai."
                        SupportedLanguage.ENGLISH -> "WhatsApp notification monitoring is active."
                    }
                    withContext(Dispatchers.Main) {
                        speechManager.speak(reply, intent.language)
                        Toast.makeText(this@MainActivity, reply, Toast.LENGTH_SHORT).show()
                    }
                }

                is JarvisIntent.Unknown -> {
                    val persona = PersonaManager.getActivePersona()
                    withContext(Dispatchers.Main) {
                        tvAiUserPrompt.text = "You: '$text'"
                        tvAiAssistantReply.text = "${persona.displayName}: 'Thinking...'"
                    }
                    val aiReply = JarvisAiBrain.chat(this@MainActivity, text, intent.language)
                    withContext(Dispatchers.Main) {
                        tvAiAssistantReply.text = "${persona.displayName}: '$aiReply'"
                        tvRecognizedSpeech.text = "AI: $aiReply"
                        speechManager.speak(aiReply, intent.language)
                    }
                }

                else -> {
                    val persona = PersonaManager.getActivePersona()
                    withContext(Dispatchers.Main) {
                        tvAiUserPrompt.text = "You: '$text'"
                        tvAiAssistantReply.text = "${persona.displayName}: 'Thinking...'"
                    }
                    val aiReply = JarvisAiBrain.chat(this@MainActivity, text, intent.language)
                    withContext(Dispatchers.Main) {
                        tvAiAssistantReply.text = "${persona.displayName}: '$aiReply'"
                        tvRecognizedSpeech.text = "AI: $aiReply"
                        speechManager.speak(aiReply, intent.language)
                    }
                }
            }
        }
    }

    private fun updateAiBadge() {
        val key = JarvisApp.instance.getGeminiApiKey()
        if (key.isNotBlank()) {
            tvAiEngineBadge.text = "GEMINI AI ACTIVE"
            tvAiEngineBadge.setTextColor(ContextCompat.getColor(this, R.color.jarvis_success))
        } else {
            tvAiEngineBadge.text = "FREE AI ACTIVE"
            tvAiEngineBadge.setTextColor(ContextCompat.getColor(this, R.color.jarvis_cyan_primary))
        }
    }

    private fun showGeminiApiKeyDialog() {
        val currentKey = JarvisApp.instance.getGeminiApiKey()
        val etKey = EditText(this).apply {
            hint = "Paste your Google Gemini API Key here"
            setText(currentKey)
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_primary))
            setHintTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_secondary))
            setPadding(40, 30, 40, 30)
        }

        AlertDialog.Builder(this)
            .setTitle("🔑 Free Gemini AI Configuration")
            .setMessage("Add your Google Gemini API key for deep conversational intelligence in Bengali, Hindi & English.\n\n(100% Free at aistudio.google.com — No credit card needed. Leave empty to use the built-in Free AI Engine).")
            .setView(etKey)
            .setPositiveButton("Save Key") { _, _ ->
                val key = etKey.text.toString().trim()
                JarvisApp.instance.setGeminiApiKey(key)
                updateAiBadge()
                val msg = if (key.isNotBlank()) "Gemini AI Key saved! Advanced AI ready." else "Cleared API key. Using Free Built-in AI."
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("Get Free Key") { _, _ ->
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/app/apikey"))
                try {
                    startActivity(browserIntent)
                } catch (e: Exception) {
                    Toast.makeText(this, "Cannot open browser", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAddAliasDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_alias, null)
        val etTag = dialogView.findViewById<EditText>(R.id.etRelationshipTag)
        val etName = dialogView.findViewById<EditText>(R.id.etContactName)
        val etPhone = dialogView.findViewById<EditText>(R.id.etPhoneNumber)
        val btnPickContact = dialogView.findViewById<Button>(R.id.btnPickContact)

        // Relation chips
        val chipBou = dialogView.findViewById<Button>(R.id.chipRelationBou)
        val chipMaa = dialogView.findViewById<Button>(R.id.chipRelationMaa)
        val chipBaba = dialogView.findViewById<Button>(R.id.chipRelationBaba)
        val chipVai = dialogView.findViewById<Button>(R.id.chipRelationVai)
        val chipBoss = dialogView.findViewById<Button>(R.id.chipRelationBoss)
        val chipFriend = dialogView.findViewById<Button>(R.id.chipRelationFriend)

        chipBou?.setOnClickListener { etTag.setText("bou") }
        chipMaa?.setOnClickListener { etTag.setText("maa") }
        chipBaba?.setOnClickListener { etTag.setText("baba") }
        chipVai?.setOnClickListener { etTag.setText("vai") }
        chipBoss?.setOnClickListener { etTag.setText("boss") }
        chipFriend?.setOnClickListener { etTag.setText("friend") }

        activeDialogEtName = etName
        activeDialogEtPhone = etPhone

        val openContacts = {
            val pickIntent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
            try {
                contactPickerLauncher.launch(pickIntent)
            } catch (e: Exception) {
                try {
                    val fallbackIntent = Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI)
                    contactPickerLauncher.launch(fallbackIntent)
                } catch (e2: Exception) {
                    Toast.makeText(this, "Cannot open contacts application", Toast.LENGTH_SHORT).show()
                }
            }
        }

        btnPickContact?.setOnClickListener { openContacts() }
        etName?.setOnClickListener { openContacts() }

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton(R.string.btn_save) { _, _ ->
                val tag = etTag.text.toString().trim().lowercase()
                val name = etName.text.toString().trim()
                var phone = etPhone.text.toString().trim()

                if (tag.isNotEmpty() && name.isNotEmpty()) {
                    lifecycleScope.launch(Dispatchers.IO) {
                        if (phone.isEmpty()) {
                            val resolved = ContactHelper.getPhoneNumberForContact(this@MainActivity, name)
                            if (resolved != null) phone = resolved
                        }
                        JarvisApp.instance.database.aliasDao().insert(
                            AliasEntity(
                                relationshipTag = tag,
                                actualContactName = name,
                                phoneNumber = phone.ifEmpty { null }
                            )
                        )
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@MainActivity, "Trained: '$tag' = '$name'", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    Toast.makeText(this, "Please enter both relationship and contact name", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .create()

        dialog.setOnDismissListener {
            activeDialogEtName = null
            activeDialogEtPhone = null
        }
        dialog.show()
    }

    private fun hasCorePermissions(): Boolean {
        return corePermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun isNotificationServiceEnabled(): Boolean {
        return NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        return JarvisAccessibilityService.isRunning()
    }

    private fun isIgnoringBatteryOptimizations(): Boolean {
        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        return powerManager?.isIgnoringBatteryOptimizations(packageName) ?: false
    }

    private fun checkAndUpdatePermissionStatuses() {
        // Core permissions
        if (hasCorePermissions()) {
            btnGrantCorePerms.text = getString(R.string.enabled)
            btnGrantCorePerms.isEnabled = false
        } else {
            btnGrantCorePerms.text = getString(R.string.grant_permission)
            btnGrantCorePerms.isEnabled = true
        }

        // Notification Listener
        if (isNotificationServiceEnabled()) {
            btnGrantNotification.text = getString(R.string.enabled)
            btnGrantNotification.isEnabled = false
        } else {
            btnGrantNotification.text = getString(R.string.enable)
            btnGrantNotification.isEnabled = true
        }

        // Accessibility Service
        if (isAccessibilityServiceEnabled()) {
            btnGrantAccessibility.text = getString(R.string.enabled)
            btnGrantAccessibility.isEnabled = false
        } else {
            btnGrantAccessibility.text = getString(R.string.enable)
            btnGrantAccessibility.isEnabled = true
        }

        // Battery Optimization
        if (isIgnoringBatteryOptimizations()) {
            btnGrantBattery.text = getString(R.string.enabled)
            btnGrantBattery.isEnabled = false
        } else {
            btnGrantBattery.text = getString(R.string.enable)
            btnGrantBattery.isEnabled = true
        }
    }
}
