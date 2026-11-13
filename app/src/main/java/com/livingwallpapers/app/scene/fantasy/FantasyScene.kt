package com.livingwallpapers.app.scene.fantasy

import android.graphics.Bitmap
import com.livingwallpapers.app.scene.LayerCache
import com.livingwallpapers.app.scene.Noise
import com.livingwallpapers.app.scene.PixelPainter
import com.livingwallpapers.app.scene.SceneRenderer
import com.livingwallpapers.app.scene.color
import com.livingwallpapers.app.scene.mixColor
import com.livingwallpapers.app.scene.withAlpha
import com.livingwallpapers.app.settings.WallpaperSettings
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * Mundo fantástico ao pôr do sol: ilhas flutuantes que derivam e balançam, cachoeiras caindo no
 * mar de nuvens, nuvens em camadas, castelo nas montanhas, pássaros e esporos luminosos.
 * Ilhas e nuvens são pré-desenhadas em bitmaps; por quadro só se movem e animam detalhes.
 */
class FantasyScene : SceneRenderer {
    companion object { const val ID = "fantasy" }

    private val p = PixelPainter()
    private val skyCache = LayerCache()
    private val rng = Random(9021)

    private val farIslands = listOf(
        island(22, 12, 11, FAR, trees = 1, x = .05f, y = .47f, speed = .55f),
        island(16, 9, 12, FAR, trees = 1, x = .38f, y = .36f, speed = .55f),
        island(26, 14, 13, FAR, trees = 2, x = .63f, y = .52f, speed = .55f),
        island(18, 10, 14, FAR, trees = 1, x = .86f, y = .30f, speed = .55f),
    )
    private val nearIslands = listOf(
        island(58, 34, 21, NEAR, trees = 3, x = .02f, y = .50f, speed = 1.5f, tower = true, waterfall = 51),
        island(38, 24, 22, NEAR, trees = 2, x = .45f, y = .66f, speed = 1.5f, waterfall = 4),
        island(30, 20, 23, NEAR, trees = 2, x = .76f, y = .41f, speed = 1.5f),
    )
    private val farClouds = List(5) {
        Cloud(makeCloud(30 + rng.nextInt(26), 14, 100 + it, FAR_CLOUD), rng.nextFloat(),
            .22f + rng.nextFloat() * .35f, .7f + rng.nextFloat() * .5f)
    }
    private val nearClouds = List(4) {
        Cloud(makeCloud(46 + rng.nextInt(30), 20, 200 + it, NEAR_CLOUD), it / 4f + rng.nextFloat() * .15f,
            .30f + rng.nextFloat() * .42f, 2.2f + rng.nextFloat() * 1.2f)
    }
    private val spores = List(80) {
        Spore(rng.nextFloat(), rng.nextFloat(), rng.nextFloat() * 6.3f, .6f + rng.nextFloat(), rng.nextInt(3) == 0)
    }

    override fun render(bitmap: Bitmap, elapsedSeconds: Float, tiltX: Float, tiltY: Float, settings: WallpaperSettings) {
        p.begin(bitmap)
        val w = p.width
        val h = p.height
        val t = elapsedSeconds * settings.timeScale()
        val dx = if (settings.parallax) tiltX.coerceIn(-1f, 1f) else 0f
        val dy = if (settings.parallax) tiltY.coerceIn(-1f, 1f) else 0f

        p.bitmap(skyCache.get(bitmap.width, bitmap.height, ::paintSky), 0f, 0f)
        mountains(w, h, dx * 1.5f, dy * .8f)
        clouds(farClouds, w, h, t, dx * 2f, dy)
        islands(farIslands, w, h, t, dx * 3f, dy * 1.5f, settings)
        clouds(nearClouds, w, h, t, dx * 5f, dy * 2f)
        islands(nearIslands, w, h, t, dx * 8f, dy * 3.5f, settings)
        birds(w, h, t, dx * 4f, dy * 2f)
        cloudSea(w, h, t, dx * 6f, .84f, 3f, 5f, color(0xFFC995BF), color(0xFFF2C4D4))
        cloudSea(w, h, t, dx * 10f, .91f, 4f, 9f, color(0xFFF4D3DE), color(0xFFFFF1EC))
        spores(w, h, t, settings, dx, dy)
    }

