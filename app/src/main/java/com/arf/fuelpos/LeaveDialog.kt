package com.arf.fuelpos

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.BounceInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.google.android.material.button.MaterialButton

/** The "Leave this page?" dialog. Every text, color, size and animation comes from settings. */
object LeaveDialog {

    private val ICONS = intArrayOf(R.drawable.ic_leave, R.drawable.ic_warning, R.drawable.ic_info, R.drawable.ic_home)

    fun show(a: Activity, toHome: Boolean, onStay: () -> Unit, onLeave: () -> Unit) {
        val p = Cfg.prefs(a)
        val d = a.resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()

        val stayColor = p.getInt(Cfg.L_STAY_COLOR, ContextCompat.getColor(a, R.color.royal_blue))
        val leaveColor = p.getInt(Cfg.L_LEAVE_COLOR, ContextCompat.getColor(a, R.color.danger_dark))
        val radius = p.getInt(Cfg.L_RADIUS, Cfg.DEF_RADIUS)
        val theme = p.getInt(Cfg.L_BORDER_THEME, 0).coerceIn(0, Cfg.BORDER_THEMES.size - 1)
        val btnH = p.getInt(Cfg.L_BTN_H, 58)
        val showBtnIcons = p.getBoolean(Cfg.L_BTN_ICONS, true)
        val leaveStyle = p.getInt(Cfg.L_LEAVE_STYLE, 0)

        val view = a.layoutInflater.inflate(R.layout.dialog_leave, null)
        view.background = GradientDrawable().apply {
            setColor(Color.WHITE); cornerRadius = dp(radius + 8).toFloat()
        }

        // ---- Texts ----
        view.findViewById<TextView>(R.id.leaveTitle).apply {
            text = Cfg.str(a, p, Cfg.L_TITLE, R.string.leave_title)
            textSize = p.getInt(Cfg.L_TITLE_SIZE, 22).toFloat()
        }
        view.findViewById<TextView>(R.id.leaveMessage).apply {
            text = if (toHome) Cfg.str(a, p, Cfg.L_MSG_HOME, R.string.leave_message_home)
            else Cfg.str(a, p, Cfg.L_MSG_OTHER, R.string.leave_message)
            textSize = p.getInt(Cfg.L_MSG_SIZE, 16).toFloat()
        }

        // ---- Top icon ----
        val iconWrap = view.findViewById<View>(R.id.leaveIconWrap)
        if (!p.getBoolean(Cfg.L_SHOW_ICON, true)) iconWrap.visibility = View.GONE
        view.findViewById<View>(R.id.leaveIconBg).backgroundTintList =
            ColorStateList.valueOf(ColorUtils.setAlphaComponent(leaveColor, 0x22))
        view.findViewById<ImageView>(R.id.leaveIcon).apply {
            setImageResource(ICONS[p.getInt(Cfg.L_ICON, 0).coerceIn(0, ICONS.size - 1)])
            imageTintList = ColorStateList.valueOf(leaveColor)
        }
        val ring = view.findViewById<View>(R.id.pulseRing)
        ring.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(ColorUtils.setAlphaComponent(leaveColor, 0x2E))
            setStroke(dp(2), ColorUtils.setAlphaComponent(leaveColor, 0x66))
        }

