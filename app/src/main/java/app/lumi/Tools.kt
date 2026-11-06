package app.lumi

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.StatFs
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.text.Html
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.Request
import java.net.URLDecoder
import java.net.URLEncoder
import java.text.DateFormat
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** The personal-agent toolbox. Same tools work for cloud models (OpenAI tool calls) and on-device Gemma 4. */
object Tools {
    lateinit var ctx: Context
    private const val UA = "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36"

    // "name" to "description", or "name" to "type|description"
    private fun def(name: String, desc: String, vararg p: Pair<String, String>, required: List<String> = p.map { it.first }) =
        buildJsonObject {
            put("name", name); put("description", desc)
            putJsonObject("parameters") {
                put("type", "object")
                putJsonObject("properties") {
                    p.forEach { (k, d) ->
                        val typed = d.contains('|')
                        putJsonObject(k) {
                            put("type", if (typed) d.substringBefore('|') else "string")
                            put("description", if (typed) d.substringAfter('|') else d)
                        }
                    }
                }
                putJsonArray("required") { required.forEach { add(it) } }
            }
        }

    val defs = listOf(
        def("web_search", "Search the web for current information. Returns titles, links and snippets.", "query" to "Search query"),
        def("fetch_url", "Read the main text of a web page.", "url" to "Full http(s) URL"),
        def("calculate", "Evaluate a math expression exactly, e.g. (2+3)*4^2/7, sqrt(2), sin(pi/2), 15% of 80 as 0.15*80.", "expression" to "Math expression"),
        def("memory", "Long-term memory about the user. Use save for lasting facts and preferences.", "action" to "save, forget or list", "text" to "The fact", required = listOf("action")),
        def("notes", "The user's notes.", "action" to "add, list, read or delete", "title" to "Note title", "content" to "Note text (for add)", required = listOf("action")),
        def("set_alarm", "Set an alarm in the clock app.", "hour" to "integer|Hour 0-23", "minute" to "integer|Minute 0-59", "label" to "Label", required = listOf("hour", "minute")),
        def("set_timer", "Start a countdown timer.", "seconds" to "integer|Length in seconds", "label" to "Label", required = listOf("seconds")),
        def("remind", "Send the user a notification later.", "text" to "Reminder text", "at" to "Local date-time, ISO-8601 like 2026-11-06T18:30", "in_minutes" to "integer|Minutes from now, instead of at", required = listOf("text")),
        def("calendar_event", "Open the calendar with a new event filled in.", "title" to "Title", "start" to "Local date-time ISO-8601", "end" to "Local date-time ISO-8601", "location" to "Location", "details" to "Description", required = listOf("title", "start")),
        def("message", "Open a ready-to-send SMS or email draft.", "channel" to "sms or email", "to" to "Phone number or email address", "subject" to "Email subject", "body" to "Message text", required = listOf("channel", "body")),
        def("call", "Open the phone dialer with a number.", "number" to "Phone number"),
        def("open", "Open a website or an installed app by name.", "target" to "URL or app name"),
        def("device_status", "Battery, charging, storage and phone model."),
        def("clipboard", "Copy text to the clipboard.", "text" to "Text to copy"),
    )
    val openAi = JsonArray(defs.map { d -> buildJsonObject { put("type", "function"); put("function", d) } })

