package com.livingwallpapers.app.scene

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import java.util.TimeZone
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Ciclo de dia e noite seguindo o relógio do celular.
 *
 * O dia tem quatro fases — [NIGHT], [DAWN], [DAY] e [DUSK] — e a hora atual é convertida numa
 * mistura suave entre duas delas ([phaseA] → [phaseB] com peso [blend]). As cenas usam isso
 * para misturar paletas, posicionar sol e lua e ligar/desligar elementos noturnos.
 */
object DayCycle {
    const val NIGHT = 0
    const val DAWN = 1
    const val DAY = 2
    const val DUSK = 3

    /** Pontos-chave do dia: (hora, fase). Entre eles, a fase é interpolada. */
    private val keyHours = floatArrayOf(0f, 5f, 6.5f, 9f, 16.5f, 18.5f, 20f, 24f)
    private val keyPhases = intArrayOf(NIGHT, NIGHT, DAWN, DAY, DAY, DUSK, NIGHT, NIGHT)

    /** Quanto de luz do dia cada fase tem (0 = noite fechada, 1 = meio-dia). */
    private val daylightByPhase = floatArrayOf(0f, .45f, 1f, .4f)

    /** Hora fixa para testes (0–24). Negativo = seguir o relógio. Usado só em builds de debug. */
    @Volatile var overrideHour = -1f

    var hour = 0f; private set
    var phaseA = NIGHT; private set
    var phaseB = NIGHT; private set
    var blend = 0f; private set

    /** 0 = noite, 1 = dia pleno. */
    val daylight: Float get() = daylightByPhase[phaseA] + (daylightByPhase[phaseB] - daylightByPhase[phaseA]) * blend

    /** 1 = noite fechada (estrelas, vaga-lumes, janelas acesas). */
    val nightness: Float get() = ((.45f - daylight) / .45f).coerceIn(0f, 1f)

    private var zoneOffsetMs = 0L
    private var zoneCheckedAt = Long.MIN_VALUE

    /** Atualiza a fase do dia (chamar uma vez por quadro; não aloca memória). */
    fun update() {
        hour = currentHour()
        var i = 0
        while (i < keyHours.size - 2 && hour >= keyHours[i + 1]) i++
        val span = keyHours[i + 1] - keyHours[i]
        val raw = if (span > 0f) (hour - keyHours[i]) / span else 0f
        phaseA = keyPhases[i]
        phaseB = keyPhases[i + 1]
        blend = raw * raw * (3f - 2f * raw)
    }

    private fun currentHour(): Float {
        val forced = overrideHour
        if (forced >= 0f) return forced % 24f
        val now = System.currentTimeMillis()
        // O fuso (e horário de verão) é consultado no máximo uma vez por minuto.
        if (now - zoneCheckedAt > 60_000L || now < zoneCheckedAt) {
            zoneOffsetMs = TimeZone.getDefault().getOffset(now).toLong()
            zoneCheckedAt = now
        }
        val dayMs = 86_400_000L
        val local = ((now + zoneOffsetMs) % dayMs + dayMs) % dayMs
        return local / 3_600_000f
    }

    /** Mistura uma cor definida para cada fase (array de 4 cores: noite, amanhecer, dia, entardecer). */
    fun pick(byPhase: IntArray): Int = mixColor(byPhase[phaseA], byPhase[phaseB], blend)

    /** Mistura um degradê definido para cada fase e escreve o resultado em [out]. */
    fun pickGradient(byPhase: Array<IntArray>, out: IntArray) {
        val a = byPhase[phaseA]
        val b = byPhase[phaseB]
        for (i in out.indices) out[i] = mixColor(a[i], b[i], blend)
    }

    /** Chave que muda a cada ~2 minutos de céu: usada para redesenhar camadas em cache. */
    fun cacheKey(): Int = phaseA * 10_000 + phaseB * 1_000 + (blend * 200).roundToInt()

    /**
     * Posição de um astro num arco entre [rise] e [set] (horas; [set] pode passar de 24).
     * Retorna o progresso 0..1 ou -1 se o astro está abaixo do horizonte.
     */
    fun arcProgress(rise: Float, set: Float): Float {
        var h = hour
        if (h < rise && set > 24f) h += 24f
        val p = (h - rise) / (set - rise)
        return if (p in 0f..1f) p else -1f
    }

    /** Altura do arco (0 no horizonte, 1 no ponto mais alto). */
    fun arcHeight(progress: Float): Float = sin(progress * PI.toFloat())
}

/**
 * Filtro de cor para a paisagem, misturado entre as quatro fases do dia. Cada fase é descrita
 * por multiplicadores e somas de RGB (6 valores). O filtro só é recriado quando a mistura muda.
 */
class LandscapeTint(private val byPhase: Array<FloatArray>) {
    private val matrix = ColorMatrix()
    private val values = FloatArray(20)
    private var lastKey = Int.MIN_VALUE
    private var filter: ColorMatrixColorFilter? = null

    fun current(): ColorMatrixColorFilter {
        val key = DayCycle.cacheKey()
        val cached = filter
        if (cached != null && key == lastKey) return cached
        val a = byPhase[DayCycle.phaseA]
        val b = byPhase[DayCycle.phaseB]
        val f = DayCycle.blend
        fun mix(i: Int) = a[i] + (b[i] - a[i]) * f
        values.fill(0f)
        values[0] = mix(0); values[4] = mix(3)
        values[6] = mix(1); values[9] = mix(4)
        values[12] = mix(2); values[14] = mix(5)
        values[18] = 1f
        matrix.set(values)
        return ColorMatrixColorFilter(matrix).also { filter = it; lastKey = key }
    }
}
