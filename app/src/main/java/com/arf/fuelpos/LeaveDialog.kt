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
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.google.android.material.button.MaterialButton

/** The "Leave this page?" dialog. Every text, color and animation comes from settings. */
object LeaveDialog {

    fun show(a: Activity, toHome: Boolean, onStay: () -> Unit, onLeave: () -> Unit) {
        val p = Cfg.prefs(a)
        val d = a.resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()

        val stayColor = p.getInt(Cfg.L_STAY_COLOR, ContextCompat.getColor(a, R.color.royal_blue))
        val leaveColor = p.getInt(Cfg.L_LEAVE_COLOR, ContextCompat.getColor(a, R.color.danger_dark))
        val radius = p.getInt(Cfg.L_RADIUS, Cfg.DEF_RADIUS)
        val theme = p.getInt(Cfg.L_BORDER_THEME, 0).coerceIn(0, Cfg.BORDER_THEMES.size - 1)

        val view = a.layoutInflater.inflate(R.layout.dialog_leave, null)
        view.background = GradientDrawable().apply {
            setColor(Color.WHITE); cornerRadius = dp(radius + 8).toFloat()
        }

        view.findViewById<TextView>(R.id.leaveTitle).text =
            Cfg.str(a, p, Cfg.L_TITLE, R.string.leave_title)
        view.findViewById<TextView>(R.id.leaveMessage).text =
            if (toHome) Cfg.str(a, p, Cfg.L_MSG_HOME, R.string.leave_message_home)
            else Cfg.str(a, p, Cfg.L_MSG_OTHER, R.string.leave_message)

        // Top icon in the "leave" color
        view.findViewById<View>(R.id.leaveIconBg).backgroundTintList =
            ColorStateList.valueOf(ColorUtils.setAlphaComponent(leaveColor, 0x22))
        view.findViewById<ImageView>(R.id.leaveIcon).imageTintList = ColorStateList.valueOf(leaveColor)
        val ring = view.findViewById<View>(R.id.pulseRing)
        ring.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(ColorUtils.setAlphaComponent(leaveColor, 0x2E))
            setStroke(dp(2), ColorUtils.setAlphaComponent(leaveColor, 0x66))
        }

        // Primary: stay
        val stay = view.findViewById<MaterialButton>(R.id.stayButton)
        stay.text = Cfg.str(a, p, Cfg.L_STAY, R.string.stay_here)
        stay.backgroundTintList = ColorStateList.valueOf(stayColor)
        stay.cornerRadius = dp(radius)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            stay.outlineSpotShadowColor = stayColor
            stay.outlineAmbientShadowColor = stayColor
        }

        // Secondary: leave, animated gradient border
        val border = AnimatedBorderDrawable(
            dp(2) + 0.5f * d, dp(radius).toFloat(),
            Cfg.BORDER_THEMES[theme],
            ColorUtils.setAlphaComponent(leaveColor, 0x0D),
            p.getInt(Cfg.L_BORDER_SPEED, Cfg.DEF_SPEED).toLong()
        )
        val leaveBtn = view.findViewById<FrameLayout>(R.id.leaveButton)
        leaveBtn.background = border
        view.findViewById<TextView>(R.id.leaveButtonText).apply {
            text = if (toHome) Cfg.str(a, p, Cfg.L_LEAVE_HOME, R.string.go_home_anyway)
            else Cfg.str(a, p, Cfg.L_LEAVE_OTHER, R.string.leave_page)
            setTextColor(leaveColor)
            val icon = ContextCompat.getDrawable(a, if (toHome) R.drawable.ic_home else R.drawable.ic_leave)!!.mutate()
            icon.setTint(leaveColor)
            icon.setBounds(0, 0, dp(22), dp(22))
            setCompoundDrawablesRelative(icon, null, null, null)
        }

        val pulse = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1800
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

        val dialog = AlertDialog.Builder(a).setView(view).setCancelable(true).create()
        var handled = false
        stay.setOnClickListener { handled = true; dialog.dismiss(); onStay() }
        leaveBtn.setOnClickListener { handled = true; dialog.dismiss(); onLeave() }
        dialog.setOnShowListener {
            if (p.getBoolean(Cfg.L_BORDER_ANIM, true)) border.start()
            if (p.getBoolean(Cfg.L_PULSE, true)) pulse.start()
            if (p.getBoolean(Cfg.L_BREATHE, true)) breathe.start()
            entrance(view, p.getInt(Cfg.L_ENTRANCE, 3), d)
        }
        dialog.setOnDismissListener {
            border.stop(); pulse.cancel(); breathe.cancel()
            if (!handled) { handled = true; onStay() }
        }
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.show()
        dialog.window?.setLayout(
            (a.resources.displayMetrics.widthPixels * 0.9).toInt().coerceAtMost(dp(440)),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    /** 0 none, 1 fade, 2 slide up, 3 zoom. */
    private fun entrance(v: View, type: Int, density: Float) {
        when (type) {
            1 -> { v.alpha = 0f; v.animate().alpha(1f).setDuration(260).start() }
            2 -> {
                v.alpha = 0f; v.translationY = 90 * density
                v.animate().alpha(1f).translationY(0f).setDuration(320)
                    .setInterpolator(DecelerateInterpolator(2f)).start()
            }
            3 -> {
                v.alpha = 0f; v.scaleX = 0.82f; v.scaleY = 0.82f
                v.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(340)
                    .setInterpolator(OvershootInterpolator(1.4f)).start()
            }
        }
    }
}
