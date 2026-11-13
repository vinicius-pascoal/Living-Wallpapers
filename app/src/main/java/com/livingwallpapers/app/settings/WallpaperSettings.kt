package com.livingwallpapers.app.settings

/** Valores predefinidos para o Galaxy S25 Edge. Valem para todas as cenas. */
data class WallpaperSettings(
    val sceneId: String = DEFAULT_SCENE_ID,
    val maxFps: Int = 30,
    val animationSpeedPercent: Int = 45,
    val particlesPercent: Int = 60,
    val parallax: Boolean = true,
    val batterySaver: Boolean = true,
) {
    fun effectiveFps(systemPowerSaveMode: Boolean): Int =
        if (batterySaver && systemPowerSaveMode) minOf(maxFps, 15) else maxFps

    fun effectiveParticles(): Int = (12 + particlesPercent.coerceIn(0, 100) * 0.65f).toInt()
    fun timeScale(): Float = animationSpeedPercent.coerceIn(0, 100) / 100f

    companion object {
        const val DEFAULT_SCENE_ID = "forest"
    }
}
