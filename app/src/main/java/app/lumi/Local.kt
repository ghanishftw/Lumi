package app.lumi

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import com.google.ai.edge.litertlm.OpenApiTool
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.ToolProvider
import com.google.ai.edge.litertlm.tool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

data class LocalModel(
    val name: String, val repo: String, val file: String, val commit: String,
    val gb: Double, val ram: Int, val tokens: Int, val vision: Boolean = false, val tools: Boolean = false, val note: String = "",
)

/** On-device models via Google LiteRT-LM (GPU/CPU). Downloads straight from Hugging Face. */
object Local {
    val catalog = listOf(
        LocalModel("Gemma 4 E2B", "litert-community/gemma-4-E2B-it-litert-lm", "gemma-4-E2B-it.litertlm",
            "7fa1d78473894f7e736a21d920c3aa80f950c0db", 2.6, 8, 4000, vision = true, tools = true, note = "Best all-rounder · images · agent"),
        LocalModel("Gemma 4 E4B", "litert-community/gemma-4-E4B-it-litert-lm", "gemma-4-E4B-it.litertlm",
            "9695417f248178c63a9f318c6e0c56cb917cb837", 3.7, 12, 4000, vision = true, tools = true, note = "Smartest · flagship phones"),
        LocalModel("Qwen 2.5 1.5B", "litert-community/Qwen2.5-1.5B-Instruct", "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm",
            "5e6e7a58d03777898b31330a15d2e0a5a9107de3", 1.6, 6, 4096, note = "Fast and light"),
        LocalModel("DeepSeek R1 Distill 1.5B", "litert-community/DeepSeek-R1-Distill-Qwen-1.5B", "DeepSeek-R1-Distill-Qwen-1.5B_multi-prefill-seq_q8_ekv4096.litertlm",
            "e34bb88632342d1f9640bad579a45134eb1cf988", 1.8, 6, 4096, note = "Thinks step by step"),
        LocalModel("Gemma 3 1B", "litert-community/Gemma3-1B-IT", "gemma3-1b-it-int4.litertlm",
            "42d538a932e8d5b12e6b3b455f5572560bd60b2c", 0.6, 6, 1024, note = "Tiny · may need a Hugging Face token"),
    )

    val progress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val files = MutableStateFlow(scan())

    fun scan() = runCatching { Store.modelDir.listFiles().orEmpty().filter { it.name.endsWith(".litertlm") }.map { it.name }.sorted() }.getOrDefault(emptyList())
    fun refresh() { files.value = scan() }
    fun file(name: String) = File(Store.modelDir, name)
    fun label(name: String) = catalog.find { it.file == name }?.name ?: name.removeSuffix(".litertlm")
    fun tokens(name: String) = catalog.find { it.file == name }?.tokens ?: 2048

    suspend fun download(m: LocalModel, token: String) = withContext(Dispatchers.IO) {
        val out = file(m.file)
        val part = File(out.path + ".part")
        progress.update { it + (m.file to 0f) }
        val rq = Request.Builder().url("https://huggingface.co/${m.repo}/resolve/${m.commit}/${m.file}?download=true")
        if (token.isNotBlank()) rq.header("Authorization", "Bearer $token")
        if (part.exists()) rq.header("Range", "bytes=${part.length()}-")
        Api.http.newCall(rq.build()).execute().use { r ->
            if (r.code == 401 || r.code == 403) throw IOException("${m.name} needs a Hugging Face token with access (Settings › On-device).")
            if (!r.isSuccessful) throw IOException("Download failed: HTTP ${r.code}")
            val append = r.code == 206
            val body = r.body ?: throw IOException("Empty download")
            var done = if (append) part.length() else 0L
            val total = body.contentLength().let { if (it > 0) it + done else (m.gb * 1e9).toLong() }
            var shown = 0L
            FileOutputStream(part, append).use { os ->
                val src = body.byteStream()
                val buf = ByteArray(1 shl 16)
                while (true) {
                    ensureActive()
                    val n = src.read(buf)
                    if (n < 0) break
                    os.write(buf, 0, n); done += n
                    if (done - shown > total / 400) { shown = done; progress.update { it + (m.file to (done.toFloat() / total).coerceAtMost(1f)) } }
                }
            }
            if (!part.renameTo(out)) throw IOException("Couldn't save model")
        }
        progress.update { it - m.file }
        refresh()
    }

