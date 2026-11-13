package com.livingwallpapers.app.wallpaper

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.SurfaceHolder
import com.livingwallpapers.app.scene.SceneRegistry
import com.livingwallpapers.app.settings.WallpaperSettings
import com.livingwallpapers.app.settings.SettingsRepository
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Serviço base: cada cena tem sua própria subclasse (um wallpaper distinto para o sistema),
 * permitindo usar cenas diferentes na tela inicial e na tela de bloqueio.
 */
abstract class SceneWallpaperService : WallpaperService() {

    /** Cena fixa deste wallpaper (id registrado em SceneRegistry). */
    protected abstract val sceneId: String

    override fun onCreateEngine(): Engine = SceneEngine()

    private inner class SceneEngine : Engine(), SensorEventListener,
        SharedPreferences.OnSharedPreferenceChangeListener {

        private val handler = Handler(Looper.getMainLooper())
        private val paint = Paint().apply { isFilterBitmap = false; isAntiAlias = false }
        private val sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        private val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        private val sensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        private val prefs = SettingsRepository.preferences(this@SceneWallpaperService)
        private var settings: WallpaperSettings = SettingsRepository.load(this@SceneWallpaperService)
        private val renderer = SceneRegistry.find(sceneId).create()

        private var visible = false
        private var surfaceReady = false
        private var disposed = false
        private var sensorActive = false
        private var screenWidth = 0
        private var screenHeight = 0
        private var raster: Bitmap? = null
        private var tiltX = 0f
        private var tiltY = 0f
        private val bootMillis = SystemClock.elapsedRealtime()

        private val drawTask = object : Runnable {
            override fun run() {
                if (!visible || !surfaceReady || disposed) return
                val started = SystemClock.uptimeMillis()
                renderFrame()
                val frameDuration = 1000L / settings.effectiveFps(powerManager.isPowerSaveMode).toLong()
                val spent = SystemClock.uptimeMillis() - started
                if (visible && surfaceReady && !disposed) {
                    handler.postDelayed(this, max(1L, frameDuration - spent))
                }
            }
        }

        init {
            prefs.registerOnSharedPreferenceChangeListener(this)
            setTouchEventsEnabled(false)
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            surfaceReady = true
            restartFrames()
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            if (width <= 0 || height <= 0) return
            screenWidth = width
            screenHeight = height
            val targetW = 180
            val targetH = (height.toFloat() / width * targetW).roundToInt().coerceIn(280, 520)
            raster?.recycle()
            raster = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
            surfaceReady = true
            restartFrames()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            refreshSensor()
            restartFrames()
        }

        override fun onSurfaceRedrawNeeded(holder: SurfaceHolder) {
            super.onSurfaceRedrawNeeded(holder)
            if (surfaceReady && !disposed) renderFrame()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            surfaceReady = false
            handler.removeCallbacks(drawTask)
            stopSensor()
            super.onSurfaceDestroyed(holder)
        }

        override fun onDestroy() {
            disposed = true
            visible = false
            handler.removeCallbacks(drawTask)
            stopSensor()
            prefs.unregisterOnSharedPreferenceChangeListener(this)
            raster?.recycle()
            raster = null
            super.onDestroy()
        }

        override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
            settings = SettingsRepository.load(this@SceneWallpaperService)
            refreshSensor()
            restartFrames()
        }

        private fun restartFrames() {
            handler.removeCallbacks(drawTask)
            if (visible && surfaceReady && screenWidth > 0 && raster != null && !disposed) {
                handler.post(drawTask)
            }
        }

        private fun refreshSensor() {
            stopSensor()
            if (visible && settings.parallax && sensor != null && !disposed) {
                val delay = if (settings.batterySaver) SensorManager.SENSOR_DELAY_UI
                    else SensorManager.SENSOR_DELAY_GAME
                sensorActive = sensorManager.registerListener(this, sensor, delay)
            } else if (!settings.parallax) {
                tiltX = 0f
                tiltY = 0f
            }
        }

        private fun stopSensor() {
            if (sensorActive) {
                sensorManager.unregisterListener(this)
                sensorActive = false
            }
        }

        override fun onSensorChanged(event: SensorEvent) {
            // Filtro passa-baixa para evitar saltos ao inclinar o telefone.
            val targetX = (-event.values[0] / 9.81f).coerceIn(-1f, 1f)
            val targetY = (-event.values[1] / 9.81f).coerceIn(-1f, 1f)
            tiltX += (targetX - tiltX) * .13f
            tiltY += (targetY - tiltY) * .13f
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

        private fun renderFrame() {
            val bitmap = raster ?: return
            if (!surfaceReady || screenWidth == 0 || screenHeight == 0) return
            val elapsed = (SystemClock.elapsedRealtime() - bootMillis) / 1000f
            renderer.render(bitmap, elapsed, tiltX, tiltY, settings)
            var canvas: Canvas? = null
            try {
                canvas = surfaceHolder.lockCanvas() ?: return
                canvas.drawColor(Color.BLACK)
                canvas.drawBitmap(
                    bitmap,
                    Rect(0, 0, bitmap.width, bitmap.height),
                    Rect(0, 0, canvas.width, canvas.height),
                    paint,
                )
            } catch (e: RuntimeException) {
                // A surface pode desaparecer quando o usuário bloqueia o aparelho.
                Log.w("LivingWallpapers", "Surface indisponível para desenhar", e)
            } finally {
                if (canvas != null) {
                    try { surfaceHolder.unlockCanvasAndPost(canvas) }
                    catch (e: RuntimeException) { Log.w("LivingWallpapers", "Surface encerrada", e) }
                }
            }
        }
    }
}
