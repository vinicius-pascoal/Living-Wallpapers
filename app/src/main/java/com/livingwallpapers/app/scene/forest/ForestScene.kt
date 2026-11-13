package com.livingwallpapers.app.scene.forest

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import com.livingwallpapers.app.scene.SceneRenderer
import com.livingwallpapers.app.settings.WallpaperSettings
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Desenha a cena pixel art numa imagem pequena (sem antialiasing).
 * Não usa arquivos de textura nem dependências de terceiros.
 * O mesmo renderer é compartilhado pela prévia e pelo WallpaperService.
 */
class ForestScene : SceneRenderer {
    companion object { const val ID = "forest" }

    private val paint = Paint().apply { isAntiAlias = false; isFilterBitmap = false }
    private val rng = Random(2468)
    private val stars = List(58) { Point(rng.nextFloat(), rng.nextFloat() * .52f, rng.nextInt(1, 3)) }
    private val fireflies = List(80) {
        Firefly(rng.nextFloat(), .40f + rng.nextFloat() * .55f, rng.nextFloat() * 6.3f,
            .7f + rng.nextFloat() * 1.3f)
    }
    private val leaves = List(30) {
        Leaf(rng.nextFloat(), .38f + rng.nextFloat() * .53f, rng.nextFloat() * 6.3f,
            .4f + rng.nextFloat() * .9f)
    }
    private val paletteSky = intArrayOf(
        0xFF0B1228.toInt(), 0xFF10182F.toInt(), 0xFF15223C.toInt(),
        0xFF1B2B46.toInt(), 0xFF263854.toInt(), 0xFF304963.toInt(),
    )

    override fun render(bitmap: Bitmap, elapsedSeconds: Float, tiltX: Float, tiltY: Float, settings: WallpaperSettings) {
        val c = Canvas(bitmap)
        val w = bitmap.width.toFloat()
        val h = bitmap.height.toFloat()
        val t = elapsedSeconds * settings.timeScale()
        val dx = if (settings.parallax) tiltX.coerceIn(-1f, 1f) else 0f
        val dy = if (settings.parallax) tiltY.coerceIn(-1f, 1f) else 0f

        sky(c, w, h, t)
        mountains(c, w, h, dx * 1.6f, dy * .8f)
        backForest(c, w, h, dx * 3.5f, dy * 1.4f)
        fog(c, w, h, t, settings)
        middleForest(c, w, h, dx * 6f, dy * 2.5f)
        foreground(c, w, h, dx * 9f, dy * 3.5f)
        particles(c, w, h, t, settings, dx, dy)
    }

    private fun sky(c: Canvas, w: Float, h: Float, t: Float) {
        for (i in paletteSky.indices) {
            fill(c, 0f, h * i / 6f, w, h * (i + 1) / 6f + 1, paletteSky[i])
        }
        for (s in stars) {
            val pulse = .5f + .5f * sin(t * .8f + s.x * 14f)
            val alpha = (100 + 120 * pulse).toInt()
            fill(c, s.x * w, s.y * h, s.x * w + s.size,
                s.y * h + s.size, Color.argb(alpha, 204, 230, 238))
        }
        // Lua quadradinha: círculos são montados com linhas de pixels.
        val cx = (w * .73f).toInt()
        val cy = (h * .19f).toInt()
        disc(c, cx, cy, 17, 0xFF465D78.toInt())
        disc(c, cx, cy, 13, 0xFFFFE4B9.toInt())
        disc(c, cx - 4, cy - 3, 3, 0xFFE5CDAA.toInt())
        disc(c, cx + 6, cy + 4, 2, 0xFFE6CCA7.toInt())
    }

