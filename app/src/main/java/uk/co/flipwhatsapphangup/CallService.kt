package uk.co.flipwhatsapphangup

enum class CallService(val id: String, val title: String, val logo: Int, val logoColor: String,
                       val packageName: String? = null) {
    WHATSAPP("whatsapp", "WhatsApp", R.drawable.logo_whatsapp, "#25D366", "com.whatsapp"),
    SNAPCHAT("snapchat", "Snapchat", R.drawable.logo_snapchat, "#FFFC00", "com.snapchat.android"),
    INSTAGRAM("instagram", "Instagram", R.drawable.logo_instagram, "#E4405F", "com.instagram.android"),
    MESSENGER("messenger", "Messenger", R.drawable.logo_messenger, "#0866FF", "com.facebook.orca"),
    GOOGLE_MEET("google_meet", "Google Meet", R.drawable.logo_googlemeet, "#00897B", "com.google.android.apps.tachyon"),
    DISCORD("discord", "Discord", R.drawable.logo_discord, "#5865F2", "com.discord");

    val available get() = packageName != null

    companion object {
        const val EXTRA_SERVICE = "call_service"
        fun fromId(id: String?) = entries.firstOrNull { it.id == id } ?: WHATSAPP
        fun fromPackageName(packageName: String) = entries.firstOrNull { it.packageName == packageName }
    }
}
