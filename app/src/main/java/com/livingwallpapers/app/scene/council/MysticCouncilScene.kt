package com.livingwallpapers.app.scene.council

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import com.livingwallpapers.app.R
import com.livingwallpapers.app.scene.CanvasScene
import com.livingwallpapers.app.scene.Noise
import com.livingwallpapers.app.scene.mixColor
import com.livingwallpapers.app.settings.WallpaperSettings
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Mystic Council: a ilustração de referência é a base e permanece intacta. Por cima dela:
 *  - os personagens respiram, as asas do anjo batem devagar e ele levita; asas negras,
 *    tentáculo, cabelos e caudas balançam — tudo por deformação de malha localizada;
 *  - as fontes de luz pulsam somando um "mapa de brilho" da própria imagem, deformado pela
 *    mesma malha (fica sempre alinhado ao que se move);
 *  - um círculo mágico na mesa e fios de energia ligam as magias do conselho; de tempos em
 *    tempos uma onda mágica acende as fontes em sequência;
 *  - partículas, penas, anéis, raios, vapor e névoa completam a atmosfera.
 * Todas as coordenadas estão no espaço da imagem original (941×1672).
 */
class MysticCouncilScene(context: Context) : CanvasScene {
    companion object {
        const val ID = "council"
        private const val IMG_W = 941f
        private const val IMG_H = 1672f
        private const val TAU = (2 * PI).toFloat()
        private const val MESH_COLS = 36
        private const val MESH_ROWS = 64
        private const val CELL_W = IMG_W / MESH_COLS
        private const val CELL_H = IMG_H / MESH_ROWS

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

        // Fontes de luz: retângulos aproximados (imagem original), alinhados depois à malha.
        private const val WINGS = 0
        private const val HALO = 1
        private const val TOME = 2
        private const val ORB = 3
        private const val EYE = 4
        private const val CRYSTAL = 5
        private const val TAILS = 6
        private val LIGHT_RECTS = arrayOf(
            floatArrayOf(268f, 478f, 668f, 742f), // asas douradas
            floatArrayOf(425f, 566f, 518f, 664f), // halo
            floatArrayOf(360f, 672f, 456f, 746f), // livro
            floatArrayOf(305f, 915f, 380f, 995f), // orbe do vazio
            floatArrayOf(664f, 726f, 719f, 784f), // olho eldritch
            floatArrayOf(828f, 664f, 870f, 742f), // cristal
            floatArrayOf(868f, 890f, 941f, 1100f), // bordas azuis das caudas
        )

        /** Células da malha [c0, r0, c1, r1] que cobrem cada fonte de luz. */
        private val LIGHT_CELLS = Array(LIGHT_RECTS.size) { i ->
            val r = LIGHT_RECTS[i]
            intArrayOf(
                floor(r[0] / CELL_W).toInt().coerceIn(0, MESH_COLS - 1),
                floor(r[1] / CELL_H).toInt().coerceIn(0, MESH_ROWS - 1),
                ceil(r[2] / CELL_W).toInt().coerceIn(1, MESH_COLS),
                ceil(r[3] / CELL_H).toInt().coerceIn(1, MESH_ROWS),
            )
        }

        private class Artwork(val image: Bitmap, val highlights: Array<Bitmap>, val topEdge: Bitmap, val bottomEdge: Bitmap)

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
                    Array(LIGHT_CELLS.size) { extractHighlights(image, LIGHT_CELLS[it]) },
                    smoothEdge(image, 0, 6),
                    smoothEdge(image, image.height - 6, 6),
                )
            }.also { artwork = it }
        }

        /**
         * Mantém só as partes luminosas das células indicadas (o resto vira preto). Somado em modo
         * aditivo, intensifica asas, orbes e cristais sem clarear o fundo nem as silhuetas.
         */
        private fun extractHighlights(image: Bitmap, cells: IntArray): Bitmap {
            val x0 = (cells[0] * CELL_W).roundToInt()
            val y0 = (cells[1] * CELL_H).roundToInt()
            val x1 = min((cells[2] * CELL_W).roundToInt(), image.width)
            val y1 = min((cells[3] * CELL_H).roundToInt(), image.height)
            val w = x1 - x0
            val h = y1 - y0
            val pixels = IntArray(w * h)
            image.getPixels(pixels, 0, w, x0, y0, w, h)
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

        // Tipos de movimento da malha.
        private const val ROTATE = 0 // gira em torno do pivô (amplitude em radianos)
        private const val BREATH = 1 // estica/encolhe a partir do pivô (amplitude = fração)
        private const val LIFT = 2   // sobe/desce (amplitude em pixels)
    }

    private val artwork = loadArtwork(context)
    private val art = artwork.image
    private val glow = makeGlow()

    // ---------- Pincéis (criados uma vez) ----------

    private val imagePaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val addImagePaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply {
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
        strokeCap = Paint.Cap.ROUND
        xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
    }
    private val solidPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    /** Cores do conselho (círculo da mesa e brasas alternam entre elas). */
    private val councilColors = intArrayOf(GOLD, VOID_VIOLET, ARCANE_BLUE, ELDRITCH)
    private val councilGlows = arrayOf(goldGlow, violetGlow, blueGlow, eldritchGlow)

    private fun tinted(color: Int, additive: Boolean = true) = Paint(Paint.FILTER_BITMAP_FLAG).apply {
        colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
        if (additive) xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
    }

    // Objetos reutilizados a cada quadro (sem alocação no laço de desenho).
    private val rectF = RectF()
    private val dst = RectF()

    // ---------- Malha para movimentos orgânicos ----------

    private val meshBase = FloatArray((MESH_COLS + 1) * (MESH_ROWS + 1) * 2).also { base ->
        var i = 0
        for (r in 0..MESH_ROWS) for (c in 0..MESH_COLS) {
            base[i++] = c * CELL_W
            base[i++] = r * CELL_H
        }
    }
    private val meshVerts = meshBase.copyOf()
    private val lightVerts = Array(LIGHT_CELLS.size) { i ->
        val c = LIGHT_CELLS[i]
        FloatArray((c[2] - c[0] + 1) * (c[3] - c[1] + 1) * 2)
    }

    /** Movimento localizado: some suavemente até a borda da elipse (cx, cy, rx, ry). */
    private class Motion(
        val kind: Int, val cx: Float, val cy: Float, val rx: Float, val ry: Float,
        val px: Float, val py: Float, val amplitude: Float, val period: Float, val phase: Float,
    ) {
        var value = 0f
    }

    private val motions = arrayOf(
        // Anjo: levita devagar e as asas batem em espelho, quase imperceptível.
        Motion(LIFT, 470f, 760f, 175f, 300f, 0f, 0f, 2.6f, 7f, 0f),
        Motion(ROTATE, 352f, 600f, 92f, 128f, 445f, 690f, .024f, 5.2f, .6f),
        Motion(ROTATE, 588f, 600f, 92f, 128f, 495f, 690f, -.024f, 5.2f, .6f),
        // Respiração de cada personagem sentado (cada um no seu ritmo).
        Motion(BREATH, 140f, 960f, 110f, 150f, 140f, 1110f, .009f, 5.6f, 0f),
        Motion(BREATH, 320f, 930f, 100f, 120f, 320f, 1060f, .01f, 6.3f, 1f),
        Motion(BREATH, 600f, 900f, 130f, 150f, 600f, 1060f, .009f, 7f, 2f),
        Motion(BREATH, 800f, 950f, 100f, 130f, 800f, 1090f, .01f, 5.9f, 3f),
        // Asas negras do mago.
        Motion(ROTATE, 235f, 800f, 125f, 160f, 290f, 905f, .02f, 7.5f, 0f),
        // Tentáculo do feiticeiro (parte alta e laço inferior) e mechas de cabelo.
        Motion(ROTATE, 765f, 815f, 70f, 85f, 730f, 900f, .065f, 6.5f, 1.2f),
        Motion(ROTATE, 655f, 950f, 70f, 62f, 722f, 905f, .025f, 6.5f, 2.1f),
        Motion(ROTATE, 598f, 795f, 48f, 48f, 612f, 760f, .035f, 4.4f, .8f),
        // Caudas e orelhas do mago raposa: atrasos diferentes, como uma brisa.
        Motion(ROTATE, 912f, 960f, 52f, 95f, 870f, 1085f, .05f, 5.6f, .3f),
        Motion(ROTATE, 915f, 1060f, 45f, 60f, 870f, 1100f, .045f, 5.6f, 1.4f),
        Motion(ROTATE, 728f, 975f, 42f, 70f, 790f, 1060f, .035f, 6.2f, 2.6f),
        Motion(ROTATE, 800f, 868f, 40f, 34f, 800f, 900f, .03f, 3.7f, .2f),
    )

    private var dispX = 0f
    private var dispY = 0f

    /** Deslocamento total do ponto (x, y) pelos movimentos atuais → dispX/dispY. */
    private fun displace(x: Float, y: Float) {
        var sx = 0f
        var sy = 0f
        for (m in motions) {
            val nx = (x - m.cx) / m.rx
            if (nx <= -1f || nx >= 1f) continue
            val ny = (y - m.cy) / m.ry
            if (ny <= -1f || ny >= 1f) continue
            val d = nx * nx + ny * ny
            if (d >= 1f) continue
            val f = (1f - d) * (1f - d) * m.value
            when (m.kind) {
                ROTATE -> { sx += -(y - m.py) * f; sy += (x - m.px) * f }
                BREATH -> { sx += (x - m.px) * f * .35f; sy += (y - m.py) * f }
                else -> sy += f
            }
        }
        dispX = sx
        dispY = sy
    }

    private fun updateMesh(t: Float) {
        for (m in motions) m.value = m.amplitude * sin(TAU * t / m.period + m.phase)
        var i = 0
        while (i < meshBase.size) {
            val x = meshBase[i]
            val y = meshBase[i + 1]
            displace(x, y)
            meshVerts[i] = x + dispX
            meshVerts[i + 1] = y + dispY
            i += 2
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

    // Intensidade da onda mágica em cada personagem neste quadro (0..1).
    private var surgeAngel = 0f
    private var surgeAutomaton = 0f
    private var surgeMage = 0f
    private var surgeEldritch = 0f
    private var surgeFox = 0f
    private var surgeTable = 0f

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

        surgeAngel = surge(t, 0f)
        surgeAutomaton = surge(t, .35f)
        surgeMage = surge(t, .5f)
        surgeEldritch = surge(t, .6f)
        surgeFox = surge(t, .75f)
        surgeTable = surge(t, 1.1f)

        canvas.drawColor(Color.BLACK)
        canvas.save()
        canvas.translate(offsetX, offsetY)
        canvas.scale(scale, scale)

        // Camada 1: fundo — faixas suavizadas da própria borda preenchem o que sobra.
        dst.set(-40f, -4000f, IMG_W + 40f, 1f)
        canvas.drawBitmap(artwork.topEdge, null, dst, imagePaint)
        dst.set(-40f, IMG_H - 1f, IMG_W + 40f, IMG_H + 4000f)
        canvas.drawBitmap(artwork.bottomEdge, null, dst, imagePaint)

        // Camadas 2–4: raios atrás de tudo que é luz, ilustração com a malha deformada.
        updateMesh(t)
        canvas.drawBitmapMesh(art, MESH_COLS, MESH_ROWS, meshVerts, 0, null, 0, imagePaint)
        divineRays(canvas, t, if (saver) .6f else 1f)

        // Camada 5: o conselho conectado — círculo na mesa e fios de energia.
        tableSigil(canvas, t, particleScale)
        energyThreads(canvas, t, saver)

        // Magias e luzes de cada personagem, cada um com seu próprio ciclo.
        angel(canvas, t, saver, particleScale)
        automaton(canvas, t, saver)
        darkMage(canvas, t, particleScale)
        eldritch(canvas, t, particleScale)
        foxMage(canvas, t, particleScale)

        // Camada 6: névoa baixa e partículas atmosféricas (parallax extra, mais "à frente").
        canvas.save()
        canvas.translate(dx * 10f, dy * 5f)
        fog(canvas, t, saver)
        atmosphere(canvas, t, particleScale)
        canvas.restore()

        canvas.restore()
    }

    // ---------- Ajudantes ----------

    private fun wave(t: Float, period: Float, phase: Float = 0f): Float = .5f + .5f * sin(TAU * t / period + phase)

    /**
     * Onda mágica: a cada ~30 unidades de tempo, as fontes acendem em sequência (cada uma com
     * seu atraso) e terminam no círculo da mesa.
     */
    private fun surge(t: Float, delay: Float): Float {
        val p = (t % 30f) - 18f - delay
        return if (p > 0f && p < 2.6f) sin(p / 2.6f * PI.toFloat()) else 0f
    }

    /** Brilho radial tingido centrado em (x, y). */
    private fun glow(canvas: Canvas, paint: Paint, x: Float, y: Float, rx: Float, ry: Float, alpha: Float) {
        if (alpha <= .004f) return
        paint.alpha = (alpha.coerceIn(0f, 1f) * 255).toInt()
        rectF.set(x - rx, y - ry, x + rx, y + ry)
        canvas.drawBitmap(glow, null, rectF, paint)
    }

    /**
     * Intensifica as partes luminosas de uma fonte de luz somando seu mapa de brilho, deformado
     * pela mesma malha da ilustração (continua alinhado quando asas e corpos se movem).
     */
    private fun boost(canvas: Canvas, index: Int, alpha: Float) {
        if (alpha <= .004f) return
        val cells = LIGHT_CELLS[index]
        val verts = lightVerts[index]
        var k = 0
        for (r in cells[1]..cells[3]) for (c in cells[0]..cells[2]) {
            val g = (r * (MESH_COLS + 1) + c) * 2
            verts[k++] = meshVerts[g]
            verts[k++] = meshVerts[g + 1]
        }
        addImagePaint.alpha = (alpha.coerceIn(0f, 1f) * 255).toInt()
        canvas.drawBitmapMesh(artwork.highlights[index], cells[2] - cells[0], cells[3] - cells[1],
            verts, 0, null, 0, addImagePaint)
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

    /** Move o canvas junto com o personagem no ponto (x, y) — efeitos acompanham o corpo. */
    private fun follow(canvas: Canvas, x: Float, y: Float) {
        displace(x, y)
        canvas.save()
        canvas.translate(dispX, dispY)
    }

    // ---------- O conselho: círculo mágico na mesa e fios de energia ----------

    private val tableX = 452f
    private val tableY = 1052f

    private fun tableSigil(canvas: Canvas, t: Float, particleScale: Float) {
        // A cor percorre as magias do conselho: dourado → violeta → azul → eldritch.
        val cycle = (t / 7f) % councilColors.size
        val from = floor(cycle).toInt()
        val to = (from + 1) % councilColors.size
        val blend = smooth(cycle - from)
        val color = mixColor(councilColors[from], councilColors[to], blend)
        val strength = .32f + .14f * wave(t, 5f) + .8f * surgeTable

        glow(canvas, councilGlows[from], tableX, tableY, 150f, 34f, strength * .45f * (1f - blend))
        glow(canvas, councilGlows[to], tableX, tableY, 150f, 34f, strength * .45f * blend)

        ringPaint.color = color
        ringPaint.strokeWidth = 1.2f
        ringPaint.alpha = (strength.coerceIn(0f, 1f) * 200).toInt()
        rectF.set(tableX - 112f, tableY - 12f, tableX + 112f, tableY + 12f)
        canvas.drawOval(rectF, ringPaint)
        ringPaint.strokeWidth = .9f
        rectF.set(tableX - 84f, tableY - 9f, tableX + 84f, tableY + 9f)
        canvas.drawOval(rectF, ringPaint)

        // Runas girando entre os dois anéis (perspectiva da mesa achatada).
        ringPaint.strokeWidth = 1.4f
        for (k in 0 until 16) {
            val a = k * TAU / 16f + t * .22f
            val x = tableX + cos(a) * 98f
            val y = tableY + sin(a) * 10.5f
            val front = .55f + .45f * sin(a)
            ringPaint.alpha = (strength.coerceIn(0f, 1f) * 230 * front).toInt()
            canvas.drawLine(x - 2.5f, y, x + 2.5f, y, ringPaint)
        }

        // Brasas subindo da mesa nas cores dos personagens.
        val embers = max(3, (12 * particleScale).toInt())
        for (k in 0 until embers) {
            val p = life(t, 5f + k * .35f, Noise.hash(k + 300), 1f)
            val x = tableX + (Noise.hash(k + 310) - .5f) * 200f + sin(t * 1.1f + k) * 6f
            val y = tableY - p * 150f
            val paint = councilGlows[k % councilGlows.size]
            spark(canvas, paint, celestialGlow, x, y, 1.4f, sin(p * PI.toFloat()) * (.6f + .4f * surgeTable))
        }
    }

    private val threadSourceX = floatArrayOf(408f, 342f, 691f, 848f, 141f)
    private val threadSourceY = floatArrayOf(705f, 954f, 755f, 703f, 873f)
    private val threadPath = Path()
    private val threadColors = intArrayOf(GOLD, VOID_VIOLET, ELDRITCH, ARCANE_BLUE, AMBER)
    private val threadGlows = arrayOf(goldGlow, voidGlow, eldritchGlow, blueGlow, brassGlow)
    private val threadCores = arrayOf(celestialGlow, violetGlow, cosmicGlow, cyanGlow, amberGlow)

    /** Fios de energia: cada magia envia pulsos de luz até o centro da mesa. */
    private fun energyThreads(canvas: Canvas, t: Float, saver: Boolean) {
        val dots = if (saver) 10 else 16
        val surges = floatArrayOfSurges()
        for (k in threadSourceX.indices) {
            val cycle = ((t / 15f + k * .21f) % 1f)
            val idle = if (cycle < .5f) sin(cycle / .5f * PI.toFloat()) * .7f else 0f
            val visibility = max(idle, surges[k])
            if (visibility < .02f) continue
            displace(threadSourceX[k], threadSourceY[k])
            val x0 = threadSourceX[k] + dispX
            val y0 = threadSourceY[k] + dispY
            val x2 = tableX + (x0 - tableX) * .12f
            val y2 = tableY - 6f
            val x1 = (x0 + x2) / 2f
            val y1 = min(y0, y2) - 70f
            // Fio contínuo bem sutil, com pulsos de luz viajando até a mesa.
            threadPath.rewind()
            threadPath.moveTo(x0, y0)
            threadPath.quadTo(x1, y1, x2, y2)
            ringPaint.color = threadColors[k]
            ringPaint.strokeWidth = 1.3f
            ringPaint.alpha = (visibility * 70).toInt()
            canvas.drawPath(threadPath, ringPaint)
            for (j in 1 until dots) {
                val s = j / dots.toFloat()
                val u = 1f - s
                val x = u * u * x0 + 2f * u * s * x1 + s * s * x2
                val y = u * u * y0 + 2f * u * s * y1 + s * s * y2
                val travel = .5f + .5f * sin(TAU * (s * 2f - t * .7f))
                val alpha = visibility * (.25f + .75f * travel * travel * travel) * sqrt(sin(s * PI.toFloat()))
                spark(canvas, threadGlows[k], threadCores[k], x, y, 1.6f, alpha)
            }
        }
    }

    private val surgeBuffer = FloatArray(5)
    private fun floatArrayOfSurges(): FloatArray {
        surgeBuffer[0] = surgeAngel
        surgeBuffer[1] = surgeMage
        surgeBuffer[2] = surgeEldritch
        surgeBuffer[3] = surgeFox
        surgeBuffer[4] = surgeAutomaton
        return surgeBuffer
    }

    // ---------- Personagem 3: figura angelical (centro) ----------

    private val angelX = 470f
    private val angelY = 615f

    private fun divineRays(canvas: Canvas, t: Float, strength: Float) {
        val intensity = wave(t, 11f) + surgeAngel
        follow(canvas, angelX, angelY)
        for (k in 0 until 7) {
            val angle = (-150f + k * 20f + 4f * sin(TAU * t / 23f + k)) * PI.toFloat() / 180f
            val length = 420f + 70f * Noise.hash(k + 40)
            val dist = 170f + length / 2f
            val alpha = (.06f + .07f * intensity) * (.6f + .4f * wave(t, 7f + k, k * 1.7f)) * strength
            canvas.save()
            canvas.translate(angelX + cos(angle) * dist, angelY + sin(angle) * dist)
            canvas.rotate(angle * 180f / PI.toFloat())
            glow(canvas, goldGlow, 0f, 0f, length / 2f, 16f, alpha)
            canvas.restore()
        }
        canvas.restore()
    }

    private fun angel(canvas: Canvas, t: Float, saver: Boolean, particleScale: Float) {
        // Asas: respiração celestial (luminosidade sobe e desce) + onda mágica.
        val breath = wave(t, 8f) + .6f * surgeAngel
        boost(canvas, WINGS, .2f + .32f * breath)
        boost(canvas, HALO, .2f + .4f * wave(t, 5.5f, 1f) + .4f * surgeAngel)
        val tome = wave(t, 6.5f, 2.3f)
        boost(canvas, TOME, .12f + .25f * tome + .4f * surgeAngel)

        follow(canvas, angelX, 700f)
        glow(canvas, goldGlow, angelX, angelY, 270f, 170f, .09f + .09f * breath)

        // Halo: pontos de luz percorrendo a circunferência.
        val haloPulse = wave(t, 5.5f, 1f)
        for (k in 0 until 4) {
            val a = t * .55f + k * TAU / 4f
            val x = angelX + cos(a) * 37f
            val y = angelY + sin(a) * 38f
            // O anel passa atrás do capuz: some sobre a cabeça.
            val hx = (x - angelX) / 30f
            val hy = (y - 650f) / 45f
            if (hx * hx + hy * hy < 1f) continue
            spark(canvas, goldGlow, celestialGlow, x, y, 2.3f, .6f + .35f * haloPulse)
        }

        // Livro: brilho dourado e páginas soltando partículas.
        glow(canvas, goldGlow, 408f, 705f, 44f, 32f, .08f + .1f * tome + .2f * surgeAngel)
        val tomeParticles = max(3, (9 * particleScale).toInt())
        for (k in 0 until tomeParticles) {
            val p = life(t, 4.5f, k / tomeParticles.toFloat(), 1f)
            val x = 385f + 45f * Noise.hash(k + 10) + 6f * sin(t * 1.3f + k)
            val y = 700f - p * 90f
            spark(canvas, goldGlow, celestialGlow, x, y, 1.1f, sin(p * PI.toFloat()) * .8f)
        }
        canvas.restore()

        // Penas douradas se desprendem e descem devagar (independentes do corpo).
        val feathers = if (saver) 3 else 7
        for (k in 0 until feathers) {
            val p = life(t, 11f, k / feathers.toFloat() + Noise.hash(k + 70) * .1f, .75f)
            if (p < 0f) continue
            val leftWing = k % 2 == 0
            val startX = if (leftWing) 300f + 120f * Noise.hash(k + 80) else 520f + 120f * Noise.hash(k + 80)
            val startY = 520f + 150f * Noise.hash(k + 90)
            val x = startX + sin(p * 9f + k) * 16f + (if (leftWing) -40f else 40f) * p
            val y = startY + p * 320f
            val alpha = min(p / .1f, 1f) * min((1f - p) / .4f, 1f) * .9f
            canvas.save()
            canvas.translate(x, y)
            canvas.rotate(40f * sin(p * 9f + k))
            glow(canvas, goldGlow, 0f, 0f, 10f, 4f, alpha * .6f)
            glow(canvas, celestialGlow, 0f, 0f, 5.5f, 1.5f, alpha)
            canvas.restore()
        }
    }

    // ---------- Personagem 1: autômato pistoleiro (extrema esquerda) ----------

    private fun automaton(canvas: Canvas, t: Float, saver: Boolean) {
        follow(canvas, 141f, 873f)
        // Olhos mecânicos: pulso + oscilações de energia; ocasionalmente um olho pisca.
        val flicker = .88f + .12f * sin(t * 17f) * sin(t * 7.3f)
        val base = (.6f + .4f * wave(t, 4.2f, .5f) + .6f * surgeAutomaton) * flicker
        drawMechanicalEye(canvas, 132f, 872f, base, blink(t, 11f, 0f))
        drawMechanicalEye(canvas, 149f, 873f, base, blink(t, 15f, .55f))

        // Vapor saindo do pescoço/ombro, em rajadas.
        if (!saver) {
            for (k in 0 until 5) {
                val p = life(t, 10f, k * .05f, .3f)
                if (p < 0f) continue
                val x = 172f + p * 22f + sin(p * 6f + k) * 5f
                val y = 912f - p * 65f
                val r = 5f + p * 15f
                glow(canvas, steamPaint, x, y, r, r * .8f, sin(p * PI.toFloat()) * .26f)
            }
        }
        canvas.restore()

        // Energia mecânica: pontos dourados acendendo pelo corpo e pelo rifle.
        for (k in 0 until 7) {
            val p = life(t, 5f + k * .8f, Noise.hash(k + 20), .14f)
            if (p < 0f) continue
            val x = 60f + 140f * Noise.hash(k + 30)
            val y = 900f + 220f * Noise.hash(k + 35)
            spark(canvas, brassGlow, amberGlow, x, y, 1.2f, sin(p * PI.toFloat()) * .9f)
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
        glow(canvas, amberGlow, x, y, 14f, 10f, intensity * .5f * open)
        glow(canvas, brassGlow, x, y, 3.8f, 3.2f, intensity * open)
    }

    // ---------- Personagem 2: mago das asas negras (esquerda central) ----------

    private val orbX = 342f
    private val orbY = 954f

    private fun darkMage(canvas: Canvas, t: Float, particleScale: Float) {
        val pulse = wave(t, 4.6f, 3f) + surgeMage
        boost(canvas, ORB, .3f + .4f * pulse)

        follow(canvas, orbX, orbY)
        // Aura violeta iluminando braço e tronco.
        glow(canvas, voidGlow, orbX, orbY - 10f, 95f, 85f, .14f + .12f * pulse)
        glow(canvas, violetGlow, orbX, orbY, 17f, 17f, .4f + .3f * pulse)

        // Ondas gravitacionais: anéis que se contraem em direção ao núcleo.
        ringPaint.color = VOID_VIOLET
        ringPaint.strokeWidth = 1.2f
        for (k in 0 until 2) {
            val ring = (t / 4.5f + k * .5f) % 1f
            ringPaint.alpha = (sin(ring * PI.toFloat()) * 85).toInt()
            rectF.set(orbX - (40f - ring * 32f), orbY - (40f - ring * 32f) * .8f,
                orbX + (40f - ring * 32f), orbY + (40f - ring * 32f) * .8f)
            canvas.drawOval(rectF, ringPaint)
        }

        // Fragmentos orbitando o núcleo (órbita inclinada: mais brilho na frente).
        for (k in 0 until 8) {
            val a = t * (1f + k * .13f) + k * TAU / 8f
            val rx = 20f + 8f * Noise.hash(k + 50)
            val x = orbX + cos(a) * rx
            val y = orbY + sin(a) * rx * .55f
            val front = .45f + .55f * (.5f + .5f * sin(a))
            spark(canvas, voidGlow, violetGlow, x, y, 1.3f, .75f * front)
        }

        // Partículas sendo atraídas em espiral para o núcleo.
        val count = max(4, (14 * particleScale).toInt())
        for (k in 0 until count) {
            val p = life(t, 3.6f + k * .3f, Noise.hash(k + 60), 1f)
            val dist = 60f * (1f - p) + 3f
            val a = Noise.hash(k + 65) * TAU + p * 4.5f
            val x = orbX + cos(a) * dist
            val y = orbY + sin(a) * dist * .8f
            spark(canvas, voidGlow, violetGlow, x, y, 1f, sin(p * PI.toFloat()) * .7f)
        }
        canvas.restore()
    }

    // ---------- Personagem 4: feiticeiro eldritch (direita central) ----------

    private val eyeX = 691f
    private val eyeY = 755f

    private fun eldritch(canvas: Canvas, t: Float, particleScale: Float) {
        val pulse = wave(t, 3.4f, 4.2f) + surgeEldritch
        boost(canvas, EYE, .3f + .5f * pulse)

        // Aura escura que se expande e some atrás da silhueta.
        val aura = (t / 9f) % 1f
        glow(canvas, cosmicGlow, 640f, 820f, 90f + aura * 60f, 100f + aura * 65f, (1f - aura) * .15f)

        follow(canvas, eyeX, eyeY)
        // O olho flutua levemente acima da mão.
        val hover = sin(TAU * t / 4.8f) * 1.6f
        canvas.translate(0f, hover)
        glow(canvas, eldritchGlow, eyeX, eyeY, 52f, 48f, .16f + .16f * pulse)

        // Anéis mágicos girando ao redor do olho.
        ringPaint.color = ELDRITCH
        ringPaint.strokeWidth = 1f
        drawRing(canvas, 28f, 9.5f, t * 26f, .34f + .24f * pulse)
        drawRing(canvas, 21f, 7f, -t * 18f + 60f, .28f + .2f * pulse)
        drawRing(canvas, 34f, 6f, t * 11f + 120f, .14f + .14f * pulse)

        // Partículas orbitando o símbolo.
        val orbiters = max(4, (8 * particleScale).toInt())
        for (k in 0 until orbiters) {
            val a = t * .8f + k * TAU / orbiters
            val x = eyeX + cos(a) * 32f
            val y = eyeY + sin(a) * 13f
            spark(canvas, eldritchGlow, cosmicGlow, x, y, 1.1f, .5f + .35f * sin(a))
        }

        // Pupila: olhares ocasionais (alvo muda a cada ~3 s, com transição suave).
        val step = floor(t / 2.8f).toInt()
        val blend = smooth(((t / 2.8f) - step) / .25f)
        val lookX = lerp(look(step - 1, 0), look(step, 0), blend)
        val lookY = lerp(look(step - 1, 1), look(step, 1), blend) * .5f
        solidPaint.color = Color.argb(235, 28, 8, 52)
        canvas.drawCircle(eyeX + lookX, eyeY + lookY, 2.6f, solidPaint)
        glow(canvas, violetGlow, eyeX + lookX - .6f, eyeY + lookY - .6f, 1.8f, 1.8f, 1f)

        // Piscada lenta em intervalos irregulares.
        val closed = max(blink(t, 8.5f, .2f), blink(t, 21f, .7f))
        if (closed > .05f) {
            solidPaint.color = Color.argb((closed * 255).toInt(), 70, 26, 140)
            rectF.set(eyeX - 12f, eyeY - 7f * closed, eyeX + 12f, eyeY + 7f * closed)
            canvas.drawOval(rectF, solidPaint)
        }
        canvas.restore()
    }

    private fun look(step: Int, axis: Int): Float = (Noise.hash(step * 2 + axis + 900) - .5f) * 3.4f

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
        val pulse = wave(t, 4f, 5.1f) + surgeFox
        boost(canvas, CRYSTAL, .3f + .5f * pulse)
        // Bordas azuis das caudas acompanham o pulso do cristal.
        boost(canvas, TAILS, .1f + .15f * pulse)

        glow(canvas, blueGlow, crystalX, crystalY, 46f, 56f, .2f + .2f * pulse)
        glow(canvas, cyanGlow, crystalX, crystalY + 4f, 9f, 17f, .35f + .3f * pulse)

        // Fragmentos azuis se desprendendo e subindo.
        val count = max(4, (11 * particleScale).toInt())
        for (k in 0 until count) {
            val p = life(t, 4f + k * .4f, Noise.hash(k + 110), 1f)
            val x = crystalX + (Noise.hash(k + 115) - .5f) * 24f + sin(t * 1.4f + k) * 7f
            val y = crystalY + 10f - p * 115f
            spark(canvas, blueGlow, cyanGlow, x, y, 1.1f, sin(p * PI.toFloat()) * .8f)
        }

        // Símbolos arcanos surgindo ao redor do cajado.
        ringPaint.color = CYAN
        ringPaint.strokeWidth = 1f
        for (k in 0 until 4) {
            val p = life(t, 6f + k * 1.3f, k * .25f, .6f)
            if (p < 0f) continue
            val alpha = sin(p * PI.toFloat())
            val x = 858f + 34f * Noise.hash(k + 120) + p * 10f
            val y = 760f + 150f * Noise.hash(k + 125) - p * 26f
            glow(canvas, blueGlow, x, y, 13f, 13f, alpha * .4f)
            ringPaint.alpha = (alpha * 220).toInt()
            canvas.save()
            canvas.translate(x, y)
            canvas.rotate(45f + p * 120f)
            canvas.drawRect(-3.5f, -3.5f, 3.5f, 3.5f, ringPaint)
            canvas.restore()
        }
    }

    // ---------- Efeitos globais ----------

    private fun fog(canvas: Canvas, t: Float, saver: Boolean) {
        val layers = if (saver) 3 else 5
        val span = IMG_W + 700f
        for (k in 0 until layers) {
            val speed = 11f + 6f * k
            val x = ((Noise.hash(k + 200) * span + t * speed) % span) - 350f
            val y = 1330f + 70f * k
            glow(canvas, fogPaint, x, y, 330f, 70f, .32f + .12f * wave(t, 13f + k, k.toFloat()))
        }
    }

    private fun atmosphere(canvas: Canvas, t: Float, particleScale: Float) {
        val count = (dust.size / 4 * particleScale).toInt().coerceIn(8, dust.size / 4)
        for (i in 0 until count) {
            val baseX = dust[i * 4]
            val baseY = dust[i * 4 + 1]
            val phase = dust[i * 4 + 2]
            val speed = dust[i * 4 + 3]
            val travel = (t * 7f * speed) % 300f
            val y = baseY - travel
            val x = baseX + sin(t * .3f * speed + phase) * 14f
            val alpha = .14f + .2f * wave(t, 5f + speed * 3f, phase)
            glow(canvas, dustGlow, x, y, 2.3f, 2.3f, alpha * fadeEdge(travel / 300f))
        }
    }

    private fun fadeEdge(p: Float): Float = min(p / .15f, 1f) * min((1f - p) / .15f, 1f)
    private fun smooth(x: Float): Float { val c = x.coerceIn(0f, 1f); return c * c * (3f - 2f * c) }
    private fun lerp(a: Float, b: Float, f: Float): Float = a + (b - a) * f
}
