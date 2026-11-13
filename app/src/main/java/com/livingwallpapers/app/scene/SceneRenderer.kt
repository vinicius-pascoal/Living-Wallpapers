package com.livingwallpapers.app.scene

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.service.wallpaper.WallpaperService
import com.livingwallpapers.app.settings.WallpaperSettings

/** Uma cena animada. Existem dois tipos de cena, com caminhos de desenho diferentes. */
sealed interface Scene

/**
 * Cena pixel art: desenha num bitmap pequeno (180 px de largura), que o serviço e a prévia
 * ampliam sem suavização.
 */
interface SceneRenderer : Scene {
    fun render(bitmap: Bitmap, elapsedSeconds: Float, tiltX: Float, tiltY: Float, settings: WallpaperSettings)
}

/**
 * Cena em resolução nativa: desenha direto no Canvas da tela (acelerado por hardware quando
 * disponível). Usada por cenas baseadas em ilustrações, como o Mystic Council.
 */
interface CanvasScene : Scene {
    fun draw(canvas: Canvas, width: Int, height: Int, elapsedSeconds: Float,
             tiltX: Float, tiltY: Float, settings: WallpaperSettings)
}

/** Descrição de uma cena disponível no app. */
data class SceneInfo(
    val id: String,
    val title: String,
    val subtitle: String,
    val create: (Context) -> Scene,
    /** Serviço de wallpaper dedicado a esta cena (aparece como wallpaper próprio no sistema). */
    val service: Class<out WallpaperService>,
)
