package com.evaone.launcher

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

/** Hosts ATMOS' existing Android widgets. Never reads private ATMOS preferences. */
internal class AtmosBridge(private val activity: ComponentActivity) {
    private val prefs = activity.getSharedPreferences("eva_atmos_bridge", Context.MODE_PRIVATE)
    private val manager = AppWidgetManager.getInstance(activity)
    private val host = AppWidgetHost(activity, 59901)
    private var pendingId = AppWidgetManager.INVALID_APPWIDGET_ID
    var widgetId by mutableIntStateOf(prefs.getInt("widget_id", AppWidgetManager.INVALID_APPWIDGET_ID))
        private set
    var notice by mutableStateOf("")
        private set

    private val bindPermission = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val id = result.data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingId) ?: pendingId
        if (result.resultCode == Activity.RESULT_OK && id > 0) saveBound(id)
        else {
            if (id > 0) runCatching { host.deleteAppWidgetId(id) }
            notice = "Koppeling geannuleerd. Probeer het gerust opnieuw."
        }
        pendingId = AppWidgetManager.INVALID_APPWIDGET_ID
    }

    fun installed(): Boolean =
        activity.packageManager.getLaunchIntentForPackage("nl.uplify.atmos") != null

    fun providers(): List<AppWidgetProviderInfo> =
        manager.installedProviders.filter { it.provider.packageName == "nl.uplify.atmos" }
            .sortedBy { rank(it) }

    fun current(): AppWidgetProviderInfo? =
        if (widgetId > 0) manager.getAppWidgetInfo(widgetId)
            ?.takeIf { it.provider.packageName == "nl.uplify.atmos" } else null

    fun bind(info: AppWidgetProviderInfo) {
        if (info.provider.packageName != "nl.uplify.atmos") return
        if (current()?.provider == info.provider) { notice = "Deze variant is al actief."; return }
        val newId = host.allocateAppWidgetId()
        pendingId = newId
        notice = ""
        try {
            if (manager.bindAppWidgetIdIfAllowed(newId, info.provider)) {
                saveBound(newId)
                pendingId = AppWidgetManager.INVALID_APPWIDGET_ID
            } else {
                bindPermission.launch(
                    Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, newId)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
                )
            }
        } catch (_: Exception) {
            runCatching { host.deleteAppWidgetId(newId) }
            pendingId = AppWidgetManager.INVALID_APPWIDGET_ID
            notice = "Widget koppelen mislukt."
        }
    }

    private fun saveBound(newId: Int) {
        if (manager.getAppWidgetInfo(newId)?.provider?.packageName != "nl.uplify.atmos") {
            runCatching { host.deleteAppWidgetId(newId) }
            notice = "ATMOS-widget niet beschikbaar."
            return
        }
        val old = widgetId
        widgetId = newId
        prefs.edit().putInt("widget_id", newId).apply()
        notice = "ATMOS is gekoppeld."
        if (old > 0 && old != newId) runCatching { host.deleteAppWidgetId(old) }
    }

    fun remove() {
        if (widgetId > 0) runCatching { host.deleteAppWidgetId(widgetId) }
        widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
        prefs.edit().remove("widget_id").apply()
        notice = ""
    }

    fun start() { runCatching { host.startListening() } }
    fun stop() { runCatching { host.stopListening() } }
    fun openApp() {
        activity.packageManager.getLaunchIntentForPackage("nl.uplify.atmos")
            ?.let { activity.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    fun view(ctx: Context, info: AppWidgetProviderInfo): AppWidgetHostView {
        val w = (ctx.resources.displayMetrics.widthPixels / ctx.resources.displayMetrics.density - 80)
            .toInt().coerceAtLeast(220)
        val h = height(info)
        return host.createView(ctx, widgetId, info).apply {
            setAppWidget(widgetId, info)
            updateAppWidgetSize(Bundle(), w, h, w, h)
        }
    }

    companion object {
        fun label(info: AppWidgetProviderInfo): String = when {
            info.provider.className.endsWith("CompactWidget") -> "Compact"
            info.provider.className.endsWith("ClockWidget") -> "Klok + weer"
            info.provider.className.endsWith("WideWidget") -> "Klok + 5 dagen"
            info.provider.className.endsWith("ForecastWidget") -> "5 dagen"
            info.provider.className.endsWith("DetailWidget") -> "Dashboard"
            else -> info.label ?: "ATMOS widget"
        }
        private fun rank(info: AppWidgetProviderInfo): Int = when (label(info)) {
            "Compact" -> 0; "Klok + weer" -> 1; "Klok + 5 dagen" -> 2
            "5 dagen" -> 3; "Dashboard" -> 4; else -> 5
        }
        fun height(info: AppWidgetProviderInfo): Int = when (label(info)) {
            "Dashboard" -> 310
            "Klok + 5 dagen", "5 dagen" -> 235
            else -> 190
        }
    }
}

@Composable
internal fun AtmosWeatherCard(p: EvaPalette, bridge: AtmosBridge) {
    var picker by remember { mutableStateOf(false) }
    val info = bridge.current()
    val available = bridge.providers()
    val installed = bridge.installed()

    DisposableEffect(bridge, bridge.widgetId) {
        if (info != null) bridge.start()
        onDispose { if (info != null) bridge.stop() }
    }

    if (picker) {
        AlertDialog(
            onDismissRequest = { picker = false },
            containerColor = p.cardEnd,
            title = { Text("ATMOS weerwidget", color = p.ink, fontWeight = FontWeight.Bold) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("Kies welke weerweergave je op EVA One wilt zien.", color = p.muted, fontSize = 12.sp)
                    Spacer(Modifier.height(10.dp))
                    available.forEach { candidate ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                bridge.bind(candidate); picker = false
                            }.padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("◈", color = p.accent, fontSize = 20.sp)
                            Spacer(Modifier.width(12.dp))
                            Text(AtmosBridge.label(candidate), modifier = Modifier.weight(1f),
                                color = p.ink, fontSize = 15.sp)
                            Text("↗", color = p.muted)
                        }
                    }
                    if (info != null) {
                        TextButton(onClick = { bridge.remove(); picker = false }) {
                            Text("Verwijder widget", color = p.muted)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { picker = false }) { Text("Sluiten", color = p.accent) }
            }
        )
    }

    val shape = RoundedCornerShape(25.dp)
    Column(
        Modifier.fillMaxWidth().background(p.card, shape)
            .border(1.dp, p.line.copy(alpha = .7f), shape).padding(15.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(36.dp).background(p.accent.copy(alpha = .15f), RoundedCornerShape(11.dp)),
                contentAlignment = Alignment.Center
            ) { Text("☁", color = p.accent, fontSize = 20.sp) }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("ATMOS", color = p.ink, fontSize = 15.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 1.8.sp)
                Text(if (info != null) "WEER / LIVE" else "WEER / KOPPELEN", color = p.muted, fontSize = 10.sp)
            }
            if (installed) {
                Text("OPEN ↗", color = p.accent, fontSize = 11.sp,
                    modifier = Modifier.clickable { bridge.openApp() }.padding(7.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
        if (info != null) {
            key(bridge.widgetId) {
                AndroidView(
                    factory = { bridge.view(it, info) },
                    modifier = Modifier.fillMaxWidth().height(AtmosBridge.height(info).dp)
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(AtmosBridge.label(info), color = p.muted, fontSize = 11.sp,
                    modifier = Modifier.weight(1f))
                Text("WISSEL WIDGET ↗", color = p.accent, fontSize = 11.sp,
                    modifier = Modifier.clickable { picker = true }.padding(8.dp))
            }
        } else {
            Text(
                if (installed) "Koppel één van de vijf ATMOS-weerwidgets aan je homescreen."
                else "Installeer onze ATMOS v0.8-app om het weer hier te zien.",
                color = p.ink, fontSize = 15.sp, lineHeight = 22.sp
            )
            Spacer(Modifier.height(8.dp))
            Text("Weer en locatie worden door ATMOS beheerd.", color = p.muted, fontSize = 12.sp)
            if (installed && available.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Button(onClick = { picker = true }, modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = p.accent,
                        contentColor = Color(0xFF07111B))) {
                    Text("ATMOS-widget toevoegen", fontWeight = FontWeight.Bold)
                }
            }
        }
        if (bridge.notice.isNotEmpty()) {
            Spacer(Modifier.height(7.dp))
            Text(bridge.notice, color = p.muted, fontSize = 11.sp)
        }
    }
}