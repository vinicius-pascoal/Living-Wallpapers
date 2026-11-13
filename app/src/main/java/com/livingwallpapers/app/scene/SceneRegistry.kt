package com.livingwallpapers.app.scene

import com.livingwallpapers.app.scene.council.MysticCouncilScene
import com.livingwallpapers.app.scene.fantasy.FantasyScene
import com.livingwallpapers.app.scene.forest.ForestScene
import com.livingwallpapers.app.scene.galaxy.GalaxyScene
import com.livingwallpapers.app.wallpaper.CouncilWallpaperService
import com.livingwallpapers.app.wallpaper.FantasyWallpaperService
import com.livingwallpapers.app.wallpaper.ForestWallpaperService
import com.livingwallpapers.app.wallpaper.GalaxyWallpaperService

/** Lista de cenas do app. Para adicionar um wallpaper novo, registre-o aqui. */
object SceneRegistry {
    val scenes: List<SceneInfo> = listOf(
        SceneInfo(
            id = ForestScene.ID,
            title = "Floresta Viva",
            subtitle = "Floresta ao anoitecer com vaga-lumes",
            create = { ForestScene() },
            service = ForestWallpaperService::class.java,
        ),
        SceneInfo(
            id = GalaxyScene.ID,
            title = "Galáxia",
            subtitle = "Galáxia espiral procedural com nebulosas",
            create = { GalaxyScene() },
            service = GalaxyWallpaperService::class.java,
        ),
        SceneInfo(
            id = FantasyScene.ID,
            title = "Mundo Fantástico",
            subtitle = "Ilhas flutuantes entre nuvens ao pôr do sol",
            create = { FantasyScene() },
            service = FantasyWallpaperService::class.java,
        ),
        SceneInfo(
            id = MysticCouncilScene.ID,
            title = "Mystic Council",
            subtitle = "Conselho de cinco figuras místicas em silhueta",
            create = { MysticCouncilScene(it) },
            service = CouncilWallpaperService::class.java,
        ),
    )

    val default: SceneInfo get() = scenes.first()

    fun find(id: String?): SceneInfo = scenes.firstOrNull { it.id == id } ?: default
}
