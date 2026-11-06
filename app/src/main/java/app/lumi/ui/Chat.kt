@file:OptIn(
    ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class, ExperimentalLayoutApi::class,
)

package app.lumi.ui

import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddComment
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.StickyNote2
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.lumi.Chat
import app.lumi.LOCAL
import app.lumi.Local
import app.lumi.ModelRef
import app.lumi.Msg
import app.lumi.Prefs
import app.lumi.ToolCall
import app.lumi.Vm
import app.lumi.splitThink
import kotlinx.coroutines.launch
import java.time.LocalTime

@Composable
fun ChatScreen(vm: Vm, openSettings: () -> Unit) {
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val chats by vm.chats.collectAsState()
    val currentId by vm.current.collectAsState()
    val prefs by vm.prefs.collectAsState()
    val busy by vm.busy.collectAsState()
    val chat = chats.find { it.id == currentId }
    var picker by remember { mutableStateOf(false) }

    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            Drawer(vm, chats, currentId,
                onNew = { vm.newChat(); scope.launch { drawer.close() } },
                onOpen = { vm.open(it); scope.launch { drawer.close() } },
                onSettings = { scope.launch { drawer.close() }; openSettings() })
        },
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.surface,
            topBar = {
                CenterAlignedTopAppBar(
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawer.open() } }, shapes = IconButtonDefaults.shapes()) { Icon(Icons.Rounded.Menu, "Chats") }
                    },
                    title = { ModelChip(prefs) { picker = true } },
                    actions = {
                        IconButton(onClick = vm::newChat, shapes = IconButtonDefaults.shapes()) { Icon(Icons.Rounded.AddComment, "New chat") }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface, scrolledContainerColor = MaterialTheme.colorScheme.surface),
                )
            },
            bottomBar = { Composer(vm, busy, prefs) },
        ) { pad ->
            if (chat == null || chat.msgs.isEmpty()) Empty(pad, vm, prefs, openSettings) else Messages(chat, busy, pad, vm)
        }
    }
    if (picker) ModelSheet(vm, prefs, onDismiss = { picker = false }, openSettings = { picker = false; openSettings() })
}

private fun modelName(ref: ModelRef?) = when {
    ref == null -> "Choose a model"
    ref.provider == LOCAL -> Local.label(ref.model)
    else -> ref.model
}

@Composable
private fun ModelChip(prefs: Prefs, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(Modifier.padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (prefs.model?.provider == LOCAL) { Icon(Icons.Rounded.PhoneAndroid, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(6.dp)) }
            Text(modelName(prefs.model), style = MaterialTheme.typography.titleSmallEmphasized, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 200.dp))
            Icon(Icons.Rounded.ExpandMore, null, Modifier.size(20.dp))
        }
    }
}

// ---------------- empty state ----------------

private val suggestions = listOf(
    Triple(Icons.Rounded.TravelExplore, "What's new today?", "Search the web and give me today's top 5 tech headlines with links."),
    Triple(Icons.Rounded.NotificationsActive, "Remind me", "Remind me in 30 minutes to stretch."),
    Triple(Icons.Rounded.Event, "Plan my day", "Help me plan a productive day. Ask me what I need to get done first."),
    Triple(Icons.Rounded.Code, "Write code", "Write a clean Kotlin function that checks if a string is a palindrome, with tests."),
    Triple(Icons.Rounded.Lightbulb, "Explain simply", "Explain how large language models work like I'm 12."),
    Triple(Icons.Rounded.Alarm, "Wake me up", "Set an alarm for 6:30 tomorrow labelled Gym."),
)

