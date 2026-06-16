package com.willi.app.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.animation.ValueAnimator
import android.view.animation.LinearInterpolator
import kotlin.math.*

/**
 * Vue canvas custom — interface holographique style JARVIS / Iron Man
 * Palette rouge comme l'armure Iron Man (au lieu du bleu original de JARVIS)
 */
class JarvisView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    // ─── Couleurs Iron Man Rouge ───────────────────────────────────────────────
    private val colorBg = Color.parseColor("#050005")
    private val colorPrimary = Color.parseColor("#CC1122")
    private val colorBright = Color.parseColor("#FF1744")
    private val colorAccent = Color.parseColor("#FF5252")
    private val colorDim = Color.parseColor("#660011")
    private val colorGlow = Color.parseColor("#FF0022")
    private val colorText = Color.parseColor("#FFE4E4")
    private val colorGrid = Color.parseColor("#2A0008")
    private val colorGold = Color.parseColor("#FFB300")

    // ─── Paints ───────────────────────────────────────────────────────────────
    private val bgPaint = Paint().apply { color = colorBg; style = Paint.Style.FILL }

    private val primaryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorPrimary
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorGlow
        style = Paint.Style.STROKE
        strokeWidth = 4f
        maskFilter = BlurMaskFilter(12f, BlurMaskFilter.Blur.OUTER)
    }

    private val brightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorBright
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }

    private val fillGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorGlow
        style = Paint.Style.FILL
        maskFilter = BlurMaskFilter(20f, BlurMaskFilter.Blur.NORMAL)
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorText
        textSize = 28f
        typeface = Typeface.MONOSPACE
    }

    private val smallTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorPrimary
        textSize = 20f
        typeface = Typeface.MONOSPACE
    }

    private val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorDim
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorGrid
        style = Paint.Style.STROKE
        strokeWidth = 0.5f
    }

    private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorAccent
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    // ─── État d'animation ─────────────────────────────────────────────────────
    private var rotation1 = 0f
    private var rotation2 = 0f
    private var rotation3 = 0f
    private var pulsePhase = 0f
    private var scanLine = 0f
    private var particlePhase = 0f

    var statusText = "WILLI — EN LIGNE"
    var subStatusText = "SYSTÈME ACTIF"
    var isListening = false
    var isSpeaking = false
    var emotionText = "CURIEUX"
    var confidenceLevel = 0.5f
    var waveAmplitude = 0f  // 0-1 pour l'animation audio

    private val animators = mutableListOf<ValueAnimator>()

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null) // nécessaire pour BlurMaskFilter
        startAnimations()
    }

    private fun startAnimations() {
        // Rotation anneau externe — lent
        ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 12000
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { rotation1 = it.animatedValue as Float; invalidate() }
            start()
        }.also { animators.add(it) }

        // Rotation anneau moyen — sens inverse
        ValueAnimator.ofFloat(360f, 0f).apply {
            duration = 8000
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { rotation2 = it.animatedValue as Float }
            start()
        }.also { animators.add(it) }

        // Rotation anneau interne — rapide
        ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 5000
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { rotation3 = it.animatedValue as Float }
            start()
        }.also { animators.add(it) }

        // Pulse
        ValueAnimator.ofFloat(0f, (2 * Math.PI).toFloat()).apply {
            duration = 2000
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { pulsePhase = it.animatedValue as Float }
            start()
        }.also { animators.add(it) }

        // Scan line
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 3000
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { scanLine = it.animatedValue as Float }
            start()
        }.also { animators.add(it) }

        // Particules
        ValueAnimator.ofFloat(0f, (2 * Math.PI).toFloat()).apply {
            duration = 4000
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { particlePhase = it.animatedValue as Float }
            start()
        }.also { animators.add(it) }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animators.forEach { it.cancel() }
    }

    // ─── Dessin principal ─────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        val cy = h * 0.42f

        // Fond
        canvas.drawRect(0f, 0f, w, h, bgPaint)

        // Grille holographique
        drawGrid(canvas, w, h)

        // Ligne de scan
        drawScanLine(canvas, w, h)

        // Panneaux latéraux
        drawLeftPanel(canvas, w, h)
        drawRightPanel(canvas, w, h)

        // Anneaux rotatifs
        drawRings(canvas, cx, cy)

        // Orbe central (présence de WILLI)
        drawCentralOrb(canvas, cx, cy)

        // Arc du bas
        drawBottomArc(canvas, cx, h)

        // Textes de statut
        drawStatusTexts(canvas, w, h, cx)

        // Visualiseur audio (si écoute/parole)
        if (isListening || isSpeaking || waveAmplitude > 0f) {
            drawAudioVisualizer(canvas, cx, cy, w)
        }

        // Particules
        drawParticles(canvas, cx, cy)

        // Boussole en bas à droite
        drawCompass(canvas, w - 80f, h - 80f)
    }

    // ─── Grille hexagonale ────────────────────────────────────────────────────

    private fun drawGrid(canvas: Canvas, w: Float, h: Float) {
        val spacing = 60f
        var x = 0f
        while (x < w) {
            canvas.drawLine(x, 0f, x, h, gridPaint)
            x += spacing
        }
        var y = 0f
        while (y < h) {
            canvas.drawLine(0f, y, w, y, gridPaint)
            y += spacing
        }
    }

    // ─── Scan line ────────────────────────────────────────────────────────────

    private fun drawScanLine(canvas: Canvas, w: Float, h: Float) {
        val y = scanLine * h
        val scanPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, y, w, y,
                intArrayOf(Color.TRANSPARENT, colorGlow, colorBright, colorGlow, Color.TRANSPARENT),
                floatArrayOf(0f, 0.2f, 0.5f, 0.8f, 1f),
                Shader.TileMode.CLAMP
            )
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
            alpha = 120
        }
        canvas.drawLine(0f, y, w, y, scanPaint)
    }

    // ─── Panneaux latéraux ────────────────────────────────────────────────────

    private fun drawLeftPanel(canvas: Canvas, w: Float, h: Float) {
        val panelRight = w * 0.22f
        val panelTop = h * 0.15f
        val panelBottom = h * 0.75f

        // Bordure
        val path = Path().apply {
            moveTo(20f, panelTop)
            lineTo(panelRight, panelTop + 20f)
            lineTo(panelRight, panelBottom - 20f)
            lineTo(20f, panelBottom)
            close()
        }
        canvas.drawPath(path, dimPaint)
        canvas.drawPath(path, glowPaint.apply { alpha = 40 })

        // Barres de données
        val barX = 30f
        val dataLabels = listOf("SYS", "MEM", "CPU", "NET", "AI ")
        val dataValues = listOf(0.85f, confidenceLevel, 0.62f, 0.45f, 0.90f)

        dataLabels.forEachIndexed { i, label ->
            val barY = panelTop + 40f + i * 55f
            val pulse = sin(pulsePhase + i * 0.5f).toFloat() * 0.05f

            canvas.drawText(label, barX, barY, smallTextPaint)
            val barWidth = (panelRight - barX - 30f) * (dataValues[i] + pulse).coerceIn(0f, 1f)
            val barBg = RectF(barX, barY + 5f, panelRight - 15f, barY + 14f)
            val barFg = RectF(barX, barY + 5f, barX + barWidth, barY + 14f)

            canvas.drawRect(barBg, dimPaint)
            val barColor = when {
                dataValues[i] > 0.7f -> colorBright
                dataValues[i] > 0.4f -> colorPrimary
                else -> colorDim
            }
            val barPaint = Paint().apply { color = barColor; style = Paint.Style.FILL }
            canvas.drawRect(barFg, barPaint)
        }

        // Texte confiance
        canvas.drawText("CONFIANCE", 30f, panelBottom - 40f, smallTextPaint)
        canvas.drawText("${(confidenceLevel * 100).toInt()}%", 30f, panelBottom - 15f,
            textPaint.apply { color = colorBright; textSize = 32f })
        textPaint.color = colorText
        textPaint.textSize = 28f
    }

    private fun drawRightPanel(canvas: Canvas, w: Float, h: Float) {
        val panelLeft = w * 0.78f
        val panelTop = h * 0.15f
        val panelBottom = h * 0.75f

        val path = Path().apply {
            moveTo(w - 20f, panelTop)
            lineTo(panelLeft, panelTop + 20f)
            lineTo(panelLeft, panelBottom - 20f)
            lineTo(w - 20f, panelBottom)
            close()
        }
        canvas.drawPath(path, dimPaint)
        canvas.drawPath(path, glowPaint.apply { alpha = 40 })

        val labels = listOf("ÉMOTION", "STADE", "MÉMOIRE", "OBJECTIF")
        val values = listOf(emotionText, "ENFANT", "ACTIVE", "EN COURS")

        labels.forEachIndexed { i, label ->
            val ty = panelTop + 50f + i * 65f
            smallTextPaint.apply { color = colorDim; textSize = 18f }
            canvas.drawText(label, panelLeft + 12f, ty - 15f, smallTextPaint)
            smallTextPaint.apply { color = colorBright; textSize = 22f }
            canvas.drawText(values[i], panelLeft + 12f, ty + 5f, smallTextPaint)
        }
        smallTextPaint.color = colorPrimary
        smallTextPaint.textSize = 20f
    }

    // ─── Anneaux rotatifs ────────────────────────────────────────────────────

    private fun drawRings(canvas: Canvas, cx: Float, cy: Float) {
        val pulse = (sin(pulsePhase) * 0.05f + 1f)

        // Anneau externe (120dp radius)
        canvas.save()
        canvas.rotate(rotation1, cx, cy)
        drawDashedArc(canvas, cx, cy, 260f * pulse, primaryPaint, 15, 4f)
        canvas.restore()

        // Ticks fixes de l'anneau externe
        for (i in 0 until 24) {
            val angle = Math.toRadians(i * 15.0)
            val r1 = 255f
            val r2 = 270f
            val px1 = cx + r1 * cos(angle).toFloat()
            val py1 = cy + r1 * sin(angle).toFloat()
            val px2 = cx + r2 * cos(angle).toFloat()
            val py2 = cy + r2 * sin(angle).toFloat()
            canvas.drawLine(px1, py1, px2, py2, brightPaint)
        }

        // Anneau moyen
        canvas.save()
        canvas.rotate(rotation2, cx, cy)
        drawDashedArc(canvas, cx, cy, 200f * pulse, accentPaint, 8, 8f)

        // Losanges sur l'anneau moyen
        for (i in 0 until 8) {
            val angle = Math.toRadians(i * 45.0)
            val r = 200f * pulse
            val px = cx + r * cos(angle).toFloat()
            val py = cy + r * sin(angle).toFloat()
            drawDiamond(canvas, px, py, 8f, brightPaint)
        }
        canvas.restore()

        // Anneau interne
        canvas.save()
        canvas.rotate(rotation3, cx, cy)
        drawDashedArc(canvas, cx, cy, 145f * pulse, glowPaint.apply { alpha = 180 }, 6, 6f)
        canvas.restore()
    }

    private fun drawDashedArc(
        canvas: Canvas, cx: Float, cy: Float, radius: Float,
        paint: Paint, segments: Int, gapDegrees: Float
    ) {
        val segmentDeg = 360f / segments
        val arcDeg = segmentDeg - gapDegrees
        val rect = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
        for (i in 0 until segments) {
            val startAngle = i * segmentDeg
            canvas.drawArc(rect, startAngle, arcDeg, false, paint)
        }
    }

    private fun drawDiamond(canvas: Canvas, cx: Float, cy: Float, size: Float, paint: Paint) {
        val path = Path().apply {
            moveTo(cx, cy - size)
            lineTo(cx + size, cy)
            lineTo(cx, cy + size)
            lineTo(cx - size, cy)
            close()
        }
        canvas.drawPath(path, paint)
    }

    // ─── Orbe central ────────────────────────────────────────────────────────

    private fun drawCentralOrb(canvas: Canvas, cx: Float, cy: Float) {
        val pulse = sin(pulsePhase).toFloat()
        val baseRadius = 80f
        val orbRadius = baseRadius + pulse * 5f

        // Glow externe
        val glowRadius = orbRadius + 30f + pulse * 10f
        val radialGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                cx, cy, glowRadius,
                intArrayOf(Color.argb(180, 255, 0, 34), Color.argb(80, 200, 0, 20), Color.TRANSPARENT),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(cx, cy, glowRadius, radialGlow)

        // Cercle principal avec gradient
        val orbGradient = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                cx - orbRadius * 0.3f, cy - orbRadius * 0.3f, orbRadius,
                intArrayOf(Color.parseColor("#FF5252"), Color.parseColor("#CC1122"), Color.parseColor("#660011")),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(cx, cy, orbRadius, orbGradient)

        // Bordure lumineuse
        glowPaint.alpha = 255
        glowPaint.strokeWidth = 3f
        canvas.drawCircle(cx, cy, orbRadius, glowPaint)

        // Lettre W au centre
        val wPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FFE4E4")
            textSize = 60f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            maskFilter = BlurMaskFilter(6f, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawText("W", cx, cy + 22f, wPaint)

        // Lignes croisées (réticule)
        val crossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorBright
            strokeWidth = 1f
            alpha = 100
        }
        val crossSize = orbRadius + 20f
        canvas.drawLine(cx - crossSize, cy, cx - orbRadius - 5f, cy, crossPaint)
        canvas.drawLine(cx + orbRadius + 5f, cy, cx + crossSize, cy, crossPaint)
        canvas.drawLine(cx, cy - crossSize, cx, cy - orbRadius - 5f, crossPaint)
        canvas.drawLine(cx, cy + orbRadius + 5f, cx, cy + crossSize, crossPaint)
    }

    // ─── Arc du bas ───────────────────────────────────────────────────────────

    private fun drawBottomArc(canvas: Canvas, cx: Float, h: Float) {
        val arcY = h * 0.85f
        val arcWidth = cx * 1.6f
        val arcRect = RectF(cx - arcWidth, arcY - 100f, cx + arcWidth, arcY + 100f)

        // Arc principal
        glowPaint.strokeWidth = 2f
        glowPaint.alpha = 200
        canvas.drawArc(arcRect, 190f, 160f, false, glowPaint)
        canvas.drawArc(arcRect, 190f, 160f, false, primaryPaint)

        // Ticks sur l'arc
        for (i in 0..20) {
            val angle = Math.toRadians(190.0 + i * 8.0)
            val r1 = arcWidth
            val r2 = arcWidth + 15f
            val tickX1 = cx + r1 * cos(angle).toFloat()
            val tickY1 = (arcY - 100f + arcY + 100f) / 2f + 100f * sin(angle).toFloat()
            val tickX2 = cx + r2 * cos(angle).toFloat()
            val tickY2 = (arcY - 100f + arcY + 100f) / 2f + 115f * sin(angle).toFloat()

            val tick = if (i % 5 == 0) primaryPaint else dimPaint
            canvas.drawLine(tickX1, tickY1, tickX2, tickY2, tick)
        }

        // Statut en bas de l'arc
        val statusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorBright
            textSize = 24f
            typeface = Typeface.MONOSPACE
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(statusText, cx, arcY + 40f, statusPaint)
    }

    // ─── Textes de statut ─────────────────────────────────────────────────────

    private fun drawStatusTexts(canvas: Canvas, w: Float, h: Float, cx: Float) {
        // Titre WILLI en haut
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorBright
            textSize = 48f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            maskFilter = BlurMaskFilter(8f, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawText("WILLI", cx, h * 0.08f, titlePaint)

        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorPrimary
            textSize = 22f
            typeface = Typeface.MONOSPACE
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(subStatusText, cx, h * 0.12f, subPaint)

        // Coordonnées coin
        val coordPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorDim
            textSize = 16f
            typeface = Typeface.MONOSPACE
        }
        canvas.drawText("WILLI v1.0 | IRON SHIELD", 20f, h - 30f, coordPaint)
        canvas.drawText(
            android.text.format.DateFormat.format("HH:mm:ss", java.util.Date()).toString(),
            w - 150f, h - 30f, coordPaint
        )
    }

    // ─── Visualiseur audio ────────────────────────────────────────────────────

    private fun drawAudioVisualizer(canvas: Canvas, cx: Float, cy: Float, w: Float) {
        val bars = 32
        val barW = w * 0.6f / bars
        val startX = cx - w * 0.3f
        val baseY = cy + 310f
        val maxH = 60f

        val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isListening) colorBright else colorAccent
            style = Paint.Style.FILL
        }

        for (i in 0 until bars) {
            val phase = particlePhase + i * 0.4f
            val amp = (sin(phase).toFloat().absoluteValue * 0.5f + 0.5f) *
                    (waveAmplitude.coerceAtLeast(0.1f))
            val barH = amp * maxH
            val x = startX + i * barW
            canvas.drawRect(x + 1f, baseY - barH, x + barW - 1f, baseY, wavePaint)
        }

        // Label
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorBright
            textSize = 20f
            typeface = Typeface.MONOSPACE
            textAlign = Paint.Align.CENTER
        }
        val label = when {
            isSpeaking -> "▶ WILLI PARLE"
            isListening -> "● ÉCOUTE EN COURS"
            else -> ""
        }
        canvas.drawText(label, cx, baseY + 30f, labelPaint)
    }

    // ─── Particules orbitales ─────────────────────────────────────────────────

    private fun drawParticles(canvas: Canvas, cx: Float, cy: Float) {
        val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorBright
            maskFilter = BlurMaskFilter(4f, BlurMaskFilter.Blur.NORMAL)
        }

        val particleCount = 8
        for (i in 0 until particleCount) {
            val angle = particlePhase + i * (2 * Math.PI / particleCount).toFloat()
            val radius = 290f + sin(particlePhase * 2 + i).toFloat() * 15f
            val px = cx + radius * cos(angle)
            val py = cy + radius * sin(angle)
            particlePaint.alpha = (150 + sin(angle).toFloat() * 80).toInt().coerceIn(0, 255)
            canvas.drawCircle(px, py, 4f, particlePaint)
        }
    }

    // ─── Boussole ─────────────────────────────────────────────────────────────

    private fun drawCompass(canvas: Canvas, cx: Float, cy: Float) {
        val r = 40f
        canvas.drawCircle(cx, cy, r, dimPaint)
        canvas.drawCircle(cx, cy, r, glowPaint.apply { alpha = 30 })

        canvas.save()
        canvas.rotate(rotation1 * 0.5f, cx, cy)
        val cPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorBright
            strokeWidth = 2f
        }
        canvas.drawLine(cx, cy - r + 8f, cx, cy, cPaint)
        cPaint.color = colorDim
        canvas.drawLine(cx, cy, cx, cy + r - 8f, cPaint)
        canvas.restore()

        val cTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorPrimary
            textSize = 14f
            typeface = Typeface.MONOSPACE
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("N", cx, cy - r - 5f, cTextPaint)
    }
}
