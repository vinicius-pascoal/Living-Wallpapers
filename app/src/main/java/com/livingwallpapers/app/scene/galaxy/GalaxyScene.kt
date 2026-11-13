package com.livingwallpapers.app.scene.galaxy

import android.graphics.Bitmap
import com.livingwallpapers.app.scene.BAYER_4X4
import com.livingwallpapers.app.scene.LayerCache
import com.livingwallpapers.app.scene.Noise
import com.livingwallpapers.app.scene.PixelPainter
import com.livingwallpapers.app.scene.SceneRenderer
import com.livingwallpapers.app.scene.color
import com.livingwallpapers.app.scene.mixColor
import com.livingwallpapers.app.scene.withAlpha
import com.livingwallpapers.app.settings.WallpaperSettings
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.concurrent.thread
import kotlin.random.Random

/**
 * Galáxia espiral procedural: nebulosa gerada por ruído com dithering, campo de estrelas em
 * três camadas de profundidade, braços espirais girando, planeta com anéis e cometas.
 */
class GalaxyScene : SceneRenderer {
    companion object {
        const val ID = "galaxy"
        private const val SEED = 7331
        private const val ARMS = 2
        private const val NEBULA_MARGIN = 14

        // A nebulosa é cara de gerar: é feita numa thread de fundo e compartilhada entre
        // instâncias (prévia, tela inicial, bloqueio) que tenham o mesmo tamanho.
        private val nebulaLock = Any()
        private val nebulaBitmaps = HashMap<Long, Bitmap>()
        private val nebulaPending = HashSet<Long>()

        private fun nebulaFor(width: Int, height: Int): Bitmap? {
            val key = (width.toLong() shl 32) or height.toLong()
            synchronized(nebulaLock) {
                nebulaBitmaps[key]?.let { return it }
                if (!nebulaPending.add(key)) return null
            }
            thread(name = "nebula-$width x $height", isDaemon = true) {
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                bitmap.setPixels(generateNebula(width, height), 0, width, 0, 0, width, height)
                synchronized(nebulaLock) {
                    nebulaBitmaps[key] = bitmap
                    nebulaPending.remove(key)
                }
            }
            return null
        }

        private fun generateNebula(w: Int, h: Int): IntArray {
            // O ruído é amostrado em blocos 2×2 (4× menos cálculo); o dithering continua por pixel.
            val cw = (w + 1) / 2
            val ch = (h + 1) / 2
            val density = FloatArray(cw * ch)
            val hue = FloatArray(cw * ch)
            for (cy in 0 until ch) {
                for (cx in 0 until cw) {
                    val nx = cx / 32f
                    val ny = cy / 32f
                    val warp = Noise.fbm(nx * 1.3f, ny * 1.3f, SEED + 5, 3)
                    val d = Noise.fbm(nx + warp * 1.7f, ny + warp * 1.2f, SEED, 4)
                    // Faixa diagonal (como a Via Láctea) concentra as nuvens.
                    val x = cx * 2f
                    val y = cy * 2f
                    val bandDistance = abs((y - h * .45f) - (x - w * .5f) * .85f) / (h * .42f)
                    val band = (1f - bandDistance).coerceIn(0f, 1f)
                    density[cy * cw + cx] = ((d - .36f) * 2.1f * (.3f + .7f * band)).coerceIn(0f, 1f)
                    hue[cy * cw + cx] = Noise.fbm(nx * .55f + 10f, ny * .55f, SEED + 9, 2)
                }
            }
            val data = IntArray(w * h)
            for (y in 0 until h) {
                val base = mixColor(SPACE_TOP, SPACE_BOTTOM, y / h.toFloat())
                for (x in 0 until w) {
                    val cell = (y / 2) * cw + (x / 2)
                    val threshold = BAYER_4X4[(y and 3) * 4 + (x and 3)] / 16f
                    val level = (density[cell] * 4f + threshold).toInt().coerceIn(0, 4)
                    val ramp = if (hue[cell] + (threshold - .5f) * .06f < .5f) PURPLE_RAMP else TEAL_RAMP
                    data[y * w + x] = if (level == 0) base else ramp[level]
                }
            }
            // Poeira de estrelas fracas embutida na nebulosa (custo zero por quadro).
            val r = Random(SEED + 1)
            repeat(w * h / 70) {
                data[r.nextInt(data.size)] = DIM_STARS[r.nextInt(DIM_STARS.size)]
            }
            return data
        }
    }

