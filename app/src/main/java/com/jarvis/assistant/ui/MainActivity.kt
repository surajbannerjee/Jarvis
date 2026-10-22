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
import com.jarvis.assistant.speech.JarvisSpeechManager
import com.jarvis.assistant.ai.JarvisAiBrain
import com.jarvis.assistant.telephony.JarvisAccessibilityService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private val tag = "MainActivity"

    // Header & Persona Views
    private lateinit var tvAppTitle: TextView
    private lateinit var tvPersonaDisplayName: TextView
    private lateinit var tvPersonaCodeName: TextView
    private lateinit var tvPersonaDescription: TextView
    private lateinit var tvPersonaGenderBadge: TextView
    private lateinit var btnSwitchPersona: Button
    private lateinit var btnPreviewVoice: Button

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

    // Google Voice Recognition fallback launcher
    private val googleSpeechLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        setListeningUi(false)
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spokenText = matches?.firstOrNull()
            if (!spokenText.isNullOrBlank()) {
                tvVoiceStatus.text = getString(R.string.tap_to_speak)
                tvRecognizedSpeech.text = "You said: \"$spokenText\""
                tvRecognizedSpeech.visibility = View.VISIBLE
                processVoiceCommand(spokenText)
            }
        } else {
            tvVoiceStatus.text = getString(R.string.tap_to_speak)
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
    }

    override fun onResume() {
        super.onResume()
        checkAndUpdatePermissionStatuses()
        updateCustomAudioUi()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopPulseAnimation()
        CustomAudioPlayer.stopAudio()
    }

    private fun initViews() {
        tvAppTitle = findViewById(R.id.tvAppTitle)
        tvPersonaDisplayName = findViewById(R.id.tvPersonaDisplayName)
        tvPersonaCodeName = findViewById(R.id.tvPersonaCodeName)
        tvPersonaDescription = findViewById(R.id.tvPersonaDescription)
        tvPersonaGenderBadge = findViewById(R.id.tvPersonaGenderBadge)
        btnSwitchPersona = findViewById(R.id.btnSwitchPersona)
        btnPreviewVoice = findViewById(R.id.btnPreviewVoice)

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
        btnSwitchPersona.setOnClickListener {
            showPersonaSelectionDialog()
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
            updatePersonaUi(StarkPersona.FRIDAY)
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

    private fun showPersonaSelectionDialog() {
        val personas = StarkPersona.entries.toTypedArray()
        val items = personas.map { "${it.displayName} - ${it.codeName}" }.toTypedArray()
        val currentIdx = personas.indexOf(PersonaManager.getActivePersona())

        AlertDialog.Builder(this)
            .setTitle("Switch AI Voice Persona")
            .setSingleChoiceItems(items, currentIdx) { dialog, which ->
                val selected = personas[which]
                PersonaManager.setActivePersona(selected)
                dialog.dismiss()
                speechManager.previewPersona(selected, inBengali = true)
                Toast.makeText(this, "${selected.displayName} voice activated!", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
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
                Log.w(tag, "Native SpeechRecognizer error: $error. Falling back to Google Speech Dialog...")
                runOnUiThread {
                    setListeningUi(false)
                    try {
                        googleSpeechLauncher.launch(speechManager.createRecognizerIntent(SupportedLanguage.BENGALI))
                    } catch (e: Exception) {
                        tvVoiceStatus.text = "Voice error: $error"
                        Toast.makeText(this, "Voice recognition error: $error", Toast.LENGTH_SHORT).show()
                    }
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
                    val alias = JarvisApp.instance.database.aliasDao().findByTag(target)

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
                        phoneToCall = ContactHelper.getPhoneNumberForContact(this@MainActivity, target)
                    }

                    if (phoneToCall != null) {
                        val reply = when (intent.language) {
                            SupportedLanguage.BENGALI -> "$contactToCall ke call korchi..."
                            SupportedLanguage.HINDI -> "$contactToCall ko call laga raha hoon..."
                            SupportedLanguage.ENGLISH -> "Calling $contactToCall now..."
                        }
                        withContext(Dispatchers.Main) {
                            speechManager.speak(reply, intent.language) {
                                ContactHelper.makePhoneCall(this@MainActivity, phoneToCall)
                            }
                        }
                    } else {
                        val reply = when (intent.language) {
                            SupportedLanguage.BENGALI -> "$contactToCall er kono phone number khuje pelam na."
                            SupportedLanguage.HINDI -> "$contactToCall ka phone number nahi mila."
                            SupportedLanguage.ENGLISH -> "Could not find a phone number for $contactToCall."
                        }
                        withContext(Dispatchers.Main) {
                            speechManager.speak(reply, intent.language)
                            Toast.makeText(this@MainActivity, reply, Toast.LENGTH_SHORT).show()
                        }
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