    private fun mountains(c: Canvas, w: Float, h: Float, offsetX: Float, offsetY: Float) {
        polygon(c, 0xFF273B52.toInt(),
            -40f + offsetX, h * .66f + offsetY,
            w * .12f + offsetX, h * .45f + offsetY,
            w * .30f + offsetX, h * .64f + offsetY,
            w * .55f + offsetX, h * .42f + offsetY,
            w * .85f + offsetX, h * .67f + offsetY,
            w + 50f + offsetX, h * .49f + offsetY,
            w + 50f, h * .83f,
            -40f, h * .83f,
        )
        polygon(c, 0xFF213B49.toInt(),
            -30f, h * .71f, w * .16f, h * .53f, w * .38f, h * .72f,
            w * .68f, h * .51f, w + 40f, h * .70f, w + 40f, h * .86f,
            -30f, h * .86f,
        )
    }

    private fun backForest(c: Canvas, w: Float, h: Float, offsetX: Float, offsetY: Float) {
        for (i in -3..24) {
            val x = i * 10f - 4f + offsetX
            val y = h * .77f + (i % 4) * 3f + offsetY
            val tall = 33f + ((i * 13 + 100) % 31)
            pine(c, x, y, tall, 0xFF1D4248.toInt(), 0xFF1D4248.toInt())
        }
    }

    private fun middleForest(c: Canvas, w: Float, h: Float, offsetX: Float, offsetY: Float) {
        polygon(c, 0xFF153F43.toInt(),
            -30f, h * .81f, w * .2f, h * .72f, w * .54f, h * .80f,
            w + 30f, h * .72f, w + 30f, h, -30f, h,
        )
        for (i in -2..15) {
            val x = i * 17f - 12f + offsetX
            val baseY = h * .85f + ((i * 11 + 40) % 9) + offsetY
            val tall = 52f + ((i * 23 + 201) % 41)
            pine(c, x, baseY, tall, 0xFF123C40.toInt(), 0xFF174A47.toInt())
        }
    }

    private fun foreground(c: Canvas, w: Float, h: Float, offsetX: Float, offsetY: Float) {
        // Pequena colina e trilha à direita, sem tirar espaço dos ícones Android.
        polygon(c, 0xFF113A39.toInt(),
            -40f, h * .9f, w * .25f, h * .86f, w * .52f, h * .91f,
            w + 40f, h * .86f, w + 40f, h + 30f, -40f, h + 30f,
        )
        polygon(c, 0xFF214B45.toInt(),
            w * .53f, h, w * .66f, h * .92f, w * .81f, h * .90f,
            w + 20f, h * .95f, w + 20f, h + 10f,
        )
        // Troncos laterais amplificam o efeito de profundidade.
        pine(c, -8f + offsetX, h * 1.04f + offsetY, h * .65f,
            0xFF0C242D.toInt(), 0xFF0E3438.toInt())
        pine(c, w + 7f + offsetX, h * 1.05f + offsetY, h * .70f,
            0xFF0C242D.toInt(), 0xFF0F3639.toInt())
        for (i in 0..46) {
            val x = ((i * 53 + 19) % 185) / 185f * w
            val y = h * (.90f + ((i * 17) % 10) / 100f)
            val green = if (i % 3 == 0) 0xFF38755B.toInt() else 0xFF2A6552.toInt()
            fill(c, x, y - 2f, x + 1f, y + 2f, green)
            if (i % 5 == 0) fill(c, x + 1f, y - 1f, x + 3f, y, green)
        }
        mushroom(c, w * .22f + offsetX, h * .95f + offsetY)
        mushroom(c, w * .77f + offsetX, h * .975f + offsetY)
    }

    private fun fog(c: Canvas, w: Float, h: Float, t: Float, settings: WallpaperSettings) {
        val alpha = if (settings.batterySaver) 13 else 23
        val color = Color.argb(alpha, 172, 213, 207)
        for (band in 0..3) {
            val y = h * (.71f + band * .062f)
            val shift = (sin(t * .11f + band * 1.8f) * 8f)
            for (x in -1..3) {
                fill(c, x * 70f + shift, y, x * 70f + 54f + shift, y + 3f, color)
            }
        }
    }

