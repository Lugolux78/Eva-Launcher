package com.evaone.launcher

import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private data class LaunchApp(val name: String, val component: ComponentName)
internal data class EvaPalette(
    val base: Color, val ink: Color, val muted: Color, val card: Color,
    val cardEnd: Color, val line: Color, val accent: Color, val violet: Color,
    val dark: Boolean
)

private fun palette(isLight: Boolean, tint: Int): EvaPalette {
    val a = when(tint) {
        1 -> Color(0xFFB69AFF)
        2 -> Color(0xFF84E8C5)
        else -> Color(0xFF65E6EF)
    }
    return if (isLight)
        EvaPalette(Color(0xFFF2F6FB), Color(0xFF0F2035), Color(0xFF67768D),
            Color(0xEFFFFFFF), Color(0xFFE4EDF9), Color(0xFFD0DBE7),
            if (tint == 0) Color(0xFF087F9B) else a, Color(0xFF9B80E6), false)
    else
        EvaPalette(Color(0xFF050913), Color(0xFFF3F7FF), Color(0xFF95A9C1),
            Color(0xE6223144), Color(0xE6111B2B), Color(0xFF3E516A),
            a, Color(0xFFAB8AF3), true)
}

class HomeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val atmos = AtmosBridge(this)
        setContent {
            EvaOne(
                launch = { app ->
                    try {
                        startActivity(Intent.makeMainActivity(app.component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (_: Exception) {}
                },
                setHome = {
                    val role = getSystemService(RoleManager::class.java)
                    if (role != null && role.isRoleAvailable(RoleManager.ROLE_HOME)) {
                        startActivity(role.createRequestRoleIntent(RoleManager.ROLE_HOME))
                    } else {
                        startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
                    }
                },
                openSettings = { startActivity(Intent(Settings.ACTION_SETTINGS)) },
                atmos = atmos
            )
        }
    }
}

@Composable
private fun EvaOne(
    launch: (LaunchApp) -> Unit,
    setHome: () -> Unit,
    openSettings: () -> Unit,
    atmos: AtmosBridge
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("eva_one_v2", Context.MODE_PRIVATE) }
    var light by remember { mutableStateOf(prefs.getBoolean("light", false)) }
    var tint by remember { mutableIntStateOf(prefs.getInt("tint", 0)) }
    var motion by remember { mutableStateOf(prefs.getBoolean("motion", true)) }
    var showQuick by remember { mutableStateOf(prefs.getBoolean("quick", true)) }
    var page by remember { mutableStateOf("home") }
    var query by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf("") }
    var aiReply by remember { mutableStateOf("Ik ben je launcher-assistent. Ik kan apps openen en je door EVA One helpen navigeren.") }
    var apps by remember { mutableStateOf(emptyList<LaunchApp>()) }
    var dockKeys by remember {
        mutableStateOf(prefs.getString("dock", "")!!.split("|").filter { it.isNotBlank() })
    }
    var time by remember { mutableStateOf(LocalDateTime.now()) }
    var battery by remember { mutableIntStateOf(-1) }
    val colors = palette(light, tint)
    val activeDock = remember(dockKeys, apps) {
        dockKeys.mapNotNull { key -> apps.firstOrNull { it.component.flattenToString() == key } }
    }

    LaunchedEffect(Unit) {
        val pm = context.packageManager
        val filter = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        apps = pm.queryIntentActivities(filter, 0)
            .mapNotNull { info ->
                info.activityInfo?.let { a ->
                    LaunchApp(info.loadLabel(pm).toString(), ComponentName(a.packageName, a.name))
                }
            }.distinctBy { it.component.flattenToString() }.sortedBy { it.name.lowercase(Locale.ROOT) }
        if (dockKeys.isEmpty()) {
            val packages = listOf("com.whatsapp", "com.google.android.gm", "com.android.chrome",
                "com.google.android.apps.maps", "com.sec.android.app.camera")
            dockKeys = (packages.mapNotNull { pkg ->
                apps.firstOrNull { it.component.packageName == pkg }?.component?.flattenToString()
            } + apps.map { it.component.flattenToString() }).distinct().take(5)
            prefs.edit().putString("dock", dockKeys.joinToString("|")).apply()
        }
        while (true) {
            time = LocalDateTime.now()
            val manager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            val v = manager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
            battery = if (v in 0..100) v else -1
            delay(30000)
        }
    }

    fun toggleDock(app: LaunchApp) {
        val id = app.component.flattenToString()
        dockKeys = if (id in dockKeys) dockKeys - id else (dockKeys + id).takeLast(5)
        prefs.edit().putString("dock", dockKeys.joinToString("|")).apply()
    }

    fun execute(value: String) {
        val clean = value.trim()
        val low = clean.lowercase(Locale.ROOT)
        val prefixes = listOf("open de app ", "open ", "start ", "launch ")
        val prefix = prefixes.firstOrNull { low.startsWith(it) }
        when {
            clean.isBlank() -> aiReply = "Typ bijvoorbeeld: open WhatsApp."
            prefix != null -> {
                val name = clean.substring(prefix.length).trim()
                val app = apps.firstOrNull { it.name.equals(name, ignoreCase = true) }
                    ?: apps.firstOrNull { it.name.contains(name, ignoreCase = true) && name.isNotBlank() }
                if (app != null) { launch(app); aiReply = "${app.name} wordt geopend." }
                else aiReply = "Ik kan die app niet vinden. Kijk bij Alle apps."
            }
            low.contains("instelling") -> openSettings()
            low.contains("alle apps") || low.contains("app overzicht") -> { query = ""; page = "apps" }
            low.contains("hoe laat") -> aiReply = "Het is ${time.format(DateTimeFormatter.ofPattern("HH:mm"))}."
            low.contains("datum") -> aiReply = time.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("nl-NL")))
            else -> aiReply = "In deze testversie kan ik apps openen en navigeren. Cloud-AI volgt later, met jouw toestemming."
        }
    }

    BackHandler(enabled = page != "home") { page = "home" }
    MaterialTheme(colorScheme = if (light) lightColorScheme(primary = colors.accent) else darkColorScheme(primary = colors.accent)) {
        Box(Modifier.fillMaxSize().background(colors.base)) {
            AuroraBackground(colors, motion)
            Column(
                Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp)
            ) {
                TopBar(colors, page,
                    onHome = { page = "home" },
                    onStudio = { page = "studio" }
                )
                Crossfade(targetState = page, animationSpec = tween(if (motion) 260 else 0), label = "evaNavigation",
                    modifier = Modifier.fillMaxSize()) { screen ->
                    when (screen) {
                        "apps" -> AppDrawer(colors, apps, dockKeys, query, { query = it },
                            onLaunch = launch, onPin = ::toggleDock, goHome = { page = "home" })
                        "eva" -> EvaSpace(colors, prompt, { prompt = it }, aiReply,
                            onExecute = { execute(prompt); prompt = "" },
                            onExample = { example -> prompt = example; execute(example) })
                        "studio" -> Studio(colors, light, { light = it; prefs.edit().putBoolean("light", it).apply() },
                            tint, { tint = it; prefs.edit().putInt("tint", it).apply() },
                            motion, { motion = it; prefs.edit().putBoolean("motion", it).apply() },
                            showQuick, { showQuick = it; prefs.edit().putBoolean("quick", it).apply() },
                            setHome = setHome)
                        else -> HomeDashboard(
                            p = colors, dateTime = time, battery = battery,
                            dock = activeDock, showQuick = showQuick,
                            onOpenApps = { query = ""; page = "apps" },
                            onEva = { page = "eva" },
                            onStudio = { page = "studio" },
                            onLaunch = launch, onPin = ::toggleDock, atmos = atmos
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AuroraBackground(p: EvaPalette, motion: Boolean) {
    val infinite = rememberInfiniteTransition(label = "ambient")
    val drift by infinite.animateFloat(0f, 1f,
        animationSpec = infiniteRepeatable(tween(15000), RepeatMode.Reverse), label = "drift")
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val d = if (motion) drift else .3f
        drawCircle(
            brush = Brush.radialGradient(
                listOf(p.violet.copy(alpha = if (p.dark) .23f else .14f), Color.Transparent),
                center = Offset(w * (.92f - .1f * d), h * .32f),
                radius = w * .82f
            ),
            center = Offset(w * (.92f - .1f * d), h * .32f), radius = w * .82f
        )
        drawCircle(
            brush = Brush.radialGradient(
                listOf(p.accent.copy(alpha = if (p.dark) .20f else .12f), Color.Transparent),
                center = Offset(w * (.1f + .09f * d), h * .82f),
                radius = w * .76f
            ),
            center = Offset(w * (.1f + .09f * d), h * .82f), radius = w * .76f
        )
        repeat(3) { i ->
            val y = h * (.34f + i * .075f) + d * 14.dp.toPx()
            val path = Path().apply {
                moveTo(-w * .20f, y)
                cubicTo(w * .28f, y - h * .23f, w * .70f, y + h * .31f, w * 1.2f, y - h * .13f)
            }
            drawPath(path,
                brush = Brush.linearGradient(
                    listOf(Color.Transparent, p.accent.copy(alpha = .06f + i * .035f),
                        p.violet.copy(alpha = .13f + i * .045f), Color.Transparent),
                    start = Offset(0f, y), end = Offset(w, y)
                ),
                style = Stroke(width = (1.8f + i * 1.4f).dp.toPx(), cap = StrokeCap.Round)
            )
        }
    }
}

@Composable
private fun TopBar(p: EvaPalette, page: String, onHome: () -> Unit, onStudio: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(64.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(33.dp).clip(RoundedCornerShape(12.dp))
            .background(Brush.linearGradient(listOf(p.accent.copy(alpha = .85f), p.violet)))
            .clickable(onClick = onHome), contentAlignment = Alignment.Center) {
            Text("✦", fontSize = 20.sp, color = Color(0xFF06101A))
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text("EVA", color = p.ink, fontSize = 17.sp, fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp, lineHeight = 18.sp)
            Text("O N E   /   B E T A", color = p.muted, fontSize = 8.sp, letterSpacing = 1.sp)
        }
        Spacer(Modifier.weight(1f))
        Text(if (page == "home") "●  LIVE" else page.uppercase(),
            fontSize = 10.sp, letterSpacing = 1.5.sp, color = p.accent)
        Spacer(Modifier.width(12.dp))
        Box(Modifier.size(35.dp).clip(CircleShape).border(1.dp, p.line.copy(alpha = .7f), CircleShape)
            .background(p.card.copy(alpha = .5f)).clickable(onClick = if (page == "studio") onHome else onStudio),
            contentAlignment = Alignment.Center) {
            Text(if (page == "studio") "×" else "⚙", color = p.ink, fontSize = 19.sp)
        }
    }
}

@Composable
private fun GlassCard(p: EvaPalette, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(25.dp)
    Column(
        modifier.clip(shape)
            .background(Brush.linearGradient(listOf(p.card, p.cardEnd)))
            .border(1.dp, p.line.copy(alpha = if (p.dark) .60f else .90f), shape)
            .padding(18.dp),
        content = content
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeDashboard(
    p: EvaPalette, dateTime: LocalDateTime, battery: Int,
    dock: List<LaunchApp>, showQuick: Boolean,
    onOpenApps: () -> Unit, onEva: () -> Unit, onStudio: () -> Unit,
    onLaunch: (LaunchApp) -> Unit, onPin: (LaunchApp) -> Unit,
    atmos: AtmosBridge
) {
    val hour = dateTime.hour
    val greeting = when (hour) { in 5..11 -> "GOEDEMORGEN"; in 12..17 -> "GOEDEMIDDAG"; else -> "GOEDENAVOND" }
    var swipe by remember { mutableFloatStateOf(0f) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { swipe = 0f },
                    onVerticalDrag = { _, drag -> swipe += drag },
                    onDragEnd = { if (swipe < -100f) onOpenApps(); swipe = 0f }
                )
            },
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(p.accent))
            Spacer(Modifier.width(8.dp))
            Text(greeting, color = p.accent, fontSize = 11.sp, letterSpacing = 3.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(8.dp))
        Text(dateTime.format(DateTimeFormatter.ofPattern("HH:mm")), color = p.ink,
            fontSize = 85.sp, fontWeight = FontWeight.Light, lineHeight = 94.sp, letterSpacing = (-3).sp)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(dateTime.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.forLanguageTag("nl-NL"))),
                color = p.muted, fontSize = 16.sp)
            Spacer(Modifier.weight(1f))
            Text(if (battery >= 0) "ϟ $battery%" else "◌ --%",
                color = p.accent, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.height(20.dp))
        AtmosWeatherCard(p, atmos)
        Spacer(Modifier.height(19.dp))
        GlassCard(p, Modifier.fillMaxWidth().clickable(onClick = onEva)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("✦", fontSize = 24.sp, color = p.accent)
                Spacer(Modifier.width(10.dp))
                Text("EVA  /  ASSIST", color = p.accent, fontSize = 11.sp, letterSpacing = 2.sp,
                    fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text("↗", fontSize = 20.sp, color = p.muted)
            }
            Spacer(Modifier.height(15.dp))
            Text("Jouw wereld. Eén gebaar.", color = p.ink,
                fontSize = 22.sp, fontWeight = FontWeight.Medium, lineHeight = 27.sp)
            Spacer(Modifier.height(6.dp))
            Text("Apps, snelle acties en Eva binnen handbereik.",
                color = p.muted, fontSize = 13.sp, lineHeight = 19.sp)
            Spacer(Modifier.height(15.dp))
            Box(
                Modifier.clip(RoundedCornerShape(12.dp))
                    .background(p.accent.copy(alpha = .13f)).padding(horizontal = 13.dp, vertical = 8.dp)
            ) {
                Text("OPEN EVA SPACE     ↗", color = p.accent, fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
            }
        }
        if (showQuick) {
            Spacer(Modifier.height(22.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("QUICK ACCESS", color = p.muted, fontSize = 10.sp, letterSpacing = 2.sp,
                    fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text("AANPASBAAR", color = p.muted.copy(alpha = .7f), fontSize = 9.sp, letterSpacing = 1.sp)
            }
            Spacer(Modifier.height(11.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                QuickTile(p, "▦", "Alle apps", Modifier.weight(1f), onOpenApps)
                QuickTile(p, "✦", "EVA Space", Modifier.weight(1f), onEva)
                QuickTile(p, "◈", "Studio", Modifier.weight(1f), onStudio)
            }
        }
        Spacer(Modifier.height(25.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
                .background(p.card.copy(alpha = .72f))
                .border(1.dp, p.line.copy(alpha = .55f), RoundedCornerShape(22.dp))
                .clickable(onClick = onOpenApps)
                .padding(horizontal = 18.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("⌕", color = p.accent, fontSize = 25.sp)
            Spacer(Modifier.width(12.dp))
            Text("Zoeken op je telefoon", color = p.muted, fontSize = 14.sp)
            Spacer(Modifier.weight(1f))
            Text("↗", color = p.muted)
        }
        Spacer(Modifier.height(17.dp))
        GlassCard(p, Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("FAVORIETEN", color = p.muted, letterSpacing = 2.sp, fontSize = 10.sp)
                Spacer(Modifier.weight(1f))
                Text("5 MAX", color = p.muted, fontSize = 10.sp)
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                if (dock.isEmpty()) {
                    Text("Kies favorieten in Alle apps", color = p.muted, fontSize = 12.sp,
                        modifier = Modifier.clickable(onClick = onOpenApps))
                }
                dock.forEach { app ->
                    AppIcon(app, p, Modifier.weight(1f), onLaunch, onPin)
                }
            }
        }
        Spacer(Modifier.height(19.dp))
        Text("↑  SWIPE VOOR ALLE APPS", color = p.muted.copy(alpha = .82f),
            fontSize = 10.sp, letterSpacing = 2.sp, modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center)
        Spacer(Modifier.height(22.dp))
    }
}

@Composable
private fun QuickTile(p: EvaPalette, glyph: String, label: String, modifier: Modifier, action: () -> Unit) {
    val shape = RoundedCornerShape(19.dp)
    Column(modifier.clip(shape).background(p.card.copy(alpha = .72f))
        .border(1.dp, p.line.copy(alpha = .5f), shape)
        .clickable(onClick = action).padding(vertical = 17.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(glyph, color = p.accent, fontSize = 25.sp)
        Spacer(Modifier.height(9.dp))
        Text(label, color = p.ink, fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
private fun AppDrawer(
    p: EvaPalette, apps: List<LaunchApp>, dock: List<String>, query: String,
    onQuery: (String) -> Unit, onLaunch: (LaunchApp) -> Unit,
    onPin: (LaunchApp) -> Unit, goHome: () -> Unit
) {
    val filtered = remember(apps, query) {
        if (query.isBlank()) apps else apps.filter { it.name.contains(query, ignoreCase = true) }
    }
    Column(Modifier.fillMaxSize()) {
        Spacer(Modifier.height(20.dp))
        Text("Apps & zoeken", color = p.ink, fontWeight = FontWeight.SemiBold, fontSize = 29.sp)
        Spacer(Modifier.height(5.dp))
        Text("${apps.size} apps  ·  alles binnen één swipe", color = p.muted, fontSize = 13.sp)
        Spacer(Modifier.height(22.dp))
        GlassCard(p, Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("⌕", color = p.accent, fontSize = 24.sp)
                Spacer(Modifier.width(12.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text("Zoek een app...", color = p.muted)
                    BasicTextField(query, onQuery, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        textStyle = TextStyle(fontSize = 16.sp, color = p.ink),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(p.accent))
                }
                if (query.isNotBlank()) Text("×", color = p.muted, fontSize = 23.sp,
                    modifier = Modifier.clickable { onQuery("") })
            }
        }
        Spacer(Modifier.height(15.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("APP LIBRARY", color = p.muted, fontSize = 10.sp,
                letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text("LANG DRUKKEN = DOCK", color = p.accent, fontSize = 9.sp, letterSpacing = 1.sp)
        }
        Spacer(Modifier.height(15.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(23.dp),
            contentPadding = PaddingValues(bottom = 30.dp)
        ) {
            items(filtered, key = { it.component.flattenToString() }) { app ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AppIcon(app, p, Modifier.fillMaxWidth(), onLaunch, onPin)
                    if (app.component.flattenToString() in dock)
                        Text("●", color = p.accent, fontSize = 10.sp)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppIcon(
    app: LaunchApp, p: EvaPalette, modifier: Modifier,
    onLaunch: (LaunchApp) -> Unit, onLongPress: (LaunchApp) -> Unit
) {
    val context = LocalContext.current
    val bitmap = remember(app.component) {
        try {
            val drawable = context.packageManager.getActivityIcon(app.component)
            val image = android.graphics.Bitmap.createBitmap(120, 120, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(image)
            drawable.setBounds(0, 0, 120, 120)
            drawable.draw(canvas)
            image.asImageBitmap()
        } catch (_: Exception) { null }
    }
    Column(modifier.combinedClickable(
        onClick = { onLaunch(app) }, onLongClick = { onLongPress(app) }),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(55.dp).clip(RoundedCornerShape(18.dp))
                .background(p.card.copy(alpha = .68f)), contentAlignment = Alignment.Center
        ) {
            if (bitmap == null) Text(app.name.take(1), color = p.accent, fontSize = 23.sp)
            else Image(bitmap = bitmap, contentDescription = app.name, modifier = Modifier.size(52.dp))
        }
        Spacer(Modifier.height(7.dp))
        Text(app.name, color = p.ink, fontSize = 10.sp, textAlign = TextAlign.Center,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun EvaSpace(
    p: EvaPalette, prompt: String, onPrompt: (String) -> Unit, reply: String,
    onExecute: () -> Unit, onExample: (String) -> Unit
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Spacer(Modifier.height(32.dp))
        Box(
            Modifier.size(76.dp).clip(RoundedCornerShape(24.dp))
                .background(Brush.linearGradient(listOf(p.accent.copy(alpha = .8f), p.violet.copy(alpha = .9f)))),
            contentAlignment = Alignment.Center
        ) { Text("✦", fontSize = 42.sp, color = Color(0xFF09101D)) }
        Spacer(Modifier.height(25.dp))
        Text("EVA Space", color = p.ink, fontSize = 33.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(7.dp))
        Text("Intelligence, without the noise.", color = p.muted, fontSize = 15.sp)
        Spacer(Modifier.height(31.dp))
        GlassCard(p, Modifier.fillMaxWidth()) {
            Text("EVA  /  OFFLINE ASSIST", color = p.accent, fontSize = 11.sp,
                letterSpacing = 1.7.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(17.dp))
            Text(reply, color = p.ink, fontSize = 17.sp, lineHeight = 26.sp)
        }
        Spacer(Modifier.height(20.dp))
        GlassCard(p, Modifier.fillMaxWidth()) {
            Box {
                if (prompt.isBlank()) Text("Vraag iets of open een app...", color = p.muted)
                BasicTextField(prompt, onPrompt, modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(color = p.ink, fontSize = 16.sp),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(p.accent),
                    maxLines = 3)
            }
        }
        Spacer(Modifier.height(14.dp))
        Button(onClick = onExecute, modifier = Modifier.fillMaxWidth().height(51.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = p.accent, contentColor = Color(0xFF07111B))) {
            Text("Uitvoeren     ↗", fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(23.dp))
        Text("SNELLE OPDRACHTEN", color = p.muted, fontSize = 10.sp, letterSpacing = 2.sp)
        Spacer(Modifier.height(12.dp))
        listOf("Alle apps", "Open WhatsApp", "Hoe laat is het?", "Open instellingen").forEach { item ->
            Row(Modifier.fillMaxWidth().padding(bottom = 9.dp)
                .clip(RoundedCornerShape(14.dp)).background(p.card.copy(alpha = .7f))
                .border(1.dp, p.line.copy(alpha = .38f), RoundedCornerShape(14.dp))
                .clickable { onExample(item) }.padding(horizontal = 15.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text("✧", color = p.accent, fontSize = 16.sp)
                Spacer(Modifier.width(11.dp))
                Text(item, color = p.ink, fontSize = 13.sp)
                Spacer(Modifier.weight(1f))
                Text("↗", color = p.muted)
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("Dit is een lokale commandofunctie, nog geen cloud-AI.",
            color = p.muted, fontSize = 12.sp)
    }
}

@Composable
private fun Studio(
    p: EvaPalette, light: Boolean, setLight: (Boolean) -> Unit,
    tint: Int, setTint: (Int) -> Unit, motion: Boolean, setMotion: (Boolean) -> Unit,
    quick: Boolean, setQuick: (Boolean) -> Unit, setHome: () -> Unit
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Spacer(Modifier.height(22.dp))
        Text("EVA Studio", color = p.ink, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(5.dp))
        Text("Maak EVA One helemaal van jou.", color = p.muted, fontSize = 14.sp)
        Spacer(Modifier.height(26.dp))
        Text("UITSTRALING", color = p.accent, fontSize = 11.sp, letterSpacing = 2.sp)
        Spacer(Modifier.height(12.dp))
        GlassCard(p, Modifier.fillMaxWidth()) {
            StudioSwitch("Light Mode", "Helder en minimalistisch", p, light, setLight)
            HorizontalDivider(color = p.line.copy(alpha = .35f), modifier = Modifier.padding(vertical = 9.dp))
            StudioSwitch("Ambient motion", "Subtiele bewegende achtergrond", p, motion, setMotion)
            HorizontalDivider(color = p.line.copy(alpha = .35f), modifier = Modifier.padding(vertical = 9.dp))
            StudioSwitch("Quick access", "Extra snelkoppelingen op Home", p, quick, setQuick)
        }
        Spacer(Modifier.height(23.dp))
        Text("KLEURACCENTEN", color = p.accent, fontSize = 11.sp, letterSpacing = 2.sp)
        Spacer(Modifier.height(13.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            listOf(Color(0xFF65E6EF), Color(0xFFB69AFF), Color(0xFF84E8C5)).forEachIndexed { i, c ->
                Box(
                    Modifier.size(54.dp).clip(RoundedCornerShape(17.dp))
                        .background(c.copy(alpha = if (tint == i) 1f else .55f))
                        .border(if (tint == i) 3.dp else 1.dp,
                            if (tint == i) p.ink else p.line, RoundedCornerShape(17.dp))
                        .clickable { setTint(i) },
                    contentAlignment = Alignment.Center
                ) {
                    if (tint == i) Text("✓", color = Color(0xFF05101B), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(27.dp))
        Text("ANDROID", color = p.accent, fontSize = 11.sp, letterSpacing = 2.sp)
        Spacer(Modifier.height(12.dp))
        Button(
            modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(17.dp),
            onClick = setHome, colors = ButtonDefaults.buttonColors(containerColor = p.accent, contentColor = Color(0xFF07111B))
        ) { Text("Instellen als standaardlauncher   ↗", fontWeight = FontWeight.SemiBold) }
        Spacer(Modifier.height(12.dp))
        Text("Terug naar Samsung? Android Instellingen → Apps → Standaard-apps → Startapp → One UI Home.",
            color = p.muted, fontSize = 12.sp, lineHeight = 19.sp)
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun StudioSwitch(title: String, subtitle: String, p: EvaPalette, on: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = p.ink, fontWeight = FontWeight.Medium, fontSize = 15.sp)
            Spacer(Modifier.height(3.dp))
            Text(subtitle, color = p.muted, fontSize = 12.sp)
        }
        Switch(checked = on, onCheckedChange = change,
            colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF07111B),
                checkedTrackColor = p.accent))
    }
}
