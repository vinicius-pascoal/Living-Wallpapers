package com.livingwallpapers.app.scene.forest

import android.graphics.Bitmap
import com.livingwallpapers.app.scene.DayCycle
import com.livingwallpapers.app.scene.LandscapeTint
import com.livingwallpapers.app.scene.LayerCache
import com.livingwallpapers.app.scene.Noise
import com.livingwallpapers.app.scene.PixelPainter
import com.livingwallpapers.app.scene.SceneRenderer
import com.livingwallpapers.app.scene.color
import com.livingwallpapers.app.scene.mixColor
import com.livingwallpapers.app.scene.withAlpha
import com.livingwallpapers.app.settings.WallpaperSettings
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.random.Random

/**
 * Floresta com ciclo de dia e noite (segue o relógio): céu com dithering, sol e lua em arco, nuvens passando, estrelas cadentes,
 * pinheiros balançando ao vento, cabana com lareira acesa, névoa, vaga-lumes e folhas.
 */
class ForestScene : SceneRenderer {
    companion object { const val ID = "forest" }

    private val p = PixelPainter()
    private val skyCache = LayerCache()
    private val rng = Random(2468)

    private val stars = List(70) {
        Star(rng.nextFloat(), rng.nextFloat() * .55f, if (rng.nextInt(7) == 0) 2 else 1, rng.nextFloat() * 6.3f)
    }
    private val clouds = List(4) {
        Cloud(rng.nextFloat(), .07f + rng.nextFloat() * .26f, 22 + rng.nextInt(28), .5f + rng.nextFloat() * .9f)
    }
    private val fireflies = List(80) {
        Firefly(rng.nextFloat(), .40f + rng.nextFloat() * .55f, rng.nextFloat() * 6.3f, .7f + rng.nextFloat() * 1.3f)
    }
    private val leaves = List(30) {
        Leaf(rng.nextFloat(), .38f + rng.nextFloat() * .53f, rng.nextFloat() * 6.3f, .4f + rng.nextFloat() * .9f)
    }

    override fun render(bitmap: Bitmap, elapsedSeconds: Float, tiltX: Float, tiltY: Float, settings: WallpaperSettings) {
        p.begin(bitmap)
        val w = p.width
        val h = p.height
        val t = elapsedSeconds * settings.timeScale()
        val dx = if (settings.parallax) tiltX.coerceIn(-1f, 1f) else 0f
        val dy = if (settings.parallax) tiltY.coerceIn(-1f, 1f) else 0f

        DayCycle.update()
        DayCycle.pickGradient(SKY_BY_PHASE, skyColors)
        p.bitmap(skyCache.get(bitmap.width, bitmap.height, DayCycle.cacheKey(), ::paintSky), 0f, 0f)
        stars(w, h, t)
        sunAndMoon(w, h)
        clouds(w, h, t, dx)
        if (DayCycle.nightness > .6f) shootingStar(w, h, t, settings)
        // A paisagem recebe a luz da hora do dia (verde e clara de dia, fria à noite).
        p.filter = landscapeTint.current()
        mountains(w, h, dx * 1.6f, dy * .8f)
        backForest(h, t, dx * 3.5f, dy * 1.4f)
        fog(w, h, t, settings)
        middleForest(w, h, t, dx * 6f, dy * 2.5f)
        foreground(w, h, t, settings, dx * 9f, dy * 3.5f)
        p.filter = null
        particles(w, h, t, settings, dx, dy)
    }

    // ---------- Céu (degradê em cache, redesenhado quando a hora muda a cor) ----------

    private val skyColors = IntArray(SKY_NIGHT.size)
    private val landscapeTint = LandscapeTint(arrayOf(
        floatArrayOf(1f, 1f, 1f, 0f, 0f, 0f),            // noite: arte original
        floatArrayOf(1.4f, 1.35f, 1.05f, 24f, 12f, 0f),  // amanhecer: luz quente
        floatArrayOf(1.7f, 1.95f, 1.35f, 14f, 18f, 6f),  // dia: verde e claro
        floatArrayOf(1.5f, 1.25f, .95f, 28f, 10f, 0f),   // entardecer: dourado
    ))
    private val moonHalo = LayerCache()
    private val sunHalo = LayerCache()
    private var moonX = -100f
    private var moonY = -100f

