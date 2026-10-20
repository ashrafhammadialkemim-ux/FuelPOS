package com.arf.fuelpos

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color

/** All app settings in one place, shared by every screen. */
object Cfg {
    const val PREFS = "fuel_pos_prefs"

    // ---- General ----
    const val URL = "server_url"
    const val HOME = "home_url"
    const val ZOOM = "text_zoom"
    const val KEEP_SCREEN = "keep_screen_on"
    const val PULL_REFRESH = "pull_refresh"
    const val FULLSCREEN = "fullscreen"
    const val EXIT_CONFIRM = "exit_confirm"
    const val INVOICE_ACTION = "invoice_action"   // 0 share, 1 print, 2 open, 3 save only

    // ---- Floating bar ----
    const val FAB_X = "fab_x"
    const val FAB_Y = "fab_y"
    const val FAB_ICON = "fab_icon"
    const val FAB_COLOR = "fab_color"
    const val FAB_SIZE = "fab_size"               // 0 small, 1 normal, 2 large
    const val FAB_ALPHA = "fab_alpha"             // 30..100 %
    const val FAB_SHOW_HOME = "fab_show_home"
    const val FAB_SHOW_LANG = "fab_show_lang"

    // ---- Language ----
    const val APP_LANG = "app_lang"               // "" auto, "ar", "en"
    const val PAGE_LANG = "page_lang"             // "" / "ar" = original, otherwise Google code

    // ---- Security ----
    const val ADMIN_USER = "admin_user"
    const val ADMIN_PASS = "admin_pass"

    // ---- Leave-page dialog: texts (stored per language: key + "_" + lang) ----
    const val L_TITLE = "l_title"
    const val L_MSG_HOME = "l_msg_home"
    const val L_MSG_OTHER = "l_msg_other"
    const val L_STAY = "l_stay"
    const val L_LEAVE_HOME = "l_leave_home"
    const val L_LEAVE_OTHER = "l_leave_other"

    // ---- Leave-page dialog: look & motion ----
    const val L_STAY_COLOR = "l_stay_color"
    const val L_LEAVE_COLOR = "l_leave_color"
    const val L_BORDER_THEME = "l_border_theme"
    const val L_BORDER_ANIM = "l_border_anim"
    const val L_BORDER_SPEED = "l_border_speed"
    const val L_BORDER_W = "l_border_w"
    const val L_LEAVE_STYLE = "l_leave_style"     // 0 animated border, 1 outlined, 2 filled
    const val L_PULSE = "l_pulse"
    const val L_PULSE_MS = "l_pulse_ms"
    const val L_BREATHE = "l_breathe"
    const val L_ENTRANCE = "l_entrance"           // 0 none, 1 fade, 2 slide up, 3 zoom, 4 slide down, 5 bounce
    const val L_ENTER_MS = "l_enter_ms"
    const val L_RADIUS = "l_radius"
    const val L_SHOW_ICON = "l_show_icon"
    const val L_ICON = "l_icon"                   // 0 leave, 1 warning, 2 info, 3 home
    const val L_BTN_ICONS = "l_btn_icons"
    const val L_ORDER = "l_order"                 // 0 stay first, 1 leave first
    const val L_BTN_H = "l_btn_h"
    const val L_TITLE_SIZE = "l_title_size"
    const val L_MSG_SIZE = "l_msg_size"
    const val L_WIDTH = "l_width"                 // % of screen width
    const val L_DIM = "l_dim"                     // background dim %
    const val L_CANCELABLE = "l_cancelable"
    const val L_HAPTIC = "l_haptic"

    val LEAVE_KEYS = listOf(
        L_STAY_COLOR, L_LEAVE_COLOR, L_BORDER_THEME, L_BORDER_ANIM, L_BORDER_SPEED, L_BORDER_W,
        L_LEAVE_STYLE, L_PULSE, L_PULSE_MS, L_BREATHE, L_ENTRANCE, L_ENTER_MS, L_RADIUS, L_SHOW_ICON,
        L_ICON, L_BTN_ICONS, L_ORDER, L_BTN_H, L_TITLE_SIZE, L_MSG_SIZE, L_WIDTH, L_DIM,
        L_CANCELABLE, L_HAPTIC
    )
    val LEAVE_TEXT_KEYS = listOf(L_TITLE, L_MSG_HOME, L_MSG_OTHER, L_STAY, L_LEAVE_HOME, L_LEAVE_OTHER)

