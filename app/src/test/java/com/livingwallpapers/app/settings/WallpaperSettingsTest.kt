package com.livingwallpapers.app.settings

import com.livingwallpapers.app.scene.SceneRegistry
import org.junit.Assert.assertEquals
import org.junit.Test

class WallpaperSettingsTest {
    @Test fun `uses 30 fps by default`() {
        assertEquals(30, WallpaperSettings().effectiveFps(systemPowerSaveMode = false))
    }

    @Test fun `slows down when Android power save is active`() {
        assertEquals(15, WallpaperSettings().effectiveFps(systemPowerSaveMode = true))
    }

    @Test fun `keeps selected fps if battery savings disabled`() {
        assertEquals(60, WallpaperSettings(maxFps = 60, batterySaver = false)
            .effectiveFps(systemPowerSaveMode = true))
    }

    @Test fun `preserves requested default settings`() {
        val s = WallpaperSettings()
        assertEquals("forest", s.sceneId)
        assertEquals(45, s.animationSpeedPercent)
        assertEquals(60, s.particlesPercent)
        assertEquals(true, s.parallax)
        assertEquals(true, s.batterySaver)
    }

    @Test fun `unknown scene falls back to default`() {
        assertEquals(SceneRegistry.default.id, SceneRegistry.find("nao-existe").id)
    }
}
