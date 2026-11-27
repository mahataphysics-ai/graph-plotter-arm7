package com.example.graphplotter

import android.app.AlertDialog
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import net.objecthunter.exp4j.ExpressionBuilder
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Locale
import kotlin.math.exp
import kotlin.math.ln

class MainActivity : AppCompatActivity() {

    private lateinit var lineChart: ScientificLineChart
    private lateinit var etFunctions: EditText
    private lateinit var tblCsvData: TableLayout

    private var minX = -10.0
    private var maxX = 10.0
    private var minY: Float? = null
    private var maxY: Float? = null
    private var gridEnabled = true
    private var lineDashed = false
    private var dataLineWidth = 2.2f
    private var dataLineColor = Color.RED
    private var fitMode = 0
    private var lastFuncSets: List<List<Entry>> = emptyList()
    private var lastDataEntries: List<Entry> = emptyList()

    private val palette = listOf(Color.BLUE, Color.rgb(0, 150, 0), Color.MAGENTA, Color.rgb(255, 120, 0), Color.CYAN)
    private val colorNames = arrayOf("Red", "Blue", "Green", "Black", "Magenta", "Orange", "Cyan")
    private val colorValues = intArrayOf(Color.RED, Color.BLUE, Color.rgb(0, 150, 0), Color.BLACK, Color.MAGENTA, Color.rgb(255, 120, 0), Color.CYAN)

