package app.lumi

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.util.Base64
import java.util.concurrent.TimeUnit

/** Streaming events produced by any model (cloud or on-device). */
sealed interface Ev {
    data class Text(val s: String) : Ev
    data class Think(val s: String) : Ev
    data class Calls(val calls: List<ToolCall>) : Ev
}

fun JsonObject.str(k: String): String? = (this[k] as? JsonPrimitive)?.takeIf { it.isString }?.content

/** One client for every OpenAI-compatible provider (DeepSeek, OpenCode Go/Zen, OpenAI, Gemini, Groq, Ollama…). */
object Api {
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS).readTimeout(10, TimeUnit.MINUTES).build()
    @Volatile var call: Call? = null
    private val JSON = "application/json".toMediaType()

    private fun req(p: Provider, url: String) = Request.Builder().url(url)
        .header("User-Agent", "Lumi/1.0 (Android)")
        .apply {
            if (p.key.isNotBlank()) header("Authorization", "Bearer ${p.key}")
            if ("anthropic.com" in p.baseUrl) header("x-api-key", p.key).header("anthropic-version", "2023-06-01")
        }

    private fun fail(code: Int, body: String?): Nothing {
        val msg = runCatching {
            val o = Store.json.parseToJsonElement(body.orEmpty()).jsonObject
            (o["error"] as? JsonObject)?.str("message") ?: o.str("error") ?: o.str("message")
        }.getOrNull()
        throw IOException("HTTP $code · ${msg ?: body.orEmpty().take(300)}")
    }

    fun models(p: Provider): List<String> {
        val url = p.modelsUrl.ifBlank { p.baseUrl.trimEnd('/') + "/models" }
        http.newCall(req(p, url).build()).execute().use { r ->
            val body = r.body?.string()
            if (!r.isSuccessful) fail(r.code, body)
            val root = Store.json.parseToJsonElement(body.orEmpty())
            val arr = (root as? JsonObject)?.let { it["data"] ?: it["models"] } ?: root
            return arr.jsonArray.mapNotNull { (it as? JsonObject)?.let { o -> o.str("id") ?: o.str("name") } }
                .map { it.removePrefix("models/") }.sorted()
        }
    }

    fun stream(
        p: Provider, model: String, messages: JsonArray, tools: JsonArray?,
        temp: Float?, think: Boolean, session: String,
    ): Flow<Ev> = flow {
        val body = buildJsonObject {
            put("model", model)
            put("messages", messages)
            put("stream", true)
            if (temp != null) put("temperature", temp)
            if (tools != null) put("tools", tools)
            if (p.id == "deepseek") putJsonObject("thinking") { put("type", if (think) "enabled" else "disabled") }
            if (think) put("reasoning_effort", "high")
        }
        val r = req(p, p.baseUrl.trimEnd('/') + "/chat/completions")
            .header("x-opencode-session", session)
            .post(body.toString().toRequestBody(JSON)).build()
        val c = http.newCall(r).also { call = it }
        c.execute().use { res ->
            if (!res.isSuccessful) fail(res.code, res.body?.string())
            val src = res.body!!.source()
            val acc = sortedMapOf<Int, Array<String>>() // index -> [id, name, args]
            while (true) {
                val line = src.readUtf8Line() ?: break
                if (!line.startsWith("data:")) continue
                val d = line.substring(5).trim()
                if (d == "[DONE]") break
                val o = runCatching { Store.json.parseToJsonElement(d).jsonObject }.getOrNull() ?: continue
                o["error"]?.takeIf { it !is JsonNull }?.let { throw IOException(((it as? JsonObject)?.str("message")) ?: it.toString()) }
                val choice = o["choices"]?.jsonArray?.firstOrNull() as? JsonObject ?: continue
                val delta = choice["delta"] as? JsonObject ?: continue
                (delta.str("reasoning_content") ?: delta.str("reasoning"))?.let { if (it.isNotEmpty()) emit(Ev.Think(it)) }
                delta.str("content")?.let { if (it.isNotEmpty()) emit(Ev.Text(it)) }
                (delta["tool_calls"] as? JsonArray)?.forEach { e ->
                    val t = e as? JsonObject ?: return@forEach
                    val i = (t["index"] as? JsonPrimitive)?.intOrNull ?: acc.size
                    val a = acc.getOrPut(i) { arrayOf("", "", "") }
                    t.str("id")?.let { a[0] = it }
                    (t["function"] as? JsonObject)?.let { f ->
                        f.str("name")?.let { a[1] += it }
                        f.str("arguments")?.let { a[2] += it }
                    }
                }
            }
            if (acc.isNotEmpty()) emit(Ev.Calls(acc.values.map { ToolCall(it[0].ifBlank { uid() }, it[1], it[2].ifBlank { "{}" }) }))
        }
    }.flowOn(Dispatchers.IO)

    /** Converts a stored message into OpenAI chat format (assistant tool calls expand into tool messages). */
    fun toApi(m: Msg, deepseek: Boolean): List<JsonObject> = when (m.role) {
        "user" -> listOf(buildJsonObject {
            put("role", "user")
            val text = m.text + m.files.joinToString("") { "\n\n--- ${it.name} ---\n${it.text}" }
            if (m.images.isEmpty()) put("content", text) else putJsonArray("content") {
                addJsonObject { put("type", "text"); put("text", text) }
                m.images.forEach { path ->
                    addJsonObject { put("type", "image_url"); putJsonObject("image_url") { put("url", dataUrl(path)) } }
                }
            }
        })
        else -> if (m.text.isBlank() && m.tools.isEmpty()) emptyList() else buildList {
            add(buildJsonObject {
                put("role", "assistant")
                put("content", m.text)
                if (deepseek && m.tools.isNotEmpty() && m.think.isNotEmpty()) put("reasoning_content", m.think)
                if (m.tools.isNotEmpty()) putJsonArray("tool_calls") {
                    m.tools.forEach { t ->
                        addJsonObject {
                            put("id", t.id); put("type", "function")
                            putJsonObject("function") { put("name", t.name); put("arguments", t.args) }
                        }
                    }
                }
            })
            m.tools.forEach { t ->
                add(buildJsonObject { put("role", "tool"); put("tool_call_id", t.id); put("content", t.result ?: "Not run (stopped).") })
            }
        }
    }

    private fun dataUrl(path: String) = "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(File(path).readBytes())
}
