@file:OptIn(ExperimentalMaterial3Api::class)

package com.coldai.assistant.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.coldai.assistant.R
import com.coldai.assistant.ai.Kind
import com.coldai.assistant.ai.UiError
import com.coldai.assistant.cmd.Confirm
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class Screen { CHAT, MEMORY, SETTINGS }

@Composable
fun ColdApp(vm: ColdVm, onMic: () -> Unit, onConfirm: (Confirm) -> Unit, onLang: (String) -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    ColdTheme(settings.theme) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            var screen by remember { mutableStateOf(Screen.CHAT) }
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val wide = maxWidth >= 840.dp
                val drawer = rememberDrawerState(DrawerValue.Closed)
                val scope = rememberCoroutineScope()
                if (wide) {
                    PermanentNavigationDrawer(drawerContent = {
                        PermanentDrawerSheet(
                            Modifier.width(300.dp),
                            drawerContainerColor = MaterialTheme.colorScheme.surfaceVariant
                        ) { Sidebar(vm, { screen = it }) {} }
                    }) { ChatOrOther(screen, vm, null, onMic, onConfirm, onLang) { screen = Screen.CHAT } }
                } else {
                    ModalNavigationDrawer(drawerState = drawer, drawerContent = {
                        ModalDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.surfaceVariant) {
                            Sidebar(vm, { screen = it }) { scope.launch { drawer.close() } }
                        }
                    }) { ChatOrOther(screen, vm, { scope.launch { drawer.open() } }, onMic, onConfirm, onLang) { screen = Screen.CHAT } }
                }
            }
        }
    }
}

@Composable
private fun ChatOrOther(
    screen: Screen, vm: ColdVm, onMenu: (() -> Unit)?, onMic: () -> Unit,
    onConfirm: (Confirm) -> Unit, onLang: (String) -> Unit, onBack: () -> Unit
) {
    when (screen) {
        Screen.CHAT -> ChatScreen(vm, onMenu, onMic, onConfirm)
        Screen.MEMORY -> MemoryScreen(vm, onBack)
        Screen.SETTINGS -> SettingsScreen(vm, onLang, onBack)
    }
}

@Composable
private fun Sidebar(vm: ColdVm, onNav: (Screen) -> Unit, afterPick: () -> Unit) {
    val chats by vm.chats.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxHeight().padding(16.dp)) {
        Text(
            stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(8.dp, 8.dp, 8.dp, 16.dp)
        )
        Button(
            { vm.newChat(); onNav(Screen.CHAT); afterPick() },
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(Icons.Default.Add, null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.new_chat))
        }
        Text(
            stringResource(R.string.history),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 20.dp, bottom = 8.dp, start = 8.dp)
        )
        LazyColumn(Modifier.weight(1f)) {
            items(chats, key = { it.id }) { c ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { vm.openChat(c.id); onNav(Screen.CHAT); afterPick() }
                        .padding(start = 10.dp, end = 2.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(c.title, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    IconButton({ vm.deleteChat(c.id) }, Modifier.size(36.dp)) {
                        Icon(Icons.Default.Delete, stringResource(R.string.delete), Modifier.size(18.dp))
                    }
                }
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outline)
        TextButton({ onNav(Screen.MEMORY); afterPick() }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.memory)) }
        TextButton({ onNav(Screen.SETTINGS); afterPick() }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.settings)) }
    }
}

@Composable
private fun errText(e: UiError): String = when (e.kind) {
    Kind.NO_KEY -> stringResource(R.string.err_no_key)
    Kind.NETWORK -> stringResource(R.string.err_network)
    Kind.RATE -> stringResource(R.string.err_rate)
    Kind.MODEL -> stringResource(R.string.err_model)
    Kind.KEY -> stringResource(R.string.err_key)
    Kind.EMPTY -> stringResource(R.string.err_empty)
    Kind.STT -> stringResource(R.string.err_stt, e.detail)
    Kind.PERM -> stringResource(R.string.err_perm)
    Kind.OTHER -> stringResource(R.string.err_other, e.detail)
}

