package uk.co.flipwhatsapphangup

import java.text.Normalizer
import java.util.Locale

/** Android CallStyle translations plus the labels used by supported calling apps. */
internal object CallLabels {
    private fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKD)
        .filter { Character.getType(it) != Character.NON_SPACING_MARK.toInt() &&
            Character.getType(it) != Character.FORMAT.toInt() }
        .trim().lowercase(Locale.ROOT)

    private val answers = AndroidCallLabels.answers.map(::normalize).toSet()
    private val videoAnswers = AndroidCallLabels.video_answers.map(::normalize).toSet()
    private val declines = AndroidCallLabels.declines.map(::normalize).toSet()
    private val hangups = AndroidCallLabels.hangups.map(::normalize).toSet()
    private val incoming = AndroidCallLabels.incoming.map(::normalize).toSet()
    private val ongoing = AndroidCallLabels.ongoing.map(::normalize).toSet()

    fun answer(label: String, video: Boolean = false, join: Boolean = false): Boolean {
        val value = normalize(label)
        return value in answers || video && value in videoAnswers ||
            "answer" in value || "accept" in value || join && "join" in value
    }

    fun decline(label: String): Boolean {
        val value = normalize(label)
        return value in declines || "decline" in value || "reject" in value
    }

    fun hangup(label: String): Boolean {
        val value = normalize(label)
        return value in hangups || listOf("end call", "hang up", "hangup", "disconnect", "leave")
            .any { it in value } || value == "end" ||
            // Accessibility labels can include a noun after the platform's translated verb.
            hangups.any { it.length >= 5 && (value.startsWith("$it ") || value.endsWith(" $it")) }
    }

    fun incomingText(text: String) = matchesCallText(normalize(text), incoming) || "incoming" in normalize(text)
    fun ongoingText(text: String) = matchesCallText(normalize(text), ongoing) || "ongoing" in normalize(text)
    fun video(text: String): Boolean {
        val value = normalize(text)
        return videoAnswers.any { it.length >= 3 && it in value } || "video" in value
    }

    // Match the complete translated phrase. A shared word such as Greek "κλήση"
    // (call) appears in both incoming and ongoing labels and must not decide phase.
    private fun matchesCallText(value: String, phrases: Set<String>): Boolean = phrases.any { phrase ->
        phrase.length >= 4 && phrase in value
    }
}
