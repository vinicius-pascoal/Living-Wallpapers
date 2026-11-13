package com.livingwallpapers.app.scene

import com.livingwallpapers.app.scene.forest.ForestScene

/** Lista de cenas do app. Para adicionar um wallpaper novo, registre-o aqui. */
object SceneRegistry {
    val scenes: List<SceneInfo> = listOf(
        SceneInfo(
            id = ForestScene.ID,
            title = "Floresta Viva",
            subtitle = "Floresta ao anoitecer com vaga-lumes",
            create = ::ForestScene,
        ),
    )

    val default: SceneInfo get() = scenes.first()

    fun find(id: String?): SceneInfo = scenes.firstOrNull { it.id == id } ?: default
}
