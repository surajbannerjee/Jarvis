package com.jarvis.assistant.nlp

import java.util.Locale
import java.util.regex.Pattern

enum class SupportedLanguage(val code: String, val locale: Locale) {
    BENGALI("bn", Locale("bn", "IN")),
    HINDI("hi", Locale("hi", "IN")),
    ENGLISH("en", Locale.US);

    companion object {
        fun fromLocale(locale: Locale): SupportedLanguage {
            return when (locale.language) {
                "bn" -> BENGALI
                "hi" -> HINDI
                else -> ENGLISH
            }
        }
    }
}

sealed class JarvisIntent(open val language: SupportedLanguage) {
    data class TrainRelation(
        val relation: String,
        val contactName: String,
        override val language: SupportedLanguage
    ) : JarvisIntent(language)

    data class CallTarget(
        val target: String,
        val isRelationHint: Boolean,
        override val language: SupportedLanguage
    ) : JarvisIntent(language)

    data class WhatsAppQuery(
        override val language: SupportedLanguage
    ) : JarvisIntent(language)

    data class Unauthorized(
        val rawText: String,
        override val language: SupportedLanguage
    ) : JarvisIntent(language)

    data class Unknown(
        val rawText: String,
        override val language: SupportedLanguage
    ) : JarvisIntent(language)
}

object CommandParser {

    // Common relationship aliases in South Asian languages & English
    val COMMON_RELATIONS = setOf(
        "bou", "wife", "stri", "patni", "বউ", "স্ত্রী",
        "maa", "ma", "mummy", "mom", "mother", "mataji", "মা", "মাথা",
        "baba", "papa", "dad", "father", "pitaji", "বাবা", "পাপা",
        "bhai", "brother", "dada", "bro", "ভাই", "দাদা",
        "bon", "sister", "didi", "sis", "বোন", "দিদি",
        "boss", "sir", "manager", "বস", "স্যার",
        "friend", "bondhu", "dost", "yaar", "বন্ধু", "দোস্ত",
        "kaku", "uncle", "chacha", "mama", "কাকা", "মামা",
        "kaki", "aunt", "chachi", "mami", "কাকি", "মামি"
    )

    fun parse(input: String?): JarvisIntent {
        if (input.isNullOrBlank()) {
            return JarvisIntent.Unknown("", SupportedLanguage.ENGLISH)
        }

        val rawLower = input.lowercase(Locale.ROOT)
        val cleaned = cleanInput(input)
        val language = detectLanguage(cleaned)

        // Check Owner Voice Security Lock if enabled
        if (com.jarvis.assistant.JarvisApp.instance.isOwnerVoiceLockEnabled()) {
            val hasOwnerMarker = rawLower.contains("suraj") || rawLower.contains("সুরজ") ||
                    rawLower.contains("boss") || rawLower.contains("বস") ||
                    rawLower.contains("amar") || rawLower.contains("আমার") ||
                    rawLower.contains("friday") || rawLower.contains("ফ্রাইডে")
            if (!hasOwnerMarker && cleaned.length > 5) {
                return JarvisIntent.Unauthorized(cleaned, language)
            }
        }

        // 1. Try matching Training / Memory Intent
        val trainIntent = matchTrainingIntent(cleaned, language)
        if (trainIntent != null) return trainIntent

        // 2. Try matching Smart Call Intent
        val callIntent = matchCallIntent(cleaned, language)
        if (callIntent != null) return callIntent

        // 3. Fallback to Unknown
        return JarvisIntent.Unknown(cleaned, language)
    }