@Composable
private fun Empty(pad: PaddingValues, vm: Vm, prefs: Prefs, openSettings: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val hour = LocalTime.now().hour
    Column(
        Modifier.fillMaxSize().padding(pad).padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Avatar(112.dp)
        Spacer(Modifier.height(28.dp))
        Text(
            when (hour) { in 5..11 -> "Good morning"; in 12..16 -> "Good afternoon"; else -> "Good evening" },
            style = MaterialTheme.typography.displaySmallEmphasized, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            if (prefs.model == null) "Add an API key or download a model that runs right on your phone."
            else "Ask anything, or hand me a task — I can search, remind, schedule and more.",
            style = MaterialTheme.typography.bodyLarge, color = c.onSurfaceVariant, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        if (prefs.model == null) {
            FilledTonalButton(onClick = openSettings, shapes = ButtonDefaults.shapes()) { Icon(Icons.Rounded.Settings, null); Spacer(Modifier.width(8.dp)); Text("Set up a model") }
        } else FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            suggestions.forEach { (icon, label, prompt) -> Pill(label, icon) { vm.input = prompt } }
        }
    }
}

// ---------------- messages ----------------

@Composable
private fun Messages(chat: Chat, busy: Boolean, pad: PaddingValues, vm: Vm) {
    val list = rememberLazyListState()
    val msgs = chat.msgs
    val last = msgs.lastOrNull()
    val atEnd by remember { derivedStateOf { !list.canScrollForward } }
    LaunchedEffect(msgs.size) { if (msgs.isNotEmpty()) list.animateScrollToItem(msgs.lastIndex) }
    LaunchedEffect(last?.text?.length, last?.think?.length, last?.tools?.size) {
        if (busy && atEnd && msgs.isNotEmpty()) list.scrollToItem(msgs.lastIndex, Int.MAX_VALUE)
    }
    LazyColumn(
        state = list,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding() + 8.dp, bottom = pad.calculateBottomPadding() + 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        itemsIndexed(msgs, key = { _, m -> m.id }) { i, m ->
            Box(Modifier.animateItem()) {
                if (m.role == "user") UserBubble(m, vm)
                else AssistantMsg(
                    m, vm,
                    head = msgs.getOrNull(i - 1)?.role != "assistant",
                    tail = msgs.getOrNull(i + 1)?.role != "assistant",
                    live = busy && i == msgs.lastIndex,
                    lastTurn = i == msgs.lastIndex,
                )
            }
        }
    }
}

@Composable
private fun UserBubble(m: Msg, vm: Vm) {
    val c = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(start = 48.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (m.images.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { m.images.take(4).forEach { Thumb(it, 96.dp) } }
        m.files.forEach { FileChip(it.name) }
        if (m.text.isNotBlank()) Box {
            Surface(shape = RoundedCornerShape(28.dp), color = c.primaryContainer) {
                Text(
                    m.text, color = c.onPrimaryContainer, style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.combinedClickable(onClick = {}, onLongClick = { menu = true }).padding(horizontal = 18.dp, vertical = 12.dp),
                )
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, shape = RoundedCornerShape(24.dp)) {
                DropdownMenuItem(text = { Text("Copy") }, leadingIcon = { Icon(Icons.Rounded.ContentCopy, null) }, onClick = { menu = false; copy(ctx, m.text) })
                DropdownMenuItem(text = { Text("Edit & resend") }, leadingIcon = { Icon(Icons.Rounded.Edit, null) }, onClick = { menu = false; vm.startEdit(m) })
            }
        }
    }
}

@Composable
private fun FileChip(name: String) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.AttachFile, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(6.dp))
            Text(name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 220.dp))
        }
    }
}

