package com.willi.app.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.*

class JarvisView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // ─── API publique (compatibilité MainActivity) ────────────────────────────
    var statusText: String = "WILLI"
    var subStatusText: String = ""
    var emotionText: String = ""
    var confidenceLevel: Float = 0.8f
    var isListening: Boolean = false
        set(value) { field = value; syncState() }
    var isSpeaking: Boolean = false
        set(value) { field = value; syncState() }
    var waveAmplitude: Float = 0f

    private enum class Mode { IDLE, LISTENING, SPEAKING }
    private var mode = Mode.IDLE

    // ─── Palette ──────────────────────────────────────────────────────────────
    private val BG       = Color.BLACK
    private val RED      = Color.parseColor("#CC0011")
    private val RED_HOT  = Color.parseColor("#FF3344")
    private val RED_DIM  = Color.parseColor("#440008")
    private val WHITE    = Color.parseColor("#FFE4E4")

    // ─── Animation ────────────────────────────────────────────────────────────
    private var t = 0f
    private val animator = ValueAnimator.ofFloat(0f, (2 * PI * 100).toFloat()).apply {
        duration = 100_000
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            t = (it.animatedValue as Float) / 100f
            tickParticles()
            invalidate()
        }
    }

    // ─── Particules ───────────────────────────────────────────────────────────
    private inner class Particle(
        var angle: Float = (Math.random() * 2 * PI).toFloat(),
        val rFac:  Float = (Math.random() * 0.6f + 0.7f).toFloat(),
        val speed: Float = (Math.random() * 0.014f + 0.003f).toFloat(),
        var alpha: Float = Math.random().toFloat(),
        val size:  Float = (Math.random() * 3.5f + 0.8f).toFloat()
    )
    private val particles = Array(80) { Particle() }

    // ─── Paints ───────────────────────────────────────────────────────────────
    private val blobP  = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val tentP  = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val partP  = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val labelP = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Typeface.MONOSPACE }

    init { setLayerType(LAYER_TYPE_SOFTWARE, null) }

    override fun onAttachedToWindow()   { super.onAttachedToWindow();   animator.start() }
    override fun onDetachedFromWindow() { super.onDetachedFromWindow(); animator.cancel() }

    private fun syncState() {
        mode = when { isListening -> Mode.LISTENING; isSpeaking -> Mode.SPEAKING; else -> Mode.IDLE }
    }

    private fun tickParticles() {
        val m = when (mode) { Mode.IDLE -> 1f; Mode.LISTENING -> 2.8f; Mode.SPEAKING -> 2f + waveAmplitude * 4f }
        particles.forEach { p ->
            p.angle += p.speed * m
            p.alpha  = (sin(t * 1.8 + p.angle.toDouble()) * 0.5 + 0.5).toFloat()
        }
    }

    // ─── Dessin ───────────────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(BG)
        val cx = width / 2f
        val cy = height / 2f
        val base = minOf(width, height) * 0.25f
        val breathe = 1f + 0.07f * sin(t * 2.0).toFloat()
        val excite = when (mode) {
            Mode.IDLE      -> 1f
            Mode.LISTENING -> 1.2f + waveAmplitude * 0.35f
            Mode.SPEAKING  -> 1.12f + waveAmplitude * 0.42f
        }
        val r = base * breathe * excite

        drawTentacles(canvas, cx, cy, r)
        drawBlob(canvas, cx, cy, r)
        drawParticles(canvas, cx, cy, r)
        drawLabels(canvas, cx, cy)
    }

    private fun mkPath(cx: Float, cy: Float, r: Float, sc: Float, ts: Float, ph: Float): Path {
        val n = 12
        val pts = Array(n) { i ->
            val a = (i * 2 * PI / n).toFloat()
            val w = 1f + 0.24f * sin((t * ts + i * 1.05 + ph).toDouble()).toFloat() +
                         0.1f * sin((t * ts * 1.6 + i * 0.55).toDouble()).toFloat()
            val rr = r * sc * w
            floatArrayOf(cx + rr * cos(a.toDouble()).toFloat(), cy + rr * sin(a.toDouble()).toFloat())
        }
        return Path().apply {
            moveTo(pts[0][0], pts[0][1])
            for (i in 0 until n) {
                val j = (i + 1) % n
                quadTo(pts[i][0], pts[i][1], (pts[i][0] + pts[j][0]) / 2, (pts[i][1] + pts[j][1]) / 2)
            }
            close()
        }
    }

    private fun drawBlob(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        blobP.color = Color.argb(45, 200, 0, 18)
        blobP.maskFilter = BlurMaskFilter(r * 0.8f, BlurMaskFilter.Blur.NORMAL)
        canvas.drawPath(mkPath(cx, cy, r, 1.25f, 1.6f, 0f), blobP)

        blobP.color = Color.argb(235, 185, 0, 18)
        blobP.maskFilter = BlurMaskFilter(r * 0.14f, BlurMaskFilter.Blur.NORMAL)
        canvas.drawPath(mkPath(cx, cy, r, 1f, 3f, 0.6f), blobP)

        blobP.color = Color.argb(255, 255, 50, 65)
        blobP.maskFilter = BlurMaskFilter(r * 0.07f, BlurMaskFilter.Blur.NORMAL)
        canvas.drawPath(mkPath(cx, cy, r, 0.4f, 5.5f, 1.5f), blobP)

        blobP.color = Color.argb(255, 255, 200, 200)
        blobP.maskFilter = BlurMaskFilter(r * 0.025f, BlurMaskFilter.Blur.NORMAL)
        canvas.drawCircle(cx, cy, r * 0.065f, blobP)
    }

    private fun drawTentacles(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        for (i in 0 until 10) {
            val base   = (i * 2 * PI / 10).toFloat()
            val wave   = (t * 2.2 + i * 0.85).toDouble()
            val len    = r * (1.6f + 0.3f * sin(wave).toFloat())
            val spread = 0.4f * sin(t * 1.7 + i * 0.5).toFloat()

            val sx = cx + r * 0.8f * cos(base.toDouble()).toFloat()
            val sy = cy + r * 0.8f * sin(base.toDouble()).toFloat()
            val mx = cx + len * 1.1f * cos((base + spread * 0.45).toDouble()).toFloat()
            val my = cy + len * 1.1f * sin((base + spread * 0.55).toDouble()).toFloat()
            val ex = cx + len * cos((base + spread).toDouble()).toFloat()
            val ey = cy + len * sin((base + spread).toDouble()).toFloat()

            val path = Path().apply { moveTo(sx, sy); quadTo(mx, my, ex, ey) }
            val a  = (150 * (0.4f + 0.6f * sin((t * 1.1 + i * 0.65).toDouble()).toFloat())).toInt().coerceIn(40, 215)
            val sw = (3.5f + waveAmplitude * 5f) * (0.55f + 0.45f * sin(wave).toFloat())
            tentP.color       = Color.argb(a, 185, 0, 18)
            tentP.strokeWidth = sw
            tentP.maskFilter  = BlurMaskFilter(8f + waveAmplitude * 20f, BlurMaskFilter.Blur.NORMAL)
            canvas.drawPath(path, tentP)
        }
    }

    private fun drawParticles(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        particles.forEach { p ->
            val x = cx + r * p.rFac * cos(p.angle.toDouble()).toFloat()
            val y = cy + r * p.rFac * sin(p.angle.toDouble()).toFloat()
            val a = (p.alpha * 210).toInt().coerceIn(0, 255)
            partP.color       = Color.argb(a, 255, 35, 35)
            partP.maskFilter  = BlurMaskFilter(p.size * 1.4f, BlurMaskFilter.Blur.NORMAL)
            canvas.drawCircle(x, y, p.size, partP)
        }
    }

    private fun drawLabels(canvas: Canvas, cx: Float, cy: Float) {
        // Nom WILLI en haut
        labelP.typeface    = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        labelP.textSize    = width * 0.052f
        labelP.color       = RED
        labelP.letterSpacing = 0.35f
        canvas.drawText(statusText, cx, height * 0.085f, labelP)

        // Émotion / sous-titre
        if (emotionText.isNotBlank()) {
            labelP.typeface    = Typeface.MONOSPACE
            labelP.textSize    = width * 0.03f
            labelP.color       = RED_DIM
            labelP.letterSpacing = 0.15f
            canvas.drawText(emotionText, cx, height * 0.138f, labelP)
        }
    }
}
