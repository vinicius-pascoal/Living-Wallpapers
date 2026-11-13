package com.livingwallpapers.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.os.SystemClock
import android.view.View
import com.livingwallpapers.app.scene.CanvasScene
import com.livingwallpapers.app.scene.Scene
import com.livingwallpapers.app.scene.SceneRegistry
import com.livingwallpapers.app.scene.SceneRenderer
import com.livingwallpapers.app.settings.WallpaperSettings

/** Preview visual no editor: mesmo renderer do wallpaper, sem precisar aplicá-lo. */
class ScenePreviewView(context: Context) : View(context) {
    private val bitmap = Bitmap.createBitmap(180, 390, Bitmap.Config.ARGB_8888)
    private val bitmapPaint = Paint().apply { isFilterBitmap = false; isAntiAlias = false }
    private val srcRect = Rect(0, 0, 180, 390)
    private val dstRect = Rect()
    private var settings: WallpaperSettings = WallpaperSettings()
    private var sceneId: String = settings.sceneId
    private var scene: Scene = SceneRegistry.find(sceneId).create(context)
    private val started = SystemClock.elapsedRealtime()

    fun updateSettings(next: WallpaperSettings) {
        settings = next
        if (next.sceneId != sceneId) {
            sceneId = next.sceneId
            scene = SceneRegistry.find(sceneId).create(context)
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val elapsed = (SystemClock.elapsedRealtime() - started) / 1000f
        when (val s = scene) {
            is SceneRenderer -> {
                s.render(bitmap, elapsed, 0f, 0f, settings)
                dstRect.set(0, 0, width, height)
                canvas.drawBitmap(bitmap, srcRect, dstRect, bitmapPaint)
            }
            is CanvasScene -> {
                canvas.save()
                canvas.clipRect(0, 0, width, height)
                s.draw(canvas, width, height, elapsed, 0f, 0f, settings)
                canvas.restore()
            }
        }
        // A prévia é atualizada apenas quando a tela do aplicativo está visível.
        if (isAttachedToWindow && windowVisibility == VISIBLE) {
            postInvalidateDelayed(1000L / settings.maxFps)
        }
    }
}
