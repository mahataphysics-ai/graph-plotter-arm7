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
        textSize = 36f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.DKGRAY
        textSize = 30f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    init {
        setDrawBorders(true)
        setBorderColor(Color.BLACK)
        setBorderWidth(1.5f)
        setExtraOffsets(26f, 24f, 12f, 24f)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (chartTitle.isNotBlank()) {
            canvas.drawText(chartTitle, width / 2f, 34f, titlePaint)
        }

        if (xAxisTitle.isNotBlank()) {
            canvas.drawText(xAxisTitle, viewPortHandler.contentCenter.x, height - 10f, labelPaint)
        }

        if (yAxisTitle.isNotBlank()) {
            canvas.save()
            canvas.rotate(-90f, 24f, viewPortHandler.contentCenter.y)
            canvas.drawText(yAxisTitle, 24f, viewPortHandler.contentCenter.y, labelPaint)
            canvas.restore()
        }
    }
}