    // ---------- Céu (estático) ----------

    private fun paintSky(sp: PixelPainter) {
        val w = sp.width
        val h = sp.height
        sp.verticalGradient(0f, h * .8f, SKY)
        sp.fill(0f, h * .8f, w, h, SKY.last())
        // Estrelas tímidas no alto do céu.
        val r = Random(55)
        repeat(34) {
            val y = r.nextFloat() * h * .22f
            sp.pixel(r.nextFloat() * w, y, withAlpha(color(0xFFFFF2FF), (200 - y / h * 600).toInt()))
        }
        // Lua crescente.
        val mx = w * .80f
        val my = h * .06f
        sp.disc(mx, my, 7, color(0xFFF6EAFF))
        sp.disc(mx + 3f, my - 2f, 6, SKY[0])
        // Sol se pondo com halo pontilhado.
        val sx = w * .30f
        val sy = h * .62f
        sp.ditherDisc(sx, sy, 52, color(0xFFFFB98E), density = 4)
        sp.ditherDisc(sx, sy, 34, color(0xFFFFCB98), density = 2)
        sp.disc(sx, sy, 18, color(0xFFFFE0A8))
        sp.disc(sx, sy, 14, color(0xFFFFF0C8))
    }

    // ---------- Montanhas e castelo ----------

    private fun mountains(w: Float, h: Float, ox: Float, oy: Float) {
        val back = color(0xFFB98AB4)
        p.polygon(back,
            -30f + ox, h * .70f + oy, w * .15f + ox, h * .60f + oy, w * .33f + ox, h * .68f + oy,
            w * .58f + ox, h * .56f + oy, w * .80f + ox, h * .66f + oy, w + 30f + ox, h * .59f + oy,
            w + 30f, h, -30f, h,
        )
        // Castelo no pico central.
        val cx = w * .58f + ox
        val cy = h * .56f + oy
        p.fill(cx - 6f, cy - 9f, cx + 6f, cy + 2f, back)
        p.fill(cx - 9f, cy - 13f, cx - 5f, cy + 2f, back)
        p.fill(cx + 5f, cy - 16f, cx + 9f, cy + 2f, back)
        p.triangle(cx - 10f, cy - 13f, cx - 7f, cy - 18f, cx - 4f, cy - 13f, back)
        p.triangle(cx + 4f, cy - 16f, cx + 7f, cy - 22f, cx + 10f, cy - 16f, back)
        p.pixel(cx + 6f, cy - 12f, color(0xFFFFE6A8))
        p.pixel(cx - 8f, cy - 9f, color(0xFFFFE6A8))

        p.polygon(color(0xFF9A6FA0),
            -30f + ox * 1.4f, h * .76f + oy, w * .22f + ox * 1.4f, h * .66f + oy,
            w * .48f + ox * 1.4f, h * .75f + oy, w * .72f + ox * 1.4f, h * .68f + oy,
            w + 30f + ox * 1.4f, h * .77f + oy, w + 30f, h, -30f, h,
        )
    }

    // ---------- Nuvens ----------

    private fun clouds(list: List<Cloud>, w: Float, h: Float, t: Float, ox: Float, oy: Float) {
        for (c in list) {
            val span = w + c.bitmap.width + 20f
            val x = wrap(c.x * span - t * c.speed, span) - c.bitmap.width - 10f + ox
            p.bitmap(c.bitmap, x, c.y * h + oy)
        }
    }

    private fun cloudSea(w: Float, h: Float, t: Float, ox: Float, baseRel: Float, amp: Float,
                         speed: Float, body: Int, light: Int) {
        val base = h * baseRel
        var x = 0
        while (x < w) {
            val xf = x.toFloat()
            val u = xf - ox + t * speed
            val top = base - abs(sin(u * .09f)) * amp * 2f - abs(sin(u * .037f + 1.3f)) * amp
            p.fill(xf, top, xf + 2f, h, body)
            p.fill(xf, top, xf + 2f, top + 1f, light)
            x += 2
        }
    }