    // ---- Defaults ----
    const val DEF_ZOOM = 115
    const val DEF_USER = "ASHRAF"
    const val DEF_PASS = "OLB111"
    const val DEF_RADIUS = 16
    const val DEF_SPEED = 3500

    val ZOOM_LEVELS = intArrayOf(85, 100, 115, 130, 150)

    /** Oracle APEX (Universal Theme / Vita) palette used by the settings screens. */
    val APEX_BLUE = Color.parseColor("#0572CE")
    val APEX_BLUE_DARK = Color.parseColor("#045BA5")

    /** Swatches offered in the color pickers. */
    val SWATCHES = intArrayOf(
        c("#0572CE"), c("#1D4ED8"), c("#0B5FA5"), c("#0F766E"), c("#15803D"), c("#65A30D"),
        c("#7C3AED"), c("#BE185D"), c("#B91C1C"), c("#DC2626"), c("#C2410C"), c("#A16207"),
        c("#0F172A"), c("#334155"), c("#081A32"), c("#4B5563")
    )

    /** Gradient themes for the animated border. */
    val BORDER_THEMES: Array<IntArray> = arrayOf(
        intArrayOf(c("#DC2626"), c("#F97316"), c("#7F1D1D"), c("#F43F5E"), c("#DC2626")), // fire
        intArrayOf(c("#B45309"), c("#FACC15"), c("#92400E"), c("#FDE68A"), c("#B45309")), // gold
        intArrayOf(c("#1D4ED8"), c("#38BDF8"), c("#1E3A8A"), c("#818CF8"), c("#1D4ED8")), // ocean
        intArrayOf(c("#15803D"), c("#4ADE80"), c("#14532D"), c("#2DD4BF"), c("#15803D")), // emerald
        intArrayOf(c("#7C3AED"), c("#EC4899"), c("#4C1D95"), c("#C084FC"), c("#7C3AED")), // violet
        intArrayOf(c("#EF4444"), c("#F59E0B"), c("#22C55E"), c("#3B82F6"), c("#A855F7"), c("#EF4444")) // rainbow
    )

    /** Page translation languages (Google Translate codes), with native names and flags. */
    data class Lang(val code: String, val native: String, val english: String, val flag: String)

    val LANGS = listOf(
        Lang("ar", "العربية", "Arabic (original)", "🇸🇦"),
        Lang("en", "English", "English", "🇬🇧"),
        Lang("ur", "اردو", "Urdu", "🇵🇰"),
        Lang("hi", "हिन्दी", "Hindi", "🇮🇳"),
        Lang("bn", "বাংলা", "Bengali", "🇧🇩"),
        Lang("tl", "Filipino", "Filipino", "🇵🇭"),
        Lang("ne", "नेपाली", "Nepali", "🇳🇵"),
        Lang("ml", "മലയാളം", "Malayalam", "🇮🇳"),
        Lang("ta", "தமிழ்", "Tamil", "🇮🇳"),
        Lang("id", "Bahasa Indonesia", "Indonesian", "🇮🇩"),
        Lang("am", "አማርኛ", "Amharic", "🇪🇹"),
        Lang("fr", "Français", "French", "🇫🇷")
    )

    private fun c(hex: String) = Color.parseColor(hex)

    fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** "ar" or "en": the language the app interface is currently shown in. */
    fun uiLang(ctx: Context): String =
        if (ctx.resources.configuration.locales[0].language == "ar") "ar" else "en"

    /** Customised text for the current interface language, or the built-in default. */
    fun str(ctx: Context, p: SharedPreferences, key: String, defRes: Int): String {
        val lang = uiLang(ctx)
        val custom = p.getString("${key}_$lang", null)
            ?: if (lang == "ar") p.getString(key, null) else null   // texts saved by v1.4
        return custom?.takeIf { it.isNotBlank() } ?: ctx.getString(defRes)
    }

    fun putStr(ctx: Context, p: SharedPreferences, key: String, value: String) {
        val lang = uiLang(ctx)
        p.edit().putString("${key}_$lang", value).apply {
            if (lang == "ar") remove(key)
        }.apply()
    }
}
