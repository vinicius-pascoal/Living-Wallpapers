package com.livingwallpapers.app.scene

import com.livingwallpapers.app.scene.fantasy.FantasyScene
import com.livingwallpapers.app.scene.forest.ForestScene
import com.livingwallpapers.app.scene.galaxy.GalaxyScene

/** Lista de cenas do app. Para adicionar um wallpaper novo, registre-o aqui. */
object SceneRegistry {
    val scenes: List<SceneInfo> = listOf(
        SceneInfo(
            id = ForestScene.ID,
            title = "Floresta Viva",
            subtitle = "Floresta ao anoitecer com vaga-lumes",
            create = ::ForestScene,
        ),
        SceneInfo(
            id = GalaxyScene.ID,
            title = "Galáxia",
            subtitle = "Galáxia espiral procedural com nebulosas",
            create = ::GalaxyScene,
        ),
        SceneInfo(
            id = FantasyScene.ID,
            title = "Mundo Fantástico",
            subtitle = "Ilhas flutuantes entre nuvens ao pôr do sol",
            create = ::FantasyScene,
        ),
    )

    val default: SceneInfo get() = scenes.first()

    fun find(id: String?): SceneInfo = scenes.firstOrNull { it.id == id } ?: default
}