    // ---------- Ilhas flutuantes ----------

    private fun islands(list: List<Island>, w: Float, h: Float, t: Float, ox: Float, oy: Float,
                        settings: WallpaperSettings) {
        for (island in list) {
            val art = island.art
            val span = w + art.bitmap.width + 30f
            val x = wrap(island.x * span - t * island.speed, span) - art.bitmap.width - 15f + ox
            val y = island.y * h + sin(t * .6f + island.phase) * 2.5f + oy
            val surface = y + art.top
            p.bitmap(art.bitmap, x, y)
            if (art.waterfall >= 0) waterfall(x + art.waterfall, surface + 1f, h * .93f, t, settings)
            if (art.towerWindowX >= 0) {
                val glow = .5f + .5f * sin(t * 2.4f + island.phase)
                val warm = mixColor(color(0xFFFFA65C), color(0xFFFFF0B0), glow)
                p.fill(x + art.towerWindowX, surface - 12f, x + art.towerWindowX + 1f, surface - 10f, warm)
            }
        }
    }

    private fun waterfall(x: Float, top: Float, bottom: Float, t: Float, settings: WallpaperSettings) {
        p.fill(x, top, x + 3f, bottom, withAlpha(color(0xFF8FD0EE), 200))
        p.fill(x + 1f, top, x + 2f, bottom, withAlpha(color(0xFFD8F4FF), 170))
        val step = 7f
        var y = top + (t * 26f) % step
        var k = 0
        while (y < bottom) {
            p.pixel(x + (k % 3), y, color(0xFFFFFFFF))
            y += step
            k++
        }
        if (!settings.batterySaver) {
            // Respingos onde a água encontra o mar de nuvens.
            for (i in 0 until 4) {
                val life = (t * 1.3f + i / 4f) % 1f
                p.pixel(x - 2f + i * 2f, bottom - 6f - life * 5f, withAlpha(color(0xFFE8F8FF), ((1f - life) * 220).toInt()))
            }
        }
    }

    private fun island(width: Int, depth: Int, seed: Int, palette: IslandPalette, trees: Int,
                       x: Float, y: Float, speed: Float, tower: Boolean = false, waterfall: Int = -1): Island =
        Island(makeIsland(width, depth, seed, palette, trees, tower, waterfall), x, y, speed, rng.nextFloat() * 6.3f)

