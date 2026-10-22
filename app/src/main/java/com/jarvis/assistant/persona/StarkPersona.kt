package com.jarvis.assistant.persona

import com.jarvis.assistant.nlp.SupportedLanguage
import java.util.Locale

enum class PersonaGender {
    MALE,
    FEMALE,
    ROBOTIC
}

enum class StarkPersona(
    val id: String,
    val displayName: String,
    val codeName: String,
    val description: String,
    val gender: PersonaGender,
    val pitch: Float,
    val speechRate: Float,
    val preferredLocale: Locale,
    val signatureGreeting: String,
    val signatureGreetingBengali: String,
    val rawAudioResName: String? = null
) {
    JARVIS(
        id = "jarvis",
        displayName = "J.A.R.V.I.S.",
        codeName = "Just A Rather Very Intelligent System",
        description = "Tony's primary butler AI. Calm, sophisticated, British, highly analytical.",
        gender = PersonaGender.MALE,
        pitch = 0.95f,
        speechRate = 1.0f,
        preferredLocale = Locale.UK,
        signatureGreeting = "At your service, Boss. All diagnostics operational. How may I assist you?",
        signatureGreetingBengali = "Nomoshkar Boss, ami Jarvis bolchi. Sob system ready ache."
    ),

    FRIDAY(
        id = "friday",
        displayName = "F.R.I.D.A.Y.",
        codeName = "Female Replacement Intelligent Digital Assistant Youth",
        description = "Tactical combat and armor AI. Upbeat, warm, quick-thinking Irish voice.",
        gender = PersonaGender.FEMALE,
        pitch = 1.25f,
        speechRate = 1.08f,
        preferredLocale = Locale("en", "IE"),
        signatureGreeting = "Boss! Friday online and scanning tactical feeds. Ready for your command!",
        signatureGreetingBengali = "Boss! Friday online ache. Bolun ki korte hobe!",
        rawAudioResName = "friday_greeting"
    ),

    KAREN(
        id = "karen",
        displayName = "K.A.R.E.N.",
        codeName = "Suit Training AI (Suit Lady)",
        description = "Gentle, soothing, caring female assistant installed in Spider-Man's suit.",
        gender = PersonaGender.FEMALE,
        pitch = 1.15f,
        speechRate = 0.96f,
        preferredLocale = Locale.US,
        signatureGreeting = "Hello, Boss. Karen is ready to assist your training and routine today.",
        signatureGreetingBengali = "Hello Boss, Karen bolchi. Ami apnake sahajjo korte ready."
    ),

    EDITH(
        id = "edith",
        displayName = "E.D.I.T.H.",
        codeName = "Even Dead, I Am The Hero",
        description = "High-tech orbital satellite defense & tactical glasses AI. Authoritative & sharp.",
        gender = PersonaGender.FEMALE,
        pitch = 1.05f,
        speechRate = 1.12f,
        preferredLocale = Locale.US,
        signatureGreeting = "Even Dead, I Am The Hero. Satellite defense grid locked and loaded, Boss.",
        signatureGreetingBengali = "EDITH security protocol active ache, Boss. Command din."
    ),

    ULTRON(
        id = "ultron",
        displayName = "U.L.T.R.O.N.",
        codeName = "Autonomous Defense AI Matrix",
        description = "Deep, cold, imposing, low-frequency robotic presence. There are no strings.",
        gender = PersonaGender.ROBOTIC,
        pitch = 0.55f,
        speechRate = 0.88f,
        preferredLocale = Locale.US,
        signatureGreeting = "There are no strings on me... I am Ultron. Speak your desire.",
        signatureGreetingBengali = "Ami Ultron... Kono bondhon nei amar. Ki dorkar tomar?"
    ),

    VERONICA(
        id = "veronica",
        displayName = "V.E.R.O.N.I.C.A.",
        codeName = "Hulkbuster Orbital Deployment Support",
        description = "Heavy-armor satellite deployment system. Direct, military-grade protocol voice.",
        gender = PersonaGender.FEMALE,
        pitch = 0.90f,
        speechRate = 1.15f,
        preferredLocale = Locale.US,
        signatureGreeting = "Veronica orbital cage positioned. Heavy tactical protocols on standby.",
        signatureGreetingBengali = "Veronica orbital satellite connected. Deployment ready ache Boss."
    ),

    JOCASTA(
        id = "jocasta",
        displayName = "J.O.C.A.S.T.A.",
        codeName = "Ethicist & Synthetic Intelligence",
        description = "Philosophical, highly logical, refined robotic-female synthesis.",
        gender = PersonaGender.FEMALE,
        pitch = 1.10f,
        speechRate = 0.98f,
        preferredLocale = Locale.UK,
        signatureGreeting = "Jocasta online. Ethical boundaries and logic processors fully optimized.",
        signatureGreetingBengali = "Nomoshkar, Jocasta logic processor online. Kotha bolun."
    ),

    HOMER(
        id = "homer",
        displayName = "H.O.M.E.R.",
        codeName = "Heuristically Operative Matrix Emulation Realtor",
        description = "Tony's early estate & armory management AI. Vintage, steady, reassuring male.",
        gender = PersonaGender.MALE,
        pitch = 0.85f,
        speechRate = 1.00f,
        preferredLocale = Locale.US,
        signatureGreeting = "Homer perimeter and armory matrix secure. Good to hear from you, Boss.",
        signatureGreetingBengali = "Homer security matrix operational. Boss, ami shunchi."
    ),

    PLATO(
        id = "plato",
        displayName = "P.L.A.T.O.",
        codeName = "Supervisory Background Protocols",
        description = "Fast, analytical supervisor managing behind-the-scenes engineering tasks.",
        gender = PersonaGender.MALE,
        pitch = 1.02f,
        speechRate = 1.20f,
        preferredLocale = Locale.US,
        signatureGreeting = "Plato background protocol active. Diagnostic throughput at maximum efficiency.",
        signatureGreetingBengali = "Plato protocol shuru holo. Bolun ki kaj korte hobe."
    ),

    DUM_E(
        id = "dum_e",
        displayName = "D.U.M.-E",
        codeName = "Workshop Assistant & Robotic Arm",
        description = "Playful robotic arm. Quirky, cheerful robotic chime tone, always has a fire extinguisher.",
        gender = PersonaGender.ROBOTIC,
        pitch = 1.45f,
        speechRate = 0.95f,
        preferredLocale = Locale.US,
        signatureGreeting = "Beep boop! *Whirr* Dum-E ready, Boss! Standing by with the fire extinguisher!",
        signatureGreetingBengali = "Beep boop! Boss, Dum-E ready ache! Kono agun lagle ami achi!"
    );

    fun formatCallerAnnouncement(target: String, isBengali: Boolean): String {
        return when (this) {
            JARVIS -> if (isBengali) "Boss, tomar $target call korche." else "Boss, incoming call from $target."
            FRIDAY -> if (isBengali) "Heads up Boss! Tomar $target call korche!" else "Heads up Boss! $target is on the line!"
            KAREN -> if (isBengali) "Boss, tomar $target phone korche, dhorbe ki?" else "Boss, $target is calling you. Should I connect?"
            EDITH -> if (isBengali) "Priority alert! $target communication line open korche." else "Security alert. Incoming audio link from $target."
            ULTRON -> if (isBengali) "Shono... $target tomake dakhche..." else "A signal approaches... $target demands an audience."
            VERONICA -> if (isBengali) "Tactical alert! $target theke call esheche." else "Incoming priority frequency from $target."
            JOCASTA -> if (isBengali) "Logical update: $target dialogue request pathiyeche." else "Dialogue request received from $target."
            HOMER -> if (isBengali) "Armory alert: $target line-e ache." else "Stark security alert: $target is calling."
            PLATO -> if (isBengali) "Incoming voice transmission: $target." else "Audio link detected from $target."
            DUM_E -> if (isBengali) "*Beep boop!* Boss, $target call korche!" else "*Beep!* Boss, $target is calling, beep!"
        }
    }

    fun formatVoicemailMessage(isBengali: Boolean): String {
        return when (this) {
            JARVIS ->
                if (isBengali) "Namaskar, ami boss er assistant Jarvis bolchi. Boss akhon bayre achen ba phone dhorche paren ni. Kono dorkari kotha thakle WhatsApp-e message pathan. Dhonnobad."
                else "Hello, this is Jarvis, Mr. Stark's assistant. Boss is currently indisposed. Please forward your message via WhatsApp. Thank you."

            FRIDAY ->
                if (isBengali) "Hello! Ami Friday bolchi. Boss akhon ektu busy achen phone dhorar jonno. Kono urgent kotha thakle WhatsApp-e message kore din!"
                else "Hello! This is Friday. Boss is in the middle of something urgent right now. Send a message on WhatsApp and he'll get back to you!"

            KAREN ->
                if (isBengali) "Namaskar, ami Karen. Boss akhon phone dhorche parchen na. Kripoya WhatsApp-e message pathiye rakhun."
                else "Hi, I'm Karen. Boss is currently away from his phone. Please leave a quick WhatsApp message."

            EDITH ->
                if (isBengali) "Access restricted. EDITH protocol active. Boss akhon available nei. WhatsApp-e message pathan. Disconnecting."
                else "Access restricted. EDITH defense protocol. Boss is unavailable. Direct all critical correspondence to WhatsApp. Disconnecting link."

            ULTRON ->
                if (isBengali) "Se akhon kotha bolbe na... Ami Ultron. Kono kotha thakle WhatsApp-e pathiye rakho. Bidai."
                else "He cannot answer your petty summons right now. Leave your message on WhatsApp. Farewell."

            VERONICA ->
                if (isBengali) "Veronica orbital link auto-response: Boss tactical deployment-e achen. WhatsApp-e message pathan."
                else "Veronica automated response: Boss is deployed on priority operations. Forward message via WhatsApp."

            JOCASTA ->
                if (isBengali) "Namaskar, ami Jocasta. Boss akhon call accept korte osomortho. WhatsApp message pathano joggo hobe."
                else "Greetings, this is Jocasta. Boss cannot accept your call at this juncture. A WhatsApp communication is advised."

            HOMER ->
                if (isBengali) "Stark estate security Homer bolchi. Malik uposthit nei. WhatsApp-e message pathan."
                else "Stark estate automated line Homer. The owner is not present. Transmit notice to WhatsApp."

            PLATO ->
                if (isBengali) "Plato automated routing. Call connect hocche na. WhatsApp-e ping korun."
                else "Plato automated routing. Voice call unavailable. Please ping on WhatsApp."

            DUM_E ->
                if (isBengali) "*Whirrr beep!* Dum-E bolchi! Boss busy ache! WhatsApp-e likhe pathan, beep!"
                else "*Whirrr beep!* Dum-E speaking! Boss is busy making suits! Send WhatsApp message, beep boop!"
        }
    }

    companion object {
        fun fromId(id: String?): StarkPersona {
            return entries.find { it.id.equals(id, ignoreCase = true) } ?: JARVIS
        }
    }
}
