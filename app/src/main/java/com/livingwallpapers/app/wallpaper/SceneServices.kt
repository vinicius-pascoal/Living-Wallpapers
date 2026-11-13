package com.livingwallpapers.app.wallpaper

import com.livingwallpapers.app.scene.fantasy.FantasyScene
import com.livingwallpapers.app.scene.forest.ForestScene
import com.livingwallpapers.app.scene.galaxy.GalaxyScene

// Um serviço por cena: o sistema trata cada um como um wallpaper diferente.
// Cada um precisa de uma entrada <service> no AndroidManifest e de um xml/wallpaper_*.xml.

class ForestWallpaperService : SceneWallpaperService() {
    override val sceneId = ForestScene.ID
}

class GalaxyWallpaperService : SceneWallpaperService() {
    override val sceneId = GalaxyScene.ID
}

class FantasyWallpaperService : SceneWallpaperService() {
    override val sceneId = FantasyScene.ID
}