    private fun makeIsland(width: Int, depth: Int, seed: Int, pal: IslandPalette, trees: Int,
                           tower: Boolean, waterfall: Int): IslandArt {
        val top = 22
        val bitmap = Bitmap.createBitmap(width + 4, top + depth + 8, Bitmap.Config.ARGB_8888)
        val ip = PixelPainter()
        ip.begin(bitmap)
        val r = Random(seed)
        val surface = top.toFloat()

        // Rocha da base: cone invertido irregular, com estratos e raízes penduradas.
        for (i in 0 until width) {
            val u = (i + .5f) / width * 2f - 1f
            val jag = .75f + .3f * Noise.value(i * .35f, 0f, seed)
            val d = (depth * (1f - abs(u)).pow(.8f) * jag).toInt().coerceAtLeast(2)
            val x = 2f + i
            ip.fill(x, surface + 1f, x + 1f, surface + 1f + d * .4f, pal.rockLight)
            ip.fill(x, surface + 1f + d * .4f, x + 1f, surface + 1f + d, pal.rockDark)
            if (i % 7 == 3) ip.fill(x, surface + 1f + d * .55f, x + 1f, surface + d, pal.rockDeep)
            if (u < -.5f) ip.pixel(x, surface + 2f + d * .2f, pal.rockHighlight)
            ip.pixel(x, surface + d, pal.rockDeep)
            if (r.nextInt(9) == 0) ip.fill(x, surface + 1f + d, x + 1f, surface + 1f + d + r.nextInt(2, 5), pal.vine)
        }
        // Gramado com bordas caindo.
        ip.fill(1f, surface - 1f, width + 3f, surface + 2f, pal.grass)
        ip.fill(2f, surface - 2f, width + 2f, surface - 1f, pal.grassLight)
        for (i in 0 until width step 3) if (r.nextBoolean()) ip.pixel(2f + i, surface + 2f, pal.grass)

        var windowX = -1
        if (tower) {
            val tx = 2f + width * .6f
            ip.fill(tx, surface - 16f, tx + 6f, surface - 1f, pal.stone)
            ip.fill(tx, surface - 16f, tx + 1f, surface - 1f, pal.stoneLight)
            ip.triangle(tx - 1.5f, surface - 16f, tx + 3f, surface - 22f, tx + 7.5f, surface - 16f, pal.roof)
            ip.fill(tx + 2f, surface - 5f, tx + 4f, surface - 1f, pal.rockDeep)
            windowX = (tx + 3f).toInt()
        }
        repeat(trees) { k ->
            val tx = 2f + width * (.12f + .7f * (k + r.nextFloat() * .5f) / trees)
            val size = r.nextInt(3, 6)
            val blossom = r.nextInt(3) == 0
            ip.fill(tx, surface - 1f - size, tx + 1f, surface - 1f, pal.trunk)
            val radius = size / 2 + 1
            ip.disc(tx, surface - 2f - size, radius, if (blossom) pal.blossom else pal.leaf)
            ip.disc(tx - 1f, surface - 3f - size, radius - 1, if (blossom) pal.blossomLight else pal.leafLight)
        }
        return IslandArt(bitmap, top, if (waterfall >= 0) 2 + waterfall else -1, windowX)
    }

    private fun makeCloud(width: Int, height: Int, seed: Int, pal: CloudPalette): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val cp = PixelPainter()
        cp.begin(bitmap)
        val r = Random(seed)
        val puffs = 3 + width / 14
        val xs = FloatArray(puffs)
        val ys = FloatArray(puffs)
        val radii = IntArray(puffs)
        for (j in 0 until puffs) {
            val radius = r.nextInt(height / 4, height / 2)
            radii[j] = radius
            xs[j] = radius + (width - 2f * radius - 1f) * j / (puffs - 1f)
            ys[j] = height - 3f - radius * .7f
        }
        for (j in 0 until puffs) cp.disc(xs[j], ys[j], radii[j], pal.shade)
        cp.fill(2f, height - 4f, width - 2f, height - 2f, pal.shade)
        for (j in 0 until puffs) cp.disc(xs[j], ys[j] - 1f, radii[j] - 1, pal.mid)
        for (j in 0 until puffs) cp.disc(xs[j] - 1f, ys[j] - 2f, radii[j] - 3, pal.lit)
        return bitmap
    }

    // ---------- Pássaros e esporos ----------

    private fun birds(w: Float, h: Float, t: Float, ox: Float, oy: Float) {
        val span = w + 90f
        val leadX = w + 30f - (t * 9f) % span + ox
        val leadY = h * .27f + sin(t * .4f) * 6f + oy
        val bird = color(0xFF4A2F5E)
        for (i in 0 until 5) {
            val offset = abs(i - 2)
            val bx = leadX + offset * 6f
            val by = leadY + offset * 3f * (if (i < 2) -1f else 1f) + sin(t * 1.3f + i) * 1f
            p.pixel(bx, by, bird)
            if (sin(t * 9f + i * 1.7f) > 0f) {
                p.pixel(bx - 1f, by - 1f, bird); p.pixel(bx + 1f, by - 1f, bird)
                p.pixel(bx - 2f, by - 2f, bird); p.pixel(bx + 2f, by - 2f, bird)
            } else {
                p.pixel(bx - 1f, by, bird); p.pixel(bx + 1f, by, bird)
                p.pixel(bx - 2f, by + 1f, bird); p.pixel(bx + 2f, by + 1f, bird)
            }
        }
    }

    private fun spores(w: Float, h: Float, t: Float, settings: WallpaperSettings, dx: Float, dy: Float) {
        val count = minOf(spores.size, settings.effectiveParticles())
        val glow = !settings.batterySaver
        val range = h * .75f
        for (i in 0 until count) {
            val s = spores[i]
            val x = s.x * w + sin(t * .7f * s.speed + s.phase) * 6f + dx * 9f
            val y = h - wrap(s.y * range + t * s.speed * 7f, range) - h * .05f + dy * 4f
            val alpha = (120 + 120 * (.5f + .5f * sin(t * 2f + s.phase))).toInt()
            val tint = if (s.golden) color(0xFFFFE9A0) else color(0xFFB8FFF0)
            if (glow) p.fill(x - 1f, y - 1f, x + 2f, y + 2f, withAlpha(tint, alpha / 5))
            p.pixel(x, y, withAlpha(tint, alpha))
        }
    }

    private fun wrap(value: Float, span: Float): Float = ((value % span) + span) % span

    private class IslandArt(val bitmap: Bitmap, val top: Int, val waterfall: Int, val towerWindowX: Int)
    private class Island(val art: IslandArt, val x: Float, val y: Float, val speed: Float, val phase: Float)
    private class Cloud(val bitmap: Bitmap, val x: Float, val y: Float, val speed: Float)
    private class Spore(val x: Float, val y: Float, val phase: Float, val speed: Float, val golden: Boolean)
}

