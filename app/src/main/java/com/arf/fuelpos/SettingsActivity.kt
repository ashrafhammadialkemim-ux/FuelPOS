package com.arf.fuelpos

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.URLUtil
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.slider.Slider
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

/** Admin control panel: system link, display, floating buttons, alert design, security. */
class SettingsActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_CURRENT_URL = "current_url"
        const val EXTRA_RELOAD = "reload"
        private val TEXT = Color.parseColor("#FFFFFF")
        private val MUTED = Color.parseColor("#94A3C4")
        private val CARD = Color.parseColor("#141F3A")
        private val STROKE = Color.parseColor("#24345C")
        private val ACCENT = Color.parseColor("#60A5FA")
    }

    private val p by lazy { Cfg.prefs(this) }
    private var reload = false
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(40))
        }
        setContentView(ScrollView(this).apply {
            setBackgroundResource(R.drawable.bg_admin)
            isFillViewport = true
            addView(root)
        })
        build(root)
    }

    private fun build(root: LinearLayout) {
        // ---------- Header ----------
        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(6), 0, dp(10))
            addView(ImageButton(context).apply {
                setImageResource(R.drawable.ic_back)
                background = circle(Color.parseColor("#1A2A52"))
                imageTintList = ColorStateList.valueOf(TEXT)
                setPadding(dp(10), dp(10), dp(10), dp(10))
                setOnClickListener { finishWithResult() }
            }, LinearLayout.LayoutParams(dp(46), dp(46)))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), 0, dp(14), 0)
                addView(text(getString(R.string.settings_menu_title), 24f, TEXT, bold = true))
                addView(text(getString(R.string.settings_subtitle), 13.5f, MUTED))
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        })

        // ---------- General ----------
        section(root, getString(R.string.sec_general), R.drawable.ic_settings).apply {
            val url = field(this, getString(R.string.menu_url), p.getString(Cfg.URL, MainActivity.DEFAULT_URL) ?: "", ltr = true)
            row(this,
                button(getString(R.string.save), R.drawable.ic_shield, primary = true) {
                    var u = url.text.toString().trim()
                    if (u.isNotEmpty() && !u.startsWith("http://") && !u.startsWith("https://")) u = "http://$u"
                    if (u.isEmpty() || !URLUtil.isValidUrl(u)) { toast(R.string.invalid_url); return@button }
                    p.edit().putString(Cfg.URL, u).remove(Cfg.HOME).apply()
                    reload = true; toast(R.string.saved)
                },
                button(getString(R.string.reset_default), null, primary = false) {
                    p.edit().putString(Cfg.URL, MainActivity.DEFAULT_URL).remove(Cfg.HOME).apply()
                    url.setText(MainActivity.DEFAULT_URL); reload = true; toast(R.string.reset_done)
                })

            val zoomLabels = listOf(
                getString(R.string.zoom_small), getString(R.string.zoom_normal),
                getString(R.string.zoom_large), getString(R.string.zoom_xlarge), getString(R.string.zoom_xxlarge)
            )
            chips(this, getString(R.string.menu_zoom), zoomLabels,
                Cfg.ZOOM_LEVELS.indexOf(p.getInt(Cfg.ZOOM, Cfg.DEF_ZOOM)).coerceAtLeast(0)) {
                p.edit().putInt(Cfg.ZOOM, Cfg.ZOOM_LEVELS[it]).apply()
            }

            label(this, getString(R.string.home_page_label))
            val current = intent.getStringExtra(EXTRA_CURRENT_URL)
            row(this,
                button(getString(R.string.menu_set_home), R.drawable.ic_home, primary = true) {
                    if (current.isNullOrBlank()) toast(R.string.no_current_page)
                    else { p.edit().putString(Cfg.HOME, current).apply(); toast(R.string.home_set) }
                },
                button(getString(R.string.default_label), null, primary = false) {
                    p.edit().remove(Cfg.HOME).apply(); toast(R.string.home_reset)
                })
        }

        // ---------- Floating buttons ----------
        section(root, getString(R.string.sec_fab), R.drawable.ic_fuel).apply {
            val icons = listOf(
                getString(R.string.icon_fuel), getString(R.string.icon_home),
                getString(R.string.icon_drop), getString(R.string.icon_receipt)
            )
            chips(this, getString(R.string.home_button_icon), icons, p.getInt(Cfg.FAB_ICON, 0)) {
                p.edit().putInt(Cfg.FAB_ICON, it).apply()
            }
            swatches(this, getString(R.string.fab_color), p.getInt(Cfg.FAB_COLOR, Color.parseColor("#081A32"))) {
                p.edit().putInt(Cfg.FAB_COLOR, it).apply()
            }
            row(this, button(getString(R.string.menu_reset_buttons), null, primary = false) {
                p.edit().remove(Cfg.FAB_X).remove(Cfg.FAB_Y).apply(); toast(R.string.saved)
            })
        }

        // ---------- Leave-page alert ----------
        section(root, getString(R.string.sec_alert), R.drawable.ic_leave).apply {
            label(this, getString(R.string.alert_texts))
            textPref(this, getString(R.string.f_title), Cfg.L_TITLE, R.string.leave_title)
            textPref(this, getString(R.string.f_msg_home), Cfg.L_MSG_HOME, R.string.leave_message_home)
            textPref(this, getString(R.string.f_msg_other), Cfg.L_MSG_OTHER, R.string.leave_message)
            textPref(this, getString(R.string.f_stay), Cfg.L_STAY, R.string.stay_here)
            textPref(this, getString(R.string.f_leave_home), Cfg.L_LEAVE_HOME, R.string.go_home_anyway)
            textPref(this, getString(R.string.f_leave_other), Cfg.L_LEAVE_OTHER, R.string.leave_page)

            swatches(this, getString(R.string.f_stay_color),
                p.getInt(Cfg.L_STAY_COLOR, ContextCompat.getColor(context, R.color.royal_blue))) {
                p.edit().putInt(Cfg.L_STAY_COLOR, it).apply()
            }
            swatches(this, getString(R.string.f_leave_color),
                p.getInt(Cfg.L_LEAVE_COLOR, ContextCompat.getColor(context, R.color.danger_dark))) {
                p.edit().putInt(Cfg.L_LEAVE_COLOR, it).apply()
            }
            chips(this, getString(R.string.f_border_theme), listOf(
                getString(R.string.th_fire), getString(R.string.th_gold), getString(R.string.th_ocean),
                getString(R.string.th_emerald), getString(R.string.th_violet), getString(R.string.th_rainbow)
            ), p.getInt(Cfg.L_BORDER_THEME, 0)) { p.edit().putInt(Cfg.L_BORDER_THEME, it).apply() }

            label(this, getString(R.string.alert_motion))
            switchRow(this, getString(R.string.f_border_anim), p.getBoolean(Cfg.L_BORDER_ANIM, true)) {
                p.edit().putBoolean(Cfg.L_BORDER_ANIM, it).apply()
            }
            slider(this, getString(R.string.f_border_speed), 1f, 8f, 0.5f,
                p.getInt(Cfg.L_BORDER_SPEED, Cfg.DEF_SPEED) / 1000f, getString(R.string.unit_seconds)) {
                p.edit().putInt(Cfg.L_BORDER_SPEED, (it * 1000).toInt()).apply()
            }
            switchRow(this, getString(R.string.f_pulse), p.getBoolean(Cfg.L_PULSE, true)) {
                p.edit().putBoolean(Cfg.L_PULSE, it).apply()
            }
            switchRow(this, getString(R.string.f_breathe), p.getBoolean(Cfg.L_BREATHE, true)) {
                p.edit().putBoolean(Cfg.L_BREATHE, it).apply()
            }
            chips(this, getString(R.string.f_entrance), listOf(
                getString(R.string.en_none), getString(R.string.en_fade),
                getString(R.string.en_slide), getString(R.string.en_zoom)
            ), p.getInt(Cfg.L_ENTRANCE, 3)) { p.edit().putInt(Cfg.L_ENTRANCE, it).apply() }
            slider(this, getString(R.string.f_radius), 6f, 30f, 1f,
                p.getInt(Cfg.L_RADIUS, Cfg.DEF_RADIUS).toFloat(), "dp") {
                p.edit().putInt(Cfg.L_RADIUS, it.toInt()).apply()
            }

            row(this,
                button(getString(R.string.preview), R.drawable.ic_info, primary = true) {
                    LeaveDialog.show(this@SettingsActivity, toHome = true, onStay = {}, onLeave = {})
                },
                button(getString(R.string.restore_defaults), null, primary = false) {
                    val e = p.edit()
                    listOf(Cfg.L_TITLE, Cfg.L_MSG_HOME, Cfg.L_MSG_OTHER, Cfg.L_STAY, Cfg.L_LEAVE_HOME,
                        Cfg.L_LEAVE_OTHER, Cfg.L_STAY_COLOR, Cfg.L_LEAVE_COLOR, Cfg.L_BORDER_THEME,
                        Cfg.L_BORDER_ANIM, Cfg.L_BORDER_SPEED, Cfg.L_PULSE, Cfg.L_BREATHE,
                        Cfg.L_ENTRANCE, Cfg.L_RADIUS).forEach { e.remove(it) }
                    e.apply(); toast(R.string.reset_done); recreate()
                })
        }

        // ---------- Security ----------
        section(root, getString(R.string.sec_security), R.drawable.ic_lock).apply {
            val user = field(this, getString(R.string.username), p.getString(Cfg.ADMIN_USER, Cfg.DEF_USER) ?: "", ltr = true)
            val pass = field(this, getString(R.string.new_password), "", ltr = true, password = true)
            row(this, button(getString(R.string.save), R.drawable.ic_shield, primary = true) {
                val u = user.text.toString().trim()
                val pw = pass.text.toString()
                if (u.isEmpty()) { toast(R.string.username_required); return@button }
                val e = p.edit().putString(Cfg.ADMIN_USER, u)
                if (pw.isNotEmpty()) {
                    if (pw.length < 4) { toast(R.string.pin_too_short); return@button }
                    e.putString(Cfg.ADMIN_PASS, pw)
                }
                e.apply(); pass.setText(""); toast(R.string.saved)
            })
        }

        root.addView(text(getString(R.string.app_name) + " • v" + BuildConfig.VERSION_NAME, 12.5f, MUTED).apply {
            gravity = Gravity.CENTER; setPadding(0, dp(18), 0, 0)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    // ===================== UI helpers =====================

    private fun text(s: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = s; textSize = size; setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun circle(color: Int) = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }

    private fun section(root: LinearLayout, title: String, icon: Int): LinearLayout {
        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(20))
        }
        inner.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(ImageView(context).apply {
                setImageResource(icon)
                imageTintList = ColorStateList.valueOf(ACCENT)
                background = circle(Color.parseColor("#1A2A52"))
                setPadding(dp(8), dp(8), dp(8), dp(8))
            }, LinearLayout.LayoutParams(dp(38), dp(38)))
            addView(text(title, 18f, TEXT, bold = true).apply { setPadding(dp(12), 0, dp(12), 0) })
        })
        val card = MaterialCardView(this).apply {
            setCardBackgroundColor(CARD)
            radius = dp(22).toFloat()
            strokeColor = STROKE
            strokeWidth = dp(1)
            cardElevation = 0f
            addView(inner)
        }
        root.addView(card, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(14)
        })
        return inner
    }

    private fun label(c: LinearLayout, s: String) {
        c.addView(text(s, 14.5f, MUTED, bold = true).apply { setPadding(0, dp(18), 0, dp(2)) })
    }

    private fun field(c: LinearLayout, hint: String, value: String, ltr: Boolean = false, password: Boolean = false): TextInputEditText {
        val layout = layoutInflater.inflate(R.layout.item_field, c, false) as TextInputLayout
        layout.hint = hint
        val input = layout.findViewById<TextInputEditText>(R.id.fieldInput)
        input.setText(value)
        if (ltr) input.textDirection = View.TEXT_DIRECTION_LTR
        if (password) {
            input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            layout.endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE
            layout.setEndIconTintList(ColorStateList.valueOf(MUTED))
        } else if (ltr) {
            input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        }
        c.addView(layout)
        return input
    }

    private fun textPref(c: LinearLayout, hint: String, key: String, defRes: Int) {
        val input = field(c, hint, Cfg.str(this, p, key, defRes))
        input.doAfterTextChanged { p.edit().putString(key, it?.toString()?.trim()).apply() }
    }

    private fun button(s: String, icon: Int?, primary: Boolean, onClick: () -> Unit): MaterialButton =
        MaterialButton(this).apply {
            text = s; textSize = 15f; isAllCaps = false
            cornerRadius = dp(14); insetTop = 0; insetBottom = 0
            minHeight = dp(52)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            icon?.let {
                this.icon = ContextCompat.getDrawable(context, it)
                iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
                iconPadding = dp(8); iconSize = dp(20)
            }
            if (primary) {
                backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.royal_blue))
                setTextColor(TEXT); iconTint = ColorStateList.valueOf(TEXT)
            } else {
                backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
                strokeColor = ColorStateList.valueOf(Color.parseColor("#3A4F80"))
                strokeWidth = dp(2)
                setTextColor(Color.parseColor("#BFD3FF")); iconTint = ColorStateList.valueOf(ACCENT)
            }
            setOnClickListener { onClick() }
        }

    private fun row(c: LinearLayout, vararg buttons: MaterialButton) {
        c.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(12), 0, 0)
            buttons.forEachIndexed { i, b ->
                addView(b, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    if (i > 0) marginStart = dp(10)
                })
            }
        })
    }

    private fun chips(c: LinearLayout, title: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
        label(c, title)
        val group = ChipGroup(this).apply { isSingleSelection = true; isSelectionRequired = true }
        val bg = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(ContextCompat.getColor(this, R.color.royal_blue), Color.parseColor("#1A2744"))
        )
        val fg = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(TEXT, Color.parseColor("#BFD3FF"))
        )
        options.forEachIndexed { i, s ->
            group.addView(Chip(this).apply {
                id = View.generateViewId()
                text = s; textSize = 15f
                isCheckable = true; isCheckedIconVisible = false
                chipBackgroundColor = bg; setTextColor(fg)
                chipStrokeColor = ColorStateList.valueOf(Color.parseColor("#33507F")); chipStrokeWidth = dp(1).toFloat()
                chipMinHeight = dp(44).toFloat()
                isChecked = i == selected
                setOnClickListener { onSelect(i) }
            })
        }
        c.addView(group)
    }

    private fun swatches(c: LinearLayout, title: String, current: Int, onPick: (Int) -> Unit) {
        label(c, title)
        val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(8), 0, dp(4)) }
        val views = mutableListOf<View>()
        fun paint(v: View, color: Int, sel: Boolean) {
            v.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL; setColor(color)
                setStroke(dp(if (sel) 4 else 1), if (sel) Color.WHITE else Color.parseColor("#3A4F80"))
            }
        }
        Cfg.SWATCHES.forEach { color ->
            val v = View(this)
            paint(v, color, color == current)
            v.setOnClickListener {
                Cfg.SWATCHES.forEachIndexed { i, col -> paint(views[i], col, col == color) }
                onPick(color)
            }
            views.add(v)
            line.addView(v, LinearLayout.LayoutParams(dp(42), dp(42)).apply { marginEnd = dp(10) })
        }
        c.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(line) })
    }

    private fun switchRow(c: LinearLayout, title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
        c.addView(SwitchMaterial(this).apply {
            text = title; textSize = 15.5f; setTextColor(TEXT)
            isChecked = checked
            minHeight = dp(52)
            thumbTintList = ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(ACCENT, Color.parseColor("#8A9BC2"))
            )
            trackTintList = ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(Color.parseColor("#2C4F9E"), Color.parseColor("#2A3657"))
            )
            setOnCheckedChangeListener { _, b -> onChange(b) }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun slider(c: LinearLayout, title: String, from: Float, to: Float, step: Float, value: Float,
                       unit: String, onChange: (Float) -> Unit) {
        val head = text("", 14.5f, MUTED, bold = true).apply { setPadding(0, dp(16), 0, 0) }
        fun fmt(v: Float) = "$title: " + (if (step < 1f) String.format("%.1f", v) else v.toInt().toString()) + " " + unit
        val start = (Math.round((value.coerceIn(from, to) - from) / step) * step + from)
        head.text = fmt(start)
        c.addView(head)
        c.addView(Slider(this).apply {
            valueFrom = from; valueTo = to; stepSize = step; this.value = start
            thumbTintList = ColorStateList.valueOf(ACCENT)
            trackActiveTintList = ColorStateList.valueOf(ACCENT)
            trackInactiveTintList = ColorStateList.valueOf(Color.parseColor("#2A3657"))
            addOnChangeListener { _, v, _ -> head.text = fmt(v); onChange(v) }
        })
    }

    private fun toast(res: Int) = Toast.makeText(this, getString(res), Toast.LENGTH_SHORT).show()

    private fun finishWithResult() {
        setResult(RESULT_OK, Intent().putExtra(EXTRA_RELOAD, reload))
        finish()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        finishWithResult()
    }
}