    private fun cleanInput(raw: String): String {
        return raw.trim()
            .replace(Regex("^(hey\\s+|hi\\s+|hello\\s+|হে\\s+|হাই\\s+|হ্যালো\\s+)?(friday|jarvis|ফ্রাইডে|জার্ভিস)[,\\s]*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("[?!.,]$"), "")
            .trim()
    }

    fun detectLanguage(text: String): SupportedLanguage {
        val lower = text.lowercase(Locale.ROOT)

        // 1. Script checks (Bengali & Devanagari)
        for (ch in text) {
            val u = ch.code
            if (u in 0x0980..0x09FF) return SupportedLanguage.BENGALI
            if (u in 0x0900..0x097F) return SupportedLanguage.HINDI
        }

        // 2. Banglish / Romanized Bengali keyword markers
        val bengaliKeywords = listOf(
            "amar", "amr", "bou", "er", "holo", "koro", "korchi", "korche", "korbo",
            "mone", "rekho", "dhorche", "dhoro", "bolchi", "bolo", "khobor", "pathiyeche",
            "ke", "kemon", "acho", "achi", "valo", "bhalo", "somoy", "baje", "koto",
            "tarikh", "tumi", "apni", "apnar", "shonao", "hasao", "kotha", "shuncho",
            "shunte", "pacho", "sahajjo", "parba", "parbo", "paro", "ki", "naam"
        )

        // 3. Hinglish / Romanized Hindi keyword markers
        val hindiKeywords = listOf(
            "mera", "meri", "mere", "ko", "karo", "karenge", "karega", "lagao", "naam",
            "hai", "ho", "hoon", "yaad", "rakho", "bol", "bhai", "kripya", "kaise", "kya",
            "samay", "batao", "suno", "madat", "tum", "aap", "kaun"
        )

        val words = lower.split("[\\s,?.!]+".toRegex()).toSet()
        var bengaliScore = words.count { it in bengaliKeywords }
        var hindiScore = words.count { it in hindiKeywords }

        // Contextual phrases
        if (lower.contains("er nam") || lower.contains("ke call") || lower.contains("call koro") ||
            lower.contains("kemon acho") || lower.contains("ki korte") || lower.contains("koto baje")
        ) {
            bengaliScore += 4
        }
        if (lower.contains("ka naam") || lower.contains("ko call") || lower.contains("call lagao") ||
            lower.contains("kaise ho") || lower.contains("kya kar") || lower.contains("kitne baje")
        ) {
            hindiScore += 4
        }

        return when {
            bengaliScore > hindiScore && bengaliScore > 0 -> SupportedLanguage.BENGALI
            hindiScore > bengaliScore && hindiScore > 0 -> SupportedLanguage.HINDI
            bengaliScore > 0 -> SupportedLanguage.BENGALI
            hindiScore > 0 -> SupportedLanguage.HINDI
            else -> SupportedLanguage.ENGLISH
        }
    }

    private fun matchTrainingIntent(text: String, lang: SupportedLanguage): JarvisIntent.TrainRelation? {
        val lower = text.lowercase(Locale.ROOT)

        // Bengali patterns:
        // "amar bou er nam Priyanka", "amar bou holo Priyanka", "amar bou Priyanka", "mone rekho amar bou holo Priyanka"
        val bnPatterns = listOf(
            Regex("(?:mone rekho )?amar\\s+([a-zA-Z\\u0980-\\u09FF]+)\\s+er\\s+na[am]\\s+(?:holo\\s+)?(.+)", RegexOption.IGNORE_CASE),
            Regex("(?:mone rekho )?amar\\s+([a-zA-Z\\u0980-\\u09FF]+)\\s+holo\\s+(.+)", RegexOption.IGNORE_CASE),
            Regex("remember amar\\s+([a-zA-Z\\u0980-\\u09FF]+)\\s+is\\s+(.+)", RegexOption.IGNORE_CASE)
        )
        for (pattern in bnPatterns) {
            val match = pattern.find(text)
            if (match != null && match.groupValues.size >= 3) {
                val relation = match.groupValues[1].trim().lowercase(Locale.ROOT)
                val name = match.groupValues[2].trim()
                if (relation.isNotEmpty() && name.isNotEmpty()) {
                    return JarvisIntent.TrainRelation(relation, name, SupportedLanguage.BENGALI)
                }
            }
        }

        // Hindi patterns:
        // "mera bhai Rahul hai", "meri mummy ka naam Sunita hai", "yaad rakhna meri wife Priyanka hai"
        val hiPatterns = listOf(
            Regex("(?:yaad rakh(?:na|o) )?mer(?:a|i)\\s+([a-zA-Z\\u0900-\\u097F]+)\\s+ka\\s+naam\\s+(.+?)(?:\\s+hai)?$", RegexOption.IGNORE_CASE),
            Regex("(?:yaad rakh(?:na|o) )?mer(?:a|i)\\s+([a-zA-Z\\u0900-\\u097F]+)\\s+(?:hai\\s+)?(.+?)(?:\\s+hai)?$", RegexOption.IGNORE_CASE),
            Regex("([a-zA-Z\\u0900-\\u097F]+)\\s+ka\\s+naam\\s+(.+?)(?:\\s+hai)?$", RegexOption.IGNORE_CASE)
        )
        for (pattern in hiPatterns) {
            val match = pattern.find(text)
            if (match != null && match.groupValues.size >= 3) {
                val relation = match.groupValues[1].trim().lowercase(Locale.ROOT)
                val name = match.groupValues[2].trim()
                if (relation.isNotEmpty() && name.isNotEmpty() && !name.equals("hai", ignoreCase = true)) {
                    return JarvisIntent.TrainRelation(relation, name, SupportedLanguage.HINDI)
                }
            }
        }

        // English patterns:
        // "remember my wife is Priyanka", "my wife is Priyanka", "remember my boss is Rahul"
        val enPatterns = listOf(
            Regex("(?:remember\\s+)?(?:that\\s+)?my\\s+([a-zA-Z]+)\\s+is\\s+(.+)", RegexOption.IGNORE_CASE),
            Regex("(?:set|save)\\s+my\\s+([a-zA-Z]+)\\s+(?:as|to)\\s+(.+)", RegexOption.IGNORE_CASE),
            Regex("(?:remember\\s+)?([a-zA-Z]+)\\s+'?s\\s+name\\s+is\\s+(.+)", RegexOption.IGNORE_CASE)
        )
        for (pattern in enPatterns) {
            val match = pattern.find(text)
            if (match != null && match.groupValues.size >= 3) {
                val relation = match.groupValues[1].trim().lowercase(Locale.ROOT)
                val name = match.groupValues[2].trim()
                if (relation.isNotEmpty() && name.isNotEmpty()) {
                    return JarvisIntent.TrainRelation(relation, name, SupportedLanguage.ENGLISH)
                }
            }
        }

        return null
    }

    private fun matchCallIntent(text: String, lang: SupportedLanguage): JarvisIntent.CallTarget? {
        val lower = text.lowercase(Locale.ROOT).trim()

        // 1. Bengali Script Patterns ("প্রিয়াঙ্কাকে কল করো", "বউকে ফোন করো", "কল করো প্রিয়াঙ্কা")
        val bnScriptPatterns = listOf(
            Regex("(?:আমার\\s+)?(.+?)\\s*(?:কে|রে)?\\s*(?:কল|ফোন|ডায়াল|ডায়াল)\\s*(?:করো|করুন|কর|লাগান|লাগাও|দাও)?", RegexOption.IGNORE_CASE),
            Regex("(?:কল|ফোন|ডায়াল|ডায়াল)\\s*(?:করো|করুন|কর|লাগান|লাগাও|দাও)?\\s*(?:আমার\\s+)?(.+?)(?:\\s*কে)?$", RegexOption.IGNORE_CASE),
            Regex("(?:কল|ফোন)\\s+(.+)$", RegexOption.IGNORE_CASE),
            Regex("(.+?)\\s*(?:কে|রে)\\s*(?:কল|ফোন)$", RegexOption.IGNORE_CASE)
        )
        for (p in bnScriptPatterns) {
            val match = p.find(text)
            if (match != null && match.groupValues.size >= 2) {
                var raw = match.groupValues[1].trim()
                    .replace(Regex("^(আমার|amr|amar)\\s+"), "")
                    .replace(Regex("\\s*(কে|re|ke)$"), "")
                    .trim()
                if (raw.isNotEmpty() && !raw.contains("না") && raw != "করো" && raw != "ফোন") {
                    val isRelation = COMMON_RELATIONS.contains(raw.lowercase(Locale.ROOT))
                    return JarvisIntent.CallTarget(raw, isRelation, SupportedLanguage.BENGALI)
                }
            }
        }

        // 2. Banglish Patterns ("amar bou ke call koro", "call Priyanka", "Priyanka ke phone koro", "call koro Priyanka")
        val bnCallPatterns = listOf(
            Regex("(?:amar\\s+)?(.+?)\\s+(?:ke|k)\\s+(?:call|phone|dial)\\s*(?:koro|korun|lagao|korte)?", RegexOption.IGNORE_CASE),
            Regex("(?:call|phone|dial)\\s+(?:koro|korun|lagao|korte)?\\s*(?:amar\\s+)?(.+?)(?:\\s+(?:ke|k))?$", RegexOption.IGNORE_CASE),
            Regex("(.+?)\\s+(?:ke|k)\\s+(?:call|phone|dial)$", RegexOption.IGNORE_CASE),
            Regex("(?:call|phone|dial)\\s+(?:amar\\s+)?(.+)", RegexOption.IGNORE_CASE)
        )
        for (p in bnCallPatterns) {
            val match = p.find(text)
            if (match != null && match.groupValues.size >= 2) {
                var rawTarget = match.groupValues[1].trim()
                    .replace(Regex("^(amar|meri|my)\\s+", RegexOption.IGNORE_CASE), "")
                    .replace(Regex("\\s+(ke|ko|k)$", RegexOption.IGNORE_CASE), "")
                    .trim()
                if (rawTarget.isNotEmpty()) {
                    val isRelation = COMMON_RELATIONS.contains(rawTarget.lowercase(Locale.ROOT))
                    return JarvisIntent.CallTarget(rawTarget, isRelation, SupportedLanguage.BENGALI)
                }
            }
        }

        // 3. Hindi patterns:
        // "mummy ko call lagao", "mera boss ko call karo", "Priyanka ko phone lagao"
        val hiCallPatterns = listOf(
            Regex("(?:mer(?:a|i)\\s+)?(.+?)\\s+ko\\s+(?:call|phone|dial)\\s+(?:lagao|karo|kijie)", RegexOption.IGNORE_CASE),
            Regex("(?:call|phone|dial)\\s+(?:lagao|karo)\\s+(?:mer(?:a|i)\\s+)?(.+?)(?:\\s+ko)?$", RegexOption.IGNORE_CASE)
        )
        for (p in hiCallPatterns) {
            val match = p.find(text)
            if (match != null && match.groupValues.size >= 2) {
                val rawTarget = match.groupValues[1].trim().replace(Regex("mer[ai]\\s+"), "").trim()
                if (rawTarget.isNotEmpty()) {
                    val isRelation = COMMON_RELATIONS.contains(rawTarget.lowercase(Locale.ROOT))
                    return JarvisIntent.CallTarget(rawTarget, isRelation, SupportedLanguage.HINDI)
                }
            }
        }

        // 4. English patterns:
        // "call my wife", "call Priyanka", "dial my boss", "phone my mother"
        val enCallPatterns = listOf(
            Regex("(?:please\\s+)?(?:call|dial|phone)\\s+(?:my\\s+)?(.+)", RegexOption.IGNORE_CASE),
            Regex("(?:make\\s+a\\s+call\\s+to\\s+)(?:my\\s+)?(.+)", RegexOption.IGNORE_CASE)
        )
        for (p in enCallPatterns) {
            val match = p.find(text)
            if (match != null && match.groupValues.size >= 2) {
                val rawTarget = match.groupValues[1].trim()
                if (rawTarget.isNotEmpty()) {
                    val isRelation = COMMON_RELATIONS.contains(rawTarget.lowercase(Locale.ROOT))
                    return JarvisIntent.CallTarget(rawTarget, isRelation, SupportedLanguage.ENGLISH)
                }
            }
        }

        return null
    }
}
