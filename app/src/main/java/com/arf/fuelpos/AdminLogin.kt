package com.arf.fuelpos

import android.animation.ObjectAnimator
import android.app.Activity
import android.app.Dialog
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

/** Full-screen Oracle APEX-style admin sign-in (username + password) before opening settings. */
object AdminLogin {

    fun show(a: Activity, onSuccess: () -> Unit) {
        val p = Cfg.prefs(a)
        val dialog = Dialog(a, R.style.Theme_FuelPOS_ApexDialog)
        val view = a.layoutInflater.inflate(R.layout.dialog_admin_login, null)
        dialog.setContentView(view)
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

        val user = view.findViewById<TextInputEditText>(R.id.userInput)
        val pass = view.findViewById<TextInputEditText>(R.id.passInput)
        val error = view.findViewById<TextView>(R.id.loginError)
        val card = view.findViewById<View>(R.id.loginCard)
        user.setText(p.getString(Cfg.ADMIN_USER, Cfg.DEF_USER))
        view.findViewById<TextView>(R.id.loginFooter).text =
            a.getString(R.string.app_name) + " • v" + BuildConfig.VERSION_NAME

        fun attempt() {
            val okUser = user.text.toString().trim()
                .equals(p.getString(Cfg.ADMIN_USER, Cfg.DEF_USER), ignoreCase = true)
            val okPass = pass.text.toString() == p.getString(Cfg.ADMIN_PASS, Cfg.DEF_PASS)
            if (okUser && okPass) {
                dialog.dismiss(); onSuccess()
            } else {
                error.visibility = View.VISIBLE
                pass.setText("")
                ObjectAnimator.ofFloat(card, View.TRANSLATION_X, 0f, 22f, -22f, 16f, -16f, 8f, -8f, 0f)
                    .setDuration(450).start()
            }
        }
        view.findViewById<MaterialButton>(R.id.loginButton).setOnClickListener { attempt() }
        pass.setOnEditorActionListener { _, id, _ ->
            if (id == EditorInfo.IME_ACTION_DONE) { attempt(); true } else false
        }
        view.findViewById<View>(R.id.loginCancel).setOnClickListener { dialog.dismiss() }

        card.alpha = 0f; card.translationY = 60f
        dialog.setOnShowListener {
            card.animate().alpha(1f).translationY(0f).setDuration(320).start()
            pass.requestFocus()
        }
        dialog.show()
    }
}