    private fun paintSky(sp: PixelPainter) {
        sp.verticalGradient(0f, sp.height * .78f, skyColors)
        sp.fill(0f, sp.height * .78f, sp.width, sp.height, skyColors.last())
    }

    /** Sol e lua percorrem um arco no céu de acordo com o relógio. */
    private fun sunAndMoon(w: Float, h: Float) {
        moonX = -100f
        val sun = DayCycle.arcProgress(5.8f, 18.8f)
        if (sun >= 0f) {
            val x = w * (.08f + .84f * sun)
            val y = h * (.62f - .48f * DayCycle.arcHeight(sun))
            val high = DayCycle.arcHeight(sun)
            p.bitmap(sunHalo.get(76, 76) { it.ditherDisc(38f, 38f, 36, color(0x55FFE3A0), 4); it.ditherDisc(38f, 38f, 24, color(0x77FFE9B8), 2) }, x - 38f, y - 38f)
            p.disc(x, y, 11, mixColor(color(0xFFFF9A55), color(0xFFFFF4D0), high))
            p.disc(x - 2f, y - 2f, 7, mixColor(color(0xFFFFC27A), color(0xFFFFFFF0), high))
        }
        val moon = DayCycle.arcProgress(18.2f, 30.6f)
        if (moon >= 0f) {
            val cx = floor(w * (.1f + .8f * moon))
            val cy = floor(h * (.6f - .45f * DayCycle.arcHeight(moon)))
            moonX = cx
            moonY = cy
            p.bitmap(moonHalo.get(76, 76) {
                it.ditherDisc(38f, 38f, 36, color(0xFF1E2F4A), density = 4)
                it.ditherDisc(38f, 38f, 27, color(0xFF2A4163), density = 2)
                it.disc(38f, 38f, 19, color(0xFF2E4768))
            }, cx - 38f, cy - 38f, (255 * DayCycle.nightness).toInt())
            p.disc(cx, cy, 13, color(0xFFFFE4B9))
            p.disc(cx - 2f, cy - 2f, 10, color(0xFFFFF0D4))
            p.disc(cx - 4f, cy - 3f, 3, color(0xFFE8CFAA))
            p.disc(cx + 6f, cy + 4f, 2, color(0xFFE6CCA7))
            p.disc(cx + 1f, cy + 8f, 1, color(0xFFE6CCA7))
        }
    }

    private fun stars(w: Float, h: Float, t: Float) {
        val night = DayCycle.nightness
        if (night <= .02f) return
        for (s in stars) {
            val x = s.x * w
            val y = s.y * h
            val ddx = x - moonX
            val ddy = y - moonY
            if (ddx * ddx + ddy * ddy < 26f * 26f) continue
            val pulse = .5f + .5f * sin(t * .8f + s.phase * 3f)
            val alpha = ((90 + 150 * pulse) * night).toInt()
            p.pixel(x, y, withAlpha(color(0xFFCCE6EE), alpha), s.size)
            // Estrelas maiores ganham um brilho em cruz quando pulsam forte.
            if (s.size == 2 && pulse > .8f) {
                val glow = withAlpha(color(0xFFCCE6EE), (70 * night).toInt())
                p.pixel(x - 1f, y, glow); p.pixel(x + 2f, y, glow)
                p.pixel(x, y - 1f, glow); p.pixel(x, y + 2f, glow)
            }
        }
    }

    private fun clouds(w: Float, h: Float, t: Float, dx: Float) {
        val span = w + 90f
        val body = withAlpha(DayCycle.pick(CLOUD_BODY), 190)
        val lit = withAlpha(DayCycle.pick(CLOUD_LIT), 160)
        for (c in clouds) {
            val x = ((c.x * span + t * c.speed * 2.4f) % span) - 45f + dx * 1.2f
            val y = c.y * h
            val len = c.length.toFloat()
            p.fill(x, y, x + len, y + 3f, body)
            p.fill(x + len * .12f, y - 2f, x + len * .72f, y, body)
            p.fill(x + len * .3f, y - 4f, x + len * .55f, y - 2f, body)
            p.fill(x + len * .3f, y - 4f, x + len * .55f, y - 3f, lit)
            p.fill(x + len * .12f, y - 2f, x + len * .3f, y - 1f, lit)
        }
    }

