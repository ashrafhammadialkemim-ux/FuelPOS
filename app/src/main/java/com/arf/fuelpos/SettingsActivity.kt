package com.arf.fuelpos

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.URLUtil
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.card.MaterialCardView
import com.google.android.material.slider.Slider
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

/**
 * Admin control panel, styled like an Oracle APEX (Universal Theme / Vita) page:
 * blue header bar, breadcrumb + page title, tabs, white regions with floating-label items.
 */
class SettingsActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_CURRENT_URL = "current_url"
        const val EXTRA_RELOAD = "reload"
        private const val STATE_TAB = "tab"
        private const val STATE_RELOAD = "reload"

        private val TEXT = Color.parseColor("#262626")
        private val LABEL = Color.parseColor("#404040")
        private val MUTED = Color.parseColor("#707070")
        private val BORDER = Color.parseColor("#E2E2E2")
        private val FIELD_BORDER = Color.parseColor("#C6C6C6")
        private val BODY = Color.parseColor("#F4F5F7")
        private val NORMAL_BTN = Color.parseColor("#F8F8F8")
        private val DANGER = Color.parseColor("#CB1100")
    }

    private val p by lazy { Cfg.prefs(this) }
    private val blue by lazy { Cfg.APEX_BLUE }
    private var reload = false
    private var tabIndex = 0
    private lateinit var content: LinearLayout
    private lateinit var scroll: ScrollView

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun dpf(v: Float) = v * resources.displayMetrics.density

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tabIndex = savedInstanceState?.getInt(STATE_TAB) ?: 0
        reload = savedInstanceState?.getBoolean(STATE_RELOAD) ?: false

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BODY)
        }
        root.addView(headerBar())
        root.addView(hero())
        root.addView(tabs())
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(4), dp(12), dp(32))
        }
        scroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(BODY)
            addView(content)
        }
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        showTab(tabIndex)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = finishWithResult()
        })
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_TAB, tabIndex)
        outState.putBoolean(STATE_RELOAD, reload)
    }

    // =========================================================================
    // Page frame: header bar, breadcrumb/title, tabs
    // =========================================================================

    private fun headerBar(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setBackgroundColor(blue)
        elevation = dpf(4f)
        setPadding(dp(6), 0, dp(14), 0)
        minimumHeight = dp(56)
        addView(ImageButton(context).apply {
            setImageResource(R.drawable.ic_back)
            imageTintList = ColorStateList.valueOf(Color.WHITE)
            background = ripple()
            setPadding(dp(12), dp(12), dp(12), dp(12))
            contentDescription = getString(R.string.cancel)
            setOnClickListener { finishWithResult() }
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        addView(ImageView(context).apply {
            setImageResource(R.mipmap.ic_launcher)
        }, LinearLayout.LayoutParams(dp(30), dp(30)).apply { marginStart = dp(4) })
        addView(TextView(context).apply {
            text = getString(R.string.app_name)
            setTextColor(Color.WHITE); textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(10), 0, dp(10), 0)
            maxLines = 1
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(ImageView(context).apply {
            setImageResource(R.drawable.ic_person)
            imageTintList = ColorStateList.valueOf(Color.WHITE)
        }, LinearLayout.LayoutParams(dp(20), dp(20)))
        addView(TextView(context).apply {
            text = p.getString(Cfg.ADMIN_USER, Cfg.DEF_USER)
            setTextColor(Color.WHITE); textSize = 14f
            setPadding(dp(6), 0, 0, 0)
        })
    }

    private fun hero(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(Color.WHITE)
        setPadding(dp(18), dp(14), dp(18), dp(12))
        addView(TextView(context).apply {
            text = getString(R.string.crumb_home) + "   ›   " + getString(R.string.crumb_settings)
            setTextColor(blue); textSize = 13f
        })
        addView(TextView(context).apply {
            text = getString(R.string.page_title)
            setTextColor(TEXT); textSize = 23f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(4), 0, 0)
        })
        addView(TextView(context).apply {
            text = getString(R.string.settings_subtitle)
            setTextColor(MUTED); textSize = 13.5f
            setPadding(0, dp(2), 0, 0)
        })
    }

    private fun tabs(): View {
        val titles = listOf(
            R.string.tab_general, R.string.tab_display, R.string.tab_buttons,
            R.string.tab_alert, R.string.tab_language, R.string.tab_security
        )
        val tl = TabLayout(this).apply {
            setBackgroundColor(Color.WHITE)
            tabMode = TabLayout.MODE_SCROLLABLE
            tabGravity = TabLayout.GRAVITY_START
            setSelectedTabIndicatorColor(blue)
            setSelectedTabIndicatorHeight(dp(3))
            setTabTextColors(LABEL, blue)
            tabRippleColor = ColorStateList.valueOf(Color.parseColor("#1A0572CE"))
            elevation = dpf(1f)
        }
        titles.forEach { tl.addTab(tl.newTab().setText(it)) }
        tl.getTabAt(tabIndex)?.select()
        tl.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) { showTab(tab.position) }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(tl)
            addView(View(context).apply { setBackgroundColor(BORDER) },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)))
        }
    }

    private fun showTab(i: Int) {
        tabIndex = i
        content.removeAllViews()
        when (i) {
            0 -> tabGeneral()
            1 -> tabDisplay()
            2 -> tabButtons()
            3 -> tabAlert()
            4 -> tabLanguage()
            5 -> tabSecurity()
        }
        footer()
        scroll.scrollTo(0, 0)
    }

    // =========================================================================
    // Tabs
    // =========================================================================

    private fun tabGeneral() {
        region(getString(R.string.r_connection), R.drawable.ic_settings).apply {
            val url = textItem(this, getString(R.string.menu_url),
                p.getString(Cfg.URL, MainActivity.DEFAULT_URL) ?: "", getString(R.string.h_url), ltr = true)
            buttons(this,
                hotButton(getString(R.string.save), R.drawable.ic_check) {
                    var u = url.text.toString().trim()
                    if (u.isNotEmpty() && !u.startsWith("http://") && !u.startsWith("https://")) u = "http://$u"
                    if (u.isEmpty() || !URLUtil.isValidUrl(u)) { toast(R.string.invalid_url); return@hotButton }
                    p.edit().putString(Cfg.URL, u).remove(Cfg.HOME).apply()
                    url.setText(u); reload = true; toast(R.string.saved)
                },
                normalButton(getString(R.string.reset_default), R.drawable.ic_restore) {
                    p.edit().putString(Cfg.URL, MainActivity.DEFAULT_URL).remove(Cfg.HOME).apply()
                    url.setText(MainActivity.DEFAULT_URL); reload = true; toast(R.string.reset_done)
                })
        }

        region(getString(R.string.r_home), R.drawable.ic_fuel).apply {
            help(this, getString(R.string.h_home))
            val current = intent.getStringExtra(EXTRA_CURRENT_URL)
            buttons(this,
                hotButton(getString(R.string.menu_set_home), R.drawable.ic_home) {
                    if (current.isNullOrBlank()) toast(R.string.no_current_page)
                    else { p.edit().putString(Cfg.HOME, current).apply(); toast(R.string.home_set) }
                },
                normalButton(getString(R.string.menu_reset_home), R.drawable.ic_restore) {
                    p.edit().remove(Cfg.HOME).apply(); toast(R.string.home_reset)
                })
        }

        region(getString(R.string.r_invoices), R.drawable.ic_receipt).apply {
            selectItem(this, getString(R.string.f_invoice_action), listOf(
                getString(R.string.act_share), getString(R.string.act_print),
                getString(R.string.act_open), getString(R.string.act_save)
            ), p.getInt(Cfg.INVOICE_ACTION, 0), getString(R.string.h_invoice_action)) {
                p.edit().putInt(Cfg.INVOICE_ACTION, it).apply()
            }
        }

        region(getString(R.string.r_behavior), R.drawable.ic_screen).apply {
            switchItem(this, getString(R.string.f_keep_screen), p.getBoolean(Cfg.KEEP_SCREEN, true),
                getString(R.string.h_keep_screen)) { p.edit().putBoolean(Cfg.KEEP_SCREEN, it).apply() }
            switchItem(this, getString(R.string.f_pull_refresh), p.getBoolean(Cfg.PULL_REFRESH, true)) {
                p.edit().putBoolean(Cfg.PULL_REFRESH, it).apply()
            }
            switchItem(this, getString(R.string.f_fullscreen), p.getBoolean(Cfg.FULLSCREEN, true),
                getString(R.string.h_fullscreen)) { p.edit().putBoolean(Cfg.FULLSCREEN, it).apply() }
            switchItem(this, getString(R.string.f_exit_confirm), p.getBoolean(Cfg.EXIT_CONFIRM, true)) {
                p.edit().putBoolean(Cfg.EXIT_CONFIRM, it).apply()
            }
        }
    }

    private fun tabDisplay() {
        region(getString(R.string.r_zoom), R.drawable.ic_text).apply {
            help(this, getString(R.string.h_zoom))
            val sample = TextView(context).apply {
                text = getString(R.string.zoom_sample)
                setTextColor(TEXT)
                background = box(Color.parseColor("#FAFAFA"), BORDER, 6)
                setPadding(dp(14), dp(14), dp(14), dp(14))
                textSize = 16f * p.getInt(Cfg.ZOOM, Cfg.DEF_ZOOM) / 100f
            }
            pillItem(this, null, listOf(
                getString(R.string.zoom_small), getString(R.string.zoom_normal), getString(R.string.zoom_large),
                getString(R.string.zoom_xlarge), getString(R.string.zoom_xxlarge)
            ), Cfg.ZOOM_LEVELS.indexOf(p.getInt(Cfg.ZOOM, Cfg.DEF_ZOOM)).coerceAtLeast(0)) {
                p.edit().putInt(Cfg.ZOOM, Cfg.ZOOM_LEVELS[it]).apply()
                sample.textSize = 16f * Cfg.ZOOM_LEVELS[it] / 100f
            }
            addView(sample, lp(top = 16))
        }
    }

    private fun tabButtons() {
        region(getString(R.string.r_home_button), R.drawable.ic_fuel).apply {
            switchItem(this, getString(R.string.f_show_home), p.getBoolean(Cfg.FAB_SHOW_HOME, true)) {
                p.edit().putBoolean(Cfg.FAB_SHOW_HOME, it).apply()
            }
            selectItem(this, getString(R.string.home_button_icon), listOf(
                getString(R.string.icon_fuel), getString(R.string.icon_home),
                getString(R.string.icon_drop), getString(R.string.icon_receipt)
            ), p.getInt(Cfg.FAB_ICON, 0)) { p.edit().putInt(Cfg.FAB_ICON, it).apply() }
        }
        region(getString(R.string.r_bar), R.drawable.ic_palette).apply {
            colorItem(this, getString(R.string.fab_color), p.getInt(Cfg.FAB_COLOR, Color.parseColor("#081A32"))) {
                p.edit().putInt(Cfg.FAB_COLOR, it).apply()
            }
            pillItem(this, getString(R.string.f_fab_size), listOf(
                getString(R.string.size_small), getString(R.string.size_normal), getString(R.string.size_large)
            ), p.getInt(Cfg.FAB_SIZE, 1)) { p.edit().putInt(Cfg.FAB_SIZE, it).apply() }
            sliderItem(this, getString(R.string.f_fab_alpha), 30f, 100f, 5f,
                p.getInt(Cfg.FAB_ALPHA, 100).toFloat(), "%") { p.edit().putInt(Cfg.FAB_ALPHA, it.toInt()).apply() }
            switchItem(this, getString(R.string.f_show_lang), p.getBoolean(Cfg.FAB_SHOW_LANG, true)) {
                p.edit().putBoolean(Cfg.FAB_SHOW_LANG, it).apply()
            }
            help(this, getString(R.string.h_drag))
            buttons(this, normalButton(getString(R.string.menu_reset_buttons), R.drawable.ic_restore) {
                p.edit().remove(Cfg.FAB_X).remove(Cfg.FAB_Y).apply(); toast(R.string.saved)
            })
        }
    }

    private fun tabAlert() {
        region(getString(R.string.r_alert_preview), R.drawable.ic_info).apply {
            help(this, getString(R.string.h_alert_preview))
            buttons(this,
                hotButton(getString(R.string.preview_home), R.drawable.ic_home) {
                    LeaveDialog.show(this@SettingsActivity, toHome = true, onStay = {}, onLeave = {})
                },
                normalButton(getString(R.string.preview_other), R.drawable.ic_leave) {
                    LeaveDialog.show(this@SettingsActivity, toHome = false, onStay = {}, onLeave = {})
                })
        }

        region(getString(R.string.r_alert_texts), R.drawable.ic_text).apply {
            val langName = if (Cfg.uiLang(context) == "ar") "العربية" else "English"
            help(this, getString(R.string.h_alert_texts_lang, langName))
            textPref(this, getString(R.string.f_title), Cfg.L_TITLE, R.string.leave_title)
            textPref(this, getString(R.string.f_msg_home), Cfg.L_MSG_HOME, R.string.leave_message_home, multi = true)
            textPref(this, getString(R.string.f_msg_other), Cfg.L_MSG_OTHER, R.string.leave_message, multi = true)
            textPref(this, getString(R.string.f_stay), Cfg.L_STAY, R.string.stay_here)
            textPref(this, getString(R.string.f_leave_home), Cfg.L_LEAVE_HOME, R.string.go_home_anyway)
            textPref(this, getString(R.string.f_leave_other), Cfg.L_LEAVE_OTHER, R.string.leave_page)
        }

        region(getString(R.string.r_alert_colors), R.drawable.ic_palette).apply {
            colorItem(this, getString(R.string.f_stay_color),
                p.getInt(Cfg.L_STAY_COLOR, ContextCompat.getColor(context, R.color.royal_blue))) {
                p.edit().putInt(Cfg.L_STAY_COLOR, it).apply()
            }
            colorItem(this, getString(R.string.f_leave_color),
                p.getInt(Cfg.L_LEAVE_COLOR, ContextCompat.getColor(context, R.color.danger_dark))) {
                p.edit().putInt(Cfg.L_LEAVE_COLOR, it).apply()
            }
            selectItem(this, getString(R.string.f_border_theme), listOf(
                getString(R.string.th_fire), getString(R.string.th_gold), getString(R.string.th_ocean),
                getString(R.string.th_emerald), getString(R.string.th_violet), getString(R.string.th_rainbow)
            ), p.getInt(Cfg.L_BORDER_THEME, 0)) { p.edit().putInt(Cfg.L_BORDER_THEME, it).apply() }
        }

        region(getString(R.string.r_alert_shape), R.drawable.ic_screen).apply {
            selectItem(this, getString(R.string.f_leave_style), listOf(
                getString(R.string.style_animated), getString(R.string.style_outlined), getString(R.string.style_filled)
            ), p.getInt(Cfg.L_LEAVE_STYLE, 0)) { p.edit().putInt(Cfg.L_LEAVE_STYLE, it).apply() }
            switchItem(this, getString(R.string.f_show_icon), p.getBoolean(Cfg.L_SHOW_ICON, true)) {
                p.edit().putBoolean(Cfg.L_SHOW_ICON, it).apply()
            }
            selectItem(this, getString(R.string.f_icon), listOf(
                getString(R.string.ic_leave_name), getString(R.string.ic_warning_name),
                getString(R.string.ic_info_name), getString(R.string.ic_home_name)
            ), p.getInt(Cfg.L_ICON, 0)) { p.edit().putInt(Cfg.L_ICON, it).apply() }
            switchItem(this, getString(R.string.f_btn_icons), p.getBoolean(Cfg.L_BTN_ICONS, true)) {
                p.edit().putBoolean(Cfg.L_BTN_ICONS, it).apply()
            }
            pillItem(this, getString(R.string.f_order), listOf(
                getString(R.string.order_stay_first), getString(R.string.order_leave_first)
            ), p.getInt(Cfg.L_ORDER, 0)) { p.edit().putInt(Cfg.L_ORDER, it).apply() }
            sliderItem(this, getString(R.string.f_radius), 4f, 30f, 1f,
                p.getInt(Cfg.L_RADIUS, Cfg.DEF_RADIUS).toFloat(), "dp") { p.edit().putInt(Cfg.L_RADIUS, it.toInt()).apply() }
            sliderItem(this, getString(R.string.f_border_w), 1f, 6f, 1f,
                p.getInt(Cfg.L_BORDER_W, 2).toFloat(), "dp") { p.edit().putInt(Cfg.L_BORDER_W, it.toInt()).apply() }
            sliderItem(this, getString(R.string.f_btn_h), 44f, 76f, 2f,
                p.getInt(Cfg.L_BTN_H, 58).toFloat(), "dp") { p.edit().putInt(Cfg.L_BTN_H, it.toInt()).apply() }
            sliderItem(this, getString(R.string.f_title_size), 16f, 30f, 1f,
                p.getInt(Cfg.L_TITLE_SIZE, 22).toFloat(), "sp") { p.edit().putInt(Cfg.L_TITLE_SIZE, it.toInt()).apply() }
            sliderItem(this, getString(R.string.f_msg_size), 12f, 22f, 1f,
                p.getInt(Cfg.L_MSG_SIZE, 16).toFloat(), "sp") { p.edit().putInt(Cfg.L_MSG_SIZE, it.toInt()).apply() }
            sliderItem(this, getString(R.string.f_width), 70f, 100f, 2f,
                p.getInt(Cfg.L_WIDTH, 90).toFloat(), "%") { p.edit().putInt(Cfg.L_WIDTH, it.toInt()).apply() }
            sliderItem(this, getString(R.string.f_dim), 0f, 90f, 5f,
                p.getInt(Cfg.L_DIM, 60).toFloat(), "%") { p.edit().putInt(Cfg.L_DIM, it.toInt()).apply() }
        }

        region(getString(R.string.r_alert_motion), R.drawable.ic_motion).apply {
            selectItem(this, getString(R.string.f_entrance), listOf(
                getString(R.string.en_none), getString(R.string.en_fade), getString(R.string.en_slide),
                getString(R.string.en_zoom), getString(R.string.en_slide_down), getString(R.string.en_bounce)
            ), p.getInt(Cfg.L_ENTRANCE, 3)) { p.edit().putInt(Cfg.L_ENTRANCE, it).apply() }
            sliderItem(this, getString(R.string.f_enter_ms), 150f, 900f, 50f,
                p.getInt(Cfg.L_ENTER_MS, 340).toFloat(), getString(R.string.unit_ms)) {
                p.edit().putInt(Cfg.L_ENTER_MS, it.toInt()).apply()
            }
            switchItem(this, getString(R.string.f_border_anim), p.getBoolean(Cfg.L_BORDER_ANIM, true)) {
                p.edit().putBoolean(Cfg.L_BORDER_ANIM, it).apply()
            }
            sliderItem(this, getString(R.string.f_border_speed), 1f, 8f, 0.5f,
                p.getInt(Cfg.L_BORDER_SPEED, Cfg.DEF_SPEED) / 1000f, getString(R.string.unit_seconds)) {
                p.edit().putInt(Cfg.L_BORDER_SPEED, (it * 1000).toInt()).apply()
            }
            switchItem(this, getString(R.string.f_pulse), p.getBoolean(Cfg.L_PULSE, true)) {
                p.edit().putBoolean(Cfg.L_PULSE, it).apply()
            }
            sliderItem(this, getString(R.string.f_pulse_ms), 800f, 3500f, 100f,
                p.getInt(Cfg.L_PULSE_MS, 1800).toFloat(), getString(R.string.unit_ms)) {
                p.edit().putInt(Cfg.L_PULSE_MS, it.toInt()).apply()
            }
            switchItem(this, getString(R.string.f_breathe), p.getBoolean(Cfg.L_BREATHE, true)) {
                p.edit().putBoolean(Cfg.L_BREATHE, it).apply()
            }
            switchItem(this, getString(R.string.f_haptic), p.getBoolean(Cfg.L_HAPTIC, true)) {
                p.edit().putBoolean(Cfg.L_HAPTIC, it).apply()
            }
            switchItem(this, getString(R.string.f_cancelable), p.getBoolean(Cfg.L_CANCELABLE, true)) {
                p.edit().putBoolean(Cfg.L_CANCELABLE, it).apply()
            }
            buttons(this, dangerButton(getString(R.string.restore_defaults), R.drawable.ic_restore) {
                val e = p.edit()
                Cfg.LEAVE_KEYS.forEach { e.remove(it) }
                Cfg.LEAVE_TEXT_KEYS.forEach { k -> e.remove(k); e.remove("${k}_ar"); e.remove("${k}_en") }
                e.apply(); toast(R.string.reset_done); showTab(3)
            })
        }
    }

    private fun tabLanguage() {
        region(getString(R.string.r_page_lang), R.drawable.ic_language).apply {
            val names = Cfg.LANGS.mapIndexed { i, l ->
                if (i == 0) "${l.flag}  " + getString(R.string.lang_original)
                else "${l.flag}  ${l.native}  ·  ${l.english}"
            }
            val current = p.getString(Cfg.PAGE_LANG, "").orEmpty()
            val sel = Cfg.LANGS.indexOfFirst { it.code == current }.let { if (it < 0) 0 else it }
            selectItem(this, getString(R.string.f_page_lang), names, sel, getString(R.string.h_page_lang)) {
                val code = Cfg.LANGS[it].code
                p.edit().putString(Cfg.PAGE_LANG, if (code == "ar") "" else code).apply()
                reload = true
            }
            switchItem(this, getString(R.string.f_show_lang), p.getBoolean(Cfg.FAB_SHOW_LANG, true)) {
                p.edit().putBoolean(Cfg.FAB_SHOW_LANG, it).apply()
            }
            infoAlert(this, getString(R.string.info_translate))
        }

        region(getString(R.string.r_ui_lang), R.drawable.ic_text).apply {
            help(this, getString(R.string.h_ui_lang))
            val codes = listOf("", "ar", "en")
            val cur = codes.indexOf(p.getString(Cfg.APP_LANG, "").orEmpty()).coerceAtLeast(0)
            pillItem(this, null, listOf(getString(R.string.lang_auto), "العربية", "English"), cur) {
                p.edit().putString(Cfg.APP_LANG, codes[it]).apply()
                AppCompatDelegate.setApplicationLocales(
                    if (codes[it].isEmpty()) LocaleListCompat.getEmptyLocaleList()
                    else LocaleListCompat.forLanguageTags(codes[it])
                )
            }
        }
    }

    private fun tabSecurity() {
        region(getString(R.string.r_account), R.drawable.ic_lock).apply {
            help(this, getString(R.string.h_account))
            val user = textItem(this, getString(R.string.username), p.getString(Cfg.ADMIN_USER, Cfg.DEF_USER).orEmpty(), ltr = true)
            val pass = textItem(this, getString(R.string.pin_change_label), "", getString(R.string.h_new_password), ltr = true, password = true)
            val pass2 = textItem(this, getString(R.string.confirm_password), "", ltr = true, password = true)
            buttons(this, hotButton(getString(R.string.save), R.drawable.ic_check) {
                val u = user.text.toString().trim()
                val pw = pass.text.toString()
                if (u.isEmpty()) { toast(R.string.username_required); return@hotButton }
                val e = p.edit().putString(Cfg.ADMIN_USER, u)
                if (pw.isNotEmpty()) {
                    if (pw.length < 4) { toast(R.string.pin_too_short); return@hotButton }
                    if (pw != pass2.text.toString()) { toast(R.string.password_mismatch); return@hotButton }
                    e.putString(Cfg.ADMIN_PASS, pw)
                }
                e.apply(); pass.setText(""); pass2.setText(""); toast(R.string.saved)
            })
        }
        region(getString(R.string.r_reset), R.drawable.ic_restore).apply {
            help(this, getString(R.string.h_reset_all))
            buttons(this, dangerButton(getString(R.string.reset_all), R.drawable.ic_warning) {
                AlertDialog.Builder(this@SettingsActivity)
                    .setMessage(R.string.reset_all_confirm)
                    .setPositiveButton(R.string.yes) { _, _ -> resetAll() }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            })
        }
    }

    private fun resetAll() {
        val keep = setOf(Cfg.URL, Cfg.ADMIN_USER, Cfg.ADMIN_PASS, "first_run_done")
        val e = p.edit()
        p.all.keys.filter { it !in keep }.forEach { e.remove(it) }
        e.apply()
        reload = true
        toast(R.string.reset_done)
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
        showTab(tabIndex)
    }

    private fun footer() {
        content.addView(TextView(this).apply {
            text = getString(R.string.app_name) + "  •  v" + BuildConfig.VERSION_NAME + "  •  " +
                getString(R.string.signed_in_as, p.getString(Cfg.ADMIN_USER, Cfg.DEF_USER))
            setTextColor(MUTED); textSize = 12f
            gravity = Gravity.CENTER
            setPadding(0, dp(20), 0, 0)
        }, lp())
    }

    // =========================================================================
    // APEX-style components
    // =========================================================================

    private fun lp(top: Int = 0) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply { topMargin = dp(top) }

    private fun box(fill: Int, stroke: Int, radius: Int) = GradientDrawable().apply {
        setColor(fill); cornerRadius = dpf(radius.toFloat()); setStroke(dp(1), stroke)
    }

    private fun ripple(): android.graphics.drawable.Drawable? {
        val tv = TypedValue()
        theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, tv, true)
        return ContextCompat.getDrawable(this, tv.resourceId)
    }

    /** APEX "Standard" region: white card, title bar with bottom border, padded body. */
    private fun region(title: String, icon: Int): LinearLayout {
        val card = MaterialCardView(this).apply {
            setCardBackgroundColor(Color.WHITE)
            radius = dpf(6f)
            strokeColor = BORDER
            strokeWidth = dp(1)
            cardElevation = dpf(1f)
        }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(13), dp(16), dp(13))
            addView(ImageView(context).apply {
                setImageResource(icon)
                imageTintList = ColorStateList.valueOf(blue)
            }, LinearLayout.LayoutParams(dp(20), dp(20)))
            addView(TextView(context).apply {
                text = title; setTextColor(TEXT); textSize = 16.5f
                setTypeface(typeface, Typeface.BOLD)
                setPadding(dp(10), 0, dp(10), 0)
            })
        })
        col.addView(View(this).apply { setBackgroundColor(BORDER) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)))
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(6), dp(16), dp(18))
        }
        col.addView(body)
        card.addView(col)
        content.addView(card, lp(top = 12))
        return body
    }

    private fun help(c: LinearLayout, s: String) {
        c.addView(TextView(this).apply {
            text = s; setTextColor(MUTED); textSize = 13.5f
            setLineSpacing(dpf(2f), 1f)
            setPadding(0, dp(10), 0, 0)
        }, lp())
    }

    private fun itemLabel(c: LinearLayout, s: String) {
        c.addView(TextView(this).apply {
            text = s; setTextColor(LABEL); textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(16), 0, dp(6))
        }, lp())
    }

    /** APEX alert, "information" style. */
    private fun infoAlert(c: LinearLayout, s: String) {
        c.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = box(Color.parseColor("#EAF3FC"), Color.parseColor("#B9D7F5"), 6)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            addView(ImageView(context).apply {
                setImageResource(R.drawable.ic_info)
                imageTintList = ColorStateList.valueOf(blue)
            }, LinearLayout.LayoutParams(dp(22), dp(22)))
            addView(TextView(context).apply {
                text = s; setTextColor(TEXT); textSize = 13.5f
                setPadding(dp(10), 0, dp(4), 0)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }, lp(top = 16))
    }

    /** Floating-label text item. */
    private fun textItem(
        c: LinearLayout, label: String, value: String, helpText: String? = null,
        ltr: Boolean = false, password: Boolean = false, multi: Boolean = false
    ): TextInputEditText {
        val layout = layoutInflater.inflate(R.layout.item_apex_field, c, false) as TextInputLayout
        layout.hint = label
        if (helpText != null) layout.helperText = helpText
        val input = layout.findViewById<TextInputEditText>(R.id.fieldInput)
        when {
            password -> {
                input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                layout.endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE
            }
            ltr -> input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            multi -> {
                input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                input.maxLines = 4
            }
            else -> input.inputType = InputType.TYPE_CLASS_TEXT
        }
        if (ltr || password) input.textDirection = View.TEXT_DIRECTION_LTR
        input.setText(value)
        c.addView(layout)
        return input
    }

    private fun textPref(c: LinearLayout, label: String, key: String, defRes: Int, multi: Boolean = false) {
        val input = textItem(c, label, Cfg.str(this, p, key, defRes), multi = multi)
        input.doAfterTextChanged { Cfg.putStr(this, p, key, it?.toString()?.trim().orEmpty()) }
    }

    /** APEX "Select List": exposed dropdown with floating label. */
    private fun selectItem(
        c: LinearLayout, label: String, options: List<String>, selected: Int,
        helpText: String? = null, onSelect: (Int) -> Unit
    ) {
        val layout = layoutInflater.inflate(R.layout.item_apex_select, c, false) as TextInputLayout
        layout.hint = label
        if (helpText != null) layout.helperText = helpText
        val input = layout.findViewById<MaterialAutoCompleteTextView>(R.id.selectInput)
        input.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, options))
        input.setText(options[selected.coerceIn(0, options.size - 1)], false)
        input.setOnItemClickListener { _, _, pos, _ -> onSelect(pos) }
        c.addView(layout)
    }

    /** APEX "Radio Group – pill buttons". */
    private fun pillItem(c: LinearLayout, label: String?, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
        if (label != null) itemLabel(c, label) else c.addView(View(this), LinearLayout.LayoutParams(1, dp(12)))
        val group = MaterialButtonToggleGroup(this).apply {
            isSingleSelection = true
            isSelectionRequired = true
        }
        val bg = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(blue, Color.WHITE)
        )
        val fg = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(Color.WHITE, TEXT)
        )
        val ids = options.mapIndexed { i, s ->
            val b = MaterialButton(this).apply {
                id = View.generateViewId()
                text = s; isAllCaps = false; textSize = 14.5f
                insetTop = 0; insetBottom = 0
                minHeight = dp(48)
                setPadding(dp(16), 0, dp(16), 0)
                backgroundTintList = bg
                setTextColor(fg)
                strokeColor = ColorStateList.valueOf(FIELD_BORDER)
                strokeWidth = dp(1)
                cornerRadius = dp(6)
                isCheckable = true
            }
            group.addView(b, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(48)))
            b.id
        }
        group.check(ids[selected.coerceIn(0, ids.size - 1)])
        group.addOnButtonCheckedListener { _, id, checked ->
            if (checked) onSelect(ids.indexOf(id))
        }
        c.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(group)
        }, lp())
    }

    /** APEX "Switch" item: label (+ help) and a toggle. */
    private fun switchItem(c: LinearLayout, label: String, checked: Boolean, helpText: String? = null, onChange: (Boolean) -> Unit) {
        val sw = SwitchMaterial(this).apply {
            isChecked = checked
            thumbTintList = ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(blue, Color.parseColor("#FAFAFA"))
            )
            trackTintList = ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(Color.parseColor("#8CC0EC"), Color.parseColor("#BDBDBD"))
            )
            setOnCheckedChangeListener { _, b -> onChange(b) }
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(12), 0, dp(4))
            isClickable = true
            setOnClickListener { sw.toggle() }
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(context).apply {
                    text = label; setTextColor(TEXT); textSize = 15.5f
                })
                if (helpText != null) addView(TextView(context).apply {
                    text = helpText; setTextColor(MUTED); textSize = 12.5f
                    setPadding(0, dp(2), 0, 0)
                })
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(sw)
        }
        c.addView(row, lp())
        c.addView(View(this).apply { setBackgroundColor(Color.parseColor("#F0F0F0")) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply { topMargin = dp(8) })
    }

    /** Number item with slider and a value badge. */
    private fun sliderItem(
        c: LinearLayout, label: String, from: Float, to: Float, step: Float, value: Float,
        unit: String, onChange: (Float) -> Unit
    ) {
        fun fmt(v: Float) = (if (step < 1f) String.format("%.1f", v) else v.toInt().toString()) + " " + unit
        val start = (Math.round((value.coerceIn(from, to) - from) / step) * step + from).coerceIn(from, to)
        val badge = TextView(this).apply {
            text = fmt(start); setTextColor(blue); textSize = 13f
            setTypeface(typeface, Typeface.BOLD)
            background = box(Color.parseColor("#EAF3FC"), Color.parseColor("#B9D7F5"), 12)
            setPadding(dp(10), dp(3), dp(10), dp(3))
        }
        c.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(16), 0, 0)
            addView(TextView(context).apply {
                text = label; setTextColor(TEXT); textSize = 15.5f
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(badge)
        }, lp())
        c.addView(Slider(this).apply {
            valueFrom = from; valueTo = to; stepSize = step; this.value = start
            thumbTintList = ColorStateList.valueOf(blue)
            trackActiveTintList = ColorStateList.valueOf(blue)
            trackInactiveTintList = ColorStateList.valueOf(Color.parseColor("#D9D9D9"))
            haloTintList = ColorStateList.valueOf(Color.parseColor("#330572CE"))
            setLabelFormatter { fmt(it) }
            addOnChangeListener { _, v, _ -> badge.text = fmt(v); onChange(v) }
        }, lp())
    }

    /** APEX "Color Picker" item. */
    private fun colorItem(c: LinearLayout, label: String, current: Int, onPick: (Int) -> Unit) {
        var color = current
        val swatch = View(this)
        val hex = TextView(this).apply { setTextColor(TEXT); textSize = 16f; textDirection = View.TEXT_DIRECTION_LTR }
        fun paint() {
            swatch.background = GradientDrawable().apply {
                setColor(color); cornerRadius = dpf(4f); setStroke(dp(1), Color.parseColor("#33000000"))
            }
            hex.text = String.format("#%06X", 0xFFFFFF and color)
        }
        paint()
        val field = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = box(Color.WHITE, FIELD_BORDER, 6)
            setPadding(dp(12), 0, dp(12), 0)
            minimumHeight = dp(56)
            isClickable = true
            addView(swatch, LinearLayout.LayoutParams(dp(30), dp(30)))
            addView(hex, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(12) })
            addView(ImageView(context).apply {
                setImageResource(R.drawable.ic_palette)
                imageTintList = ColorStateList.valueOf(MUTED)
            }, LinearLayout.LayoutParams(dp(22), dp(22)))
            setOnClickListener {
                pickColor(label, color) { picked -> color = picked; paint(); onPick(picked) }
            }
        }
        itemLabel(c, label)
        c.addView(field, lp())
    }

    private fun pickColor(title: String, current: Int, onPick: (Int) -> Unit) {
        val grid = GridLayout(this).apply { columnCount = 4 }
        val size = dp(52)
        var dialog: AlertDialog? = null
        Cfg.SWATCHES.forEach { col ->
            grid.addView(FrameLayout(this).apply {
                background = GradientDrawable().apply {
                    setColor(col); cornerRadius = dpf(8f)
                    setStroke(dp(if (col == current) 4 else 1), if (col == current) TEXT else Color.parseColor("#22000000"))
                }
                if (col == current) addView(ImageView(context).apply {
                    setImageResource(R.drawable.ic_check)
                    imageTintList = ColorStateList.valueOf(Color.WHITE)
                }, FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER))
                setOnClickListener { onPick(col); dialog?.dismiss() }
            }, GridLayout.LayoutParams().apply {
                width = size; height = size
                setMargins(dp(6), dp(6), dp(6), dp(6))
            })
        }
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(8), dp(18), 0)
            gravity = Gravity.CENTER_HORIZONTAL
            addView(grid)
        }
        val hexInput = textItem(wrap, getString(R.string.hex_color), String.format("#%06X", 0xFFFFFF and current), ltr = true)
        dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setView(ScrollView(this).apply { addView(wrap) })
            .setPositiveButton(R.string.apply) { _, _ ->
                try { onPick(Color.parseColor(hexInput.text.toString().trim())) }
                catch (e: Exception) { toast(R.string.invalid_color) }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ---- Buttons (APEX hot / normal / danger) ----

    private fun baseButton(s: String, icon: Int?, onClick: () -> Unit) = MaterialButton(this).apply {
        text = s; textSize = 14.5f; isAllCaps = false
        cornerRadius = dp(6); insetTop = 0; insetBottom = 0
        minHeight = dp(50)
        setPadding(dp(14), dp(8), dp(14), dp(8))
        if (icon != null) {
            this.icon = ContextCompat.getDrawable(context, icon)
            iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
            iconPadding = dp(8); iconSize = dp(19)
        }
        setOnClickListener { onClick() }
    }

    private fun hotButton(s: String, icon: Int?, onClick: () -> Unit) = baseButton(s, icon, onClick).apply {
        backgroundTintList = ColorStateList.valueOf(blue)
        setTextColor(Color.WHITE); iconTint = ColorStateList.valueOf(Color.WHITE)
    }

    private fun normalButton(s: String, icon: Int?, onClick: () -> Unit) = baseButton(s, icon, onClick).apply {
        backgroundTintList = ColorStateList.valueOf(NORMAL_BTN)
        strokeColor = ColorStateList.valueOf(Color.parseColor("#D0D0D0")); strokeWidth = dp(1)
        setTextColor(TEXT); iconTint = ColorStateList.valueOf(LABEL)
        elevation = 0f; stateListAnimator = null
    }

    private fun dangerButton(s: String, icon: Int?, onClick: () -> Unit) = baseButton(s, icon, onClick).apply {
        backgroundTintList = ColorStateList.valueOf(Color.WHITE)
        strokeColor = ColorStateList.valueOf(DANGER); strokeWidth = dp(1)
        setTextColor(DANGER); iconTint = ColorStateList.valueOf(DANGER)
        elevation = 0f; stateListAnimator = null
    }

    private fun buttons(c: LinearLayout, vararg list: MaterialButton) {
        c.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(16), 0, 0)
            list.forEachIndexed { i, b ->
                addView(b, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    if (i > 0) marginStart = dp(10)
                })
            }
        }, lp())
    }

    private fun toast(res: Int) = Toast.makeText(this, getString(res), Toast.LENGTH_SHORT).show()

    private fun finishWithResult() {
        setResult(RESULT_OK, Intent().putExtra(EXTRA_RELOAD, reload))
        finish()
    }
}
