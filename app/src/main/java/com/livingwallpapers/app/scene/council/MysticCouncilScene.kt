package com.livingwallpapers.app.scene.council

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import com.livingwallpapers.app.R
import com.livingwallpapers.app.scene.CanvasScene
import com.livingwallpapers.app.scene.Noise
import com.livingwallpapers.app.settings.WallpaperSettings
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Mystic Council: a ilustração de referência é a base e permanece intacta. Por cima dela:
 *  - fontes de luz pulsam redesenhando a própria região da imagem em modo aditivo (só o que já
 *    brilha fica mais claro; as silhuetas escuras não mudam);
 *  - asas escuras, tentáculo e caudas balançam por deformação de malha localizada;
 *  - partículas, penas, anéis, raios, vapor e névoa são desenhados como efeitos.
 * Todas as coordenadas abaixo estão no espaço da imagem original (941×1672).
 */
class MysticCouncilScene(context: Context) : CanvasScene {
    companion object {
        const val ID = "council"
        private const val IMG_W = 941f
        private const val IMG_H = 1672f
        private const val TAU = (2 * PI).toFloat()
        private const val MESH_COLS = 36
        private const val MESH_ROWS = 64

        // Paleta da especificação.
        private const val AMBER = 0xFFFFB347.toInt()
        private const val BRASS = 0xFFD8A45B.toInt()
        private const val VOID_PURPLE = 0xFF7535FF.toInt()
        private const val VOID_VIOLET = 0xFF9A4DFF.toInt()
        private const val GOLD = 0xFFFFD38A.toInt()
        private const val CELESTIAL = 0xFFFFE7AF.toInt()
        private const val ELDRITCH = 0xFF9B4DFF.toInt()
        private const val COSMIC = 0xFF6325B5.toInt()
        private const val ARCANE_BLUE = 0xFF328BFF.toInt()
        private const val CYAN = 0xFF65C7FF.toInt()
        private const val DUST = 0xFFC9B8F0.toInt()
        private const val FOG = 0xFF3A2752.toInt()
        private const val STEAM = 0xFFB8A9C9.toInt()

        // Regiões das fontes de luz (coordenadas da imagem original).
        private val WINGS = Rect(268, 478, 668, 742)
        private val HALO = Rect(425, 566, 518, 664)
        private val TOME = Rect(360, 672, 456, 746)
        private val ORB = Rect(305, 915, 380, 995)
        private val EYE = Rect(664, 726, 719, 784)
        private val CRYSTAL = Rect(828, 664, 870, 742)
        private val TAILS = Rect(868, 890, 941, 1100)
        private val LIGHT_REGIONS = arrayOf(WINGS, HALO, TOME, ORB, EYE, CRYSTAL, TAILS)

        private class Artwork(val image: Bitmap, val highlights: Array<Bitmap>, val topEdge: Bitmap, val bottomEdge: Bitmap)

        /**
         * Faixa de 1 px para preencher o espaço acima/abaixo da ilustração: média de várias linhas
         * da borda, desfocada na horizontal (esticar uma linha só gera listras).
         */
        private fun smoothEdge(image: Bitmap, firstRow: Int, rows: Int): Bitmap {
            val w = image.width
            val line = IntArray(w * rows)
            image.getPixels(line, 0, w, 0, firstRow, w, rows)
            val r = FloatArray(w); val g = FloatArray(w); val b = FloatArray(w)
            for (y in 0 until rows) for (x in 0 until w) {
                val c = line[y * w + x]
                r[x] += ((c shr 16) and 0xFF) / rows.toFloat()
                g[x] += ((c shr 8) and 0xFF) / rows.toFloat()
                b[x] += (c and 0xFF) / rows.toFloat()
            }
            val radius = 60
            val out = IntArray(w)
            for (x in 0 until w) {
                var sr = 0f; var sg = 0f; var sb = 0f; var n = 0
                for (k in maxOf(0, x - radius)..minOf(w - 1, x + radius)) { sr += r[k]; sg += g[k]; sb += b[k]; n++ }
                out[x] = Color.rgb((sr / n).toInt(), (sg / n).toInt(), (sb / n).toInt())
            }
            return Bitmap.createBitmap(out, w, 1, Bitmap.Config.ARGB_8888)
        }

        // A ilustração (6 MB em memória) é decodificada uma vez e compartilhada entre
        // prévia, tela inicial e tela de bloqueio.
        @Volatile private var artwork: Artwork? = null

        private fun loadArtwork(context: Context): Artwork = artwork ?: synchronized(this) {
            artwork ?: run {
                val image = BitmapFactory.decodeResource(
                    context.applicationContext.resources, R.drawable.mystic_council,
                    BitmapFactory.Options().apply { inScaled = false; inPreferredConfig = Bitmap.Config.ARGB_8888 },
                )
                Artwork(
                    image,
                    Array(LIGHT_REGIONS.size) { extractHighlights(image, LIGHT_REGIONS[it]) },
                    smoothEdge(image, 0, 6),
                    smoothEdge(image, image.height - 6, 6),
                )
            }.also { artwork = it }
        }

        /**
         * Mantém só as partes luminosas de uma região (o resto vira preto). Somado em modo
         * aditivo, intensifica asas, orbes e cristais sem clarear o fundo nem as silhuetas.
         */
        private fun extractHighlights(image: Bitmap, region: Rect): Bitmap {
            val w = region.width()
            val h = region.height()
            val pixels = IntArray(w * h)
            image.getPixels(pixels, 0, w, region.left, region.top, w, h)
            for (i in pixels.indices) {
                val c = pixels[i]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                val lum = maxOf(r, g, b) / 255f
                val x = ((lum - .38f) / .47f).coerceIn(0f, 1f)
                val k = x * x * (3f - 2f * x)
                pixels[i] = Color.rgb((r * k).toInt(), (g * k).toInt(), (b * k).toInt())
            }
            return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
        }

        /** Brilho radial branco reutilizado (tingido por color filter) por todos os efeitos de luz. */
        private fun makeGlow(): Bitmap {
            val size = 64
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(size / 2f, size / 2f, size / 2f,
                    intArrayOf(Color.WHITE, Color.argb(150, 255, 255, 255), Color.argb(40, 255, 255, 255), Color.TRANSPARENT),
                    floatArrayOf(0f, .3f, .65f, 1f), Shader.TileMode.CLAMP)
            }
            Canvas(bmp).drawCircle(size / 2f, size / 2f, size / 2f, paint)
            return bmp
        }
    }

    private val artwork = loadArtwork(context)
    private val art = artwork.image
    private val glow = makeGlow()

    // ---------- Pincéis (criados uma vez) ----------

    private val imagePaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val addImagePaint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
    }
    private val fogPaint = tinted(FOG, additive = false)
    private val steamPaint = tinted(STEAM, additive = false)
    private val amberGlow = tinted(AMBER)
    private val brassGlow = tinted(BRASS)
    private val goldGlow = tinted(GOLD)
    private val celestialGlow = tinted(CELESTIAL)
    private val voidGlow = tinted(VOID_PURPLE)
    private val violetGlow = tinted(VOID_VIOLET)
    private val eldritchGlow = tinted(ELDRITCH)
    private val cosmicGlow = tinted(COSMIC)
    private val blueGlow = tinted(ARCANE_BLUE)
    private val cyanGlow = tinted(CYAN)
    private val dustGlow = tinted(DUST)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
    }
    private val solidPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private fun tinted(color: Int, additive: Boolean = true) = Paint(Paint.FILTER_BITMAP_FLAG).apply {
        colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
        if (additive) xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
    }

    // Objetos reutilizados a cada quadro (sem alocação no laço de desenho).
    private val rectF = RectF()
    private val src = Rect()
    private val dst = RectF()

    // ---------- Malha para movimentos orgânicos ----------

    private val meshBase = FloatArray((MESH_COLS + 1) * (MESH_ROWS + 1) * 2).also { base ->
        var i = 0
        for (r in 0..MESH_ROWS) for (c in 0..MESH_COLS) {
            base[i++] = c * IMG_W / MESH_COLS
            base[i++] = r * IMG_H / MESH_ROWS
        }
    }
    private val meshVerts = meshBase.copyOf()

    /** Região que gira levemente em torno de um pivô, com efeito sumindo suavemente na borda. */
    private class Bend(
        val cx: Float, val cy: Float, val rx: Float, val ry: Float,
        val px: Float, val py: Float, val amplitude: Float, val period: Float, val phase: Float,
    )

    private val bends = arrayOf(
        // Asas negras do mago: "respiração" quase imperceptível.
        Bend(235f, 800f, 125f, 160f, 290f, 905f, .012f, 9f, 0f),
        // Tentáculo do feiticeiro: balanço lento e orgânico (parte alta e laço inferior).
        Bend(765f, 815f, 70f, 85f, 730f, 900f, .045f, 7.5f, 1.2f),
        Bend(655f, 950f, 70f, 62f, 722f, 905f, .018f, 7.5f, 2.1f),
        // Caudas do mago raposa: cada grupo com atraso próprio, como uma brisa.
        Bend(912f, 960f, 52f, 95f, 870f, 1085f, .035f, 6.2f, .3f),
        Bend(915f, 1060f, 45f, 60f, 870f, 1100f, .03f, 6.2f, 1.4f),
        Bend(728f, 975f, 42f, 70f, 790f, 1060f, .025f, 6.8f, 2.6f),
    )

    private fun updateMesh(t: Float) {
        System.arraycopy(meshBase, 0, meshVerts, 0, meshBase.size)
        for (b in bends) {
            val angle = b.amplitude * sin(TAU * t / b.period + b.phase)
            val x0 = b.cx - b.rx
            val x1 = b.cx + b.rx
            val y0 = b.cy - b.ry
            val y1 = b.cy + b.ry
            var i = 0
            while (i < meshBase.size) {
                val x = meshBase[i]
                val y = meshBase[i + 1]
                if (x in x0..x1 && y in y0..y1) {
                    val nx = (x - b.cx) / b.rx
                    val ny = (y - b.cy) / b.ry
                    val d = nx * nx + ny * ny
                    if (d < 1f) {
                        val f = (1f - d) * (1f - d)
                        val a = angle * f
                        // Rotação de ângulo pequeno em torno do pivô.
                        meshVerts[i] += -(y - b.py) * a
                        meshVerts[i + 1] += (x - b.px) * a
                    }
                }
                i += 2
            }
        }
    }

    // ---------- Dados fixos dos efeitos (gerados uma vez) ----------

    private val dust = FloatArray(60 * 4).also { a ->
        for (i in 0 until 60) {
            a[i * 4] = Noise.hash(i * 7 + 1) * IMG_W
            a[i * 4 + 1] = 250f + Noise.hash(i * 7 + 2) * 1300f
            a[i * 4 + 2] = Noise.hash(i * 7 + 3) * TAU
            a[i * 4 + 3] = .5f + Noise.hash(i * 7 + 4)
        }
    }

    override fun draw(canvas: Canvas, width: Int, height: Int, elapsedSeconds: Float,
                      tiltX: Float, tiltY: Float, settings: WallpaperSettings) {
        val t = elapsedSeconds * settings.timeScale() * 2f
        val saver = settings.batterySaver
        val particleScale = settings.effectiveParticles() / 77f * (if (saver) .5f else 1f)
        val dx = if (settings.parallax) tiltX.coerceIn(-1f, 1f) else 0f
        val dy = if (settings.parallax) tiltY.coerceIn(-1f, 1f) else 0f

        // Encaixe: largura total (+4% de folga para o parallax), espaço extra no topo
        // para o relógio — a composição e os cinco personagens ficam inteiros.
        val scale = width / IMG_W * 1.04f
        val offsetX = (width - IMG_W * scale) / 2f + dx * 12f * scale
        val offsetY = (height - IMG_H * scale) * .85f + dy * 6f * scale

        canvas.drawColor(Color.BLACK)
        canvas.save()
        canvas.translate(offsetX, offsetY)
        canvas.scale(scale, scale)

        // Camada 1: fundo — a primeira/última linha da imagem esticada preenche o que sobra.
        dst.set(-40f, -4000f, IMG_W + 40f, 1f)
        canvas.drawBitmap(artwork.topEdge, null, dst, imagePaint)
        dst.set(-40f, IMG_H - 1f, IMG_W + 40f, IMG_H + 4000f)
        canvas.drawBitmap(artwork.bottomEdge, null, dst, imagePaint)

        // Camada 2–4: ilustração com malha deformada (asas, tentáculo, caudas).
        updateMesh(t)
        canvas.drawBitmapMesh(art, MESH_COLS, MESH_ROWS, meshVerts, 0, null, 0, imagePaint)

        // Camada 5: magias e luzes de cada personagem, cada um com seu próprio ciclo.
        divineRays(canvas, t, if (saver) .6f else 1f)
        angel(canvas, t, saver, particleScale)
        automaton(canvas, t, saver)
        darkMage(canvas, t, particleScale)
        eldritch(canvas, t, particleScale)
        foxMage(canvas, t, particleScale)

        // Névoa baixa e partículas atmosféricas (com parallax extra, mais "à frente").
        canvas.save()
        canvas.translate(dx * 10f, dy * 5f)
        fog(canvas, t, saver)
        atmosphere(canvas, t, particleScale)
        canvas.restore()

        canvas.restore()
    }

    // ---------- Ajudantes ----------

    private fun wave(t: Float, period: Float, phase: Float = 0f): Float = .5f + .5f * sin(TAU * t / period + phase)

    /** Brilho radial tingido centrado em (x, y). */
    private fun glow(canvas: Canvas, paint: Paint, x: Float, y: Float, rx: Float, ry: Float, alpha: Float) {
        if (alpha <= .004f) return
        paint.alpha = (alpha.coerceIn(0f, 1f) * 255).toInt()
        rectF.set(x - rx, y - ry, x + rx, y + ry)
        canvas.drawBitmap(glow, null, rectF, paint)
    }

    /** Intensifica as partes luminosas de uma região somando seu mapa de brilho (modo aditivo). */
    private fun boost(canvas: Canvas, region: Rect, alpha: Float) {
        if (alpha <= .004f) return
        val index = LIGHT_REGIONS.indexOf(region)
        addImagePaint.alpha = (alpha.coerceIn(0f, 1f) * 255).toInt()
        canvas.drawBitmap(artwork.highlights[index], region.left.toFloat(), region.top.toFloat(), addImagePaint)
    }

    /** Ponto de luz: halo suave + núcleo. */
    private fun spark(canvas: Canvas, paint: Paint, core: Paint, x: Float, y: Float, r: Float, alpha: Float) {
        glow(canvas, paint, x, y, r * 3f, r * 3f, alpha * .7f)
        glow(canvas, core, x, y, r, r, alpha)
    }

    /** Ciclo de vida 0..1 de um elemento recorrente; retorna -1 quando está "dormindo". */
    private fun life(t: Float, period: Float, offset: Float, activeFraction: Float): Float {
        val p = ((t / period + offset) % 1f + 1f) % 1f
        return if (p < activeFraction) p / activeFraction else -1f
    }

    // ---------- Personagem 3: figura angelical (centro) ----------

    private val angelX = 470f
    private val angelY = 615f

    private fun divineRays(canvas: Canvas, t: Float, strength: Float) {
        val intensity = wave(t, 11f)
        for (k in 0 until 7) {
            val angle = (-150f + k * 20f + 4f * sin(TAU * t / 23f + k)) * PI.toFloat() / 180f
            val length = 420f + 70f * Noise.hash(k + 40)
            val dist = 170f + length / 2f
            val alpha = (.05f + .06f * intensity) * (.6f + .4f * wave(t, 7f + k, k * 1.7f)) * strength
            canvas.save()
            canvas.translate(angelX + cos(angle) * dist, angelY + sin(angle) * dist)
            canvas.rotate(angle * 180f / PI.toFloat())
            glow(canvas, goldGlow, 0f, 0f, length / 2f, 16f, alpha)
            canvas.restore()
        }
    }

    private fun angel(canvas: Canvas, t: Float, saver: Boolean, particleScale: Float) {
        // Asas: respiração celestial (luminosidade sobe e desce).
        val breath = wave(t, 8f)
        glow(canvas, goldGlow, angelX, angelY, 270f, 170f, .08f + .08f * breath)
        boost(canvas, WINGS, .12f + .22f * breath)

        // Halo: pulso próprio e pontos de luz percorrendo a circunferência.
        val haloPulse = wave(t, 5.5f, 1f)
        boost(canvas, HALO, .15f + .35f * haloPulse)
        for (k in 0 until 3) {
            val a = t * .5f + k * TAU / 3f
            val x = angelX + cos(a) * 37f
            val y = angelY + sin(a) * 38f
            // O anel passa atrás do capuz: some sobre a cabeça.
            val hx = (x - angelX) / 30f
            val hy = (y - 650f) / 45f
            if (hx * hx + hy * hy < 1f) continue
            spark(canvas, goldGlow, celestialGlow, x, y, 2.2f, .55f + .35f * haloPulse)
        }

        // Livro: brilho dourado discreto e partículas subindo das páginas.
        val tome = wave(t, 6.5f, 2.3f)
        boost(canvas, TOME, .08f + .18f * tome)
        glow(canvas, goldGlow, 408f, 705f, 40f, 30f, .06f + .08f * tome)
        val tomeParticles = max(2, (6 * particleScale).toInt())
        for (k in 0 until tomeParticles) {
            val p = life(t, 5f, k / tomeParticles.toFloat(), 1f)
            if (p < 0f) continue
            val x = 385f + 45f * Noise.hash(k + 10) + 5f * sin(t * 1.3f + k)
            val y = 700f - p * 70f
            spark(canvas, goldGlow, celestialGlow, x, y, 1.1f, sin(p * PI.toFloat()) * .7f)
        }

        // Penas douradas se desprendem de vez em quando e descem devagar.
        val feathers = if (saver) 2 else 5
        for (k in 0 until feathers) {
            val p = life(t, 16f, k / feathers.toFloat() + Noise.hash(k + 70) * .1f, .7f)
            if (p < 0f) continue
            val leftWing = k % 2 == 0
            val startX = if (leftWing) 300f + 120f * Noise.hash(k + 80) else 520f + 120f * Noise.hash(k + 80)
            val startY = 520f + 150f * Noise.hash(k + 90)
            val x = startX + sin(p * 9f + k) * 14f + (if (leftWing) -30f else 30f) * p
            val y = startY + p * 300f
            val alpha = min(p / .1f, 1f) * min((1f - p) / .4f, 1f) * .8f
            canvas.save()
            canvas.translate(x, y)
            canvas.rotate(35f * sin(p * 9f + k))
            glow(canvas, goldGlow, 0f, 0f, 9f, 3.5f, alpha * .6f)
            glow(canvas, celestialGlow, 0f, 0f, 5f, 1.4f, alpha)
            canvas.restore()
        }
    }

    // ---------- Personagem 1: autômato pistoleiro (extrema esquerda) ----------

    private fun automaton(canvas: Canvas, t: Float, saver: Boolean) {
        // Olhos mecânicos: pulso lento + pequenas oscilações de energia.
        val flicker = .9f + .1f * sin(t * 17f) * sin(t * 7.3f)
        val base = (.55f + .35f * wave(t, 4.2f, .5f)) * flicker
        drawMechanicalEye(canvas, 132f, 872f, base, blink(t, 13f, 0f))
        drawMechanicalEye(canvas, 149f, 873f, base, blink(t, 17f, .55f))

        // Vapor discreto saindo do pescoço/ombro, em rajadas pouco frequentes.
        if (!saver) {
            for (k in 0 until 4) {
                val p = life(t, 14f, k * .06f, .3f)
                if (p < 0f) continue
                val x = 172f + p * 18f + sin(p * 6f + k) * 4f
                val y = 912f - p * 55f
                val r = 5f + p * 13f
                glow(canvas, steamPaint, x, y, r, r * .8f, sin(p * PI.toFloat()) * .22f)
            }
        }

        // Energia mecânica: pontos dourados acendendo brevemente pelo corpo.
        for (k in 0 until 5) {
            val p = life(t, 6f + k, Noise.hash(k + 20), .12f)
            if (p < 0f) continue
            val x = 95f + 95f * Noise.hash(k + 30)
            val y = 950f + 160f * Noise.hash(k + 35)
            spark(canvas, brassGlow, amberGlow, x, y, 1.2f, sin(p * PI.toFloat()) * .8f)
        }
    }

    /** 0 = aberto, 1 = fechado. Piscadas curtas em intervalos próprios. */
    private fun blink(t: Float, period: Float, offset: Float): Float {
        val p = ((t / period + offset) % 1f + 1f) % 1f
        val window = .35f / period
        return if (p < window) sin(p / window * PI.toFloat()) else 0f
    }

    private fun drawMechanicalEye(canvas: Canvas, x: Float, y: Float, intensity: Float, closed: Float) {
        if (closed > .05f) {
            // Pálpebra mecânica: apaga o brilho original do olho.
            solidPaint.color = Color.argb((closed * 255).toInt(), 14, 10, 22)
            canvas.drawCircle(x, y, 3.2f, solidPaint)
        }
        val open = 1f - closed
        glow(canvas, amberGlow, x, y, 12f, 9f, intensity * .45f * open)
        glow(canvas, brassGlow, x, y, 3.5f, 3f, intensity * open)
    }

    // ---------- Personagem 2: mago das asas negras (esquerda central) ----------

    private val orbX = 342f
    private val orbY = 954f

    private fun darkMage(canvas: Canvas, t: Float, particleScale: Float) {
        val pulse = wave(t, 4.6f, 3f)
        // Aura violeta iluminando braço e tronco.
        glow(canvas, voidGlow, orbX, orbY - 10f, 85f, 75f, .12f + .1f * pulse)
        boost(canvas, ORB, .2f + .35f * pulse)
        glow(canvas, violetGlow, orbX, orbY, 16f, 16f, .35f + .25f * pulse)

        // Onda gravitacional: anel que se contrai em direção ao núcleo.
        val ring = (t / 5.5f) % 1f
        ringPaint.color = VOID_VIOLET
        ringPaint.strokeWidth = 1.2f
        ringPaint.alpha = (sin(ring * PI.toFloat()) * 70).toInt()
        canvas.drawCircle(orbX, orbY, 36f - ring * 28f, ringPaint)

        // Fragmentos orbitando o núcleo (órbita inclinada: mais brilho na frente).
        for (k in 0 until 7) {
            val a = t * (.9f + k * .12f) + k * TAU / 7f
            val rx = 20f + 6f * Noise.hash(k + 50)
            val x = orbX + cos(a) * rx
            val y = orbY + sin(a) * rx * .55f
            val front = .45f + .55f * (.5f + .5f * sin(a))
            spark(canvas, voidGlow, violetGlow, x, y, 1.3f, .7f * front)
        }

        // Partículas sendo atraídas em espiral para o núcleo.
        val count = max(3, (10 * particleScale).toInt())
        for (k in 0 until count) {
            val p = life(t, 4f + k * .3f, Noise.hash(k + 60), 1f)
            val dist = 48f * (1f - p) + 3f
            val a = Noise.hash(k + 65) * TAU + p * 4f
            val x = orbX + cos(a) * dist
            val y = orbY + sin(a) * dist * .8f
            spark(canvas, voidGlow, violetGlow, x, y, 1f, sin(p * PI.toFloat()) * .65f)
        }
    }

    // ---------- Personagem 4: feiticeiro eldritch (direita central) ----------

    private val eyeX = 691f
    private val eyeY = 755f

    private fun eldritch(canvas: Canvas, t: Float, particleScale: Float) {
        val pulse = wave(t, 3.4f, 4.2f)

        // Aura escura que se expande e some atrás da silhueta.
        val aura = (t / 12f) % 1f
        glow(canvas, cosmicGlow, 640f, 820f, 90f + aura * 50f, 100f + aura * 55f, (1f - aura) * .12f)

        glow(canvas, eldritchGlow, eyeX, eyeY, 48f, 44f, .14f + .14f * pulse)
        boost(canvas, EYE, .2f + .45f * pulse)

        // Anéis mágicos girando ao redor do olho.
        ringPaint.color = ELDRITCH
        ringPaint.strokeWidth = 1f
        drawRing(canvas, 27f, 9f, t * 22f, .28f + .2f * pulse)
        drawRing(canvas, 21f, 7f, -t * 15f + 60f, .22f + .18f * pulse)

        // Partículas orbitando o símbolo.
        val orbiters = max(3, (6 * particleScale).toInt())
        for (k in 0 until orbiters) {
            val a = t * .7f + k * TAU / orbiters
            val x = eyeX + cos(a) * 31f
            val y = eyeY + sin(a) * 13f
            spark(canvas, eldritchGlow, cosmicGlow, x, y, 1.1f, .5f + .3f * sin(a))
        }

        // Pupila: pequenos olhares ocasionais (alvo muda a cada ~3 s, com transição suave).
        val step = floor(t / 3.2f).toInt()
        val blend = smooth(((t / 3.2f) - step) / .25f)
        val lookX = lerp(look(step - 1, 0), look(step, 0), blend)
        val lookY = lerp(look(step - 1, 1), look(step, 1), blend) * .5f
        solidPaint.color = Color.argb(235, 28, 8, 52)
        canvas.drawCircle(eyeX + lookX, eyeY + lookY, 2.6f, solidPaint)
        glow(canvas, violetGlow, eyeX + lookX - .6f, eyeY + lookY - .6f, 1.6f, 1.6f, .9f)

        // Piscada lenta em intervalos irregulares.
        val closed = max(blink(t, 9.5f, .2f), blink(t, 23f, .7f))
        if (closed > .05f) {
            solidPaint.color = Color.argb((closed * 255).toInt(), 70, 26, 140)
            rectF.set(eyeX - 12f, eyeY - 7f * closed, eyeX + 12f, eyeY + 7f * closed)
            canvas.drawOval(rectF, solidPaint)
        }
    }

    private fun look(step: Int, axis: Int): Float = (Noise.hash(step * 2 + axis + 900) - .5f) * 3f

    private fun drawRing(canvas: Canvas, rx: Float, ry: Float, degrees: Float, alpha: Float) {
        ringPaint.alpha = (alpha.coerceIn(0f, 1f) * 255).toInt()
        canvas.save()
        canvas.rotate(degrees, eyeX, eyeY)
        rectF.set(eyeX - rx, eyeY - ry, eyeX + rx, eyeY + ry)
        canvas.drawOval(rectF, ringPaint)
        canvas.restore()
    }

    // ---------- Personagem 5: mago raposa (extrema direita) ----------

    private val crystalX = 848f
    private val crystalY = 703f

    private fun foxMage(canvas: Canvas, t: Float, particleScale: Float) {
        val pulse = wave(t, 4f, 5.1f)
        glow(canvas, blueGlow, crystalX, crystalY, 42f, 50f, .18f + .18f * pulse)
        boost(canvas, CRYSTAL, .2f + .45f * pulse)
        glow(canvas, cyanGlow, crystalX, crystalY + 4f, 8f, 16f, .3f + .3f * pulse)

        // Bordas azuis das caudas acompanham o pulso do cristal.
        boost(canvas, TAILS, .06f + .1f * pulse)

        // Fragmentos azuis se desprendendo e subindo.
        val count = max(3, (8 * particleScale).toInt())
        for (k in 0 until count) {
            val p = life(t, 4.5f + k * .4f, Noise.hash(k + 110), 1f)
            val x = crystalX + (Noise.hash(k + 115) - .5f) * 20f + sin(t * 1.4f + k) * 6f
            val y = crystalY + 10f - p * 95f
            spark(canvas, blueGlow, cyanGlow, x, y, 1.1f, sin(p * PI.toFloat()) * .75f)
        }

        // Símbolos arcanos surgindo ao redor do cajado.
        ringPaint.color = CYAN
        ringPaint.strokeWidth = 1f
        for (k in 0 until 3) {
            val p = life(t, 7f + k * 1.5f, k * .33f, .6f)
            if (p < 0f) continue
            val alpha = sin(p * PI.toFloat())
            val x = 860f + 30f * Noise.hash(k + 120) + p * 8f
            val y = 770f + 130f * Noise.hash(k + 125) - p * 20f
            glow(canvas, blueGlow, x, y, 12f, 12f, alpha * .35f)
            ringPaint.alpha = (alpha * 200).toInt()
            canvas.save()
            canvas.translate(x, y)
            canvas.rotate(45f + p * 90f)
            canvas.drawRect(-3.5f, -3.5f, 3.5f, 3.5f, ringPaint)
            canvas.restore()
        }
    }

    // ---------- Efeitos globais ----------

    private fun fog(canvas: Canvas, t: Float, saver: Boolean) {
        val layers = if (saver) 3 else 5
        val span = IMG_W + 700f
        for (k in 0 until layers) {
            val speed = 9f + 5f * k
            val x = ((Noise.hash(k + 200) * span + t * speed) % span) - 350f
            val y = 1330f + 70f * k
            glow(canvas, fogPaint, x, y, 330f, 70f, .3f + .1f * wave(t, 13f + k, k.toFloat()))
        }
    }

    private fun atmosphere(canvas: Canvas, t: Float, particleScale: Float) {
        val count = (dust.size / 4 * particleScale).toInt().coerceIn(6, dust.size / 4)
        for (i in 0 until count) {
            val baseX = dust[i * 4]
            val baseY = dust[i * 4 + 1]
            val phase = dust[i * 4 + 2]
            val speed = dust[i * 4 + 3]
            val y = baseY - ((t * 6f * speed) % 300f)
            val x = baseX + sin(t * .3f * speed + phase) * 12f
            val alpha = .12f + .18f * wave(t, 5f + speed * 3f, phase)
            glow(canvas, dustGlow, x, y, 2.2f, 2.2f, alpha * fadeEdge((t * 6f * speed) % 300f / 300f))
        }
    }

    private fun fadeEdge(p: Float): Float = min(p / .15f, 1f) * min((1f - p) / .15f, 1f)
    private fun smooth(x: Float): Float { val c = x.coerceIn(0f, 1f); return c * c * (3f - 2f * c) }
    private fun lerp(a: Float, b: Float, f: Float): Float = a + (b - a) * f
}
