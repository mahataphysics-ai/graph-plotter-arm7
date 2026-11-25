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
        etDataPoints = findViewById(R.id.etDataPoints)
        tvXLabel = findViewById(R.id.tvXLabel)
        tvYLabel = findViewById(R.id.tvYLabel)

        findViewById<Button>(R.id.btnPlot).setOnClickListener { plotAll() }
        findViewById<Button>(R.id.btnCsv).setOnClickListener { csvPicker.launch("*/*") }
        findViewById<Button>(R.id.btnSave).setOnClickListener { saveChartImage() }

        findViewById<Button>(R.id.btnXRange).setOnClickListener { showXRangeDialog() }
        findViewById<Button>(R.id.btnYRange).setOnClickListener { showYRangeDialog() }
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

        setupChartDefaults()
        plotAll()
    }

    private fun setupChartDefaults() {
        lineChart.setBackgroundColor(Color.WHITE)
        lineChart.xAxis.position = XAxis.XAxisPosition.BOTTOM
        lineChart.axisRight.isEnabled = false
        lineChart.description.isEnabled = false
    }

    private fun showXRangeDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 20, 40, 10)
        }
        val inputMin = EditText(this).apply {
            hint = "Min X"
            setText(minX.toString())
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val inputMax = EditText(this).apply {
            hint = "Max X"
            setText(maxX.toString())
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        layout.addView(inputMin)
        layout.addView(inputMax)

        AlertDialog.Builder(this)
            .setTitle("Set X Axis Range")
            .setView(layout)
            .setPositiveButton("Apply") { _, _ ->
                val newMin = inputMin.text.toString().toDoubleOrNull()
                val newMax = inputMax.text.toString().toDoubleOrNull()
                if (newMin != null && newMax != null && newMin < newMax) {
                    minX = newMin
                    maxX = newMax
                    plotAll()
                } else {
                    Toast.makeText(this, "Invalid X range", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showYRangeDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 20, 40, 10)
        }
        val inputMin = EditText(this).apply {
            hint = "Min Y (leave blank for Auto)"
            setText(minY?.toString() ?: "")
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val inputMax = EditText(this).apply {
            hint = "Max Y (leave blank for Auto)"
            setText(maxY?.toString() ?: "")
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        layout.addView(inputMin)
        layout.addView(inputMax)

        AlertDialog.Builder(this)
            .setTitle("Set Y Axis Range")
            .setView(layout)
            .setPositiveButton("Apply") { _, _ ->
                val newMin = inputMin.text.toString().toFloatOrNull()
                val newMax = inputMax.text.toString().toFloatOrNull()
                if (newMin != null && newMax != null && newMin < newMax) {
                    minY = newMin
                    maxY = newMax
                } else {
                    minY = null
                    maxY = null
                }
                plotAll()
            }
            .setNeutralButton("Auto Scale") { _, _ ->
                minY = null
                maxY = null
                plotAll()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showLabelsDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 20, 40, 10)
        }
        val inputX = EditText(this).apply {
            hint = "X Axis Label"
            setText(tvXLabel.text.toString())
        }
        val inputY = EditText(this).apply {
            hint = "Y Axis Label"
            setText(tvYLabel.text.toString())
        }
        layout.addView(inputX)
        layout.addView(inputY)

        AlertDialog.Builder(this)
            .setTitle("Set Axis Labels")
            .setView(layout)
            .setPositiveButton("Apply") { _, _ ->
                tvXLabel.text = inputX.text.toString().ifBlank { "X" }
                tvYLabel.text = inputY.text.toString().ifBlank { "Y" }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun plotAll() {
        val dataSets = mutableListOf<LineDataSet>()
        val step = (maxX - minX) / 200.0

        val funcs = etFunctions.text.toString().split(";").map { it.trim() }.filter { it.isNotEmpty() }
        funcs.forEachIndexed { index, eq ->
            try {
                val entries = generateFunctionEntries(eq, minX, maxX, step)
                val lineColor = palette.get(index % palette.size)
                dataSets.add(LineDataSet(entries, "f${index + 1}(x) = $eq").apply {
                    color = lineColor
                    setDrawCircles(false)
                    lineWidth = 2.2f
                })
            } catch (e: Exception) {
                Toast.makeText(this, "Invalid equation: $eq", Toast.LENGTH_SHORT).show()
            }
        }

        val pointsText = etDataPoints.text.toString().trim()
        if (pointsText.isNotEmpty()) {
            val entries = parseDataPoints(pointsText)
            if (entries.isNotEmpty()) {
                val label = if (isScatterMode) "Data (Scatter)" else "Data (Line)"
                dataSets.add(LineDataSet(entries, label).apply {
                    color = if (isScatterMode) Color.TRANSPARENT else Color.RED
                    setCircleColor(Color.RED)
                    setDrawCircles(true)
                    circleRadius = if (isScatterMode) 5.5f else 4.5f
                    lineWidth = if (isScatterMode) 0f else 1.8f
                })
            }
        }

        lineChart.xAxis.setDrawGridLines(gridEnabled)
        lineChart.axisLeft.setDrawGridLines(gridEnabled)

        lineChart.xAxis.axisMinimum = minX.toFloat()
        lineChart.xAxis.axisMaximum = maxX.toFloat()

        if (minY != null && maxY != null) {
            lineChart.axisLeft.axisMinimum = minY!!
            lineChart.axisLeft.axisMaximum = maxY!!
        } else {
            lineChart.axisLeft.resetAxisMinimum()
            lineChart.axisLeft.resetAxisMaximum()
        }

        lineChart.data = LineData(dataSets.toList())
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