    private fun shootingStar(w: Float, h: Float, t: Float, settings: WallpaperSettings) {
        if (settings.particlesPercent == 0) return
        val period = 14f
        val index = floor(t / period).toInt()
        val phase = t - index * period
        val duration = 1.1f
        if (phase > duration) return
        val progress = phase / duration
        val startX = w * (.25f + .7f * Noise.hash(index * 3 + 1))
        val startY = h * (.04f + .22f * Noise.hash(index * 3 + 2))
        val headX = startX - progress * 60f
        val headY = startY + progress * 26f
        p.streak(headX, headY, 2.3f, -1f, 12, color(0xFFFFF6DA), 1f - progress * .6f)
    }

    // ---------- Montanhas e árvores ----------

    private fun mountains(w: Float, h: Float, offsetX: Float, offsetY: Float) {
        p.polygon(color(0xFF273B52),
            -40f + offsetX, h * .66f + offsetY,
            w * .12f + offsetX, h * .45f + offsetY,
            w * .30f + offsetX, h * .64f + offsetY,
            w * .55f + offsetX, h * .42f + offsetY,
            w * .85f + offsetX, h * .67f + offsetY,
            w + 50f + offsetX, h * .49f + offsetY,
            w + 50f, h * .83f,
            -40f, h * .83f,
        )
        // Encosta iluminada pela lua (lado direito dos picos).
        p.triangle(w * .55f + offsetX, h * .42f + offsetY, w * .62f + offsetX, h * .48f + offsetY,
            w * .57f + offsetX, h * .47f + offsetY, color(0xFF34506B))
        p.triangle(w * .12f + offsetX, h * .45f + offsetY, w * .18f + offsetX, h * .51f + offsetY,
            w * .14f + offsetX, h * .50f + offsetY, color(0xFF34506B))
        p.polygon(color(0xFF213B49),
            -30f, h * .71f, w * .16f, h * .53f, w * .38f, h * .72f,
            w * .68f, h * .51f, w + 40f, h * .70f, w + 40f, h * .86f,
            -30f, h * .86f,
        )
    }

    private fun wind(t: Float, x: Float): Float = sin(t * .7f + x * .05f) + .3f * sin(t * 1.9f + x * .13f)

    private fun backForest(h: Float, t: Float, offsetX: Float, offsetY: Float) {
        for (i in -3..24) {
            val x = i * 10f - 4f + offsetX
            val y = h * .77f + (i % 4) * 3f + offsetY
            val tall = 33f + ((i * 13 + 100) % 31)
            pine(x, y, tall, color(0xFF1D4248), color(0xFF1D4248), color(0xFF24504F), wind(t, x) * .5f)
        }
    }

    private fun middleForest(w: Float, h: Float, t: Float, offsetX: Float, offsetY: Float) {
        p.polygon(color(0xFF153F43),
            -30f, h * .81f, w * .2f, h * .72f, w * .54f, h * .80f,
            w + 30f, h * .72f, w + 30f, h, -30f, h,
        )
        for (i in -2..15) {
            val x = i * 17f - 12f + offsetX
            val baseY = h * .85f + ((i * 11 + 40) % 9) + offsetY
            val tall = 52f + ((i * 23 + 201) % 41)
            pine(x, baseY, tall, color(0xFF123C40), color(0xFF174A47), color(0xFF2A5F57), wind(t, x) * 1.1f)
        }
    }