    private fun particles(c: Canvas, w: Float, h: Float, t: Float,
                          settings: WallpaperSettings, dx: Float, dy: Float) {
        val number = minOf(fireflies.size, settings.effectiveParticles())
        val glow = !settings.batterySaver
        for (i in 0 until number) {
            val f = fireflies[i]
            val x = (f.x * w + sin(t * .48f * f.speed + f.phase) * 9f + dx * 7f)
            val y = (f.y * h + cos(t * .36f * f.speed + f.phase) * 7f + dy * 3f)
            val alpha = (105 + 140 * (.5f + .5f * sin(t * 2.2f + f.phase))).toInt()
            if (glow) fill(c, x - 2f, y - 2f, x + 3f, y + 3f,
                Color.argb(alpha / 6, 241, 242, 126))
            fill(c, x, y, x + 2f, y + 2f, Color.argb(alpha, 243, 245, 154))
            if (i % 6 == 0) fill(c, x, y, x + 1f, y + 1f,
                Color.argb(alpha, 255, 255, 221))
        }
        val leafCount = if (settings.batterySaver) 8 else leaves.size
        for (i in 0 until leafCount) {
            val l = leaves[i]
            val x = (l.x * w + t * (1.6f + l.speed) * 3f + sin(l.phase + t) * 4f) % (w + 20f) - 10f
            val y = (l.y * h + t * (1.1f + l.speed)) % h
            fill(c, x, y, x + 2f, y + 2f, 0xFF5A8A67.toInt())
        }
    }

    private fun pine(c: Canvas, x: Float, bottom: Float, height: Float, trunk: Int, leaves: Int) {
        val top = bottom - height
        fill(c, x - 2f, bottom - height * .42f, x + 2f, bottom, trunk)
        for (tier in 0..3) {
            val triangleTop = top + tier * height * .18f
            val halfWidth = height * (.14f + tier * .018f)
            polygon(c, leaves,
                x, triangleTop, x - halfWidth, triangleTop + height * .39f,
                x + halfWidth, triangleTop + height * .39f,
            )
        }
        fill(c, x - 1f, top + height * .13f, x, top + height * .25f,
            0xFF33675A.toInt())
    }

    private fun mushroom(c: Canvas, x: Float, y: Float) {
        fill(c, x, y - 5f, x + 2f, y, 0xFFE0D2B8.toInt())
        fill(c, x - 3f, y - 8f, x + 5f, y - 5f, 0xFFBF5E65.toInt())
        fill(c, x - 1f, y - 8f, x + 1f, y - 7f, 0xFFFFD4BF.toInt())
    }

    private fun disc(c: Canvas, cx: Int, cy: Int, radius: Int, color: Int) {
        for (row in -radius..radius) {
            val ext = sqrt((radius * radius - row * row).toDouble()).toInt()
            fill(c, (cx - ext).toFloat(), (cy + row).toFloat(),
                (cx + ext + 1).toFloat(), (cy + row + 1).toFloat(), color)
        }
    }

    private fun polygon(c: Canvas, color: Int, vararg points: Float) {
        if (points.size < 6) return
        val path = Path().apply {
            moveTo(points[0], points[1])
            var i = 2
            while (i < points.size) { lineTo(points[i], points[i + 1]); i += 2 }
            close()
        }
        paint.color = color
        c.drawPath(path, paint)
    }

    private fun fill(c: Canvas, l: Float, t: Float, r: Float, b: Float, color: Int) {
        paint.color = color
        c.drawRect(l, t, r, b, paint)
    }

    private data class Point(val x: Float, val y: Float, val size: Int)
    private data class Firefly(val x: Float, val y: Float, val phase: Float, val speed: Float)
    private data class Leaf(val x: Float, val y: Float, val phase: Float, val speed: Float)
}
