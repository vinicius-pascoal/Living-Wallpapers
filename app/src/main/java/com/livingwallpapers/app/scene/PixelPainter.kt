package com.livingwallpapers.app.scene

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.floor
import kotlin.math.sqrt

/** Converte uma cor literal 0xAARRGGBB em Int. */
fun color(hex: Long): Int = hex.toInt()

/** Mesma cor RGB com outro alfa (0–255). */
fun withAlpha(color: Int, alpha: Int): Int =
    (alpha.coerceIn(0, 255) shl 24) or (color and 0x00FFFFFF)

/** Interpolação linear entre duas cores ARGB. */
fun mixColor(a: Int, b: Int, fraction: Float): Int {
    val f = fraction.coerceIn(0f, 1f)
    var result = 0
    for (shift in intArrayOf(24, 16, 8, 0)) {
        val ca = (a ushr shift) and 0xFF
        val cb = (b ushr shift) and 0xFF
        result = result or (((ca + (cb - ca) * f).toInt() and 0xFF) shl shift)
    }
    return result
}

/** Matriz de Bayer 4×4 (0–15) para dithering ordenado. */
val BAYER_4X4 = intArrayOf(0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5)

/** Ruído determinístico, sem alocação, para gerar cenas procedurais. */
object Noise {
    fun hash(n: Int): Float {
        var x = n * 374761393 + 668265263
        x = (x xor (x ushr 13)) * 1274126177
        x = x xor (x ushr 16)
        return (x and 0xFFFFFF) / 16777215f
    }

    fun hash2(x: Int, y: Int, seed: Int): Float = hash(x * 73856093 xor y * 19349663 xor seed * 83492791)

    fun value(x: Float, y: Float, seed: Int): Float {
        val xi = floor(x).toInt()
        val yi = floor(y).toInt()
        val xf = x - xi
        val yf = y - yi
        val u = xf * xf * (3f - 2f * xf)
        val v = yf * yf * (3f - 2f * yf)
        val a = hash2(xi, yi, seed)
        val b = hash2(xi + 1, yi, seed)
        val c = hash2(xi, yi + 1, seed)
        val d = hash2(xi + 1, yi + 1, seed)
        val top = a + (b - a) * u
        val bottom = c + (d - c) * u
        return top + (bottom - top) * v
    }

    fun fbm(x: Float, y: Float, seed: Int, octaves: Int = 4): Float {
        var sum = 0f
        var amplitude = .5f
        var frequency = 1f
        var norm = 0f
        for (o in 0 until octaves) {
            sum += value(x * frequency, y * frequency, seed + o * 131) * amplitude
            norm += amplitude
            amplitude *= .5f
            frequency *= 2f
        }
        return sum / norm
    }
}

/**
 * Ferramentas de desenho pixel art sobre um Bitmap pequeno. Reaproveita Canvas e Path entre
 * quadros para não gerar lixo a cada frame. Coordenadas são arredondadas para o pixel.
 */
class PixelPainter {
    private val paint = Paint().apply { isAntiAlias = false; isFilterBitmap = false }
    private val canvas = Canvas()
    private val path = Path()
    private var target: Bitmap? = null

    var width = 0f
        private set
    var height = 0f
        private set

    /** Filtro de cor aplicado a tudo que for desenhado até ser trocado (ex.: luz do dia). */
    var filter: ColorFilter? = null
        set(value) {
            field = value
            paint.colorFilter = value
        }

    fun begin(bitmap: Bitmap) {
        if (target !== bitmap) {
            canvas.setBitmap(bitmap)
            target = bitmap
        }
        width = bitmap.width.toFloat()
        height = bitmap.height.toFloat()
    }

    fun fill(l: Float, t: Float, r: Float, b: Float, color: Int) {
        paint.color = color
        canvas.drawRect(floor(l), floor(t), floor(r), floor(b), paint)
    }

    fun pixel(x: Float, y: Float, color: Int, size: Int = 1) {
        val px = floor(x)
        val py = floor(y)
        paint.color = color
        canvas.drawRect(px, py, px + size, py + size, paint)
    }

    fun triangle(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float, color: Int) {
        path.rewind()
        path.moveTo(x1, y1)
        path.lineTo(x2, y2)
        path.lineTo(x3, y3)
        path.close()
        paint.color = color
        canvas.drawPath(path, paint)
    }

