package com.example.graphplotter

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import com.github.mikephil.charting.charts.LineChart

class ScientificLineChart @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : LineChart(context, attrs, defStyle) {

    var chartTitle: String = "Graph Plot"
    var xAxisTitle: String = "X Axis"
    var yAxisTitle: String = "Y Axis"

    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = 34f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.DKGRAY
        textSize = 26f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    init {
        setDrawBorders(true)
        setBorderColor(Color.BLACK)
        setBorderWidth(1.5f)
        setExtraOffsets(18f, 20f, 8f, 18f)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (chartTitle.isNotBlank()) {
            canvas.drawText(chartTitle, viewPortHandler.contentCenter.x, viewPortHandler.contentTop() - 10f, titlePaint)
        }

        if (xAxisTitle.isNotBlank()) {
            canvas.drawText(xAxisTitle, viewPortHandler.contentCenter.x, viewPortHandler.contentBottom() + 44f, labelPaint)
        }

        if (yAxisTitle.isNotBlank()) {
            val yX = (viewPortHandler.contentLeft() - 56f).coerceAtLeast(22f)
            val yY = viewPortHandler.contentCenter.y
            canvas.save()
            canvas.rotate(-90f, yX, yY)
            canvas.drawText(yAxisTitle, yX, yY, labelPaint)
            canvas.restore()
        }
    }
}
