package com.livingwallpapers.app.scene

import android.graphics.Bitmap
import com.livingwallpapers.app.settings.WallpaperSettings

/**
 * Contrato de uma cena animada. Cada wallpaper desenha num bitmap pequeno (pixel art),
 * que o serviço e a prévia ampliam sem suavização.
 */
interface SceneRenderer {
    fun render(bitmap: Bitmap, elapsedSeconds: Float, tiltX: Float, tiltY: Float, settings: WallpaperSettings)
}

/** Descrição de uma cena disponível no app. */
data class SceneInfo(
    val id: String,
    val title: String,
    val subtitle: String,
    val create: () -> SceneRenderer,
)
