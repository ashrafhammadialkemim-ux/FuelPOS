package com.arf.fuelpos

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color

/** All app settings in one place, shared by every screen. */
object Cfg {
    const val PREFS = "fuel_pos_prefs"

    const val URL = "server_url"
    const val HOME = "home_url"
    const val ZOOM = "text_zoom"
    const val FAB_X = "fab_x"
    const val FAB_Y = "fab_y"
    const val FAB_ICON = "fab_icon"
    const val FAB_COLOR = "fab_color"

    const val ADMIN_USER = "admin_user"
    const val ADMIN_PASS = "admin_pass"

    // Leave-page dialog
    const val L_TITLE = "l_title"
    const val L_MSG_HOME = "l_msg_home"
    const val L_MSG_OTHER = "l_msg_other"
    const val L_STAY = "l_stay"
    const val L_LEAVE_HOME = "l_leave_home"
    const val L_LEAVE_OTHER = "l_leave_other"
    const val L_STAY_COLOR = "l_stay_color"
    const val L_LEAVE_COLOR = "l_leave_color"
    const val L_BORDER_THEME = "l_border_theme"
    const val L_BORDER_ANIM = "l_border_anim"
    const val L_BORDER_SPEED = "l_border_speed"
    const val L_PULSE = "l_pulse"
    const val L_BREATHE = "l_breathe"
    const val L_ENTRANCE = "l_entrance"
    const val L_RADIUS = "l_radius"

    const val DEF_ZOOM = 115
    const val DEF_USER = "ASHRAF"
    const val DEF_PASS = "OLB111"
    const val DEF_RADIUS = 16
    const val DEF_SPEED = 3500

    val ZOOM_LEVELS = intArrayOf(85, 100, 115, 130, 150)

    /** Swatches offered in the color pickers. */
    val SWATCHES = intArrayOf(
        Color.parseColor("#1D4ED8"), Color.parseColor("#0B5FA5"), Color.parseColor("#0F766E"),
        Color.parseColor("#15803D"), Color.parseColor("#7C3AED"), Color.parseColor("#B91C1C"),
        Color.parseColor("#C2410C"), Color.parseColor("#A16207"), Color.parseColor("#BE185D"),
        Color.parseColor("#0F172A"), Color.parseColor("#334155"), Color.parseColor("#081A32")
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

    private fun c(hex: String) = Color.parseColor(hex)

    fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun str(ctx: Context, p: SharedPreferences, key: String, defRes: Int): String =
        p.getString(key, null)?.takeIf { it.isNotBlank() } ?: ctx.getString(defRes)
}
