package com.example.graphplotter

import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import net.objecthunter.exp4j.ExpressionBuilder

class MainActivity : AppCompatActivity() {

    private lateinit var lineChart: LineChart
    private lateinit var etFunction: EditText
    private lateinit var etDataPoints: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        lineChart = findViewById(R.id.lineChart)
        etFunction = findViewById(R.id.etFunction)
        etDataPoints = findViewById(R.id.etDataPoints)
        val btnPlot = findViewById<Button>(R.id.btnPlot)

        btnPlot.setOnClickListener { plotAll() }
        plotAll()
    }

    private fun plotAll() {
        val dataSets = mutableListOf<LineDataSet>()

        val funcText = etFunction.text.toString().trim()
        if (funcText.isNotEmpty()) {
            try {
                val entries = generateFunctionEntries(funcText, -10.0, 10.0, 0.2)
                dataSets.add(LineDataSet(entries, "f(x) = $funcText").apply {
                    color = Color.BLUE
                    setDrawCircles(false)
                    lineWidth = 2f
                })
            } catch (e: Exception) {
                Toast.makeText(this, "Invalid function", Toast.LENGTH_SHORT).show()
            }
        }

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
                val x = parts[0].trim().toFloatOrNull()
                val y = parts[1].trim().toFloatOrNull()
                if (x != null && y != null) Entry(x, y) else null
            } else null
        }.sortedBy { it.x }
    }
}
