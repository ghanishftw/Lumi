@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package app.lumi.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import app.lumi.LOCAL
import app.lumi.Local
import app.lumi.LocalModel
import app.lumi.ModelRef
import app.lumi.Prefs
import app.lumi.Provider
import app.lumi.Vm

@Composable
fun SettingsScreen(vm: Vm, onBack: () -> Unit) {
    val prefs by vm.prefs.collectAsState()
    val progress by Local.progress.collectAsState()
    val files by Local.files.collectAsState()
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var editing by remember { mutableStateOf<Provider?>(null) }
    var confirmWipe by remember { mutableStateOf(false) }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let { u -> vm.importModel(u) } }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let { u -> vm.export(u) } }

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("Settings") },
                subtitle = { Text("Models, keys, agent & look") },
                navigationIcon = {
                    FilledTonalIconButton(onClick = onBack, shapes = IconButtonDefaults.shapes(), modifier = Modifier.padding(start = 8.dp)) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                    }
                },
                scrollBehavior = scroll,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface, scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer),
            )
        },
    ) { pad ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding() + 8.dp, bottom = pad.calculateBottomPadding() + 32.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item {
                Section("Look") {
                    FlowRow(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill("System", Icons.Rounded.BrightnessAuto, prefs.theme == 0) { vm.edit { it.copy(theme = 0) } }
                        Pill("Light", Icons.Rounded.LightMode, prefs.theme == 1) { vm.edit { it.copy(theme = 1) } }
                        Pill("Dark", Icons.Rounded.DarkMode, prefs.theme == 2) { vm.edit { it.copy(theme = 2) } }
                    }
                    SwitchItem("Dynamic color", "Match your wallpaper colors", Icons.Rounded.Palette, prefs.dynamic) { v -> vm.edit { it.copy(dynamic = v) } }
                }
            }
            item {
                Section("Cloud providers · your API keys") {
                    prefs.providers.forEach { p ->
                        Item(
                            p.name,
                            when {
                                p.key.isNotBlank() -> "Key added · ${p.models.size} models"
                                p.local -> "No key needed · ${p.baseUrl.removePrefix("http://")}"
                                else -> "Tap to add API key"
                            },
                            if (p.local) Icons.Rounded.Smartphone else Icons.Rounded.Key, onClick = { editing = p },
                        ) {
                            if (p.key.isNotBlank()) Box(Modifier.size(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                        }
                    }
                    Item("Add custom provider", "Any OpenAI-compatible endpoint", Icons.Rounded.Add, onClick = { editing = Provider(name = "", baseUrl = "https://") })
                }
            }
            item {
                Section("On-device models · run offline") {
                    Local.catalog.forEach { m -> LocalRow(m, m.file in files, progress[m.file], prefs, vm) }
                    files.filter { f -> Local.catalog.none { it.file == f } }.forEach { f ->
                        Item(Local.label(f), "Imported model", Icons.Rounded.Memory, onClick = { vm.setModel(ModelRef(LOCAL, f)) }) {
                            IconButton(onClick = { vm.deleteModel(f) }, shapes = IconButtonDefaults.shapes()) { Icon(Icons.Rounded.Delete, "Delete") }
                        }
                    }
                    Item("Import .litertlm file", "Any LiteRT-LM model from your storage", Icons.Rounded.Upload, onClick = { importer.launch(arrayOf("*/*")) })
                    SwitchItem("Use GPU", "Faster. Falls back to CPU automatically", Icons.Rounded.Tune, prefs.gpu) { v -> vm.edit { it.copy(gpu = v) } }
                    Field("Hugging Face token (optional, for gated models)", prefs.hfToken, Modifier.padding(8.dp), secret = true) { v -> vm.edit { it.copy(hfToken = v.trim()) } }
                }
            }
            item {
                Section("Personal agent") {
                    SwitchItem("Agent tools", "Search, read pages, reminders, alarms, timers, calendar, SMS/email, calls, apps, notes", Icons.Rounded.Build, prefs.agent) { v -> vm.edit { it.copy(agent = v) } }
                    SwitchItem("Think harder", "Use reasoning mode where the model supports it", Icons.Rounded.Psychology, prefs.think) { v -> vm.edit { it.copy(think = v) } }
                    SwitchItem("Custom creativity", if (prefs.customTemp) "Temperature %.1f".format(prefs.temp) else "Model default", Icons.Rounded.Tune, prefs.customTemp) { v -> vm.edit { it.copy(customTemp = v) } }
                    if (prefs.customTemp) Slider(
                        value = prefs.temp, onValueChange = { v -> vm.edit { it.copy(temp = (v * 10).toInt() / 10f) } },
                        valueRange = 0f..2f, modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
            item {
                Section("About you") {
                    Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Field("What should Lumi know about you?", prefs.about, lines = 3) { v -> vm.edit { it.copy(about = v) } }
                        Field("How should Lumi respond?", prefs.style, lines = 3) { v -> vm.edit { it.copy(style = v) } }
                    }
                }
            }
            item {
                Section("Memory · ${prefs.memory.size}") {
                    if (prefs.memory.isEmpty()) Text(
                        "Lumi saves lasting facts here when you share them (\"I'm vegetarian\", \"my sister is Ana\").",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(12.dp),
                    )
                    prefs.memory.forEach { mem ->
                        Item(mem) {
                            IconButton(onClick = { vm.edit { p -> p.copy(memory = p.memory - mem) } }, shapes = IconButtonDefaults.shapes()) { Icon(Icons.Rounded.Close, "Forget") }
                        }
                    }
                    if (prefs.notes.isNotEmpty()) Item("${prefs.notes.size} notes saved", prefs.notes.joinToString(" · ") { it.title }.take(120))
                }
            }
            item {
                Section("Data") {
                    Item("Export chats", "Save every chat as JSON", Icons.Rounded.Save, onClick = { export.launch("lumi-chats.json") })
                    Item("Delete all chats", null, Icons.Rounded.DeleteSweep, onClick = { confirmWipe = true })
                }
            }
            item {
                Text(
                    "Keys and chats stay on this phone. Cloud messages go only to the provider you pick.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
        }
    }

    editing?.let { p -> ProviderSheet(p, vm, isNew = prefs.providers.none { it.id == p.id }) { editing = null } }
    if (confirmWipe) androidx.compose.material3.AlertDialog(
        onDismissRequest = { confirmWipe = false }, shape = RoundedCornerShape(36.dp),
        title = { Text("Delete all chats?") }, text = { Text("This can't be undone.") },
        confirmButton = { TextButton(onClick = { vm.deleteAll(); confirmWipe = false }, shapes = ButtonDefaults.shapes()) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { confirmWipe = false }, shapes = ButtonDefaults.shapes()) { Text("Cancel") } },
    )
}

@Composable
private fun LocalRow(m: LocalModel, installed: Boolean, progress: Float?, prefs: Prefs, vm: Vm) {
    val c = MaterialTheme.colorScheme
    val selected = prefs.model == ModelRef(LOCAL, m.file)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(if (selected) c.secondaryContainer else c.surfaceContainer)
            .clickable(enabled = installed) { vm.setModel(ModelRef(LOCAL, m.file)) }.padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(40.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(m.name, style = MaterialTheme.typography.titleMediumEmphasized)
                Text("${m.gb} GB · ${m.ram}+ GB RAM · ${m.note}", style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
            }
            when {
                progress != null -> IconButton(onClick = { vm.cancelDownload(m) }, shapes = IconButtonDefaults.shapes()) { Icon(Icons.Rounded.Close, "Cancel") }
                installed -> IconButton(onClick = { vm.deleteModel(m.file) }, shapes = IconButtonDefaults.shapes()) { Icon(Icons.Rounded.Delete, "Delete") }
                else -> FilledTonalIconButton(onClick = { vm.download(m) }, shapes = IconButtonDefaults.shapes()) { Icon(Icons.Rounded.Download, "Download") }
            }
        }
        if (progress != null) {
            Spacer(Modifier.height(10.dp))
            LinearWavyProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            Text("${(progress * 100).toInt()}% · keep Lumi open", style = MaterialTheme.typography.labelSmall, color = c.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun ProviderSheet(p: Provider, vm: Vm, isNew: Boolean, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(p.name) }
    var url by remember { mutableStateOf(p.baseUrl) }
    var key by remember { mutableStateOf(p.key) }
    var models by remember { mutableStateOf(p.models.joinToString(", ")) }
    fun build() = p.copy(
        name = name.ifBlank { url.substringAfter("://").substringBefore('/') }, baseUrl = url.trim().trimEnd('/'), key = key.trim(),
        models = models.split(',', '\n').map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
    )
    ModalBottomSheet(onDismissRequest = onDismiss, shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp)) {
        Column(
            Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (p.local) Icons.Rounded.Smartphone else Icons.Rounded.Cloud, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text(if (isNew) "New provider" else p.name, style = MaterialTheme.typography.headlineSmallEmphasized)
            }
            Field("Name", p.name) { name = it }
            Field("Base URL (OpenAI-compatible)", p.baseUrl) { url = it }
            Field("API key", p.key, secret = true) { key = it }
            Field("Models (comma separated, or fetch)", models, lines = 3) { models = it }
            if (p.id == "opencode-go") Text("Use your OpenCode Go key from opencode.ai/auth.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (p.local) Text("Set your computer's local IP. In Ollama, set OLLAMA_HOST=0.0.0.0 so your phone can reach it.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.saveProvider(build()); onDismiss() }, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f)) { Text("Save") }
                FilledTonalButton(onClick = { val b = build(); vm.saveProvider(b); vm.fetchModels(b); onDismiss() }, shapes = ButtonDefaults.shapes()) {
                    Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Fetch models")
                }
            }
            if (!isNew) TextButton(onClick = { vm.removeProvider(p); onDismiss() }, shapes = ButtonDefaults.shapes(), modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Remove provider", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