    private val p = PixelPainter()
    private val coreCache = LayerCache()
    private val rng = Random(SEED)

    private val stars = List(130) {
        Star(rng.nextFloat(), rng.nextFloat(), it % 3, rng.nextFloat() * 6.3f, STAR_TINTS[rng.nextInt(STAR_TINTS.size)])
    }

    /** Partículas dos braços espirais, em coordenadas polares do disco galáctico. */
    private val armStars: List<ArmStar> = List(1800) {
        val arm = it % ARMS
        // Mais estrelas perto do centro, rareando para a borda.
        val radius = .05f + .95f * rng.nextFloat().pow(1.35f)
        // Espalhamento em torno do braço: mais concentrado perto do eixo.
        val scatter = (rng.nextFloat() + rng.nextFloat() + rng.nextFloat() - 1.5f) * .38f * (1.2f - radius * .6f)
        val angle = arm * (2f * PI.toFloat() / ARMS) + radius * 3.4f + scatter
        val tint = when {
            radius < .16f -> mixColor(color(0xFFFFF4D6), color(0xFFFFD99A), rng.nextFloat())
            rng.nextInt(11) == 0 -> color(0xFFFF8FC8) // regiões de formação estelar
            radius < .45f -> mixColor(color(0xFFE6E2FF), color(0xFFA9BAFF), rng.nextFloat())
            else -> mixColor(color(0xFF9DB0FF), color(0xFF7A7FE0), rng.nextFloat())
        }
        val alpha = (255 * (1.1f - radius * .55f)).toInt().coerceIn(130, 255)
        ArmStar(radius * cos(angle), radius * sin(angle), withAlpha(tint, alpha), withAlpha(tint, 34))
    }

    private val tiltCos = cos(-.42f)
    private val tiltSin = sin(-.42f)

    override fun render(bitmap: Bitmap, elapsedSeconds: Float, tiltX: Float, tiltY: Float, settings: WallpaperSettings) {
        p.begin(bitmap)
        val w = p.width
        val h = p.height
        val t = elapsedSeconds * settings.timeScale()
        val dx = if (settings.parallax) tiltX.coerceIn(-1f, 1f) else 0f
        val dy = if (settings.parallax) tiltY.coerceIn(-1f, 1f) else 0f

        val nebula = nebulaFor(bitmap.width + NEBULA_MARGIN * 2, bitmap.height + NEBULA_MARGIN * 2)
        if (nebula != null) {
            p.bitmap(nebula, -NEBULA_MARGIN + dx * 3f, -NEBULA_MARGIN + dy * 2f)
        } else {
            // Enquanto a nebulosa é gerada em segundo plano: só o fundo do espaço.
            p.fill(0f, 0f, w, h * .5f, SPACE_TOP)
            p.fill(0f, h * .5f, w, h, SPACE_BOTTOM)
        }

        starField(w, h, t, dx, dy)
        galaxy(w * .52f + dx * 6f, h * .38f + dy * 3.5f, w * .62f, t, settings)
        comet(w, h, t, settings)
        planet(w * .2f + dx * 10f, h * .80f + dy * 5f, t)
    }

    // ---------- Estrelas em camadas ----------

