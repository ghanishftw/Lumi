package app.lumi

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

const val LOCAL = "local"

fun uid() = UUID.randomUUID().toString().replace("-", "").take(16)
fun now() = System.currentTimeMillis()

@Serializable
data class Provider(
    val id: String = uid(),
    val name: String,
    val baseUrl: String,
    val key: String = "",
    val models: List<String> = emptyList(),
    val modelsUrl: String = "",
) {
    val local get() = baseUrl.startsWith("http://")
    val ready get() = key.isNotBlank() || (local && models.isNotEmpty())
}

@Serializable data class ModelRef(val provider: String, val model: String)
@Serializable data class ToolCall(val id: String, val name: String, val args: String, val result: String? = null)
@Serializable data class Att(val name: String, val text: String)
@Serializable data class Note(val title: String, val body: String, val time: Long = now())

@Serializable
data class Msg(
    val id: String = uid(),
    val role: String, // "user" | "assistant"
    val text: String = "",
    val think: String = "",
    val images: List<String> = emptyList(),
    val files: List<Att> = emptyList(),
    val tools: List<ToolCall> = emptyList(),
    val model: String = "",
    val error: String? = null,
)

@Serializable
data class Chat(
    val id: String = uid(),
    val title: String = "New chat",
    val msgs: List<Msg> = emptyList(),
    val updated: Long = now(),
    val pinned: Boolean = false,
)

@Serializable
data class Prefs(
    val providers: List<Provider> = DEFAULT_PROVIDERS,
    val model: ModelRef? = null,
    val theme: Int = 0, // 0 system · 1 light · 2 dark
    val dynamic: Boolean = true,
    val about: String = "",
    val style: String = "",
    val memory: List<String> = emptyList(),
    val notes: List<Note> = emptyList(),
    val agent: Boolean = true,
    val think: Boolean = false,
    val temp: Float = 0.7f,
    val customTemp: Boolean = false, // only send temperature when the user sets one (some reasoning models reject it)
    val hfToken: String = "",
    val gpu: Boolean = true,
)

val DEFAULT_PROVIDERS = listOf(
    Provider("deepseek", "DeepSeek", "https://api.deepseek.com", models = listOf("deepseek-flash", "deepseek-v4-pro")),
    Provider(
        "opencode-go", "OpenCode Go", "https://opencode.ai/inference/go/openai/v1",
        models = listOf("deepseek-v4.1-flash", "deepseek-v4-pro", "kimi-k3", "glm-5.3", "qwen3.8-max", "minimax-m3.1-flash", "mimo-v2.6-pro"),
        modelsUrl = "https://opencode.ai/inference/go/v1/models",
    ),
    Provider("opencode-zen", "OpenCode Zen", "https://opencode.ai/zen/v1"),
    Provider("openai", "OpenAI", "https://api.openai.com/v1"),
    Provider("anthropic", "Anthropic", "https://api.anthropic.com/v1"),
    Provider("gemini", "Google Gemini", "https://generativelanguage.googleapis.com/v1beta/openai"),
    Provider("openrouter", "OpenRouter", "https://openrouter.ai/api/v1"),
    Provider("groq", "Groq", "https://api.groq.com/openai/v1"),
    Provider("mistral", "Mistral", "https://api.mistral.ai/v1"),
    Provider("xai", "xAI", "https://api.x.ai/v1"),
    Provider("ollama", "Ollama (your PC)", "http://192.168.1.2:11434/v1"),
    Provider("lmstudio", "LM Studio (your PC)", "http://192.168.1.2:1234/v1"),
)

/** Tiny JSON-file store: one file per chat + prefs.json, all in app-private storage. */
object Store {
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }
    val prefs = MutableStateFlow(Prefs())
    val chats = MutableStateFlow<List<Chat>>(emptyList())
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private lateinit var dir: File
    private val chatDir get() = File(dir, "chats").apply { mkdirs() }
    val modelDir get() = File(dir, "models").apply { mkdirs() }
    val imgDir get() = File(dir, "img").apply { mkdirs() }

    fun init(ctx: Context) {
        dir = ctx.filesDir
        runCatching { prefs.value = json.decodeFromString<Prefs>(File(dir, "prefs.json").readText()) }
        chats.value = chatDir.listFiles().orEmpty()
            .mapNotNull { runCatching { json.decodeFromString<Chat>(it.readText()) }.getOrNull() }
            .sortedWith(order)
    }

    private val order = compareByDescending<Chat> { it.pinned }.thenByDescending { it.updated }

    fun edit(f: (Prefs) -> Prefs) {
        val p = prefs.updateAndGet(f)
        io.launch { File(dir, "prefs.json").writeText(json.encodeToString(p)) }
    }

    fun chat(id: String?) = chats.value.find { it.id == id }

    fun put(c: Chat, save: Boolean = true) {
        chats.update { l -> (l.filter { it.id != c.id } + c).sortedWith(order) }
        if (save) io.launch { File(chatDir, "${c.id}.json").writeText(json.encodeToString(c)) }
    }

    fun delete(id: String) {
        chats.update { l -> l.filter { it.id != id } }
        io.launch { File(chatDir, "$id.json").delete() }
    }

    fun exportAll(): String = json.encodeToString(chats.value)
}