    suspend fun run(name: String, args: String): String = withContext(Dispatchers.IO) {
        val a = runCatching { Store.json.parseToJsonElement(args).jsonObject }.getOrElse { JsonObject(emptyMap()) }
        fun s(k: String) = (a[k] as? JsonPrimitive)?.contentOrNull.orEmpty()
        fun i(k: String) = (a[k] as? JsonPrimitive)?.let { it.intOrNull ?: it.doubleOrNull?.toInt() ?: it.contentOrNull?.toIntOrNull() }
        runCatching {
            when (name) {
                "web_search" -> search(s("query"))
                "fetch_url" -> fetch(s("url"))
                "calculate" -> Calc(s("expression")).run().let { if (it % 1.0 == 0.0 && abs(it) < 1e15) it.toLong().toString() else it.toString() }
                "memory" -> memory(s("action"), s("text"))
                "notes" -> notes(s("action"), s("title"), s("content"))
                "set_alarm" -> {
                    val h = i("hour") ?: 7; val m = i("minute") ?: 0
                    start(Intent(AlarmClock.ACTION_SET_ALARM).putExtra(AlarmClock.EXTRA_HOUR, h).putExtra(AlarmClock.EXTRA_MINUTES, m)
                        .putExtra(AlarmClock.EXTRA_MESSAGE, s("label").ifBlank { "Lumi" }).putExtra(AlarmClock.EXTRA_SKIP_UI, true))
                    "Alarm set for %02d:%02d.".format(h, m)
                }
                "set_timer" -> {
                    val sec = i("seconds") ?: error("seconds is required")
                    start(Intent(AlarmClock.ACTION_SET_TIMER).putExtra(AlarmClock.EXTRA_LENGTH, sec)
                        .putExtra(AlarmClock.EXTRA_MESSAGE, s("label").ifBlank { "Lumi" }).putExtra(AlarmClock.EXTRA_SKIP_UI, true))
                    "Timer started for $sec seconds."
                }
                "remind" -> remind(s("text"), s("at"), i("in_minutes"))
                "calendar_event" -> {
                    val ev = Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
                        .putExtra(CalendarContract.Events.TITLE, s("title"))
                        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, millis(s("start")))
                        .putExtra(CalendarContract.Events.EVENT_LOCATION, s("location"))
                        .putExtra(CalendarContract.Events.DESCRIPTION, s("details"))
                    if (s("end").isNotBlank()) ev.putExtra(CalendarContract.EXTRA_EVENT_END_TIME, millis(s("end")))
                    start(ev); "Opened the calendar with the event; the user taps Save to confirm."
                }
                "message" -> {
                    if (s("channel").equals("email", true)) start(
                        Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", s("to"), null))
                            .putExtra(Intent.EXTRA_SUBJECT, s("subject")).putExtra(Intent.EXTRA_TEXT, s("body"))
                    ) else start(Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", s("to"), null)).putExtra("sms_body", s("body")))
                    "Draft opened; the user taps send."
                }
                "call" -> { start(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", s("number"), null))); "Dialer opened." }
                "open" -> open(s("target"))
                "device_status" -> status()
                "clipboard" -> withContext(Dispatchers.Main) {
                    ctx.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Lumi", s("text"))); "Copied."
                }
                else -> "Unknown tool: $name"
            }
        }.getOrElse { "Error: ${it.message ?: it.javaClass.simpleName}" }
    }

    private fun start(i: Intent) = ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

    private fun millis(iso: String): Long =
        LocalDateTime.parse(iso.trim().take(16)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun get(url: String): String = Api.http.newCall(Request.Builder().url(url).header("User-Agent", UA).build()).execute().use {
        if (!it.isSuccessful) error("HTTP ${it.code}")
        it.body?.string().orEmpty()
    }

    fun text(html: String): String = Html.fromHtml(
        html.replace(Regex("(?is)<(script|style|noscript|svg|head|nav|footer)[^>]*>.*?</\\1>"), " "), Html.FROM_HTML_MODE_COMPACT,
    ).toString().replace(Regex("[ \\t\\x0B\\f\\r\\u00A0]+"), " ").replace(Regex("\\n\\s*\\n+"), "\n\n").trim()

    private fun search(q: String): String {
        val html = get("https://html.duckduckgo.com/html/?q=" + URLEncoder.encode(q, "UTF-8"))
        val links = Regex("""class="result__a"[^>]*href="([^"]+)"[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL).findAll(html).toList()
        val snippets = Regex("""class="result__snippet"[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL).findAll(html).map { text(it.groupValues[1]) }.toList()
        if (links.isEmpty()) return "No results. Try fetch_url on a known site instead."
        return links.take(6).mapIndexed { n, m ->
            val href = m.groupValues[1].replace("&amp;", "&")
            val url = Regex("uddg=([^&]+)").find(href)?.groupValues?.get(1)?.let { URLDecoder.decode(it, "UTF-8") } ?: href
            "${n + 1}. ${text(m.groupValues[2])}\n$url\n${snippets.getOrElse(n) { "" }}"
        }.joinToString("\n\n")
    }

    private fun fetch(url: String): String {
        val body = get(if (url.startsWith("http")) url else "https://$url")
        val title = Regex("(?is)<title[^>]*>(.*?)</title>").find(body)?.groupValues?.get(1)?.trim().orEmpty()
        val t = if (body.trimStart().startsWith("<") || "<html" in body.take(3000).lowercase()) text(body) else body
        return "$title\n\n${t.take(12_000)}"
    }

    private fun memory(action: String, text: String): String = when (action.lowercase()) {
        "save" -> { Store.edit { it.copy(memory = (it.memory + text.trim()).distinct()) }; "Saved to memory." }
        "forget" -> {
            val before = Store.prefs.value.memory.size
            Store.edit { p -> p.copy(memory = p.memory.filterNot { it.contains(text, true) || text.contains(it, true) }) }
            if (Store.prefs.value.memory.size < before) "Forgotten." else "Nothing matched."
        }
        else -> Store.prefs.value.memory.joinToString("\n") { "- $it" }.ifBlank { "Memory is empty." }
    }

    private fun notes(action: String, title: String, content: String): String {
        val all = Store.prefs.value.notes
        val hit = all.firstOrNull { it.title.equals(title, true) } ?: all.firstOrNull { title.isNotBlank() && it.title.contains(title, true) }
        return when (action.lowercase()) {
            "add" -> { Store.edit { p -> p.copy(notes = p.notes.filterNot { it.title.equals(title, true) } + Note(title.ifBlank { content.take(30) }, content)) }; "Note saved." }
            "read" -> hit?.let { "${it.title}\n${it.body}" } ?: "No such note."
            "delete" -> hit?.let { h -> Store.edit { p -> p.copy(notes = p.notes - h) }; "Deleted." } ?: "No such note."
            else -> all.joinToString("\n") { "- ${it.title}" }.ifBlank { "No notes yet." }
        }
    }

    private fun remind(text: String, at: String, inMinutes: Int?): String {
        val t = when {
            inMinutes != null -> now() + inMinutes * 60_000L
            at.isNotBlank() -> millis(at)
            else -> error("Give 'at' or 'in_minutes'")
        }
        val pi = PendingIntent.getBroadcast(
            ctx, (t / 1000 % Int.MAX_VALUE).toInt(), Intent(ctx, ReminderReceiver::class.java).putExtra("text", text),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        ctx.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi)
        return "Reminder set for ${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(t)}."
    }

    private fun open(target: String): String {
        val t = target.trim()
        if (t.startsWith("http") || ('.' in t && ' ' !in t)) {
            start(Intent(Intent.ACTION_VIEW, Uri.parse(if (t.startsWith("http")) t else "https://$t"))); return "Opened $t."
        }
        val pm = ctx.packageManager
        val q = t.lowercase()
        val apps = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .map { it to it.loadLabel(pm).toString() }
        val hit = apps.filter { (_, l) -> l.lowercase().let { q in it || it in q } }
            .minByOrNull { (_, l) -> abs(l.length - q.length) } ?: return "No installed app called \"$t\"."
        start(pm.getLaunchIntentForPackage(hit.first.activityInfo.packageName) ?: return "${hit.second} can't be opened.")
        return "Opened ${hit.second}."
    }

    private fun status(): String {
        val bm = ctx.getSystemService(BatteryManager::class.java)
        val free = StatFs(ctx.filesDir.path).availableBytes / 1_000_000_000.0
        return "${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}. " +
            "Battery ${bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)}%${if (bm.isCharging) ", charging" else ""}. " +
            "Free storage %.1f GB.".format(free)
    }
}

/** Small, safe expression evaluator for the calculate tool. */
private class Calc(src: String) {
    private val x = src.lowercase().replace(" ", "").replace("×", "*").replace("÷", "/").replace("**", "^")
    private var i = 0
    private fun eat(c: Char) = (x.getOrNull(i) == c).also { if (it) i++ }

    fun run(): Double = expr().also { if (i < x.length) error("Unexpected '${x[i]}'") }

    private fun expr(): Double {
        var v = term()
        while (true) v = when { eat('+') -> v + term(); eat('-') -> v - term(); else -> return v }
    }

    private fun term(): Double {
        var v = power()
        while (true) v = when { eat('*') -> v * power(); eat('/') -> v / power(); eat('%') -> v % power(); else -> return v }
    }

    private fun power(): Double { val b = unary(); return if (eat('^')) b.pow(power()) else b }
    private fun unary(): Double = if (eat('-')) -unary() else if (eat('+')) unary() else atom()

    private fun atom(): Double {
        if (eat('(')) return expr().also { if (!eat(')')) error("Missing )") }
        val st = i
        if (x.getOrNull(i)?.isLetter() == true) {
            while (x.getOrNull(i)?.isLetter() == true) i++
            return when (val f = x.substring(st, i)) {
                "pi" -> Math.PI
                "e" -> Math.E
                else -> unary().let { v ->
                    when (f) {
                        "sqrt" -> sqrt(v); "sin" -> sin(v); "cos" -> cos(v); "tan" -> tan(v)
                        "log" -> log10(v); "ln" -> ln(v); "abs" -> abs(v); "exp" -> exp(v)
                        else -> error("Unknown function $f")
                    }
                }
            }
        }
        while (x.getOrNull(i)?.let { it.isDigit() || it == '.' } == true) i++
        return x.substring(st, i).toDoubleOrNull() ?: error("Bad number at ${st + 1}")
    }
}
