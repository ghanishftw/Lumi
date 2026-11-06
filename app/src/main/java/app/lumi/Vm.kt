package app.lumi

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.provider.OpenableColumns
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.IntentCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class Vm : ViewModel() {
    private val ctx get() = App.ctx
    val prefs = Store.prefs
    val chats = Store.chats
    val current = MutableStateFlow<String?>(null)
    val busy = MutableStateFlow(false)
    val toasts = MutableSharedFlow<String>(extraBufferCapacity = 8)

    var input by mutableStateOf("")
    val images = mutableStateListOf<String>()
    val files = mutableStateListOf<Att>()
    var editing by mutableStateOf<String?>(null)
    var speaking by mutableStateOf<String?>(null)

    private var job: Job? = null
    private var stopped = false
    private var tts: TextToSpeech? = null
    private val downloads = mutableMapOf<String, Job>()

    fun toast(s: String) { toasts.tryEmit(s) }
    fun edit(f: (Prefs) -> Prefs) = Store.edit(f)
    fun chat() = Store.chat(current.value)

    // ---- chats ----
    fun newChat() { if (busy.value) stop(); current.value = null; clearDraft() }
    fun open(id: String) { if (busy.value) stop(); current.value = id; clearDraft() }
    fun delete(id: String) { if (current.value == id) newChat(); Store.delete(id) }
    fun rename(id: String, title: String) { Store.chat(id)?.let { Store.put(it.copy(title = title.ifBlank { it.title })) } }
    fun pin(id: String) { Store.chat(id)?.let { Store.put(it.copy(pinned = !it.pinned)) } }
    fun deleteAll() { newChat(); chats.value.forEach { Store.delete(it.id) } }
    private fun clearDraft() { input = ""; images.clear(); files.clear(); editing = null }

    fun setModel(ref: ModelRef) = edit { it.copy(model = ref) }

    fun send() {
        val text = input.trim()
        if (busy.value || (text.isEmpty() && images.isEmpty() && files.isEmpty())) return
        val model = prefs.value.model ?: return toast("Pick a model first")
        val old = chat() ?: Chat()
        val keep = editing?.let { id -> old.msgs.takeWhile { it.id != id } } ?: old.msgs
        val title = if (keep.isEmpty()) text.lines().first().take(48).ifBlank { files.firstOrNull()?.name ?: "Image chat" } else old.title
        val c = old.copy(title = title, msgs = keep + Msg(role = "user", text = text, images = images.toList(), files = files.toList()), updated = now())
        Store.put(c); current.value = c.id; clearDraft()
        generate(c.id, model)
    }

    fun regenerate() {
        val c = chat() ?: return
        if (busy.value) return
        val i = c.msgs.indexOfLast { it.role == "user" }
        if (i < 0) return
        Store.put(c.copy(msgs = c.msgs.take(i + 1)))
        generate(c.id, prefs.value.model ?: return toast("Pick a model first"))
    }

    fun startEdit(m: Msg) { editing = m.id; input = m.text; images.clear(); images.addAll(m.images); files.clear(); files.addAll(m.files) }

    fun stop() { stopped = true; Api.call?.cancel(); Local.stop(); job?.cancel() }

    private fun generate(id: String, ref: ModelRef) {
        stopped = false
        job = viewModelScope.launch {
            busy.value = true
            try {
                if (ref.provider == LOCAL) runLocal(id, ref.model) else runRemote(id, ref)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                if (!stopped) {
                    if (Store.chat(id)?.msgs?.lastOrNull()?.role != "assistant") append(id, Msg(role = "assistant", model = ref.model))
                    patchLast(id) { it.copy(error = e.message ?: e.javaClass.simpleName) }
                }
            } finally {
                busy.value = false
                Store.chat(id)?.let { Store.put(it) }
            }
        }
    }

    private fun append(id: String, m: Msg) { Store.chat(id)?.let { Store.put(it.copy(msgs = it.msgs + m, updated = now()), save = false) } }
    private fun patchLast(id: String, f: (Msg) -> Msg) {
        Store.chat(id)?.let { c -> if (c.msgs.isNotEmpty()) Store.put(c.copy(msgs = c.msgs.dropLast(1) + f(c.msgs.last())), save = false) }
    }

    private suspend fun runRemote(id: String, ref: ModelRef) {
        val p = prefs.value.providers.find { it.id == ref.provider } ?: error("Provider was removed. Pick another model.")
        if (p.key.isBlank() && !p.local) error("Add your ${p.name} API key in Settings.")
        repeat(12) { // agent loop: model → tools → model …
            val c = Store.chat(id) ?: return
            val pr = prefs.value
            val history = buildJsonArray {
                add(buildJsonObject { put("role", "system"); put("content", system()) })
                c.msgs.filter { it.error == null }.forEach { m -> Api.toApi(m, p.id == "deepseek").forEach { add(it) } }
            }
            append(id, Msg(role = "assistant", model = ref.model))
            var calls = emptyList<ToolCall>()
            Api.stream(p, ref.model, history, if (pr.agent) Tools.openAi else null, if (pr.customTemp) pr.temp else null, pr.think, id)
                .collect { ev ->
                    when (ev) {
                        is Ev.Text -> patchLast(id) { it.copy(text = it.text + ev.s) }
                        is Ev.Think -> patchLast(id) { it.copy(think = it.think + ev.s) }
                        is Ev.Calls -> { calls = ev.calls; patchLast(id) { it.copy(tools = ev.calls) } }
                    }
                }
            if (calls.isEmpty()) return
            val done = calls.map { it.copy(result = Tools.run(it.name, it.args)) }
            patchLast(id) { it.copy(tools = done) }
        }
    }

    private suspend fun runLocal(id: String, name: String) {
        if (!Local.file(name).exists()) error("Model file missing. Download it in Settings › On-device models.")
        val c = Store.chat(id) ?: return
        val pr = prefs.value
        append(id, Msg(role = "assistant", model = name))
        val tools = if (pr.agent && Local.catalog.find { it.file == name }?.tools == true)
            Local.tools { tc -> viewModelScope.launch { patchLast(id) { it.copy(tools = it.tools + tc) } } } else emptyList()
        // keep recent history within the model's context window (≈3 chars per token, half the window for history)
        var budget = Local.tokens(name) * 3 / 2
        val history = c.msgs.dropLast(1).filter { it.error == null && it.text.isNotBlank() }.asReversed()
            .takeWhile { budget -= it.text.length; budget > 0 }.asReversed()
        Local.stream(ctx, name, pr.gpu, system(), history, c.msgs.last(), pr.temp, pr.think, tools)
            .collect { ev ->
                when (ev) {
                    is Ev.Text -> patchLast(id) { it.copy(text = it.text + ev.s) }
                    is Ev.Think -> patchLast(id) { it.copy(think = it.think + ev.s) }
                    is Ev.Calls -> Unit
                }
            }
    }

    private fun system(): String {
        val p = prefs.value
        val nowText = LocalDateTime.now().format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy, HH:mm"))
        return buildString {
            append("You are Lumi, a warm, sharp personal AI assistant living on the user's Android phone. ")
            append("Now: $nowText (${ZoneId.systemDefault()}).")
            if (p.about.isNotBlank()) append("\n\nAbout the user:\n${p.about}")
            if (p.style.isNotBlank()) append("\n\nHow to respond:\n${p.style}")
            if (p.memory.isNotEmpty()) append("\n\nWhat you remember about the user:\n" + p.memory.joinToString("\n") { "- $it" })
            if (p.agent) append(
                "\n\nYou can act for the user with tools: web search and reading pages, calculator, memory, notes, alarms, timers, " +
                    "reminders, calendar events, SMS/email drafts, calls, opening apps or links, device status, clipboard. " +
                    "Use them whenever they help, search the web for anything recent, and save lasting personal facts with the memory tool. " +
                    "After acting, confirm briefly what you did."
            )
            append("\n\nFormat replies in Markdown. Be concise unless the user wants depth.")
        }
    }

    // ---- attachments ----
    fun attach(uri: Uri) = viewModelScope.launch(Dispatchers.IO) {
        runCatching {
            val cr = ctx.contentResolver
            val mime = cr.getType(uri).orEmpty()
            when {
                mime.startsWith("image/") -> addImage(ImageDecoder.decodeBitmap(ImageDecoder.createSource(cr, uri)) { d, info, _ ->
                    val s = minOf(1f, 1536f / maxOf(info.size.width, info.size.height))
                    d.setTargetSize((info.size.width * s).toInt().coerceAtLeast(1), (info.size.height * s).toInt().coerceAtLeast(1))
                    d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                })
                mime == "application/pdf" -> cr.openFileDescriptor(uri, "r")!!.use { fd ->
                    PdfRenderer(fd).use { r ->
                        for (i in 0 until minOf(r.pageCount, 5)) r.openPage(i).use { pg ->
                            val b = Bitmap.createBitmap(pg.width * 2, pg.height * 2, Bitmap.Config.ARGB_8888)
                            b.eraseColor(android.graphics.Color.WHITE)
                            pg.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            addImage(b)
                        }
                    }
                }
                else -> {
                    val name = cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                        ?.use { if (it.moveToFirst()) it.getString(0) else null } ?: "file.txt"
                    val bytes = cr.openInputStream(uri)!!.use { it.readBytes() }
                    if (bytes.take(4096).any { it == 0.toByte() }) error("binary")
                    val att = Att(name, String(bytes).take(120_000))
                    withContext(Dispatchers.Main) { files.add(att) }
                }
            }
        }.onFailure { toast("Attach images, PDFs or text/code files") }
    }

    private suspend fun addImage(b: Bitmap) {
        val f = File(Store.imgDir, uid() + ".jpg")
        f.outputStream().use { b.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        withContext(Dispatchers.Main) { images.add(f.path) }
    }

    fun shared(i: Intent) {
        i.getStringExtra(Intent.EXTRA_TEXT)?.let { input = (input + " " + it).trim() }
        IntentCompat.getParcelableExtra(i, Intent.EXTRA_STREAM, Uri::class.java)?.let { attach(it) }
    }

    // ---- voice out ----
    fun speak(m: Msg) {
        if (speaking == m.id) { tts?.stop(); speaking = null; return }
        val say = { t: TextToSpeech ->
            t.speak(plain(m.text), TextToSpeech.QUEUE_FLUSH, null, m.id); speaking = m.id
        }
        tts?.let(say) ?: run {
            tts = TextToSpeech(ctx) { st -> if (st == TextToSpeech.SUCCESS) tts?.let(say) }.apply {
                setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) { speaking = null }
                    @Deprecated("Deprecated in Java") override fun onError(utteranceId: String?) { speaking = null }
                })
            }
        }
    }

    // ---- providers & models ----
    fun fetchModels(p: Provider) = viewModelScope.launch {
        runCatching { withContext(Dispatchers.IO) { Api.models(p) } }
            .onSuccess { ms ->
                edit { pr -> pr.copy(providers = pr.providers.map { if (it.id == p.id) it.copy(models = ms) else it }) }
                toast("${ms.size} models from ${p.name}")
            }
            .onFailure { toast("${p.name}: ${it.message}") }
    }

    fun saveProvider(p: Provider) {
        edit { pr -> pr.copy(providers = if (pr.providers.any { it.id == p.id }) pr.providers.map { if (it.id == p.id) p else it } else pr.providers + p) }
        if (p.models.isEmpty() && (p.key.isNotBlank() || p.local)) fetchModels(p)
    }

    fun removeProvider(p: Provider) = edit { pr -> pr.copy(providers = pr.providers - p, model = pr.model?.takeIf { it.provider != p.id }) }

    fun download(m: LocalModel) {
        if (downloads[m.file]?.isActive == true) return
        downloads[m.file] = viewModelScope.launch {
            runCatching { Local.download(m, prefs.value.hfToken) }
                .onSuccess { toast("${m.name} is ready offline"); if (prefs.value.model == null) setModel(ModelRef(LOCAL, m.file)) }
                .onFailure { e -> Local.progress.value = Local.progress.value - m.file; if (e !is CancellationException) toast(e.message ?: "Download failed") }
        }
    }

    fun cancelDownload(m: LocalModel) { downloads.remove(m.file)?.cancel() }

    fun deleteModel(name: String) {
        Local.file(name).delete(); File(Local.file(name).path + ".part").delete(); Local.refresh()
        if (prefs.value.model == ModelRef(LOCAL, name)) edit { it.copy(model = null) }
    }

    fun importModel(uri: Uri) = viewModelScope.launch(Dispatchers.IO) {
        runCatching {
            val cr = ctx.contentResolver
            val name = cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null } ?: "model.litertlm"
            if (!name.endsWith(".litertlm")) error("Pick a .litertlm model file")
            toast("Importing $name…")
            cr.openInputStream(uri)!!.use { i -> Local.file(name).outputStream().use { i.copyTo(it, 1 shl 16) } }
            Local.refresh(); toast("$name imported")
        }.onFailure { toast(it.message ?: "Import failed") }
    }

    fun export(uri: Uri) = viewModelScope.launch(Dispatchers.IO) {
        runCatching { ctx.contentResolver.openOutputStream(uri)!!.use { it.write(Store.exportAll().toByteArray()) } }
            .onSuccess { toast("Chats exported") }.onFailure { toast("Export failed") }
    }

    override fun onCleared() { tts?.shutdown() }
}

/** Splits inline <think>…</think> (DeepSeek-R1, Qwen 3, many Ollama models) from the visible answer. */
fun splitThink(text: String): Pair<String, String> {
    val t = text.trimStart()
    if (!t.startsWith("<think>")) return "" to text
    val end = t.indexOf("</think>")
    return if (end < 0) t.removePrefix("<think>") to "" else t.substring(7, end) to t.substring(end + 8).trimStart()
}

fun plain(md: String) = md.replace(Regex("```[\\s\\S]*?```"), " (code) ")
    .replace(Regex("\\[(.*?)]\\(.*?\\)"), "$1").replace(Regex("[*_`#>|~]"), "").trim()