@Composable
private fun AssistantMsg(m: Msg, vm: Vm, head: Boolean, tail: Boolean, live: Boolean, lastTurn: Boolean) {
    val c = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val (inlineThink, answer) = remember(m.text) { splitThink(m.text) }
    val think = m.think + inlineThink
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (head) Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(28.dp)
            Spacer(Modifier.width(10.dp))
            Text(if (m.model.endsWith(".litertlm")) Local.label(m.model) else m.model.ifBlank { "Lumi" }, style = MaterialTheme.typography.labelLarge, color = c.onSurfaceVariant)
        }
        if (think.isNotBlank()) ThinkCard(think, live && answer.isEmpty())
        if (m.tools.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { m.tools.forEach { ToolChip(it) } }
        when {
            answer.isNotEmpty() -> SelectionContainer { Markdown(answer) }
            live && think.isBlank() -> LoadingIndicator(Modifier.size(44.dp))
        }
        m.error?.let { err ->
            Surface(shape = RoundedCornerShape(24.dp), color = c.errorContainer) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.ErrorOutline, null, tint = c.onErrorContainer)
                    Spacer(Modifier.width(10.dp))
                    Text(err, color = c.onErrorContainer, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f, fill = false))
                }
            }
        }
        AnimatedVisibility(tail && !live && (answer.isNotBlank() || m.error != null)) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Action(Icons.Rounded.ContentCopy, "Copy") { copy(ctx, answer) }
                Action(if (vm.speaking == m.id) Icons.Rounded.Stop else Icons.AutoMirrored.Rounded.VolumeUp, "Read aloud") { vm.speak(m.copy(text = answer)) }
                Action(Icons.Rounded.Share, "Share") { share(ctx, answer) }
                if (lastTurn) Action(Icons.Rounded.Refresh, "Regenerate") { vm.regenerate() }
            }
        }
    }
}

@Composable
private fun Action(icon: ImageVector, desc: String, onClick: () -> Unit) =
    IconButton(onClick = onClick, shapes = IconButtonDefaults.shapes(), modifier = Modifier.size(40.dp)) {
        Icon(icon, desc, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }

@Composable
private fun ThinkCard(text: String, live: Boolean) {
    var open by remember { mutableStateOf(false) }
    val c = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(24.dp), color = c.surfaceContainerLow, modifier = Modifier.fillMaxWidth().animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec())) {
        Column(Modifier.clickable { open = !open }.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (live) LoadingIndicator(Modifier.size(22.dp)) else Icon(Icons.Rounded.Psychology, null, Modifier.size(20.dp), tint = c.primary)
                Spacer(Modifier.width(10.dp))
                Text(if (live) "Thinking…" else "Thought process", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = c.onSurfaceVariant)
            }
            if (open || live) Text(
                if (open) text.trim() else text.trim().takeLast(240),
                style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

private fun toolIcon(name: String) = when (name) {
    "web_search" -> Icons.Rounded.TravelExplore to "Searched the web"
    "fetch_url" -> Icons.Rounded.Language to "Read a page"
    "calculate" -> Icons.Rounded.Calculate to "Calculated"
    "memory" -> Icons.Rounded.Memory to "Memory"
    "notes" -> Icons.Rounded.StickyNote2 to "Notes"
    "set_alarm" -> Icons.Rounded.Alarm to "Alarm"
    "set_timer" -> Icons.Rounded.Timer to "Timer"
    "remind" -> Icons.Rounded.NotificationsActive to "Reminder"
    "calendar_event" -> Icons.Rounded.Event to "Calendar"
    "message" -> Icons.Rounded.Sms to "Draft message"
    "call" -> Icons.Rounded.Call to "Dialer"
    "open" -> Icons.Rounded.Apps to "Opened"
    "device_status" -> Icons.Rounded.PhoneAndroid to "Device status"
    "clipboard" -> Icons.Rounded.ContentCopy to "Copied"
    else -> Icons.Rounded.Build to name
}

@Composable
private fun ToolChip(t: ToolCall) {
    var open by remember { mutableStateOf(false) }
    val c = MaterialTheme.colorScheme
    val (icon, label) = toolIcon(t.name)
    Surface(onClick = { open = !open }, shape = RoundedCornerShape(20.dp), color = c.tertiaryContainer, modifier = Modifier.animateContentSize()) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (t.result == null) LoadingIndicator(Modifier.size(18.dp), color = c.onTertiaryContainer)
                else Icon(icon, null, Modifier.size(16.dp), tint = c.onTertiaryContainer)
                Spacer(Modifier.width(8.dp))
                Text(label, style = MaterialTheme.typography.labelLarge, color = c.onTertiaryContainer)
            }
            if (open) Text(
                "${t.args}\n\n${t.result.orEmpty().take(1200)}", style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = c.onTertiaryContainer, modifier = Modifier.padding(top = 6.dp).widthIn(max = 320.dp),
            )
        }
    }
}

