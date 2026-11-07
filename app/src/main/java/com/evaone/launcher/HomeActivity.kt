package com.evaone.launcher

import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Cyan = Color(0xFF65E6EF)
private val Violet = Color(0xFFAF90FF)
private data class LaunchApp(val name: String, val component: ComponentName)
private val appSort = compareBy<LaunchApp>(String.CASE_INSENSITIVE_ORDER) { it.name }

class HomeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { EvaLauncher(
            launch = { app ->
                try { startActivity(Intent.makeMainActivity(app.component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                catch (_: Exception) { }
            },
            requestHome = {
                val role = getSystemService(RoleManager::class.java)
                if (role?.isRoleAvailable(RoleManager.ROLE_HOME) == true)
                    startActivity(role.createRequestRoleIntent(RoleManager.ROLE_HOME))
                else startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
            }
        ) }
    }
}

@Composable
private fun EvaLauncher(launch: (LaunchApp) -> Unit, requestHome: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("eva", MODE_PRIVATE) }
    var light by remember { mutableStateOf(prefs.getBoolean("light", false)) }
    val fg = if (light) Color(0xFF142335) else Color(0xFFF2F7FF)
    val secondary = if (light) Color(0xFF66758A) else Color(0xFFAABBCD)
    val bg = if (light) Color(0xFFF3F7FA) else Color(0xFF080D14)
    val panel = if (light) Color(0xDDE8F0F6) else Color(0xDD172635)
    val accent = if (light) Color(0xFF087B91) else Cyan
    var page by remember { mutableStateOf("home") }
    var query by remember { mutableStateOf("") }
    var command by remember { mutableStateOf("") }
    var response by remember { mutableStateOf("Open apps met ‘open WhatsApp’ of zoek ze op naam.") }
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    var apps by remember { mutableStateOf(emptyList<LaunchApp>()) }
    var favoriteKeys by remember { mutableStateOf(prefs.getString("favorites", "")!!.split("|").filter { it.isNotBlank() }) }
    LaunchedEffect(Unit) {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        apps = pm.queryIntentActivities(intent, 0).mapNotNull { it.activityInfo?.let { a ->
            LaunchApp(it.loadLabel(pm).toString(), ComponentName(a.packageName, a.name))
        } }.distinctBy { it.component.flattenToString() }.sortedWith(appSort)
        if (favoriteKeys.isEmpty()) {
            val preferred = listOf("com.whatsapp", "com.google.android.gm", "com.android.chrome", "com.google.android.apps.maps", "com.sec.android.app.camera")
            favoriteKeys = (preferred.mapNotNull { p -> apps.firstOrNull { it.component.packageName == p }?.component?.flattenToString() } +
                apps.map { it.component.flattenToString() }).distinct().take(5)
        }
        while (true) { now = LocalDateTime.now(); delay(1000) }
    }
    fun toggleFavorite(a: LaunchApp) {
        val key = a.component.flattenToString()
        favoriteKeys = if (key in favoriteKeys) favoriteKeys - key else (favoriteKeys + key).take(5)
        prefs.edit().putString("favorites", favoriteKeys.joinToString("|")).apply()
    }
    BackHandler(page != "home") { page = "home" }
    MaterialTheme(colorScheme = if (light) lightColorScheme(primary = accent) else darkColorScheme(primary = accent)) {
        Box(Modifier.fillMaxSize().background(bg)) {
            Canvas(Modifier.fillMaxSize()) {
                val p = Path().apply {
                    moveTo(size.width * 0.55f, 0f)
                    cubicTo(size.width * 1.17f, size.height * 0.18f, size.width * 0.15f, size.height * 0.76f, size.width * 0.95f, size.height)
                }
                drawPath(p, Brush.linearGradient(listOf(accent.copy(alpha = .1f), accent.copy(alpha = .7f), Violet.copy(alpha = .55f))), style = Stroke(width = 64f))
                drawCircle(brush = Brush.radialGradient(listOf(Violet.copy(alpha = .22f), Color.Transparent)), radius = size.width * .85f, center = Offset(size.width, size.height * .6f))
            }
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp)) {
                Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("◈", color = accent, fontSize = 26.sp)
                    Spacer(Modifier.width(8.dp))
                    Text("EVA One", color = fg, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    Text(if (page == "home") "STUDIO" else "HOME", color = secondary, fontSize = 12.sp,
                        modifier = Modifier.clickable { page = if (page == "home") "studio" else "home" })
                }
                when (page) {
                    "home" -> {
                        Box(Modifier.weight(1f).fillMaxWidth().pointerInput(Unit) {
                            var total = 0f
                            detectVerticalDragGestures(onVerticalDrag = { _, amount -> total += amount },
                                onDragEnd = { if (total < -75f) { query = ""; page = "apps" }; total = 0f })
                        }) {
                            Column(Modifier.fillMaxWidth().padding(top = 76.dp)) {
                                Text(now.format(DateTimeFormatter.ofPattern("HH:mm")), color = fg, fontSize = 76.sp, fontWeight = FontWeight.Light)
                                Text(now.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.forLanguageTag("nl-NL"))), color = accent, fontSize = 17.sp)
                                Spacer(Modifier.height(42.dp))
                                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(panel).padding(22.dp)) {
                                    Text("JOUW DAG", color = secondary, fontSize = 11.sp, letterSpacing = 2.sp)
                                    Spacer(Modifier.height(10.dp))
                                    Text("Alles onder controle.", color = fg, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                                    Text("Snel. Rustig. Helemaal van jou.", color = secondary, fontSize = 13.sp)
                                }
                                Spacer(Modifier.height(20.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    MiniTile("⌕", "Alle apps", panel, fg, Modifier.weight(1f)) { query = ""; page = "apps" }
                                    MiniTile("✦", "Vraag Eva", panel, fg, Modifier.weight(1f)) { page = "eva" }
                                }
                            }
                            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("↑  Veeg omhoog voor apps", color = secondary, fontSize = 12.sp)
                                Spacer(Modifier.height(14.dp))
                                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(25.dp)).background(panel).padding(12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                                    favoriteKeys.mapNotNull { key -> apps.firstOrNull { it.component.flattenToString() == key } }.forEach { a ->
                                        AppTile(a, fg, Modifier.weight(1f), launch)
                                    }
                                }
                                Spacer(Modifier.height(12.dp))
                            }
                        }
                    }
                    "apps" -> {
                        Spacer(Modifier.height(25.dp))
                        Text("Alle apps", color = fg, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(15.dp))
                        SearchBox(query, { query = it }, panel, fg, secondary, "Zoek apps...")
                        Spacer(Modifier.height(18.dp))
                        Text("Lang indrukken = toevoegen aan dock", color = secondary, fontSize = 12.sp)
                        Spacer(Modifier.height(12.dp))
                        val filtered = remember(apps, query) { apps.filter { it.name.contains(query, ignoreCase = true) } }
                        LazyVerticalGrid(columns = GridCells.Fixed(4), modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(17.dp)) {
                            items(filtered, key = { it.component.flattenToString() }) { a ->
                                AppTile(a, fg, Modifier.fillMaxWidth(), launch, { toggleFavorite(a) })
                            }
                        }
                    }
                    "eva" -> {
                        Spacer(Modifier.height(40.dp))
                        Text("✦ EVA Space", fontSize = 32.sp, color = fg, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(12.dp))
                        Text("Jouw slimme startpunt", color = secondary)
                        Spacer(Modifier.height(40.dp))
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(panel).padding(20.dp)) {
                            Text(response, color = fg, fontSize = 16.sp)
                        }
                        Spacer(Modifier.height(20.dp))
                        SearchBox(command, { command = it }, panel, fg, secondary, "Open WhatsApp...")
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = {
                            val input = command.trim()
                            val needle = input.removePrefix("open ").removePrefix("start ").trim()
                            val match = apps.firstOrNull { it.name.equals(needle, true) } ?: apps.firstOrNull { it.name.contains(needle, true) }
                            response = if (input.startsWith("open ", true) || input.startsWith("start ", true)) {
                                if (match != null) { launch(match); "${match.name} openen..." } else "Die app kan ik niet vinden."
                            } else "Probeer: open WhatsApp. Uitgebreide AI komt in een volgende versie."
                        }, modifier = Modifier.fillMaxWidth()) { Text("Uitvoeren") }
                        Spacer(Modifier.weight(1f))
                    }
                    "studio" -> {
                        Spacer(Modifier.height(35.dp))
                        Text("EVA Studio", color = fg, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(10.dp))
                        Text("Personaliseer je launcher", color = secondary)
                        Spacer(Modifier.height(30.dp))
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(panel).padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Light Mode", color = fg, modifier = Modifier.weight(1f))
                            Switch(checked = light, onCheckedChange = { light = it; prefs.edit().putBoolean("light", it).apply() })
                        }
                        Spacer(Modifier.height(14.dp))
                        Button(onClick = requestHome, modifier = Modifier.fillMaxWidth()) { Text("Instellen als standaardlauncher") }
                        Spacer(Modifier.height(12.dp))
                        Text("Je kunt altijd terug naar One UI Home via Android Instellingen → Standaard-apps → Startapp.", color = secondary, fontSize = 13.sp)
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniTile(symbol: String, label: String, bg: Color, fg: Color, modifier: Modifier, action: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(19.dp)).background(bg).clickable(onClick = action).padding(17.dp)) {
        Text(symbol, color = Cyan, fontSize = 26.sp)
        Spacer(Modifier.height(8.dp))
        Text(label, color = fg, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SearchBox(value: String, change: (String) -> Unit, bg: Color, fg: Color, secondary: Color, placeholder: String) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(bg).padding(18.dp)) {
        if (value.isEmpty()) Text(placeholder, color = secondary)
        BasicTextField(value = value, onValueChange = change, modifier = Modifier.fillMaxWidth(), singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(color = fg, fontSize = 17.sp), cursorBrush = androidx.compose.ui.graphics.SolidColor(Cyan))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppTile(app: LaunchApp, textColor: Color, modifier: Modifier, launch: (LaunchApp) -> Unit, longPress: (() -> Unit)? = null) {
    val context = LocalContext.current
    val icon = remember(app.component) {
        try {
            val drawable = context.packageManager.getActivityIcon(app.component)
            val bmp = android.graphics.Bitmap.createBitmap(104, 104, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bmp)
            drawable.setBounds(0, 0, 104, 104)
            drawable.draw(canvas)
            bmp.asImageBitmap()
        } catch (_: Exception) { null }
    }
    Column(modifier.combinedClickable(onClick = { launch(app) }, onLongClick = longPress), horizontalAlignment = Alignment.CenterHorizontally) {
        if (icon != null) Image(bitmap = icon, contentDescription = app.name, modifier = Modifier.size(49.dp))
        else Box(Modifier.size(49.dp).clip(CircleShape).background(Cyan.copy(alpha = .2f)), contentAlignment = Alignment.Center) {
            Text(app.name.take(1), color = Cyan, fontSize = 24.sp)
        }
        Spacer(Modifier.height(5.dp))
        Text(app.name, color = textColor, fontSize = 10.sp, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