@Composable
private fun ChatScreen(vm: ColdVm, onMenu: (() -> Unit)?, onMic: () -> Unit, onConfirm: (Confirm) -> Unit) {
    val ctx = LocalContext.current
    val msgs by vm.messages.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val partial by vm.partial.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val pending by vm.pending.collectAsStateWithLifecycle()
    val chats by vm.chats.collectAsStateWithLifecycle()
    val chatId by vm.chatId.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }
    val list = rememberLazyListState()
    LaunchedEffect(msgs.size, partial) { if (msgs.isNotEmpty() || partial.isNotEmpty()) list.animateScrollToItem(msgs.size) }
    val busy = status == Status.THINKING || status == Status.SPEAKING
    val title = chats.firstOrNull { it.id == chatId }?.title ?: stringResource(R.string.app_name)

    var typedId by remember { mutableStateOf(-1L) }
    var typedLen by remember { mutableIntStateOf(0) }
    val last = msgs.lastOrNull()
    LaunchedEffect(last?.id, last?.text) {
        if (last == null || last.role == "user") return@LaunchedEffect
        if (last.id == typedId && typedLen >= last.text.length) return@LaunchedEffect
        typedId = last.id
        if (last.text.length > 900) {
            typedLen = last.text.length
            return@LaunchedEffect
        }
        typedLen = 0
        while (typedLen < last.text.length) {
            delay(12L)
            typedLen += 1
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            stringResource(
                                when (status) {
                                    Status.IDLE -> R.string.status_ready
                                    Status.LISTENING -> R.string.status_listening
                                    Status.THINKING -> R.string.status_thinking
                                    Status.SPEAKING -> R.string.status_speaking
                                }
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                navigationIcon = {
                    if (onMenu != null) IconButton(onMenu) { Icon(Icons.Default.Menu, stringResource(R.string.menu)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (msgs.isEmpty() && partial.isEmpty()) {
                    EmptyHero(status, onMic) { vm.send(ctx, it) }
                } else {
                    LazyColumn(
                        state = list, modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(msgs, key = { it.id }) { m ->
                            val shown = if (m.role != "user" && m.id == typedId) m.text.take(typedLen) else m.text
                            Bubble(
                                text = shown,
                                user = m.role == "user",
                                onCopy = { copyText(ctx, m.text) },
                                onSpeak = if (m.role != "user") ({ vm.speakAgain(m.text) }) else null
                            )
                        }
                        if (partial.isNotEmpty()) item { Bubble(partial, true, {}, null) }
                        if (status == Status.THINKING) item { TypingDots() }
                    }
                }
            }
            error?.let {
                Text(errText(it), Modifier.padding(horizontal = 20.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.error)
            }
            Composer(
                input = input,
                onInput = { input = it },
                status = status,
                busy = busy,
                onMic = onMic,
                onSend = { vm.send(ctx, input); input = "" },
                onStop = { vm.cancel() }
            )
        }
    }

    pending?.let { p ->
        AlertDialog(
            onDismissRequest = { vm.cancelPending(ctx, false) },
            title = { Text(stringResource(R.string.confirm_title)) },
            text = { Text(p.prompt) },
            confirmButton = { TextButton({ onConfirm(p) }) { Text(stringResource(R.string.confirm)) } },
            dismissButton = { TextButton({ vm.cancelPending(ctx, false) }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

@Composable
private fun EmptyHero(status: Status, onMic: () -> Unit, onChip: (String) -> Unit) {
    val chips = listOf(
        stringResource(R.string.chip_time),
        stringResource(R.string.chip_date),
        stringResource(R.string.chip_help),
        stringResource(R.string.chip_yt)
    )
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.welcome),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(28.dp))
        CallColdButton(status, onMic, large = true)
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.call_cold),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(28.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(chips) { label ->
                SuggestionChip(onClick = { onChip(label) }, label = { Text(label) })
            }
        }
    }
}

@Composable
private fun CallColdButton(status: Status, onMic: () -> Unit, large: Boolean) {
    val live = status == Status.LISTENING || status == Status.SPEAKING
    val inf = rememberInfiniteTransition(label = "orb")
    val pulse by inf.animateFloat(
        initialValue = 1f,
        targetValue = if (live) 1.08f else 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "pulse"
    )
    val size = if (large) 92.dp else 56.dp
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(if (large) 120.dp else 64.dp)) {
        if (live) {
            Box(
                Modifier
                    .size(size + 18.dp)
                    .scale(pulse)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
            )
        }
        Surface(
            onClick = onMic,
            modifier = Modifier.size(size).scale(if (live) pulse else 1f),
            shape = CircleShape,
            color = if (status == Status.LISTENING) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            shadowElevation = 8.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                when (status) {
                    Status.SPEAKING, Status.LISTENING -> WaveBars(Color.White)
                    else -> Icon(
                        painterResource(R.drawable.ic_call),
                        contentDescription = stringResource(R.string.call_cold),
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(if (large) 36.dp else 24.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun WaveBars(color: Color) {
    val inf = rememberInfiniteTransition(label = "wave")
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf(320, 420, 280, 500, 360).forEachIndexed { i, dur ->
            val h by inf.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(dur, easing = LinearEasing), RepeatMode.Reverse),
                label = "b$i"
            )
            Box(
                Modifier
                    .width(3.dp)
                    .height((6 + 16 * h).dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color)
            )
        }
    }
}

@Composable
private fun TypingDots() {
    val inf = rememberInfiniteTransition(label = "dots")
    Row(Modifier.padding(start = 8.dp, top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(3) { i ->
            val a by inf.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    tween(380, delayMillis = i * 120),
                    RepeatMode.Reverse
                ),
                label = "d$i"
            )
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = a))
            )
        }
    }
}

@Composable
private fun Composer(
    input: String,
    onInput: (String) -> Unit,
    status: Status,
    busy: Boolean,
    onMic: () -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(12.dp, 8.dp, 12.dp, 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            input, onInput, Modifier.weight(1f),
            placeholder = { Text(stringResource(R.string.hint)) },
            maxLines = 4,
            shape = RoundedCornerShape(24.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            )
        )
        Spacer(Modifier.width(8.dp))
        if (busy) {
            IconButton(onStop) { Icon(Icons.Default.Close, stringResource(R.string.stop)) }
        } else {
            IconButton(onSend, enabled = input.isNotBlank()) {
                Icon(Icons.AutoMirrored.Filled.Send, stringResource(R.string.send))
            }
        }
        CallColdButton(status, onMic, large = false)
    }
}

@Composable
private fun Bubble(text: String, user: Boolean, onCopy: () -> Unit, onSpeak: (() -> Unit)?) {
    val align = if (user) Alignment.CenterEnd else Alignment.CenterStart
    Box(Modifier.fillMaxWidth(), contentAlignment = align) {
        Column(Modifier.widthIn(max = 720.dp).fillMaxWidth(0.92f), horizontalAlignment = if (user) Alignment.End else Alignment.Start) {
            Surface(
                shape = RoundedCornerShape(
                    topStart = 18.dp, topEnd = 18.dp,
                    bottomStart = if (user) 18.dp else 4.dp,
                    bottomEnd = if (user) 4.dp else 18.dp
                ),
                color = if (user) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                modifier = if (user) Modifier else Modifier
            ) {
                SelectionContainer {
                    Text(
                        text,
                        Modifier.padding(if (user) 12.dp else 4.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            if (!user && text.isNotEmpty()) {
                Row {
                    TextButton(onCopy, contentPadding = PaddingValues(8.dp, 0.dp)) {
                        Text(stringResource(R.string.copy), style = MaterialTheme.typography.labelSmall)
                    }
                    if (onSpeak != null) {
                        TextButton(onSpeak, contentPadding = PaddingValues(8.dp, 0.dp)) {
                            Text(stringResource(R.string.speak_again), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

private fun copyText(ctx: Context, text: String) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("cold", text))
}

@Composable
private fun Page(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 720.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), content = content)
        }
    }
}

@Composable
private fun MemoryScreen(vm: ColdVm, onBack: () -> Unit) {
    val mem by vm.memories.collectAsStateWithLifecycle()
    var text by remember { mutableStateOf("") }
    Page(stringResource(R.string.memory), onBack) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(text, { text = it }, Modifier.weight(1f), placeholder = { Text(stringResource(R.string.memory_new)) }, shape = RoundedCornerShape(16.dp))
            Spacer(Modifier.width(8.dp))
            Button({ vm.addMemory(text); text = "" }, enabled = text.isNotBlank(), shape = RoundedCornerShape(14.dp)) { Text(stringResource(R.string.add)) }
        }
        Spacer(Modifier.height(12.dp))
        if (mem.isEmpty()) Text(stringResource(R.string.memory_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
        mem.forEach { m ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(m.text, Modifier.weight(1f))
                IconButton({ vm.deleteMemory(m.id) }) { Icon(Icons.Default.Delete, stringResource(R.string.delete)) }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        }
        if (mem.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            OutlinedButton({ vm.clearMemories() }, shape = RoundedCornerShape(14.dp)) { Text(stringResource(R.string.clear_all)) }
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked, onChange)
    }
}

@Composable
private fun SettingsScreen(vm: ColdVm, onLang: (String) -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val s by vm.settings.collectAsStateWithLifecycle()
    val ready by vm.voice.ttsReady.collectAsStateWithLifecycle()
    var key by remember(s.apiKey) { mutableStateOf(s.apiKey) }
    var model by remember(s.model) { mutableStateOf(s.model) }
    var menu by remember { mutableStateOf(false) }
    val voices = remember(s.lang, ready) { vm.voice.voices(s.lang) }

    Page(stringResource(R.string.settings), onBack) {
        Section(stringResource(R.string.s_language))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(s.lang == "ru", { onLang("ru") }, { Text("Русский") })
            FilterChip(s.lang == "uk", { onLang("uk") }, { Text("Українська") })
        }

        Section(stringResource(R.string.s_theme))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(s.theme == "system", { vm.prefs.update { a -> a.copy(theme = "system") } }, { Text(stringResource(R.string.theme_system)) })
            FilterChip(s.theme == "light", { vm.prefs.update { a -> a.copy(theme = "light") } }, { Text(stringResource(R.string.theme_light)) })
            FilterChip(s.theme == "dark", { vm.prefs.update { a -> a.copy(theme = "dark") } }, { Text(stringResource(R.string.theme_dark)) })
        }

        Section(stringResource(R.string.s_gemini))
        OutlinedTextField(
            key, { key = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.api_key)) },
            singleLine = true, visualTransformation = PasswordVisualTransformation(), shape = RoundedCornerShape(16.dp)
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(model, { model = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.model)) }, singleLine = true, shape = RoundedCornerShape(16.dp))
        Spacer(Modifier.height(8.dp))
        Button({ vm.prefs.update { a -> a.copy(apiKey = key.trim(), model = model.trim()) } }, shape = RoundedCornerShape(14.dp)) { Text(stringResource(R.string.save)) }
        Text(stringResource(R.string.api_hint), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))

        Section(stringResource(R.string.s_voice))
        SwitchRow(stringResource(R.string.auto_speak), s.autoSpeak) { v -> vm.prefs.update { a -> a.copy(autoSpeak = v) } }
        Text(stringResource(R.string.rate))
        Slider(s.rate, { v -> vm.prefs.update { a -> a.copy(rate = v) } }, valueRange = 0.5f..2f)
        Text(stringResource(R.string.pitch))
        Slider(s.pitch, { v -> vm.prefs.update { a -> a.copy(pitch = v) } }, valueRange = 0.5f..2f)
        Box {
            OutlinedButton({ menu = true }, shape = RoundedCornerShape(14.dp)) { Text(s.voice.ifEmpty { stringResource(R.string.voice_default) }) }
            DropdownMenu(menu, { menu = false }) {
                DropdownMenuItem({ Text(stringResource(R.string.voice_default)) }, { vm.prefs.update { a -> a.copy(voice = "") }; menu = false })
                voices.forEach { v ->
                    DropdownMenuItem({ Text(v.name) }, { vm.prefs.update { a -> a.copy(voice = v.name) }; menu = false })
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Button({ vm.testVoice(ctx) }, shape = RoundedCornerShape(14.dp)) { Text(stringResource(R.string.voice_test_btn)) }

        Section(stringResource(R.string.s_memory))
        SwitchRow(stringResource(R.string.save_history), s.saveHistory) { v -> vm.prefs.update { a -> a.copy(saveHistory = v) } }
        SwitchRow(stringResource(R.string.use_memory), s.useMemory) { v -> vm.prefs.update { a -> a.copy(useMemory = v) } }
        Spacer(Modifier.height(8.dp))
        OutlinedButton({ vm.clearHistory() }, shape = RoundedCornerShape(14.dp)) { Text(stringResource(R.string.clear_history)) }

        Section(stringResource(R.string.s_wake))
        Text(stringResource(R.string.wake_note), style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.s_about), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 16.dp))
    }
}
