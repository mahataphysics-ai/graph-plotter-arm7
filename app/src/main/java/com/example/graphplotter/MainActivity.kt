package com.example.graphplotter

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import net.objecthunter.exp4j.ExpressionBuilder
import java.io.BufferedReader
import java.io.InputStreamReader

class MainActivity : AppCompatActivity() {

    private lateinit var lineChart: LineChart
    private lateinit var etFunctions: EditText
    private lateinit var etMinX: EditText
    private lateinit var etMaxX: EditText
    private lateinit var etDataPoints: EditText

    private val palette = listOf(
        Color.BLUE,
        Color.rgb(0, 150, 0),
        Color.MAGENTA,
        Color.rgb(255, 120, 0),
        Color.CYAN
    )

    private val csvPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { loadCsvFromUri(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        lineChart = findViewById(R.id.lineChart)
        etFunctions = findViewById(R.id.etFunctions)
        etMinX = findViewById(R.id.etMinX)
        etMaxX = findViewById(R.id.etMaxX)
        etDataPoints = findViewById(R.id.etDataPoints)

        findViewById<Button>(R.id.btnPlot).setOnClickListener { plotAll() }
        findViewById<Button>(R.id.btnCsv).setOnClickListener { csvPicker.launch("*/*") }
        findViewById<Button>(R.id.btnSave).setOnClickListener { saveChartImage() }

        lineChart.setBackgroundColor(Color.WHITE)
        plotAll()
    }

    private fun plotAll() {
        val dataSets = mutableListOf<LineDataSet>()

        val minX = etMinX.text.toString().toDoubleOrNull() ?: -10.0
        val maxX = etMaxX.text.toString().toDoubleOrNull() ?: 10.0
        if (minX >= maxX) {
            Toast.makeText(this, "Min X must be less than Max X", Toast.LENGTH_SHORT).show()
            return
        }
        val step = (maxX - minX) / 200.0

        // 1. Plot multiple functions separated by ';'
        val funcs = etFunctions.text.toString().split(";").map { it.trim() }.filter { it.isNotEmpty() }
        funcs.forEachIndexed { index, eq ->
            try {
                val entries = generateFunctionEntries(eq, minX, maxX, step)
                val lineColor = palette[index % palette.size]
                dataSets.add(LineDataSet(entries, "f${index + 1}(x) = $eq").apply {
                    color = lineColor
                    setDrawCircles(false)
                    lineWidth = 2.2f
                })
            } catch (e: Exception) {
                Toast.makeText(this, "Invalid equation: $eq", Toast.LENGTH_SHORT).show()
            }
        }

        // 2. Plot discrete data points (manual or imported from CSV)
        val pointsText = etDataPoints.text.toString().trim()
        if (pointsText.isNotEmpty()) {
            val entries = parseDataPoints(pointsText)
            if (entries.isNotEmpty()) {
                dataSets.add(LineDataSet(entries, "Data Points").apply {
                    color = Color.RED
                    setCircleColor(Color.RED)
                    circleRadius = 5f
                    lineWidth = 1.5f
                })
            }
        }

        lineChart.xAxis.axisMinimum = minX.toFloat()
        lineChart.xAxis.axisMaximum = maxX.toFloat()
        lineChart.data = LineData(dataSets.toList())
        lineChart.description.text = ""
        lineChart.invalidate()
    }

    private fun generateFunctionEntries(eq: String, minX: Double, maxX: Double, step: Double): List<Entry> {
        val entries = mutableListOf<Entry>()
        val expression = ExpressionBuilder(eq).variable("x").build()
        var x = minX
        while (x <= maxX) {
            try {
                val y = expression.setVariable("x", x).evaluate()
                if (!y.isNaN() && !y.isInfinite()) {
                    entries.add(Entry(x.toFloat(), y.toFloat()))
                }
            } catch (_: Exception) {}
            x += step
        }
        return entries
    }

    private fun parseDataPoints(input: String): List<Entry> {
        return input.split(";").mapNotNull { pair ->
            val parts = pair.trim().split(",")
            if (parts.size == 2) {
                val x = parts.trim().toFloatOrNull()
                val y = parts.trim().toFloatOrNull()
                if (x != null && y != null) Entry(x, y) else null
            } else null
        }.sortedBy { it.x }
    }

    private fun loadCsvFromUri(uri: Uri) {
        try {
            val parsedPairs = mutableListOf<String>()
            contentResolver.openInputStream(uri)?.use { stream ->
                BufferedReader(InputStreamReader(stream)).forEachLine { line ->
                    val cols = line.split(",")
                    if (cols.size >= 2) {
                        val x = cols.trim().toFloatOrNull()
                        val y = cols.trim().toFloatOrNull()
                        if (x != null && y != null) {
                            parsedPairs.add("$x,$y")
                        }
                    }
                }
            }
            if (parsedPairs.isNotEmpty()) {
                etDataPoints.setText(parsedPairs.joinToString("; "))
                plotAll()
                Toast.makeText(this, "Loaded ${parsedPairs.size} CSV points", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "No valid x,y numbers found in CSV", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to read CSV", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveChartImage() {
        try {
            val bitmap = lineChart.chartBitmap
            val filename = "graph_${System.currentTimeMillis()}.png"
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
                }
            }
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                contentResolver.openOutputStream(uri)?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                Toast.makeText(this, "Saved to Pictures/$filename", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