        // ---- Primary: stay ----
        val stay = view.findViewById<MaterialButton>(R.id.stayButton)
        stay.text = Cfg.str(a, p, Cfg.L_STAY, R.string.stay_here)
        stay.backgroundTintList = ColorStateList.valueOf(stayColor)
        stay.cornerRadius = dp(radius)
        stay.minHeight = dp(btnH)
        if (!showBtnIcons) stay.icon = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            stay.outlineSpotShadowColor = stayColor
            stay.outlineAmbientShadowColor = stayColor
        }

        // ---- Secondary: leave ----
        val border = AnimatedBorderDrawable(
            dp(p.getInt(Cfg.L_BORDER_W, 2)) + 0.5f * d, dp(radius).toFloat(),
            Cfg.BORDER_THEMES[theme],
            ColorUtils.setAlphaComponent(leaveColor, 0x0D),
            p.getInt(Cfg.L_BORDER_SPEED, Cfg.DEF_SPEED).toLong()
        )
        val leaveBtn = view.findViewById<FrameLayout>(R.id.leaveButton)
        leaveBtn.minimumHeight = dp(btnH)
        val leaveTextColor: Int
        when (leaveStyle) {
            1 -> { // plain outlined
                leaveBtn.background = GradientDrawable().apply {
                    cornerRadius = dp(radius).toFloat()
                    setColor(Color.WHITE)
                    setStroke(dp(p.getInt(Cfg.L_BORDER_W, 2)), leaveColor)
                }
                leaveTextColor = leaveColor
            }
            2 -> { // filled
                leaveBtn.background = GradientDrawable().apply {
                    cornerRadius = dp(radius).toFloat(); setColor(leaveColor)
                }
                leaveTextColor = Color.WHITE
            }
            else -> { leaveBtn.background = border; leaveTextColor = leaveColor }
        }
        view.findViewById<TextView>(R.id.leaveButtonText).apply {
            text = if (toHome) Cfg.str(a, p, Cfg.L_LEAVE_HOME, R.string.go_home_anyway)
            else Cfg.str(a, p, Cfg.L_LEAVE_OTHER, R.string.leave_page)
            setTextColor(leaveTextColor)
            if (showBtnIcons) {
                val icon = ContextCompat.getDrawable(a, if (toHome) R.drawable.ic_home else R.drawable.ic_leave)!!.mutate()
                icon.setTint(leaveTextColor)
                icon.setBounds(0, 0, dp(22), dp(22))
                setCompoundDrawablesRelative(icon, null, null, null)
            }
        }

        // ---- Button order ----
        if (p.getInt(Cfg.L_ORDER, 0) == 1) {
            val box = view as LinearLayout
            val stayIndex = box.indexOfChild(stay)
            box.removeView(leaveBtn)
            box.addView(leaveBtn, stayIndex)
            (leaveBtn.layoutParams as LinearLayout.LayoutParams).topMargin = dp(26)
            (stay.layoutParams as LinearLayout.LayoutParams).topMargin = dp(12)
        }

        // ---- Animations ----
        val pulse = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = p.getInt(Cfg.L_PULSE_MS, 1800).toLong()
            repeatCount = ValueAnimator.INFINITE
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                val t = it.animatedValue as Float
                ring.scaleX = 1f + 0.42f * t; ring.scaleY = 1f + 0.42f * t; ring.alpha = 1f - t
            }
        }
        val breathe = ObjectAnimator.ofPropertyValuesHolder(
            view.findViewById<View>(R.id.leaveIconBg),
            PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.06f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.06f)
        ).apply {
            duration = 900
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
        }
        if (!p.getBoolean(Cfg.L_PULSE, true)) ring.visibility = View.INVISIBLE

        val cancelable = p.getBoolean(Cfg.L_CANCELABLE, true)
        val dialog = AlertDialog.Builder(a).setView(view).setCancelable(cancelable).create()
        dialog.setCanceledOnTouchOutside(cancelable)
        var handled = false
        stay.setOnClickListener { handled = true; dialog.dismiss(); onStay() }
        leaveBtn.setOnClickListener { handled = true; dialog.dismiss(); onLeave() }
        dialog.setOnShowListener {
            if (leaveStyle == 0 && p.getBoolean(Cfg.L_BORDER_ANIM, true)) border.start()
            if (p.getBoolean(Cfg.L_PULSE, true)) pulse.start()
            if (p.getBoolean(Cfg.L_BREATHE, true)) breathe.start()
            if (p.getBoolean(Cfg.L_HAPTIC, true)) view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            entrance(view, p.getInt(Cfg.L_ENTRANCE, 3), p.getInt(Cfg.L_ENTER_MS, 340).toLong(), d)
        }
        dialog.setOnDismissListener {
            border.stop(); pulse.cancel(); breathe.cancel()
            if (!handled) { handled = true; onStay() }
        }
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.show()
        dialog.window?.setDimAmount(p.getInt(Cfg.L_DIM, 60) / 100f)
        dialog.window?.setLayout(
            (a.resources.displayMetrics.widthPixels * p.getInt(Cfg.L_WIDTH, 90) / 100f).toInt()
                .coerceAtMost(dp(520)),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    /** 0 none, 1 fade, 2 slide up, 3 zoom, 4 slide down, 5 bounce. */
    private fun entrance(v: View, type: Int, ms: Long, density: Float) {
        when (type) {
            1 -> { v.alpha = 0f; v.animate().alpha(1f).setDuration(ms).start() }
            2 -> {
                v.alpha = 0f; v.translationY = 90 * density
                v.animate().alpha(1f).translationY(0f).setDuration(ms)
                    .setInterpolator(DecelerateInterpolator(2f)).start()
            }
            3 -> {
                v.alpha = 0f; v.scaleX = 0.82f; v.scaleY = 0.82f
                v.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(ms)
                    .setInterpolator(OvershootInterpolator(1.4f)).start()
            }
            4 -> {
                v.alpha = 0f; v.translationY = -90 * density
                v.animate().alpha(1f).translationY(0f).setDuration(ms)
                    .setInterpolator(DecelerateInterpolator(2f)).start()
            }
            5 -> {
                v.alpha = 0f; v.translationY = -160 * density
                v.animate().alpha(1f).translationY(0f).setDuration((ms * 2).coerceAtMost(1400))
                    .setInterpolator(BounceInterpolator()).start()
            }
        }
    }
}
