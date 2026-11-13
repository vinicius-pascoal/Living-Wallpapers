package com.livingwallpapers.app

import android.app.WallpaperManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.horizontalScroll
import com.livingwallpapers.app.scene.SceneRegistry
import com.livingwallpapers.app.settings.WallpaperSettings
import com.livingwallpapers.app.settings.SettingsRepository
import com.livingwallpapers.app.ui.ScenePreviewView
import com.livingwallpapers.app.wallpaper.LivingWallpaperService
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFFB8E0B1),
                    background = Color(0xFF0D1425),
                    surface = Color(0xFF152335),
                    onSurface = Color(0xFFF0F5ED),
                ),
            ) {
                SettingsScreen(onApply = {
                    val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                        putExtra(
                            WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                            android.content.ComponentName(this@MainActivity, LivingWallpaperService::class.java),
                        )
                    }
                    // O seletor nativo decide onde a One UI permite aplicar o wallpaper.
                    try { startActivity(intent) }
                    catch (_: android.content.ActivityNotFoundException) {
                        startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
                    }
                })
            }
        }
    }
}

@Composable
private fun SettingsScreen(onApply: () -> Unit) {
    val context = LocalContext.current
    var settings by remember { mutableStateOf(SettingsRepository.load(context)) }
    fun change(next: WallpaperSettings) {
        settings = next
        SettingsRepository.save(context, next)
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(17.dp),
        ) {
            val scene = SceneRegistry.find(settings.sceneId)
            Text("LIVING WALLPAPERS", style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text("${scene.title} · ${scene.subtitle}", color = Color(0xFFBED0D0))

            if (SceneRegistry.scenes.size > 1) {
                Text("Cena", style = MaterialTheme.typography.titleMedium)
                Row(Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SceneRegistry.scenes.forEach { option ->
                        FilterChip(
                            selected = settings.sceneId == option.id,
                            onClick = { change(settings.copy(sceneId = option.id)) },
                            label = { Text(option.title) },
                        )
                    }
                }
            }

            Text("Pré-visualização", style = MaterialTheme.typography.titleMedium)

            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                AndroidView(
                    factory = { ScenePreviewView(it).apply { updateSettings(settings) } },
                    update = { it.updateSettings(settings) },
                    modifier = Modifier.width(195.dp).height(420.dp)
                        .border(2.dp, Color(0xFF46685E), RoundedCornerShape(14.dp)),
                )
            }
            Text("A inclinação do celular funciona no wallpaper aplicado, não nesta prévia.",
                style = MaterialTheme.typography.bodySmall, color = Color(0xFFADBBB9),
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())

            Text("Limite de FPS", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(15, 30, 60).forEach { fps ->
                    FilterChip(
                        selected = settings.maxFps == fps,
                        onClick = { change(settings.copy(maxFps = fps)) },
                        label = { Text("$fps FPS") },
                    )
                }
            }
            ValueSlider("Velocidade da animação", settings.animationSpeedPercent) {
                change(settings.copy(animationSpeedPercent = it))
            }
            ValueSlider("Partículas e vaga-lumes", settings.particlesPercent) {
                change(settings.copy(particlesPercent = it))
            }
            SettingsSwitch("Parallax pelos sensores", "Profundidade ao inclinar o aparelho",
                settings.parallax) { change(settings.copy(parallax = it)) }
            SettingsSwitch("Economia de bateria", "Reduz efeitos e usa 15 FPS quando o Android ativa a economia de energia",
                settings.batterySaver) { change(settings.copy(batterySaver = it)) }

            Button(onClick = onApply, modifier = Modifier.fillMaxWidth().height(54.dp)) {
                Text("Aplicar wallpaper")
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun ValueSlider(title: String, value: Int, onChange: (Int) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title)
            Text("$value%", color = MaterialTheme.colorScheme.primary)
        }
        Slider(value = value.toFloat(), onValueChange = { onChange(it.roundToInt()) },
            valueRange = 0f..100f)
    }
}

@Composable
private fun SettingsSwitch(title: String, subtitle: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFACB9B7))
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = value, onCheckedChange = onChange)
    }
}