    private val csvPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { loadCsvFromUri(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        lineChart = findViewById(R.id.lineChart)
        etFunctions = findViewById(R.id.etFunctions)
        tblCsvData = findViewById(R.id.tblCsvData)

        findViewById<Button>(R.id.btnPlot).setOnClickListener { plotAll() }
        findViewById<Button>(R.id.btnAddRow).setOnClickListener { addEditableRow("", "") }
        findViewById<Button>(R.id.btnDelRow).setOnClickListener { deleteLastRow() }
        findViewById<Button>(R.id.btnCsv).setOnClickListener { csvPicker.launch("*/*") }
        findViewById<Button>(R.id.btnSave).setOnClickListener { showExportDialog() }
        findViewById<Button>(R.id.btnText).setOnClickListener { showTextMenu() }
        findViewById<Button>(R.id.btnRange).setOnClickListener { showRangeMenu() }
        findViewById<Button>(R.id.btnGrid).setOnClickListener { showGridDialog() }
        findViewById<Button>(R.id.btnType).setOnClickListener { showTypeMenu() }
        findViewById<Button>(R.id.btnStyle).setOnClickListener { showStyleMenu() }
        findViewById<Button>(R.id.btnFit).setOnClickListener { showFitMenu() }

        lineChart.setBackgroundColor(Color.WHITE)
        lineChart.xAxis.position = XAxis.XAxisPosition.BOTTOM
        lineChart.axisRight.isEnabled = true
        lineChart.axisRight.setDrawLabels(false)
        lineChart.axisRight.setDrawGridLines(false)
        lineChart.description.isEnabled = false
        lineChart.applyFontSettings()

        populateEditableTable(listOf(Pair("-6", "2"), Pair("-2", "-3"), Pair("1", "4"), Pair("5", "1")))
        plotAll()
    }

    private fun showTextMenu() {
        val items = arrayOf("Title", "X Label", "Y Label", "Key (Legend)", "Font", "Font Size")
        AlertDialog.Builder(this).setTitle("Text & Labels").setItems(items) { _, which ->
            when (which) {
                0 -> showSingleTextDialog("Graph Title", lineChart.chartTitle) { lineChart.chartTitle = it; lineChart.invalidate() }
                1 -> showSingleTextDialog("X Axis Label", lineChart.xAxisTitle) { lineChart.xAxisTitle = it; lineChart.invalidate() }
                2 -> showSingleTextDialog("Y Axis Label", lineChart.yAxisTitle) { lineChart.yAxisTitle = it; lineChart.invalidate() }
                3 -> showLegendDialog()
                4 -> showFontDialog()
                5 -> showFontSizeDialog()
            }
        }.show()
    }

    private fun showSingleTextDialog(title: String, current: String, onSave: (String) -> Unit) {
        val input = EditText(this).apply { setText(current) }
        AlertDialog.Builder(this).setTitle(title).setView(input)
            .setPositiveButton("Apply") { _, _ -> onSave(input.text.toString()) }
            .setNegativeButton("Cancel", null).show()
    }

    private fun showLegendDialog() {
        val opts = arrayOf("Top Left (Inside)", "Top Right (Inside)", "Bottom Left (Inside)", "Bottom Right (Inside)", "Hide Legend")
        AlertDialog.Builder(this).setTitle("Key (Legend) Position").setItems(opts) { _, which ->
            lineChart.legend.isEnabled = which != 4
            lineChart.legend.setDrawInside(true)
            when (which) {
                0 -> { lineChart.legend.verticalAlignment = Legend.LegendVerticalAlignment.TOP; lineChart.legend.horizontalAlignment = Legend.LegendHorizontalAlignment.LEFT }
                1 -> { lineChart.legend.verticalAlignment = Legend.LegendVerticalAlignment.TOP; lineChart.legend.horizontalAlignment = Legend.LegendHorizontalAlignment.RIGHT }
                2 -> { lineChart.legend.verticalAlignment = Legend.LegendVerticalAlignment.BOTTOM; lineChart.legend.horizontalAlignment = Legend.LegendHorizontalAlignment.LEFT }
                3 -> { lineChart.legend.verticalAlignment = Legend.LegendVerticalAlignment.BOTTOM; lineChart.legend.horizontalAlignment = Legend.LegendHorizontalAlignment.RIGHT }
            }
            lineChart.invalidate()
        }.show()
    }

    private fun showFontDialog() {
        val fonts = arrayOf("Default (Sans)", "Serif", "Monospace")
        AlertDialog.Builder(this).setTitle("Select Font").setItems(fonts) { _, which ->
            lineChart.customTypeface = when (which) { 1 -> Typeface.SERIF; 2 -> Typeface.MONOSPACE; else -> Typeface.SANS_SERIF }
            lineChart.applyFontSettings()
        }.show()
    }

    private fun showFontSizeDialog() {
        val input = EditText(this).apply { setText(lineChart.baseFontSizeSp.toString()); inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL }
        AlertDialog.Builder(this).setTitle("Font Size (sp)").setView(input)
            .setPositiveButton("Apply") { _, _ -> input.text.toString().toFloatOrNull()?.let { lineChart.baseFontSizeSp = it.coerceIn(8f, 24f); lineChart.applyFontSettings() } }
            .setNegativeButton("Cancel", null).show()
    }

    private fun showRangeMenu() {
        AlertDialog.Builder(this).setTitle("Select Axis Range").setItems(arrayOf("X Range", "Y Range")) { _, which -> showRangeDialog(which == 0) }.show()
    }

    private fun showGridDialog() {
        AlertDialog.Builder(this).setTitle("Grid Lines").setItems(arrayOf("Grid ON", "Grid OFF")) { _, which -> gridEnabled = (which == 0); plotAll() }.show()
    }

    private fun showTypeMenu() {
        val types = arrayOf("Points / Scatter", "Line", "LinePoints", "Box / Bar", "Histogram")
        AlertDialog.Builder(this).setTitle("Plot Type").setItems(types) { _, which -> lineChart.plotType = which; plotAll() }.show()
    }

    private fun showStyleMenu() {
        val items = arrayOf("1. Marker Type", "2. Marker Size", "3. Marker Color", "4. Line Type", "5. Line Width", "6. Line Color")
        AlertDialog.Builder(this).setTitle("Plot Style").setItems(items) { _, which ->
            when (which) {
                0 -> AlertDialog.Builder(this).setTitle("Marker Type")
                    .setItems(arrayOf("Dot (1px)", "Plus (+)", "Asterisk (*)", "Filled Circle", "Open Circle")) { _, m -> lineChart.markerType = m; plotAll() }.show()
                1 -> showNumberDialog("Marker Size (1-15)", lineChart.markerSize) { lineChart.markerSize = it.coerceIn(1f, 15f); plotAll() }
                2 -> AlertDialog.Builder(this).setTitle("Marker Color").setItems(colorNames) { _, c -> lineChart.markerColor = colorValues.get(c); plotAll() }.show()
                3 -> AlertDialog.Builder(this).setTitle("Line Type").setItems(arrayOf("Solid", "Dash-Dot")) { _, l -> lineDashed = (l == 1); plotAll() }.show()
                4 -> showNumberDialog("Line Width (0.5-10)", dataLineWidth) { dataLineWidth = it.coerceIn(0.5f, 10f); plotAll() }
                5 -> AlertDialog.Builder(this).setTitle("Line Color").setItems(colorNames) { _, c -> dataLineColor = colorValues.get(c); plotAll() }.show()
            }
        }.show()
    }

    private fun showFitMenu() {
        val fits = arrayOf("None (Clear Fit)", "Linear Fit (y = mx + c)", "Quadratic Fit (y = ax² + bx + c)", "Exponential Fit (y = a·e^(bx))")
        AlertDialog.Builder(this).setTitle("Curve Fit Scatter Data").setItems(fits) { _, which -> fitMode = which; plotAll() }.show()
    }

    private fun showNumberDialog(title: String, current: Float, onSave: (Float) -> Unit) {
        val input = EditText(this).apply { setText(current.toString()); inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL }
        AlertDialog.Builder(this).setTitle(title).setView(input)
            .setPositiveButton("Apply") { _, _ -> input.text.toString().toFloatOrNull()?.let(onSave) }
            .setNegativeButton("Cancel", null).show()
    }

    private fun showRangeDialog(isX: Boolean) {
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 20, 40, 10) }
        val inputMin = EditText(this).apply {
            hint = if (isX) "Min X" else "Min Y (blank = Auto)"
            setText(if (isX) minX.toString() else (minY?.toString() ?: ""))
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val inputMax = EditText(this).apply {
            hint = if (isX) "Max X" else "Max Y (blank = Auto)"
            setText(if (isX) maxX.toString() else (maxY?.toString() ?: ""))
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        layout.addView(inputMin); layout.addView(inputMax)
        AlertDialog.Builder(this).setTitle(if (isX) "Set X Range" else "Set Y Range").setView(layout)
            .setPositiveButton("Apply") { _, _ ->
                val a = inputMin.text.toString().toDoubleOrNull()
                val b = inputMax.text.toString().toDoubleOrNull()
                if (isX && a != null && b != null && a < b) { minX = a; maxX = b }
                else if (!isX) { minY = a?.toFloat(); maxY = b?.toFloat() }
                plotAll()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun plotAll() {
        val dataSets = mutableListOf<LineDataSet>()
        val funcLists = mutableListOf<List<Entry>>()
        val step = (maxX - minX) / 200.0
        etFunctions.text.toString().split(";").map { it.trim() }.filter { it.isNotEmpty() }.forEachIndexed { idx, eq ->
            try {
                val entries = mutableListOf<Entry>()
                val expr = ExpressionBuilder(eq).variable("x").build()
                var x = minX
                while (x <= maxX) {
                    try {
                        val y = expr.setVariable("x", x).evaluate()
                        if (!y.isNaN() && !y.isInfinite()) entries.add(Entry(x.toFloat(), y.toFloat()))
                    } catch (_: Exception) {}
                    x += step
                }
                funcLists.add(entries)
                dataSets.add(LineDataSet(entries, "f${idx + 1}(x)=$eq").apply {
                    color = palette.get(idx % palette.size); setDrawCircles(false); lineWidth = 2.2f
                    if (lineDashed) enableDashedLine(18f, 8f, 0f)
                })
            } catch (_: Exception) { Toast.makeText(this, "Invalid: $eq", Toast.LENGTH_SHORT).show() }
        }
        lastFuncSets = funcLists

        val entries = readEditableTableEntries()
        lastDataEntries = entries
        lineChart.customDataPoints = entries
        lineChart.histogramBins = emptyList()

        if (entries.isNotEmpty()) {
            when (lineChart.plotType) {
                0 -> dataSets.add(LineDataSet(entries, "Scatter").apply { color = Color.TRANSPARENT; setDrawCircles(false) })
                1, 2 -> dataSets.add(LineDataSet(entries, if (lineChart.plotType == 1) "Data Line" else "LinePoints").apply {
                    color = dataLineColor; setDrawCircles(false); lineWidth = dataLineWidth
                    if (lineDashed) enableDashedLine(18f, 8f, 0f)
                })
                3 -> dataSets.add(LineDataSet(entries, "Box/Bar").apply { color = Color.TRANSPARENT; setDrawCircles(false) })
                4 -> {
                    val ys = entries.map { it.y }
                    val lo = ys.minOrNull() ?: 0f; val hi = ys.maxOrNull() ?: 1f
                    val bins = 5; val bw = ((hi - lo) / bins).coerceAtLeast(0.5f)
                    val counts = (0 until bins).map { b ->
                        val c = lo + (b + 0.5f) * bw
                        val cnt = ys.count { it >= lo + b * bw && (b == bins - 1 || it < lo + (b + 1) * bw) }.toFloat()
                        Pair(c, cnt)
                    }
                    lineChart.binWidth = bw; lineChart.histogramBins = counts
                    val dummy = counts.flatMap { listOf(Entry(it.first - bw / 2f, 0f), Entry(it.first + bw / 2f, it.second)) }
                    dataSets.add(LineDataSet(dummy, "Histogram").apply { color = Color.TRANSPARENT; setDrawCircles(false) })
                }
            }
            computeFit(entries)?.let { (fitPts, label) ->
                dataSets.add(LineDataSet(fitPts, label).apply {
                    color = Color.DKGRAY; setDrawCircles(false); lineWidth = 2.4f; enableDashedLine(14f, 8f, 0f)
                })
            }
        }

        lineChart.xAxis.setDrawGridLines(gridEnabled)
        lineChart.axisLeft.setDrawGridLines(gridEnabled)
        if (lineChart.plotType != 4) {
            lineChart.xAxis.axisMinimum = minX.toFloat(); lineChart.xAxis.axisMaximum = maxX.toFloat()
        } else {
            lineChart.xAxis.resetAxisMinimum(); lineChart.xAxis.resetAxisMaximum()
        }
        if (minY != null && maxY != null) {
            lineChart.axisLeft.axisMinimum = minY!!; lineChart.axisLeft.axisMaximum = maxY!!
        } else {
            lineChart.axisLeft.resetAxisMinimum(); lineChart.axisLeft.resetAxisMaximum()
        }
        lineChart.data = LineData(dataSets.toList())
        lineChart.invalidate()
    }

    private fun computeFit(pts: List<Entry>): Pair<List<Entry>, String>? {
        if (fitMode == 0 || pts.size < 2) return null
        val xLo = pts.minOf { it.x }.toDouble(); val xHi = pts.maxOf { it.x }.toDouble()
        val step = ((xHi - xLo) / 100.0).coerceAtLeast(0.05)
        val n = pts.size.toDouble()
        if (fitMode == 1) {
            val sx = pts.sumOf { it.x.toDouble() }; val sy = pts.sumOf { it.y.toDouble() }
            val sxx = pts.sumOf { it.x.toDouble() * it.x }; val sxy = pts.sumOf { it.x.toDouble() * it.y }
            val denom = n * sxx - sx * sx; if (denom == 0.0) return null
            val m = (n * sxy - sx * sy) / denom; val c = (sy - m * sx) / n
            val curve = generateSequence(xLo) { it + step }.takeWhile { it <= xHi }.map { Entry(it.toFloat(), (m * it + c).toFloat()) }.toList()
            return Pair(curve, String.format(Locale.US, "Fit: %.2fx+%.2f", m, c))
        } else if (fitMode == 2 && pts.size >= 3) {
            val sx = pts.sumOf { it.x.toDouble() }; val sx2 = pts.sumOf { Math.pow(it.x.toDouble(), 2.0) }
            val sx3 = pts.sumOf { Math.pow(it.x.toDouble(), 3.0) }; val sx4 = pts.sumOf { Math.pow(it.x.toDouble(), 4.0) }
            val sy = pts.sumOf { it.y.toDouble() }; val sxy = pts.sumOf { it.x.toDouble() * it.y }
            val sx2y = pts.sumOf { Math.pow(it.x.toDouble(), 2.0) * it.y }
            val d = n * (sx2 * sx4 - sx3 * sx3) - sx * (sx * sx4 - sx2 * sx3) + sx2 * (sx * sx3 - sx2 * sx2)
            if (d == 0.0) return null
            val c = (sy * (sx2 * sx4 - sx3 * sx3) - sx * (sxy * sx4 - sx3 * sx2y) + sx2 * (sxy * sx3 - sx2 * sx2y)) / d
            val b = (n * (sxy * sx4 - sx3 * sx2y) - sy * (sx * sx4 - sx2 * sx3) + sx2 * (sx * sx2y - sxy * sx2)) / d
            val a = (n * (sx2 * sx2y - sx3 * sxy) - sx * (sx * sx2y - sx2 * sxy) + sy * (sx * sx3 - sx2 * sx2)) / d
            val curve = generateSequence(xLo) { it + step }.takeWhile { it <= xHi }.map { Entry(it.toFloat(), (a * it * it + b * it + c).toFloat()) }.toList()
            return Pair(curve, String.format(Locale.US, "Fit: %.2fx²+%.2fx+%.2f", a, b, c))
        } else if (fitMode == 3) {
            val pos = pts.filter { it.y > 0f }; if (pos.size < 2) return null
            val mN = pos.size.toDouble(); val sx = pos.sumOf { it.x.toDouble() }; val sly = pos.sumOf { ln(it.y.toDouble()) }
            val sxx = pos.sumOf { it.x.toDouble() * it.x }; val sxly = pos.sumOf { it.x.toDouble() * ln(it.y.toDouble()) }
            val denom = mN * sxx - sx * sx; if (denom == 0.0) return null
            val b = (mN * sxly - sx * sly) / denom; val a = exp((sly - b * sx) / mN)
            val curve = generateSequence(xLo) { it + step }.takeWhile { it <= xHi }.map { Entry(it.toFloat(), (a * exp(b * it)).toFloat()) }.toList()
            return Pair(curve, String.format(Locale.US, "Fit: %.2fe^(%.2fx)", a, b))
        }
        return null
    }

    private fun populateEditableTable(rows: List<Pair<String, String>>) {
        tblCsvData.removeAllViews()
        rows.forEach { addEditableRow(it.first, it.second) }
    }

    private fun addEditableRow(xVal: String, yVal: String) {
        val row = TableRow(this)
        listOf(xVal, yVal).forEach { cellText ->
            row.addView(EditText(this).apply {
                setText(cellText); hint = "0.0"
                setTextColor(Color.WHITE); setHintTextColor(Color.LTGRAY)
                gravity = Gravity.CENTER; textSize = 12f; setPadding(6, 4, 6, 4)
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED or InputType.TYPE_NUMBER_FLAG_DECIMAL
            })
        }
        tblCsvData.addView(row)
    }

    private fun deleteLastRow() {
        if (tblCsvData.childCount > 0) { tblCsvData.removeViewAt(tblCsvData.childCount - 1); plotAll() }
    }

    private fun readEditableTableEntries(): List<Entry> {
        val list = mutableListOf<Entry>()
        for (i in 0 until tblCsvData.childCount) {
            val row = tblCsvData.getChildAt(i) as? TableRow ?: continue
            val x = (row.getChildAt(0) as? EditText)?.text?.toString()?.trim()?.toFloatOrNull()
            val y = (row.getChildAt(1) as? EditText)?.text?.toString()?.trim()?.toFloatOrNull()
            if (x != null && y != null) list.add(Entry(x, y))
        }
        return list.sortedBy { it.x }
    }

    private fun loadCsvFromUri(uri: Uri) {
        try {
            val pairs = mutableListOf<Pair<String, String>>()
            contentResolver.openInputStream(uri)?.use { stream ->
                BufferedReader(InputStreamReader(stream)).forEachLine { line ->
                    val cols = line.split(",")
                    val x = cols.firstOrNull()?.trim()?.toFloatOrNull()
                    val y = cols.getOrNull(1)?.trim()?.toFloatOrNull()
                    if (x != null && y != null) pairs.add(Pair(x.toString(), y.toString()))
                }
            }
            if (pairs.isNotEmpty()) { populateEditableTable(pairs); plotAll() }
        } catch (_: Exception) { Toast.makeText(this, "CSV read failed", Toast.LENGTH_SHORT).show() }
    }

    private fun showExportDialog() {
        AlertDialog.Builder(this).setTitle("Export Graph As")
            .setItems(arrayOf("PNG Image", "SVG Vector")) { _, which -> if (which == 0) savePng() else saveSvg() }
            .show()
    }

    private fun savePng() {
        try {
            val filename = "graph_${System.currentTimeMillis()}.png"
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
            }
            contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)?.let { uri ->
                contentResolver.openOutputStream(uri)?.use { lineChart.chartBitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                Toast.makeText(this, "Saved PNG to Pictures/$filename", Toast.LENGTH_LONG).show()
            }
        } catch (_: Exception) { Toast.makeText(this, "PNG export failed", Toast.LENGTH_SHORT).show() }
    }

    private fun saveSvg() {
        try {
            val w = 800f; val h = 600f; val pad = 80f
            val plotW = w - 2 * pad; val plotH = h - 2 * pad
            val yLo = lineChart.axisLeft.axisMinimum; val yHi = lineChart.axisLeft.axisMaximum
            val xLo = minX.toFloat(); val xHi = maxX.toFloat()
            fun mapX(x: Float) = pad + ((x - xLo) / (xHi - xLo).coerceAtLeast(0.001f)) * plotW
            fun mapY(y: Float) = h - pad - ((y - yLo) / (yHi - yLo).coerceAtLeast(0.001f)) * plotH

            val sb = StringBuilder("""<svg xmlns="http://w3.org" width="800" height="600" viewBox="0 0 800 600">""")
            sb.append("""<rect width="100%" height="100%" fill="white"/><rect x="$pad" y="$pad" width="$plotW" height="$plotH" fill="none" stroke="black" stroke-width="2"/>""")
            sb.append("""<text x="${w/2}" y="55" text-anchor="middle" font-size="22" font-weight="bold">${lineChart.chartTitle}</text>""")
            sb.append("""<text x="${w/2}" y="${h-35}" text-anchor="middle" font-size="18">${lineChart.xAxisTitle}</text>""")
            sb.append("""<text x="40" y="${h/2}" text-anchor="middle" font-size="18" transform="rotate(-90 40 ${h/2})">${lineChart.yAxisTitle}</text>""")

            val colors = listOf("blue", "green", "magenta", "orange", "cyan")
            lastFuncSets.forEachIndexed { i, pts ->
                val d = pts.joinToString(" ") { String.format(Locale.US, "%.1f,%.1f", mapX(it.x), mapY(it.y)) }
                if (d.isNotEmpty()) sb.append("""<polyline fill="none" stroke="${colors.get(i % colors.size)}" stroke-width="2.5" points="$d"/>""")
            }
            if (lastDataEntries.isNotEmpty()) {
                if (lineChart.plotType == 1 || lineChart.plotType == 2) {
                    val d = lastDataEntries.joinToString(" ") { String.format(Locale.US, "%.1f,%.1f", mapX(it.x), mapY(it.y)) }
                    sb.append("""<polyline fill="none" stroke="red" stroke-width="$dataLineWidth" points="$d"/>""")
                }
                if (lineChart.plotType == 0 || lineChart.plotType == 2) {
                    lastDataEntries.forEach { sb.append(String.format(Locale.US, """<circle cx="%.1f" cy="%.1f" r="${lineChart.markerSize}" fill="red"/>""", mapX(it.x), mapY(it.y))) }
                }
            }
            sb.append("</svg>")

            val filename = "graph_${System.currentTimeMillis()}.svg"
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/svg+xml")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS)
            }
            contentResolver.insert(MediaStore.Files.getContentUri("external"), values)?.let { uri ->
                contentResolver.openOutputStream(uri)?.use { it.write(sb.toString().toByteArray()) }
                Toast.makeText(this, "Saved SVG to Documents/$filename", Toast.LENGTH_LONG).show()
            }
        } catch (_: Exception) { Toast.makeText(this, "SVG export failed", Toast.LENGTH_SHORT).show() }
    }
}
