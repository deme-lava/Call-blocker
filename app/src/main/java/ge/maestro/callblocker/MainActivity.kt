package ge.maestro.callblocker

import android.app.Activity
import android.app.AlertDialog
import android.app.role.RoleManager
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var list: LinearLayout
    private lateinit var tabRules: TextView
    private lateinit var tabHistory: TextView
    private var showHistory = false
    private val fmt = SimpleDateFormat("dd.MM.yyyy  HH:mm", Locale.getDefault())
    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        runOnUiThread { render() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        list = findViewById(R.id.list)
        tabRules = findViewById(R.id.tabRules)
        tabHistory = findViewById(R.id.tabHistory)
        tabRules.setOnClickListener { showHistory = false; render() }
        tabHistory.setOnClickListener { showHistory = true; render() }
        findViewById<View>(R.id.fab).setOnClickListener { showAddDialog() }
        findViewById<View>(R.id.enableBtn).setOnClickListener { requestRole() }
    }

    override fun onResume() {
        super.onResume()
        Store.prefs(this).registerOnSharedPreferenceChangeListener(prefsListener)
        render()
    }

    override fun onPause() {
        super.onPause()
        Store.prefs(this).unregisterOnSharedPreferenceChangeListener(prefsListener)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        render()
    }

    private fun roleHeld() = getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_CALL_SCREENING)

    private fun requestRole() {
        val rm = getSystemService(RoleManager::class.java)
        startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING), 1)
    }

    private fun color(id: Int) = getColor(id)
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun render() {
        val on = roleHeld()
        findViewById<TextView>(R.id.statusText).text =
            if (on) "ბლოკი აქტიურია" else "ბლოკი გამორთულია — ჩართე"
        findViewById<View>(R.id.statusDot).backgroundTintList =
            ColorStateList.valueOf(color(if (on) R.color.ok else R.color.accent))
        findViewById<View>(R.id.enableBtn).visibility = if (on) View.GONE else View.VISIBLE

        val rules = Store.rules(this)
        val history = Store.history(this)
        tabRules.text = "წესები (${rules.size})"
        tabHistory.text = "ისტორია (${history.size})"
        styleTab(tabRules, !showHistory)
        styleTab(tabHistory, showHistory)
        findViewById<View>(R.id.fab).visibility = if (showHistory) View.GONE else View.VISIBLE

        list.removeAllViews()
        if (showHistory) renderHistory(history) else renderRules(rules, history)
    }

    private fun styleTab(t: TextView, active: Boolean) {
        t.setBackgroundResource(if (active) R.drawable.bg_tab_on else 0)
        t.setTextColor(color(if (active) R.color.text else R.color.muted))
        t.setTypeface(null, if (active) Typeface.BOLD else Typeface.NORMAL)
    }

    private fun renderRules(rules: List<Rule>, history: List<Blocked>) {
        if (rules.isEmpty()) { empty("წესები ჯერ არ არის.\nდააჭირე + ღილაკს"); return }
        rules.forEach { r ->
            val count = history.count { it.pattern == r.pattern && it.type == r.type }
            val card = card()
            val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            col.addView(text(r.pattern, 18f, R.color.text, true))
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(6), 0, 0)
            }
            row.addView(chip(r.type))
            row.addView(text("   დაბლოკა: $count", 13f, R.color.muted, false))
            col.addView(row)
            card.addView(col, LinearLayout.LayoutParams(0, -2, 1f))
            val del = text("✕", 20f, R.color.muted, false).apply {
                setPadding(dp(12), dp(4), dp(4), dp(4))
                setOnClickListener {
                    AlertDialog.Builder(this@MainActivity)
                        .setMessage("წავშალო წესი „${r.pattern}“?")
                        .setPositiveButton("წაშლა") { _, _ -> Store.removeRule(this@MainActivity, r.id) }
                        .setNegativeButton("გაუქმება", null).show()
                }
            }
            card.addView(del)
            list.addView(card)
        }
    }

    private fun renderHistory(history: List<Blocked>) {
        if (history.isEmpty()) { empty("დაბლოკილი ზარები ჯერ არ არის"); return }
        val clear = text("ისტორიის გასუფთავება", 14f, R.color.accent, true).apply {
            gravity = Gravity.END
            setPadding(0, 0, dp(4), dp(10))
            setOnClickListener {
                AlertDialog.Builder(this@MainActivity)
                    .setMessage("გავასუფთავო მთელი ისტორია?")
                    .setPositiveButton("კი") { _, _ -> Store.clearHistory(this@MainActivity) }
                    .setNegativeButton("არა", null).show()
            }
        }
        list.addView(clear, LinearLayout.LayoutParams(-1, -2))
        history.forEach { h ->
            val card = card()
            val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            col.addView(text(h.number, 17f, R.color.text, true))
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(6), 0, 0)
            }
            row.addView(chip(h.type))
            row.addView(text("  ${h.pattern}", 13f, R.color.muted, false))
            col.addView(row)
            col.addView(text(fmt.format(Date(h.time)), 12f, R.color.muted, false).apply {
                setPadding(0, dp(6), 0, 0)
            })
            card.addView(col, LinearLayout.LayoutParams(0, -2, 1f))
            list.addView(card)
        }
    }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setBackgroundResource(R.drawable.bg_card)
        setPadding(dp(16), dp(14), dp(12), dp(14))
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) }
    }

    private fun text(s: String, size: Float, colorId: Int, bold: Boolean) = TextView(this).apply {
        text = s; textSize = size; setTextColor(color(colorId))
        if (bold) setTypeface(null, Typeface.BOLD)
    }

    private fun chip(t: RuleType): TextView {
        val (bg, fg) = when (t) {
            RuleType.STARTS -> R.color.starts_bg to R.color.starts_fg
            RuleType.CONTAINS -> R.color.contains_bg to R.color.contains_fg
            RuleType.EXACT -> R.color.exact_bg to R.color.exact_fg
        }
        return text(t.label, 12f, fg, true).apply {
            setPadding(dp(10), dp(3), dp(10), dp(3))
            background = GradientDrawable().apply { setColor(color(bg)); cornerRadius = dp(20).toFloat() }
        }
    }

    private fun empty(msg: String) {
        list.addView(text(msg, 15f, R.color.muted, false).apply {
            gravity = Gravity.CENTER; setPadding(0, dp(48), 0, 0)
        }, LinearLayout.LayoutParams(-1, -2))
    }

    private fun showAddDialog() {
        val v = layoutInflater.inflate(R.layout.dialog_add, null)
        val input = v.findViewById<EditText>(R.id.input)
        val group = v.findViewById<RadioGroup>(R.id.types)
        val dlg = AlertDialog.Builder(this)
            .setTitle("ახალი წესი")
            .setView(v)
            .setPositiveButton("დამატება", null)
            .setNegativeButton("გაუქმება", null)
            .create()
        dlg.setOnShowListener {
            dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val p = input.text.toString().trim()
                if (p.none { it.isDigit() }) {
                    input.error = "ჩაწერე ნომერი"; return@setOnClickListener
                }
                val type = when (group.checkedRadioButtonId) {
                    R.id.rContains -> RuleType.CONTAINS
                    R.id.rExact -> RuleType.EXACT
                    else -> RuleType.STARTS
                }
                Store.addRule(this, p, type)
                Toast.makeText(this, "დაემატა", Toast.LENGTH_SHORT).show()
                dlg.dismiss()
            }
        }
        dlg.show()
    }
}
