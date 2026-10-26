package ge.maestro.callblocker

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

enum class RuleType(val label: String) {
    STARTS("იწყება"), CONTAINS("შეიცავს"), EXACT("ზუსტი")
}

data class Rule(val id: Long, val pattern: String, val type: RuleType)

data class Blocked(val number: String, val time: Long, val pattern: String, val type: RuleType)

object Store {
    private const val PREFS = "store"
    private const val MAX_HISTORY = 500

    fun prefs(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    fun rules(c: Context): List<Rule> {
        val p = prefs(c)
        if (!p.contains("rules")) {
            val def = listOf(Rule(1, "+99532222", RuleType.STARTS))
            saveRules(c, def)
            return def
        }
        val arr = JSONArray(p.getString("rules", "[]"))
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Rule(o.getLong("id"), o.getString("p"), RuleType.valueOf(o.getString("t")))
        }
    }

    @Synchronized
    fun saveRules(c: Context, list: List<Rule>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("id", it.id).put("p", it.pattern).put("t", it.type.name)) }
        prefs(c).edit().putString("rules", arr.toString()).apply()
    }

    fun addRule(c: Context, pattern: String, type: RuleType) =
        saveRules(c, rules(c) + Rule(System.currentTimeMillis(), pattern, type))

    fun removeRule(c: Context, id: Long) = saveRules(c, rules(c).filter { it.id != id })

    @Synchronized
    fun history(c: Context): List<Blocked> {
        val arr = JSONArray(prefs(c).getString("history", "[]"))
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Blocked(o.getString("n"), o.getLong("ts"), o.getString("p"), RuleType.valueOf(o.getString("t")))
        }
    }

    @Synchronized
    fun addHistory(c: Context, b: Blocked) {
        val list = (listOf(b) + history(c)).take(MAX_HISTORY)
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("n", it.number).put("ts", it.time).put("p", it.pattern).put("t", it.type.name))
        }
        prefs(c).edit().putString("history", arr.toString()).apply()
    }

    fun clearHistory(c: Context) = prefs(c).edit().putString("history", "[]").apply()
}

object Matcher {
    private fun digits(s: String) = s.filter { it.isDigit() }

    /** Brings +995 32 222…, 00995…, 032 222…, 32 222… to one form: 32222… */
    fun national(raw: String): String {
        var d = digits(raw)
        val intl = raw.trim().startsWith("+") || d.startsWith("00")
        if (d.startsWith("00")) d = d.drop(2)
        if (d.startsWith("995") && (intl || d.length >= 12)) d = d.drop(3)
        if (d.startsWith("0")) d = d.drop(1)
        return d
    }

    fun matches(number: String, rule: Rule): Boolean {
        val nd = digits(number); val nn = national(number)
        val pd = digits(rule.pattern); val pn = national(rule.pattern)
        if (pd.isEmpty() || nd.isEmpty()) return false
        return when (rule.type) {
            RuleType.STARTS -> (pn.isNotEmpty() && nn.startsWith(pn)) || nd.startsWith(pd)
            RuleType.EXACT -> nn == pn || nd == pd
            RuleType.CONTAINS -> nd.contains(pd) || nn.contains(pd)
        }
    }

    fun find(number: String, rules: List<Rule>): Rule? =
        rules.firstOrNull { it.type == RuleType.EXACT && matches(number, it) }
            ?: rules.firstOrNull { matches(number, it) }
}
