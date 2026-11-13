package com.livingwallpapers.app.settings

import android.content.Context
import android.content.SharedPreferences
import com.livingwallpapers.app.scene.SceneRegistry

object SettingsRepository {
    const val PREFERENCES_NAME = "living_wallpapers_settings"

    private const val KEY_SCENE = "scene_id"
    private const val KEY_FPS = "max_fps"
    private const val KEY_SPEED = "animation_speed"
    private const val KEY_PARTICLES = "particles"
    private const val KEY_PARALLAX = "parallax"
    private const val KEY_SAVER = "battery_saver"

    fun preferences(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun load(context: Context): WallpaperSettings {
        val prefs = preferences(context)
        return WallpaperSettings(
            sceneId = SceneRegistry.find(prefs.getString(KEY_SCENE, null)).id,
            maxFps = prefs.getInt(KEY_FPS, 30).let { if (it in listOf(15, 30, 60)) it else 30 },
            animationSpeedPercent = prefs.getInt(KEY_SPEED, 45).coerceIn(0, 100),
            particlesPercent = prefs.getInt(KEY_PARTICLES, 60).coerceIn(0, 100),
            parallax = prefs.getBoolean(KEY_PARALLAX, true),
            batterySaver = prefs.getBoolean(KEY_SAVER, true),
        )
    }

    fun save(context: Context, settings: WallpaperSettings) {
        preferences(context).edit()
            .putString(KEY_SCENE, settings.sceneId)
            .putInt(KEY_FPS, settings.maxFps)
            .putInt(KEY_SPEED, settings.animationSpeedPercent)
            .putInt(KEY_PARTICLES, settings.particlesPercent)
            .putBoolean(KEY_PARALLAX, settings.parallax)
            .putBoolean(KEY_SAVER, settings.batterySaver)
            .apply()
    }
}