// ---------------- composer ----------------

@Composable
private fun Composer(vm: Vm, busy: Boolean, prefs: Prefs) {
    val c = MaterialTheme.colorScheme
    var sheet by remember { mutableStateOf(false) }
    val pickImages = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(6)) { uris -> uris.forEach { vm.attach(it) } }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let { u -> vm.attach(u) } }
    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { vm.input = (vm.input + " " + it).trim() }
    }
    val canSend = vm.input.isNotBlank() || vm.images.isNotEmpty() || vm.files.isNotEmpty()

    Column(Modifier.fillMaxWidth().background(c.surface).navigationBarsPadding().imePadding().padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (vm.editing != null) Pill("Editing message · tap to cancel", Icons.Rounded.Edit, selected = true) { vm.editing = null; vm.input = "" }
        if (vm.images.isNotEmpty() || vm.files.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(vm.images.toList()) { p ->
                Box {
                    Thumb(p, 72.dp)
                    Box(Modifier.align(Alignment.TopEnd).padding(4.dp).size(22.dp).clip(CircleShape).background(c.inverseSurface).clickable { vm.images.remove(p) }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Close, "Remove", Modifier.size(14.dp), tint = c.inverseOnSurface)
                    }
                }
            }
            items(vm.files.toList()) { f -> Box(Modifier.clip(CircleShape).clickable { vm.files.remove(f) }) { FileChip(f.name) } }
        }
        Surface(shape = RoundedCornerShape(32.dp), color = c.surfaceContainerHigh) {
            Row(Modifier.padding(6.dp), verticalAlignment = Alignment.Bottom) {
                FilledTonalIconButton(onClick = { sheet = true }, shapes = IconButtonDefaults.shapes()) { Icon(Icons.Rounded.Add, "Attach & tools") }
                BasicTextField(
                    value = vm.input, onValueChange = { vm.input = it }, maxLines = 6,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = c.onSurface), cursorBrush = SolidColor(c.primary),
                    modifier = Modifier.weight(1f).heightIn(min = 40.dp).padding(horizontal = 10.dp, vertical = 10.dp),
                    decorationBox = { inner ->
                        Box { if (vm.input.isEmpty()) Text("Message Lumi", style = MaterialTheme.typography.bodyLarge, color = c.onSurfaceVariant); inner() }
                    },
                )
                if (!canSend && !busy) IconButton(onClick = {
                    runCatching {
                        voice.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM))
                    }.onFailure { vm.toast("No voice input on this device") }
                }, shapes = IconButtonDefaults.shapes()) { Icon(Icons.Rounded.Mic, "Voice") }
                FilledIconButton(onClick = { if (busy) vm.stop() else vm.send() }, enabled = busy || canSend, shapes = IconButtonDefaults.shapes()) {
                    AnimatedContent(busy, label = "send") { b -> Icon(if (b) Icons.Rounded.Stop else Icons.Rounded.ArrowUpward, if (b) "Stop" else "Send") }
                }
            }
        }
    }

    if (sheet) ModalBottomSheet(onDismissRequest = { sheet = false }, shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp)) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BigTile(Icons.Rounded.Image, "Photos", Modifier.weight(1f)) {
                    sheet = false; pickImages.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
                BigTile(Icons.Rounded.AttachFile, "Files & PDFs", Modifier.weight(1f)) { sheet = false; pickFile.launch(arrayOf("*/*")) }
            }
            Surface(shape = RoundedCornerShape(32.dp), color = c.surfaceContainer) {
                Column(Modifier.padding(8.dp)) {
                    SwitchItem("Agent tools", "Web, reminders, alarms, calendar, messages, notes, apps", Icons.Rounded.Build, prefs.agent) { v -> vm.edit { it.copy(agent = v) } }
                    SwitchItem("Think harder", "Reasoning mode on models that support it", Icons.Rounded.Psychology, prefs.think) { v -> vm.edit { it.copy(think = v) } }
                }
            }
        }
    }
}