    private fun starField(w: Float, h: Float, t: Float, dx: Float, dy: Float) {
        for (s in stars) {
            val depth = 1f + s.layer * 2.5f
            val x = (s.x * (w + 30f) + dx * depth * 2f + t * .25f * depth) % (w + 30f) - 15f
            val y = s.y * h + dy * depth
            val pulse = .5f + .5f * sin(t * (1.1f + s.layer * .4f) + s.phase)
            val alpha = (80 + 175 * pulse).toInt()
            p.pixel(x, y, withAlpha(s.tint, alpha))
            if (s.layer == 2 && pulse > .75f) {
                val glow = withAlpha(s.tint, 90)
                p.pixel(x - 1f, y, glow); p.pixel(x + 1f, y, glow)
                p.pixel(x, y - 1f, glow); p.pixel(x, y + 1f, glow)
            }
        }
    }

    // ---------- Galáxia espiral ----------

    private fun galaxy(cx: Float, cy: Float, radius: Float, t: Float, settings: WallpaperSettings) {
        val core = coreCache.get(56, 56, draw = ::paintCore)
        p.bitmap(core, cx - 28f, cy - 28f)

        val fraction = settings.effectiveParticles() / 77f
        val saver = if (settings.batterySaver) .65f else 1f
        val count = (armStars.size * (.35f + .65f * fraction) * saver).toInt().coerceIn(200, armStars.size)
        // Rotação rígida (onda de densidade): os braços mantêm a forma enquanto giram.
        // Posições pré-calculadas em (cos, sin); a rotação vira uma multiplicação de matriz.
        val rotCos = cos(t * .06f)
        val rotSin = sin(t * .06f)
        val glow = !settings.batterySaver
        for (i in 0 until count) {
            val s = armStars[i]
            val px = (s.ux * rotCos - s.uy * rotSin) * radius
            val py = (s.ux * rotSin + s.uy * rotCos) * radius * .45f
            val x = cx + px * tiltCos - py * tiltSin
            val y = cy + px * tiltSin + py * tiltCos
            // Parte das estrelas ganha um brilho difuso que dá corpo aos braços.
            if (glow && i % 4 == 0) p.fill(x - 1f, y - 1f, x + 2f, y + 2f, s.glow)
            p.pixel(x, y, s.color)
        }
        // Núcleo brilhante por cima das partículas.
        val pulse = .5f + .5f * sin(t * .9f)
        p.disc(cx, cy, 3, withAlpha(color(0xFFFFF1C9), (200 + 55 * pulse).toInt()))
        p.disc(cx, cy, 1, color(0xFFFFFFFF))
    }

    private fun paintCore(cp: PixelPainter) {
        val c = cp.width / 2f
        cp.ditherDisc(c, c, 27, color(0xFF2C1F5A), density = 4)
        cp.ditherDisc(c, c, 20, color(0xFF5A3F8C), density = 4)
        cp.ditherDisc(c, c, 14, color(0xFF9A7BC4), density = 2)
        cp.ditherDisc(c, c, 9, color(0xFFE8CFA8), density = 2)
        cp.disc(c, c, 6, color(0xFFF6DDB0))
    }

    private fun comet(w: Float, h: Float, t: Float, settings: WallpaperSettings) {
        if (settings.particlesPercent == 0) return
        val period = 11f
        val index = floor(t / period).toInt()
        val phase = t - index * period
        val duration = 1.6f
        if (phase > duration) return
        val progress = phase / duration
        val fromLeft = Noise.hash(index * 5 + 3) > .5f
        val startX = if (fromLeft) -10f else w + 10f
        val startY = h * (.05f + .45f * Noise.hash(index * 5 + 1))
        val direction = if (fromLeft) 1f else -1f
        val headX = startX + direction * progress * (w + 20f)
        val headY = startY + progress * h * .18f
        val tint = if (Noise.hash(index * 5 + 2) > .5f) color(0xFFBFF4FF) else color(0xFFFFF0C8)
        p.streak(headX, headY, -direction * 1.8f, -.36f, 16, tint, 1f - progress * .3f)
    }

    // ---------- Planeta com anéis ----------