    private fun foreground(w: Float, h: Float, t: Float, settings: WallpaperSettings, offsetX: Float, offsetY: Float) {
        // Pequena colina e trilha à direita, sem tirar espaço dos ícones Android.
        p.polygon(color(0xFF113A39),
            -40f, h * .9f, w * .25f, h * .86f, w * .52f, h * .91f,
            w + 40f, h * .86f, w + 40f, h + 30f, -40f, h + 30f,
        )
        p.polygon(color(0xFF214B45),
            w * .53f, h, w * .66f, h * .92f, w * .81f, h * .90f,
            w + 20f, h * .95f, w + 20f, h + 10f,
        )
        cabin(w * .60f + offsetX * .6f, h * .905f + offsetY * .6f, t, settings)

        // Troncos laterais amplificam o efeito de profundidade.
        pine(-8f + offsetX, h * 1.04f + offsetY, h * .65f,
            color(0xFF0C242D), color(0xFF0E3438), color(0xFF16443F), wind(t, 0f) * 2f)
        pine(w + 7f + offsetX, h * 1.05f + offsetY, h * .70f,
            color(0xFF0C242D), color(0xFF0F3639), color(0xFF17463F), wind(t, w) * 2f)

        // Grama balançando.
        for (i in 0..46) {
            val x = ((i * 53 + 19) % 185) / 185f * w
            val y = h * (.90f + ((i * 17) % 10) / 100f)
            val green = if (i % 3 == 0) color(0xFF38755B) else color(0xFF2A6552)
            val sway = sin(t * 1.4f + i * .9f)
            p.fill(x, y - 1f, x + 1f, y + 2f, green)
            p.pixel(x + if (sway > .3f) 1f else if (sway < -.3f) -1f else 0f, y - 2f, green)
            if (i % 5 == 0) p.fill(x + 1f, y - 1f, x + 3f, y, green)
        }
        mushroom(w * .22f + offsetX, h * .95f + offsetY)
        mushroom(w * .77f + offsetX, h * .975f + offsetY)
    }

    private fun cabin(x: Float, base: Float, t: Float, settings: WallpaperSettings) {
        // A lareira acende ao anoitecer: de dia a janela fica escura.
        val lamp = (DayCycle.nightness * 1.3f + .1f).coerceIn(0f, 1f)
        val fire = mixColor(color(0xFFE38A3F), color(0xFFFFD27A), .55f + .45f * sin(t * 6.3f) * sin(t * 2.7f))
        val warm = mixColor(color(0xFF3A2D3D), fire, lamp)
        // Luz da janela no chão.
        p.fill(x - 1f, base, x + 10f, base + 2f, withAlpha(warm, 50))
        p.fill(x, base - 9f, x + 15f, base, color(0xFF2B2230))
        p.fill(x, base - 9f, x + 1f, base, color(0xFF3A2D3D))
        p.triangle(x - 2f, base - 9f, x + 7.5f, base - 16f, x + 17f, base - 9f, color(0xFF1A1622))
        p.fill(x + 11f, base - 17f, x + 13f, base - 11f, color(0xFF231C29))
        p.fill(x + 3f, base - 7f, x + 6f, base - 4f, warm)
        p.fill(x + 4f, base - 7f, x + 5f, base - 4f, withAlpha(color(0xFF2B2230), 160))
        p.fill(x + 9f, base - 6f, x + 12f, base, color(0xFF17121A))
        if (settings.batterySaver) return
        // Fumaça da chaminé.
        for (k in 0 until 7) {
            val life = (t * .3f + k / 7f) % 1f
            val sx = x + 11.5f + sin(t * .8f + k * 2.1f) * 1.5f + life * 7f
            val sy = base - 18f - life * 28f
            p.fill(sx, sy, sx + 2f, sy + 2f, withAlpha(color(0xFFA9B6C4), ((1f - life) * 80).toInt()))
        }
    }

    private fun fog(w: Float, h: Float, t: Float, settings: WallpaperSettings) {
        val alpha = if (settings.batterySaver) 13 else 23
        val fogColor = withAlpha(color(0xFFACD5CF), alpha)
        for (band in 0..3) {
            val y = h * (.71f + band * .062f)
            val shift = sin(t * .11f + band * 1.8f) * 8f
            for (x in -1..3) {
                p.fill(x * 70f + shift, y, x * 70f + 54f + shift, y + 3f, fogColor)
            }
        }
    }

