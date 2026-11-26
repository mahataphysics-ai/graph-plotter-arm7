package com.example.graphplotter

import android.app.AlertDialog
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Color
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
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import net.objecthunter.exp4j.ExpressionBuilder
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var lineChart: ScientificLineChart
    private lateinit var etFunctions: EditText
    private lateinit var tblCsvData: TableLayout

    private var minX = -10.0
    private var maxX = 10.0
    private var minY: Float? = null
    private var maxY: Float? = null
    private var gridEnabled = true
    private var isScatterMode = false
    private var lastFuncSets: List<List<Entry>> = emptyList()
    private var lastDataEntries: List<Entry> = emptyList()

    private val palette = listOf(Color.BLUE, Color.rgb(0, 150, 0), Color.MAGENTA, Color.rgb(255, 120, 0), Color.CYAN)

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
        findViewById<Button>(R.id.btnCsv).setOnClickListener { csvPicker.launch("*/*") }
        findViewById<Button>(R.id.btnSave).setOnClickListener { showExportDialog() }
        findViewById<Button>(R.id.btnTitle).setOnClickListener { showTitleDialog() }
        findViewById<Button>(R.id.btnXRange).setOnClickListener { showRangeDialog(true) }
        findViewById<Button>(R.id.btnYRange).setOnClickListener { showRangeDialog(false) }
        findViewById<Button>(R.id.btnLabels).setOnClickListener { showLabelsDialog() }

        val btnGrid = findViewById<Button>(R.id.btnGrid)
        btnGrid.setOnClickListener {
            gridEnabled = !gridEnabled
            btnGrid.text = if (gridEnabled) "Grid: ON" else "Grid: OFF"
            plotAll()
        }

        val btnCsvMode = findViewById<Button>(R.id.btnCsvMode)
        btnCsvMode.setOnClickListener {
            isScatterMode = !isScatterMode
            btnCsvMode.text = if (isScatterMode) "CSV: Scatter" else "CSV: Line"
            plotAll()
        }

        lineChart.setBackgroundColor(Color.WHITE)
        lineChart.xAxis.position = XAxis.XAxisPosition.BOTTOM
        lineChart.axisRight.isEnabled = true
        lineChart.axisRight.setDrawLabels(false)
        lineChart.axisRight.setDrawGridLines(false)
        lineChart.description.isEnabled = false

        populateEditableTable(listOf(Pair("-6", "2"), Pair("-2", "-3"), Pair("1", "4"), Pair("5", "1")))
        plotAll()
    }

    private fun showTitleDialog() {
        val input = EditText(this).apply { hint = "Graph Title"; setText(lineChart.chartTitle) }
        AlertDialog.Builder(this).setTitle("Set Graph Title").setView(input)
            .setPositiveButton("Apply") { _, _ -> lineChart.chartTitle = input.text.toString(); lineChart.invalidate() }
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

    private fun showLabelsDialog() {
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 20, 40, 10) }
        val inputX = EditText(this).apply { hint = "X Label"; setText(lineChart.xAxisTitle) }
        val inputY = EditText(this).apply { hint = "Y Label"; setText(lineChart.yAxisTitle) }
        layout.addView(inputX); layout.addView(inputY)
        AlertDialog.Builder(this).setTitle("Set Axis Labels").setView(layout)
            .setPositiveButton("Apply") { _, _ ->
                lineChart.xAxisTitle = inputX.text.toString()
                lineChart.yAxisTitle = inputY.text.toString()
                lineChart.invalidate()
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
                })
            } catch (_: Exception) { Toast.makeText(this, "Invalid: $eq", Toast.LENGTH_SHORT).show() }
        }
        lastFuncSets = funcLists

        val entries = readEditableTableEntries()
        lastDataEntries = entries

        if (entries.isNotEmpty()) {
            dataSets.add(LineDataSet(entries, if (isScatterMode) "Data (Scatter)" else "Data (Line)").apply {
                color = if (isScatterMode) Color.TRANSPARENT else Color.RED
                setCircleColor(Color.RED); setDrawCircles(true)
                circleRadius = if (isScatterMode) 5.5f else 4.5f
                lineWidth = if (isScatterMode) 0f else 1.8f
            })
        }

        lineChart.xAxis.setDrawGridLines(gridEnabled)
        lineChart.axisLeft.setDrawGridLines(gridEnabled)
        lineChart.xAxis.axisMinimum = minX.toFloat()
        lineChart.xAxis.axisMaximum = maxX.toFloat()
        if (minY != null && maxY != null) {
            lineChart.axisLeft.axisMinimum = minY!!; lineChart.axisLeft.axisMaximum = maxY!!
        } else {
            lineChart.axisLeft.resetAxisMinimum(); lineChart.axisLeft.resetAxisMaximum()
        }
        lineChart.data = LineData(dataSets.toList())
        lineChart.invalidate()
    }

    private fun populateEditableTable(rows: List<Pair<String, String>>) {
        tblCsvData.removeAllViews()
        val header = TableRow(this).apply { setBackgroundColor(Color.parseColor("#DCE3EE")) }
        listOf("X", "Y").forEach { col ->
            header.addView(TextView(this).apply {
                text = col; gravity = Gravity.CENTER; setPadding(8, 6, 8, 6); textSize = 12f
            })
        }
        tblCsvData.addView(header)
        rows.forEach { addEditableRow(it.first, it.second) }
    }

    private fun addEditableRow(xVal: String, yVal: String) {
        val row = TableRow(this)
        listOf(xVal, yVal).forEach { cellText ->
            row.addView(EditText(this).apply {
                setText(cellText)
                hint = "0.0"
                gravity = Gravity.CENTER
                textSize = 12f
                setPadding(6, 4, 6, 4)
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED or InputType.TYPE_NUMBER_FLAG_DECIMAL
            })
        }
        tblCsvData.addView(row)
    }

    private fun readEditableTableEntries(): List<Entry> {
        val list = mutableListOf<Entry>()
        for (i in 1 until tblCsvData.childCount) {
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
        val options = arrayOf("PNG Image", "SVG Vector")
        AlertDialog.Builder(this).setTitle("Export Graph As")
            .setItems(options) { _, which -> if (which == 0) savePng() else saveSvg() }
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

            val sb = StringBuilder()
            sb.append("""<svg xmlns="http://w3.org" width="800" height="600" viewBox="0 0 800 600">""")
            sb.append("""<rect width="100%" height="100%" fill="white"/>""")
            sb.append("""<rect x="$pad" y="$pad" width="$plotW" height="$plotH" fill="none" stroke="black" stroke-width="2"/>""")
            sb.append("""<text x="${w/2}" y="55" text-anchor="middle" font-size="22" font-weight="bold">${lineChart.chartTitle}</text>""")
            sb.append("""<text x="${w/2}" y="${h-35}" text-anchor="middle" font-size="18">${lineChart.xAxisTitle}</text>""")
            sb.append("""<text x="40" y="${h/2}" text-anchor="middle" font-size="18" transform="rotate(-90 40 ${h/2})">${lineChart.yAxisTitle}</text>""")

            val colors = listOf("blue", "green", "magenta", "orange", "cyan")
            lastFuncSets.forEachIndexed { i, pts ->
                val d = pts.joinToString(" ") { String.format(Locale.US, "%.1f,%.1f", mapX(it.x), mapY(it.y)) }
                if (d.isNotEmpty()) sb.append("""<polyline fill="none" stroke="${colors.get(i % colors.size)}" stroke-width="2.5" points="$d"/>""")
            }
            if (lastDataEntries.isNotEmpty()) {
                if (!isScatterMode) {
                    val d = lastDataEntries.joinToString(" ") { String.format(Locale.US, "%.1f,%.1f", mapX(it.x), mapY(it.y)) }
                    sb.append("""<polyline fill="none" stroke="red" stroke-width="2" points="$d"/>""")
                }
                lastDataEntries.forEach {
                    sb.append(String.format(Locale.US, """<circle cx="%.1f" cy="%.1f" r="4.5" fill="red"/>""", mapX(it.x), mapY(it.y)))
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