    private fun planet(cx: Float, cy: Float, t: Float) {
        val radius = 26
        ring(cx, cy, radius, front = false)
        // Atmosfera.
        p.disc(cx, cy, radius + 1, color(0xFF3E5A9A))
        val lightX = cx + 9f
        val lightY = cy - 9f
        for (row in -radius..radius) {
            val ext = sqrt((radius * radius - row * row).toFloat())
            val y = cy + row
            val bandIndex = ((row + radius) * .22f + sin(row * .45f) * .7f).toInt()
            val band = PLANET_BANDS[((bandIndex % PLANET_BANDS.size) + PLANET_BANDS.size) % PLANET_BANDS.size]
            // Sombra: parte da linha que fica fora de um círculo deslocado para a luz.
            val shadowRadius = radius + 4f
            val sy = y - lightY
            val litExt = if (abs(sy) < shadowRadius) sqrt(shadowRadius * shadowRadius - sy * sy) else -1f
            val left = cx - ext
            val right = cx + ext + 1f
            p.fill(left, y, right, y + 1f, mixColor(band, color(0xFF0B0A1C), .78f))
            if (litExt > 0f) {
                val litLeft = maxOf(left, lightX - litExt)
                val litRight = minOf(right, lightX + litExt)
                if (litRight > litLeft) p.fill(litLeft, y, litRight, y + 1f, band)
            }
        }
        // Tempestade deslizando sobre a faixa.
        val stormX = cx - radius + ((t * 2.2f) % (radius * 2f))
        val stormY = cy + 7f
        if (abs(stormX - cx) < radius - 4) {
            p.fill(stormX, stormY, stormX + 4f, stormY + 2f, color(0xFFE9B48A))
            p.pixel(stormX + 1f, stormY, color(0xFFFFE0C0))
        }
        ring(cx, cy, radius, front = true)
    }

    private fun ring(cx: Float, cy: Float, radius: Int, front: Boolean) {
        val outer = radius * 1.9f
        val inner = radius * 1.4f
        val steps = 150
        for (layer in 0..2) {
            val r = inner + (outer - inner) * layer / 2f
            val ringColor = RING_COLORS[layer]
            for (i in 0 until steps) {
                val a = i * (2f * PI.toFloat() / steps)
                val s = sin(a)
                if ((s > 0f) != front) continue
                val px = cos(a) * r
                val py = s * r * .22f
                val x = cx + px * .95f - py * .3f
                val y = cy + px * .3f + py * .95f
                p.pixel(x, y, ringColor)
            }
        }
    }

    private data class Star(val x: Float, val y: Float, val layer: Int, val phase: Float, val tint: Int)
    private class ArmStar(val ux: Float, val uy: Float, val color: Int, val glow: Int)
}

private val SPACE_TOP = color(0xFF05040F)
private val SPACE_BOTTOM = color(0xFF0B0820)
private val PURPLE_RAMP = intArrayOf(
    color(0xFF07061A), color(0xFF140C2E), color(0xFF241245), color(0xFF3C1B60), color(0xFF5E2878),
)
private val TEAL_RAMP = intArrayOf(
    color(0xFF07061A), color(0xFF0A1632), color(0xFF0F2647), color(0xFF173D62), color(0xFF235C7E),
)
private val DIM_STARS = intArrayOf(color(0xFF4A4D72), color(0xFF5D6390), color(0xFF6E6A8E), color(0xFF3E5070))
private val STAR_TINTS = intArrayOf(color(0xFFFFFFFF), color(0xFFCFE0FF), color(0xFFFFE6C2), color(0xFFE0D0FF))
private val PLANET_BANDS = intArrayOf(color(0xFFD69A6A), color(0xFFB8735A), color(0xFFE6C08F), color(0xFF9C5E52))
private val RING_COLORS = intArrayOf(color(0xFF8E7FA8), color(0xFFC9B7D8), color(0xFF7A6C96))
