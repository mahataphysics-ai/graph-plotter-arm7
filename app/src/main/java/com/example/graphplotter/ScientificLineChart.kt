package com.example.graphplotter

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.utils.MPPointD

class ScientificLineChart @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : LineChart(context, attrs, defStyle) {

    var chartTitle: String = "Graph Plot"
    var xAxisTitle: String = "X Axis"
    var yAxisTitle: String = "Y Axis"
    var customTypeface: Typeface = Typeface.DEFAULT
    var baseFontSizeSp: Float = 12f

    // Plot type: 0=Points/Scatter, 1=Line, 2=LinePoints, 3=Box/Bar, 4=Histogram
    var plotType: Int = 0
    // Marker type: 0=Dot(1px), 1=Plus(+), 2=Asterisk(*), 3=Filled Circle, 4=Open Circle
    var markerType: Int = 3
    var markerSize: Float = 6f
    var markerColor: Int = Color.RED
    var customDataPoints: List<Entry> = emptyList()
    var histogramBins: List<Pair<Float, Float>> = emptyList()
    var binWidth: Float = 1f

    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.DKGRAY
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val shapePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        setDrawBorders(true)
        setBorderColor(Color.BLACK)
        setBorderWidth(1.5f)
        setExtraOffsets(18f, 20f, 8f, 18f)
        legend.setDrawInside(true)
        legend.verticalAlignment = Legend.LegendVerticalAlignment.TOP
        legend.horizontalAlignment = Legend.LegendHorizontalAlignment.RIGHT
        legend.orientation = Legend.LegendOrientation.VERTICAL
    }


    var canvasBgColor: Int = Color.WHITE

    fun setCanvasBackground(bgColor: Int) {
        canvasBgColor = bgColor
        setBackgroundColor(bgColor)
        val isDark = Color.luminance(bgColor) < 0.4f
        val fgColor = if (isDark) Color.WHITE else Color.BLACK
        val subColor = if (isDark) Color.LTGRAY else Color.DKGRAY
        setBorderColor(fgColor)
        titlePaint.color = fgColor
        labelPaint.color = subColor
        xAxis?.textColor = fgColor
        axisLeft?.textColor = fgColor
        legend?.textColor = fgColor
        invalidate()
    }

    fun applyFontSettings() {
        val density = resources?.displayMetrics?.scaledDensity ?: 2.5f
        titlePaint.typeface = customTypeface
        titlePaint.textSize = (baseFontSizeSp + 3f) * density
        labelPaint.typeface = customTypeface
        labelPaint.textSize = baseFontSizeSp * density
        xAxis?.typeface = customTypeface
        xAxis?.textSize = (baseFontSizeSp - 2f).coerceAtLeast(8f)
        axisLeft?.typeface = customTypeface
        axisLeft?.textSize = (baseFontSizeSp - 2f).coerceAtLeast(8f)
        legend?.typeface = customTypeface
        legend?.textSize = (baseFontSizeSp - 1f).coerceAtLeast(9f)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val contentRect = viewPortHandler.contentRect
        canvas.save()
        canvas.clipRect(contentRect)

        val trans = getTransformer(axisLeft.axisDependency)
        val zeroPt = trans.getPixelForValues(0f, 0f)
        val zeroY = zeroPt.y.toFloat().coerceIn(contentRect.top, contentRect.bottom)
        MPPointD.recycleInstance(zeroPt)

        // Draw Box/Bar (type 3)
        if (plotType == 3 && customDataPoints.isNotEmpty()) {
            shapePaint.style = Paint.Style.FILL
            shapePaint.color = Color.argb(110, Color.red(markerColor), Color.green(markerColor), Color.blue(markerColor))
            val halfW = (contentRect.width() / (customDataPoints.size * 2.5f)).coerceIn(6f, 28f)
            customDataPoints.forEach { e ->
                val pt = trans.getPixelForValues(e.x, e.y)
                val px = pt.x.toFloat(); val py = pt.y.toFloat()
                canvas.drawRect(px - halfW, minOf(py, zeroY), px + halfW, maxOf(py, zeroY), shapePaint)
                MPPointD.recycleInstance(pt)
            }
        }

        // Draw Histogram (type 4)
        if (plotType == 4 && histogramBins.isNotEmpty()) {
            shapePaint.style = Paint.Style.FILL
            shapePaint.color = Color.argb(130, 30, 136, 229)
            histogramBins.forEach { (center, count) ->
                val pLeft = trans.getPixelForValues(center - binWidth / 2f, count)
                val pRight = trans.getPixelForValues(center + binWidth / 2f, 0f)
                canvas.drawRect(pLeft.x.toFloat(), pLeft.y.toFloat(), pRight.x.toFloat(), pRight.y.toFloat(), shapePaint)
                MPPointD.recycleInstance(pLeft); MPPointD.recycleInstance(pRight)
            }
        }

        // Draw Custom Markers for Points/Scatter (0) or LinePoints (2)
        if ((plotType == 0 || plotType == 2) && customDataPoints.isNotEmpty()) {
            shapePaint.color = markerColor
            shapePaint.strokeWidth = 2.5f
            val r = markerSize * 2.2f
            customDataPoints.forEach { e ->
                val pt = trans.getPixelForValues(e.x, e.y)
                val px = pt.x.toFloat(); val py = pt.y.toFloat()
                when (markerType) {
                    0 -> { shapePaint.style = Paint.Style.FILL; canvas.drawPoint(px, py, shapePaint) }
                    1 -> {
                        canvas.drawLine(px - r, py, px + r, py, shapePaint)
                        canvas.drawLine(px, py - r, px, py + r, shapePaint)
                    }
                    2 -> {
                        val d = r * 0.75f
                        canvas.drawLine(px - r, py, px + r, py, shapePaint)
                        canvas.drawLine(px, py - r, px, py + r, shapePaint)
                        canvas.drawLine(px - d, py - d, px + d, py + d, shapePaint)
                        canvas.drawLine(px - d, py + d, px + d, py - d, shapePaint)
                    }
                    3 -> { shapePaint.style = Paint.Style.FILL; canvas.drawCircle(px, py, r, shapePaint) }
                    4 -> { shapePaint.style = Paint.Style.STROKE; canvas.drawCircle(px, py, r, shapePaint) }
                }
                MPPointD.recycleInstance(pt)
            }
        }
        canvas.restore()

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