    // ---- inference ----
    private var engine: Engine? = null
    private var loaded = ""
    private var conv: Conversation? = null

    @Synchronized
    private fun engine(ctx: Context, name: String, gpu: Boolean): Engine {
        engine?.let { if (loaded == "$name/$gpu") return it }
        runCatching { conv?.close() }; conv = null
        runCatching { engine?.close() }; engine = null
        val vision = catalog.find { it.file == name }?.vision == true
        fun make(g: Boolean) = Engine(
            EngineConfig(
                modelPath = file(name).path,
                backend = if (g) Backend.GPU() else Backend.CPU(),
                visionBackend = if (vision) Backend.GPU() else null,
                maxNumTokens = tokens(name),
                cacheDir = ctx.cacheDir.path,
            )
        ).also { it.initialize() }
        val e = try { make(gpu) } catch (t: Throwable) { if (gpu) make(false) else throw t } // GPU unsupported → CPU
        engine = e; loaded = "$name/$gpu"
        return e
    }

    /** Tools exposed to on-device models that support function calling (Gemma 4). */
    fun tools(onRun: (ToolCall) -> Unit): List<ToolProvider> = Tools.defs.map { d ->
        val name = (d["name"] as JsonPrimitive).content
        tool(object : OpenApiTool {
            override fun getToolDescriptionJsonString() = d.toString()
            override fun execute(paramsJsonString: String): String {
                val result = runBlocking { Tools.run(name, paramsJsonString) }
                onRun(ToolCall(uid(), name, paramsJsonString, result))
                return JsonPrimitive(result).toString()
            }
        })
    }

    fun stream(
        ctx: Context, name: String, gpu: Boolean, system: String, history: List<Msg>, last: Msg,
        temp: Float, think: Boolean, tools: List<ToolProvider>,
    ): Flow<Ev> = callbackFlow {
        val e = engine(ctx, name, gpu)
        val c = e.createConversation(
            ConversationConfig(
                systemInstruction = Contents.of(system),
                initialMessages = turns(history).map { (user, text) -> if (user) Message.user(text) else Message.model(text) },
                tools = tools,
                samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = temp.toDouble()),
            )
        )
        runCatching { conv?.close() }; conv = c
        val parts = buildList<Content> {
            last.images.forEach { add(Content.ImageFile(it)) }
            add(Content.Text(last.text + last.files.joinToString("") { "\n\n--- ${it.name} ---\n${it.text}" }))
        }
        c.sendMessageAsync(
            Contents.of(parts),
            object : MessageCallback {
                override fun onMessage(message: Message) {
                    message.channels["thought"]?.let { if (it.isNotEmpty()) trySend(Ev.Think(it)) }
                    val t = message.toString()
                    if (t.isNotEmpty()) trySend(Ev.Text(t))
                }
                override fun onDone() { close() }
                override fun onError(throwable: Throwable) { close(throwable) }
            },
            mapOf("enable_thinking" to think),
        )
        awaitClose { }
    }.flowOn(Dispatchers.Default)

    fun stop() { runCatching { conv?.cancelProcess() } }

    /** Strict user/model alternation (chat templates require it): merge same-role runs, start with the user. */
    private fun turns(history: List<Msg>): List<Pair<Boolean, String>> {
        val out = mutableListOf<Pair<Boolean, String>>()
        history.filter { it.text.isNotBlank() }.forEach { m ->
            val user = m.role == "user"
            if (out.lastOrNull()?.first == user) out[out.lastIndex] = user to out.last().second + "\n\n" + m.text
            else out += user to m.text
        }
        return out.dropWhile { !it.first }.dropLastWhile { it.first }
    }
}