private class IslandPalette(
    val grass: Int, val grassLight: Int, val rockLight: Int, val rockDark: Int, val rockDeep: Int,
    val rockHighlight: Int, val vine: Int, val trunk: Int, val leaf: Int, val leafLight: Int,
    val blossom: Int, val blossomLight: Int, val stone: Int, val stoneLight: Int, val roof: Int,
)

private class CloudPalette(val lit: Int, val mid: Int, val shade: Int)

private val NEAR = IslandPalette(
    grass = color(0xFF4FA866), grassLight = color(0xFF9BE07A),
    rockLight = color(0xFF8A6F86), rockDark = color(0xFF5E4A6B), rockDeep = color(0xFF3E3150),
    rockHighlight = color(0xFFC4919A), vine = color(0xFF4E8F5C), trunk = color(0xFF5A3A3A),
    leaf = color(0xFF3F9A62), leafLight = color(0xFF7FD08A),
    blossom = color(0xFFE987B4), blossomLight = color(0xFFFFC9DE),
    stone = color(0xFFB3A2BE), stoneLight = color(0xFFE3D3E6), roof = color(0xFF5B3C7A),
)

private val FAR = IslandPalette(
    grass = color(0xFF9C8FC0), grassLight = color(0xFFB9ACD8),
    rockLight = color(0xFF9580AE), rockDark = color(0xFF84709F), rockDeep = color(0xFF776493),
    rockHighlight = color(0xFFAC94BC), vine = color(0xFF8E86B4), trunk = color(0xFF7E6C9C),
    leaf = color(0xFF8E88BA), leafLight = color(0xFFA7A0CE),
    blossom = color(0xFFB79BC9), blossomLight = color(0xFFC9B2D8),
    stone = color(0xFFA796BE), stoneLight = color(0xFFB8A8CC), roof = color(0xFF7E6C9C),
)

private val FAR_CLOUD = CloudPalette(color(0xFFF6CBD2), color(0xFFE2A9C2), color(0xFFC291B6))
private val NEAR_CLOUD = CloudPalette(color(0xFFFFEDE6), color(0xFFF7C4CE), color(0xFFD196BA))

private val SKY = intArrayOf(
    color(0xFF2B2766), color(0xFF3D2F7A), color(0xFF57388A), color(0xFF7A4294),
    color(0xFFA34E96), color(0xFFD06690), color(0xFFEE8888), color(0xFFF8A884),
)