    fun polygon(color: Int, vararg points: Float) {
        if (points.size < 6) return
        path.rewind()
        path.moveTo(points[0], points[1])
        var i = 2
        while (i < points.size) {
            path.lineTo(points[i], points[i + 1])
            i += 2
        }
        path.close()
        paint.color = color
        canvas.drawPath(path, paint)
    }

    /** Círculo cheio montado com linhas de pixels. */
    fun disc(cx: Float, cy: Float, radius: Int, color: Int) {
        if (radius <= 0) return
        val x0 = floor(cx)
        val y0 = floor(cy)
        paint.color = color
        for (row in -radius..radius) {
            val ext = sqrt((radius * radius - row * row).toFloat()).toInt()
            canvas.drawRect(x0 - ext, y0 + row, x0 + ext + 1, y0 + row + 1, paint)
        }
    }

    /** Círculo pontilhado (1 a cada [density] pixels) para halos e brilhos sem transparência. */
    fun ditherDisc(cx: Float, cy: Float, radius: Int, color: Int, density: Int = 2) {
        val x0 = floor(cx).toInt()
        val y0 = floor(cy).toInt()
        paint.color = color
        for (row in -radius..radius) {
            val ext = sqrt((radius * radius - row * row).toFloat()).toInt()
            val y = y0 + row
            for (x in x0 - ext..x0 + ext) {
                val pattern = BAYER_4X4[(y and 3) * 4 + (x and 3)]
                if (pattern < 16 / density) {
                    canvas.drawRect(x.toFloat(), y.toFloat(), x + 1f, y + 1f, paint)
                }
            }
        }
    }

    /** Gradiente vertical em faixas com transições pontilhadas (estilo pixel art). */
    fun verticalGradient(top: Float, bottom: Float, colors: IntArray) {
        val bands = colors.size
        val bandHeight = (bottom - top) / bands
        for (i in 0 until bands) {
            fill(0f, top + i * bandHeight, width, top + (i + 1) * bandHeight + 1f, colors[i])
        }
        for (i in 1 until bands) {
            val boundary = floor(top + i * bandHeight).toInt()
            paint.color = colors[i]
            for (row in 1..3) {
                val y = boundary - row
                val step = 1 shl row
                var x = if ((y and 1) == 0) 0 else step / 2
                while (x < width) {
                    canvas.drawRect(x.toFloat(), y.toFloat(), x + 1f, y + 1f, paint)
                    x += step
                }
            }
        }
    }

    /** Rastro de meteoro/cometa: cabeça brilhante e cauda que some. */
    fun streak(headX: Float, headY: Float, stepX: Float, stepY: Float, length: Int, color: Int, alpha: Float) {
        for (k in 0 until length) {
            val fade = (1f - k / length.toFloat()) * alpha
            pixel(headX + stepX * k, headY + stepY * k, withAlpha(color, (255 * fade).toInt()))
        }
    }

    fun bitmap(source: Bitmap, x: Float, y: Float, alpha: Int = 255) {
        paint.color = -1
        paint.alpha = alpha
        canvas.drawBitmap(source, floor(x), floor(y), paint)
        paint.alpha = 255
    }

    /** Escreve pixels prontos (ex.: nebulosa gerada por ruído) no bitmap alvo. */
    fun setPixels(data: IntArray) {
        val bmp = target ?: return
        bmp.setPixels(data, 0, bmp.width, 0, 0, bmp.width, bmp.height)
    }
}

/**
 * Camada estática desenhada uma única vez e reaproveitada a cada quadro.
 * É redesenhada só quando o tamanho ou a [key] mudam (ex.: a cor do céu ao longo do dia).
 */
class LayerCache {
    private val painter = PixelPainter()
    private var bitmap: Bitmap? = null
    private var lastKey = 0

    fun get(width: Int, height: Int, key: Int = 0, draw: (PixelPainter) -> Unit): Bitmap {
        val current = bitmap
        if (current != null && current.width == width && current.height == height) {
            if (key == lastKey) return current
            // Mesmo tamanho, conteúdo novo: redesenha no próprio bitmap (sem alocar).
            current.eraseColor(0)
            painter.begin(current)
            draw(painter)
            lastKey = key
            return current
        }
        val next = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        painter.begin(next)
        draw(painter)
        bitmap = next
        lastKey = key
        return next
    }
}
