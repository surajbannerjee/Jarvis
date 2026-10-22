package com.jarvis.assistant.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
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
import com.jarvis.assistant.contacts.ContactHelper
import com.jarvis.assistant.data.entity.AliasEntity
import com.jarvis.assistant.nlp.CommandParser
import com.jarvis.assistant.nlp.JarvisIntent
import com.jarvis.assistant.nlp.SupportedLanguage
import com.jarvis.assistant.persona.PersonaGender
import com.jarvis.assistant.persona.PersonaManager
import com.jarvis.assistant.persona.StarkPersona
import com.jarvis.assistant.speech.JarvisSpeechManager
import com.jarvis.assistant.telephony.JarvisAccessibilityService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupRecyclerView()
        setupListeners()
        observeAliases()
        observePersona()
    }

    override fun onResume() {
        super.onResume()
        checkAndUpdatePermissionStatuses()
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
            speechManager.previewPersona(persona)
            Toast.makeText(this, "Playing ${persona.displayName} voice preview...", Toast.LENGTH_SHORT).show()
        }

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

        btnMic.setOnClickListener {
            startVoiceAssistant()
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
            }
        }
    }

    private fun showPersonaSelectionDialog() {
        val personas = StarkPersona.entries.toTypedArray()
        val items = personas.map { "${it.displayName} - ${it.codeName}" }.toTypedArray()
        val currentIdx = personas.indexOf(PersonaManager.getActivePersona())

        AlertDialog.Builder(this)
            .setTitle("Select Stark AI Persona")
            .setSingleChoiceItems(items, currentIdx) { dialog, which ->
                val selected = personas[which]
                PersonaManager.setActivePersona(selected)
                dialog.dismiss()
                speechManager.previewPersona(selected)
                Toast.makeText(this, "${selected.displayName} active!", Toast.LENGTH_SHORT).show()
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

    private fun startVoiceAssistant() {
        if (!hasCorePermissions()) {
            Toast.makeText(this, "Please grant core permissions first.", Toast.LENGTH_SHORT).show()
            permissionLauncher.launch(corePermissions.toTypedArray())
            return
        }

        tvVoiceStatus.text = getString(R.string.listening)
        tvRecognizedSpeech.visibility = View.GONE

        speechManager.startListening(
            preferredLanguage = SupportedLanguage.BENGALI,
            onReady = {
                tvVoiceStatus.text = "Listening… Speak now"
            },
            onResults = { recognizedText ->
                tvVoiceStatus.text = getString(R.string.tap_to_speak)
                tvRecognizedSpeech.text = "You said: \"$recognizedText\""
                tvRecognizedSpeech.visibility = View.VISIBLE
                processVoiceCommand(recognizedText)
            },
            onError = { error ->
                tvVoiceStatus.text = "Error: $error"
            }
        )
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
                    var contactToCall = target
                    var phoneToCall: String? = null

                    if (intent.isRelationHint || CommandParser.COMMON_RELATIONS.contains(target.lowercase())) {
                        val alias = JarvisApp.instance.database.aliasDao().findByTag(target.lowercase())
                        if (alias != null) {
                            contactToCall = alias.actualContactName
                            phoneToCall = alias.phoneNumber ?: ContactHelper.getPhoneNumberForContact(this@MainActivity, contactToCall)
                        } else {
                            val notFoundReply = when (intent.language) {
                                SupportedLanguage.BENGALI -> "Ami $target er number jani na. Kar sathe connect korbo?"
                                SupportedLanguage.HINDI -> "Mujhe $target ka number nahi pata. Kise call karoon?"
                                SupportedLanguage.ENGLISH -> "I don't know the number for $target. Who should I connect with?"
                            }
                            withContext(Dispatchers.Main) {
                                speechManager.speak(notFoundReply, intent.language)
                                Toast.makeText(this@MainActivity, notFoundReply, Toast.LENGTH_LONG).show()
                            }
                            return@launch
                        }
                    } else {
                        phoneToCall = ContactHelper.getPhoneNumberForContact(this@MainActivity, target)
                    }

                    if (!phoneToCall.isNullOrBlank()) {
                        val callingReply = when (intent.language) {
                            SupportedLanguage.BENGALI -> "$contactToCall ke call korchi..."
                            SupportedLanguage.HINDI -> "$contactToCall ko call laga raha hoon..."
                            SupportedLanguage.ENGLISH -> "Calling $contactToCall..."
                        }
                        withContext(Dispatchers.Main) {
                            speechManager.speak(callingReply, intent.language) {
                                ContactHelper.makePhoneCall(this@MainActivity, phoneToCall)
                            }
                        }
                    } else {
                        val noNumberReply = when (intent.language) {
                            SupportedLanguage.BENGALI -> "$contactToCall er kono phone number pelam na."
                            SupportedLanguage.HINDI -> "$contactToCall ka phone number nahi mila."
                            SupportedLanguage.ENGLISH -> "Could not find a phone number for $contactToCall."
                        }
                        withContext(Dispatchers.Main) {
                            speechManager.speak(noNumberReply, intent.language)
                            Toast.makeText(this@MainActivity, noNumberReply, Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                is JarvisIntent.WhatsAppQuery -> {
                    val reply = "WhatsApp notification monitoring active."
                    withContext(Dispatchers.Main) {
                        speechManager.speak(reply, intent.language)
                    }
                }

                is JarvisIntent.Unknown -> {
                    val reply = when (intent.language) {
                        SupportedLanguage.BENGALI -> "Ami bujhte parlam na. Abar bolun."
                        SupportedLanguage.HINDI -> "Mujhe samajh nahi aaya. Kripya dobara bolein."
                        SupportedLanguage.ENGLISH -> "I could not understand that. Please try again."
                    }
                    withContext(Dispatchers.Main) {
                        speechManager.speak(reply, intent.language)
                    }
                }
            }
        }
    }

    private fun showAddAliasDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_alias, null)
        val etTag = dialogView.findViewById<EditText>(R.id.etRelationshipTag)
        val etName = dialogView.findViewById<EditText>(R.id.etContactName)
        val etPhone = dialogView.findViewById<EditText>(R.id.etPhoneNumber)

        AlertDialog.Builder(this)
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
                    }
                } else {
                    Toast.makeText(this, "Please enter both relationship and contact name", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
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
