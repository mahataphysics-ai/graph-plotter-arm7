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
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import net.objecthunter.exp4j.ExpressionBuilder
import java.io.BufferedReader
import java.io.InputStreamReader

class MainActivity : AppCompatActivity() {

    private lateinit var lineChart: LineChart
    private lateinit var etFunctions: EditText
    private lateinit var etDataPoints: EditText
    private lateinit var tvXLabel: TextView
    private lateinit var tvYLabel: TextView

    private var minX = -10.0
    private var maxX = 10.0
    private var minY: Float? = null
    private var maxY: Float? = null
    private var gridEnabled = true
    private var isScatterMode = false

    private val palette = listOf(Color.BLUE, Color.rgb(0, 150, 0), Color.MAGENTA, Color.rgb(255, 120, 0), Color.CYAN)

    private val csvPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { loadCsvFromUri(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        lineChart = findViewById(R.id.lineChart)
        etFunctions = findViewById(R.id.etFunctions)
        etDataPoints = findViewById(R.id.etDataPoints)
        tvXLabel = findViewById(R.id.tvXLabel)
        tvYLabel = findViewById(R.id.tvYLabel)

        findViewById<Button>(R.id.btnPlot).setOnClickListener { plotAll() }
        findViewById<Button>(R.id.btnCsv).setOnClickListener { csvPicker.launch("*/*") }
        findViewById<Button>(R.id.btnSave).setOnClickListener { saveChartImage() }
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
        lineChart.axisRight.isEnabled = false
        lineChart.description.isEnabled = false
        plotAll()
    }

    private fun showRangeDialog(isX: Boolean) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 20, 40, 10)
        }
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
        layout.addView(inputMin)
        layout.addView(inputMax)

        AlertDialog.Builder(this)
            .setTitle(if (isX) "Set X Range" else "Set Y Range")
            .setView(layout)
            .setPositiveButton("Apply") { _, _ ->
                val a = inputMin.text.toString().toDoubleOrNull()
                val b = inputMax.text.toString().toDoubleOrNull()
                if (isX) {
                    if (a != null && b != null && a < b) { minX = a; maxX = b; plotAll() }
                    else Toast.makeText(this, "Invalid X range", Toast.LENGTH_SHORT).show()
                } else {
                    if (a != null && b != null && a < b) { minY = a.toFloat(); maxY = b.toFloat() }
                    else { minY = null; maxY = null }
                    plotAll()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showLabelsDialog() {
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 20, 40, 10) }
        val inputX = EditText(this).apply { hint = "X Label"; setText(tvXLabel.text.toString()) }
        val inputY = EditText(this).apply { hint = "Y Label"; setText(tvYLabel.text.toString()) }
        layout.addView(inputX); layout.addView(inputY)
        AlertDialog.Builder(this).setTitle("Set Axis Labels").setView(layout)
            .setPositiveButton("Apply") { _, _ ->
                tvXLabel.text = inputX.text.toString().ifBlank { "X" }
                tvYLabel.text = inputY.text.toString().ifBlank { "Y" }
            }.setNegativeButton("Cancel", null).show()
    }

    private fun plotAll() {
        val dataSets = mutableListOf<LineDataSet>()
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
                dataSets.add(LineDataSet(entries, "f${idx + 1}(x)=$eq").apply {
                    color = palette.get(idx % palette.size); setDrawCircles(false); lineWidth = 2.2f
                })
            } catch (_: Exception) { Toast.makeText(this, "Invalid: $eq", Toast.LENGTH_SHORT).show() }
        }

        val entries = etDataPoints.text.toString().split(";").mapNotNull { pair ->
            val parts = pair.trim().split(",")
            val x = parts.firstOrNull()?.trim()?.toFloatOrNull()
            val y = parts.getOrNull(1)?.trim()?.toFloatOrNull()
            if (x != null && y != null) Entry(x, y) else null
        }.sortedBy { it.x }

        if (entries.isNotEmpty()) {
            dataSets.add(LineDataSet(entries, if (isScatterMode) "CSV (Scatter)" else "CSV (Line)").apply {
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

    private fun loadCsvFromUri(uri: Uri) {
        try {
            val pairs = mutableListOf<String>()
            contentResolver.openInputStream(uri)?.use { stream ->
                BufferedReader(InputStreamReader(stream)).forEachLine { line ->
                    val cols = line.split(",")
                    val x = cols.firstOrNull()?.trim()?.toFloatOrNull()
                    val y = cols.getOrNull(1)?.trim()?.toFloatOrNull()
                    if (x != null && y != null) pairs.add("$x,$y")
                }
            }
            if (pairs.isNotEmpty()) {
                etDataPoints.setText(pairs.joinToString("; "))
                plotAll()
                Toast.makeText(this, "Loaded ${pairs.size} CSV points", Toast.LENGTH_SHORT).show()
            }
        } catch (_: Exception) { Toast.makeText(this, "CSV read failed", Toast.LENGTH_SHORT).show() }
    }

    private fun saveChartImage() {
        try {
            val bitmap = lineChart.chartBitmap
            val filename = "graph_${System.currentTimeMillis()}.png"
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
            }
            contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)?.let { uri ->
                contentResolver.openOutputStream(uri)?.use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
                Toast.makeText(this, "Saved to Pictures/$filename", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) { Toast.makeText(this, "Export failed", Toast.LENGTH_SHORT).show() }
    }
}