@Composable
private fun BigTile(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(32.dp), color = MaterialTheme.colorScheme.secondaryContainer, modifier = modifier) {
        Column(Modifier.padding(vertical = 22.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
            Spacer(Modifier.height(8.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}

// ---------------- drawer ----------------

@Composable
private fun Drawer(vm: Vm, chats: List<Chat>, current: String?, onNew: () -> Unit, onOpen: (String) -> Unit, onSettings: () -> Unit) {
    val c = MaterialTheme.colorScheme
    var q by remember { mutableStateOf("") }
    var renaming by remember { mutableStateOf<Chat?>(null) }
    val shown = remember(chats, q) {
        if (q.isBlank()) chats else chats.filter { ch -> ch.title.contains(q, true) || ch.msgs.any { it.text.contains(q, true) } }
    }
    ModalDrawerSheet(drawerShape = RoundedCornerShape(topEnd = 40.dp, bottomEnd = 40.dp), drawerContainerColor = c.surfaceContainerLow) {
        Column(Modifier.padding(horizontal = 12.dp).fillMaxSize()) {
            Row(Modifier.padding(start = 12.dp, top = 20.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(36.dp); Spacer(Modifier.width(12.dp))
                Text("Lumi", style = MaterialTheme.typography.headlineSmallEmphasized)
            }
            FilledTonalButton(onClick = onNew, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Icon(Icons.Rounded.AddComment, null); Spacer(Modifier.width(10.dp)); Text("New chat", style = MaterialTheme.typography.titleSmall)
            }
            Spacer(Modifier.height(12.dp))
            Surface(shape = CircleShape, color = c.surfaceContainerHigh) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Search, null, Modifier.size(20.dp), tint = c.onSurfaceVariant)
                    Spacer(Modifier.width(10.dp))
                    BasicTextField(
                        value = q, onValueChange = { q = it }, singleLine = true, cursorBrush = SolidColor(c.primary),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = c.onSurface), modifier = Modifier.weight(1f),
                        decorationBox = { inner -> Box { if (q.isEmpty()) Text("Search chats", color = c.onSurfaceVariant); inner() } },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (shown.isEmpty()) item {
                    Text("No chats yet", color = c.onSurfaceVariant, modifier = Modifier.padding(20.dp))
                }
                items(shown, key = { it.id }) { ch -> ChatRow(ch, ch.id == current, vm, onOpen, onRename = { renaming = ch }) }
            }
            Row(
                Modifier.fillMaxWidth().padding(vertical = 12.dp).clip(CircleShape).clickable(onClick = onSettings).padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Settings, null, tint = c.onSurfaceVariant); Spacer(Modifier.width(14.dp))
                Text("Settings", style = MaterialTheme.typography.titleSmall)
            }
        }
    }
    renaming?.let { ch ->
        var title by remember(ch.id) { mutableStateOf(ch.title) }
        AlertDialog(
            onDismissRequest = { renaming = null }, shape = RoundedCornerShape(36.dp),
            title = { Text("Rename chat") },
            text = { Field("Title", ch.title) { title = it } },
            confirmButton = { TextButton(onClick = { vm.rename(ch.id, title); renaming = null }, shapes = ButtonDefaults.shapes()) { Text("Save") } },
            dismissButton = { TextButton(onClick = { renaming = null }, shapes = ButtonDefaults.shapes()) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ChatRow(ch: Chat, selected: Boolean, vm: Vm, onOpen: (String) -> Unit, onRename: () -> Unit) {
    val c = MaterialTheme.colorScheme
    var menu by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.fillMaxWidth().clip(CircleShape).background(if (selected) c.secondaryContainer else Color.Transparent)
                .combinedClickable(onClick = { onOpen(ch.id) }, onLongClick = { menu = true }).padding(start = 18.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (ch.pinned) { Icon(Icons.Rounded.PushPin, null, Modifier.size(14.dp), tint = c.primary); Spacer(Modifier.width(6.dp)) }
            Text(ch.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge,
                color = if (selected) c.onSecondaryContainer else c.onSurface, modifier = Modifier.weight(1f))
            IconButton(onClick = { menu = true }, shapes = IconButtonDefaults.shapes()) { Icon(Icons.Rounded.MoreVert, "More", tint = c.onSurfaceVariant) }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, shape = RoundedCornerShape(24.dp)) {
            DropdownMenuItem(text = { Text(if (ch.pinned) "Unpin" else "Pin") }, leadingIcon = { Icon(Icons.Rounded.PushPin, null) }, onClick = { menu = false; vm.pin(ch.id) })
            DropdownMenuItem(text = { Text("Rename") }, leadingIcon = { Icon(Icons.Rounded.Edit, null) }, onClick = { menu = false; onRename() })
            DropdownMenuItem(text = { Text("Delete") }, leadingIcon = { Icon(Icons.Rounded.Delete, null) }, onClick = { menu = false; vm.delete(ch.id) })
        }
    }
}

// ---------------- model picker ----------------

@Composable
private fun ModelSheet(vm: Vm, prefs: Prefs, onDismiss: () -> Unit, openSettings: () -> Unit) {
    val local by Local.files.collectAsState()
    ModalBottomSheet(onDismissRequest = onDismiss, shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp)) {
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            item { Text("Choose a model", style = MaterialTheme.typography.headlineSmallEmphasized, modifier = Modifier.padding(start = 8.dp, bottom = 12.dp)) }
            item { Label("On this phone · private & offline") }
            if (local.isEmpty()) item { Item("Download a model", "Gemma 4, Qwen, DeepSeek R1…", Icons.Rounded.Download, onClick = openSettings) }
            items(local, key = { "local/$it" }) { f ->
                ModelRow(Local.label(f), prefs.model == ModelRef(LOCAL, f)) { vm.setModel(ModelRef(LOCAL, f)); onDismiss() }
            }
            prefs.providers.filter { it.ready }.forEach { p ->
                item(key = "h/${p.id}") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { Label(p.name) }
                        IconButton(onClick = { vm.fetchModels(p) }, shapes = IconButtonDefaults.shapes()) { Icon(Icons.Rounded.Refresh, "Refresh models", Modifier.size(20.dp)) }
                    }
                }
                if (p.models.isEmpty()) item(key = "e/${p.id}") { Text("Tap refresh to load models", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 12.dp, bottom = 8.dp)) }
                items(p.models, key = { "${p.id}/$it" }) { m ->
                    ModelRow(m, prefs.model == ModelRef(p.id, m)) { vm.setModel(ModelRef(p.id, m)); onDismiss() }
                }
            }
            item { Spacer(Modifier.height(8.dp)); Item("Add API keys & providers", "DeepSeek, OpenCode Go, OpenAI, Gemini, OpenRouter, Ollama…", Icons.Rounded.Settings, onClick = openSettings) }
        }
    }
}

@Composable
private fun Label(text: String) = Text(
    text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
    modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 6.dp),
)

@Composable
private fun ModelRow(name: String, selected: Boolean, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clip(CircleShape).background(if (selected) c.secondaryContainer else Color.Transparent).clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = if (selected) c.onSecondaryContainer else c.onSurface)
        if (selected) Icon(Icons.Rounded.Check, null, tint = c.onSecondaryContainer)
    }
}