    private fun particles(w: Float, h: Float, t: Float, settings: WallpaperSettings, dx: Float, dy: Float) {
        // Vaga-lumes aparecem ao anoitecer; de dia restam só alguns.
        val number = minOf(fireflies.size, (settings.effectiveParticles() * (.12f + .88f * DayCycle.nightness)).toInt())
        val glow = !settings.batterySaver
        for (i in 0 until number) {
            val f = fireflies[i]
            val x = f.x * w + sin(t * .48f * f.speed + f.phase) * 9f + dx * 7f
            val y = f.y * h + cos(t * .36f * f.speed + f.phase) * 7f + dy * 3f
            val alpha = (105 + 140 * (.5f + .5f * sin(t * 2.2f + f.phase))).toInt()
            if (glow) p.fill(x - 2f, y - 2f, x + 3f, y + 3f, withAlpha(color(0xFFF1F27E), alpha / 6))
            p.fill(x, y, x + 2f, y + 2f, withAlpha(color(0xFFF3F59A), alpha))
            if (i % 6 == 0) p.pixel(x, y, withAlpha(color(0xFFFFFFDD), alpha))
        }
        val leafCount = if (settings.batterySaver) 8 else leaves.size
        for (i in 0 until leafCount) {
            val l = leaves[i]
            val x = (l.x * w + t * (1.6f + l.speed) * 3f + sin(l.phase + t) * 4f) % (w + 20f) - 10f
            val y = (l.y * h + t * (1.1f + l.speed)) % h
            val leafColor = if (i % 4 == 0) color(0xFF8A7A4A) else color(0xFF5A8A67)
            p.fill(x, y, x + 2f, y + if (sin(t * 3f + l.phase) > 0f) 2f else 1f, leafColor)
        }
    }

    private fun pine(x: Float, bottom: Float, height: Float, trunk: Int, leaves: Int, light: Int, sway: Float) {
        val top = bottom - height
        p.fill(x - 2f, bottom - height * .42f, x + 2f, bottom, trunk)
        for (tier in 0..3) {
            val shift = sway * (1f - tier / 4f)
            val triangleTop = top + tier * height * .18f
            val halfWidth = height * (.14f + tier * .018f)
            val baseY = triangleTop + height * .39f
            p.triangle(x + shift, triangleTop, x - halfWidth + shift * .4f, baseY,
                x + halfWidth + shift * .4f, baseY, leaves)
            // Borda direita iluminada pela lua.
            p.triangle(x + shift, triangleTop, x + halfWidth * .55f + shift * .4f, baseY - 1f,
                x + halfWidth + shift * .4f, baseY, light)
            p.triangle(x + shift, triangleTop + 2f, x + halfWidth * .35f + shift * .4f, baseY - 1f,
                x + halfWidth * .85f + shift * .4f, baseY - 1f, leaves)
        }
    }

    private fun mushroom(x: Float, y: Float) {
        p.fill(x, y - 5f, x + 2f, y, color(0xFFE0D2B8))
        p.fill(x - 3f, y - 8f, x + 5f, y - 5f, color(0xFFBF5E65))
        p.fill(x - 1f, y - 8f, x + 1f, y - 7f, color(0xFFFFD4BF))
    }

    private data class Star(val x: Float, val y: Float, val size: Int, val phase: Float)
    private data class Cloud(val x: Float, val y: Float, val length: Int, val speed: Float)
    private data class Firefly(val x: Float, val y: Float, val phase: Float, val speed: Float)
    private data class Leaf(val x: Float, val y: Float, val phase: Float, val speed: Float)
}

// Céu em cada fase do dia (topo → horizonte).
private val SKY_NIGHT = intArrayOf(
    color(0xFF0A1026), color(0xFF0E162D), color(0xFF131E37), color(0xFF182742),
    color(0xFF1F314D), color(0xFF283C58), color(0xFF324A65),
)
private val SKY_DAWN = intArrayOf(
    color(0xFF2B3566), color(0xFF46477C), color(0xFF6E5A8E), color(0xFFA46A8C),
    color(0xFFD9807E), color(0xFFF2A07A), color(0xFFF8C48A),
)
private val SKY_DAY = intArrayOf(
    color(0xFF3274C8), color(0xFF3F82D0), color(0xFF5293D8), color(0xFF67A4E0),
    color(0xFF7FB5E6), color(0xFF9AC6EC), color(0xFFB8D8F0),
)
private val SKY_DUSK = intArrayOf(
    color(0xFF1C2350), color(0xFF2E2D62), color(0xFF4C3872), color(0xFF7A4478),
    color(0xFFB0557A), color(0xFFE07A6E), color(0xFFF2A66A),
)
private val SKY_BY_PHASE = arrayOf(SKY_NIGHT, SKY_DAWN, SKY_DAY, SKY_DUSK)

private val CLOUD_BODY = intArrayOf(color(0xFF33496A), color(0xFF7A5F86), color(0xFFE6EEF8), color(0xFF5E4570))
private val CLOUD_LIT = intArrayOf(color(0xFF8DA6C4), color(0xFFF4B89E), color(0xFFFFFFFF), color(0xFFF2A07E))
