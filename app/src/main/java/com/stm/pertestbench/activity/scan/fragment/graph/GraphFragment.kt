package com.stm.pertestbench.activity.scan.fragment.graph

import android.annotation.SuppressLint
import android.bluetooth.le.ScanResult
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import com.jjoe64.graphview.series.DataPoint
import com.jjoe64.graphview.series.LineGraphSeries
import com.jjoe64.graphview.series.PointsGraphSeries
import com.stm.pertestbench.PERApplication.Companion.app
import com.stm.pertestbench.PERViewModel
import com.stm.pertestbench.R
import com.stm.pertestbench.ble.BLEManager
import com.stm.pertestbench.databinding.FragmentGraphBinding
import com.stm.pertestbench.extension.*
import timber.log.Timber

private const val SCROLL_TO_END = false
private const val MAX_DATA_POINTS = 100

/**
 * Graph Fragment shows the user live data from the selected device's
 * advertising data. The PER & RSSI data is presented on 2 graphs created
 * using the GraphView library. The user may also save the data into 2 CSV
 * files in the documents folder.
 *
 * @author Claudio Vertemara
 */

class GraphFragment : Fragment(), GraphInterface {

    private lateinit var binding: FragmentGraphBinding
    private val viewModel = PERViewModel()
    private val sharedPrefs by lazy {
        activity?.getSharedPreferences("params", AppCompatActivity.MODE_PRIVATE)
    }

    // Graph Dimensions
    private val maxX = 20.0
    private val minX = 0.0
    private val perMaxY = 20.0

    // Graph Data Series
    /** RSSI Data Points for RSSI Graph */
    private val rssiSeries = PointsGraphSeries<DataPoint>()
    /** PER Data Points for PER Graph */
    private val perSeries = PointsGraphSeries<DataPoint>()
    /** Current X Position */
    private var x = 0.0

    /** Fixed Line on PER Graph */
    private val fixedLine = LineGraphSeries<DataPoint>()
    /** Fixed Line Y Position */
    private val fixedY = 30.8

    // Current Data
    private var index: Int = -1
    private var receivedPackets = 0
    private var per = 0f
    private var rssi = 0

    /**
     * Sets up the fragment, interface, graphs, & fixed line. Adds
     * data point series to the graphs and loads total pack number.
     *
     * @see setupGraphs
     * @see setupFixedLine
     * @see rssiSeries
     * @see perSeries
     */
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DataBindingUtil.inflate(
            inflater,
            R.layout.fragment_graph,
            container,
            false
        )

        binding.fragment = this
        BLEManager.graphInterface = this

        // Add Data Series to Graph
        binding.rssiGraph.addSeries(rssiSeries)
        binding.perGraph.addSeries(perSeries)

        // Load Total Packet Number
        binding.totalPacketsText.text = sharedPrefs?.getString("packetNumber", "0")

        setupGraphs()
        setupFixedLine()

        return binding.root
    }

    /**
     * Reset graphs & data and saves data files.
     *
     * @see clearGraphs
     */
    override fun onStop() {
        super.onStop()
        clearGraphs()
    }

    /** Graphs */

    /**
     * Sets up the graph dimensions & data points and makes
     * the graphs scrollable on the X axis.
     *
     * @see rssiSeries
     * @see perSeries
     */
    private fun setupGraphs() {
        with (binding) {
            // Setup Graph Dimensions
            rssiGraph.viewport.isYAxisBoundsManual = true
            rssiGraph.viewport.setMaxY(0.0)
            rssiGraph.viewport.setMinY(-125.0)

            rssiGraph.viewport.isXAxisBoundsManual = true
            rssiGraph.viewport.setMaxX(maxX)
            rssiGraph.viewport.setMinX(minX)

            perGraph.viewport.isYAxisBoundsManual = true
            perGraph.viewport.setMaxY(perMaxY)
            perGraph.viewport.setMinY(0.0)

            perGraph.viewport.isXAxisBoundsManual = true
            perGraph.viewport.setMaxX(maxX)
            perGraph.viewport.setMinX(minX)

            // Make Graphs Scrollable
            rssiGraph.viewport.isScrollable = true
            perGraph.viewport.isScrollable = true

            // Set Data Points Size
            rssiSeries.size = 5f
            perSeries.size = 5f

            // Set Data Points Color
            rssiSeries.color = ContextCompat.getColor(app.applicationContext, R.color.st_light_blue)
            perSeries.color = ContextCompat.getColor(app.applicationContext, R.color.st_light_blue)
        }
    }

    /**
     * Saves the data files (new method) and resets the
     * data points, fixed line, and graphs.
     *
     * Called on Clear Graphs button click.
     *
     * @see PERViewModel.saveFile
     * @see rssiSeries
     * @see perSeries
     * @see fixedLine
     */
    fun clearGraphs() {
        // Save Data Files
        with(viewModel) {
            saveFile(context, false)
            saveFile(context, true)
            saveDataClicked = false
        }

        // Reset Data
        rssiSeries.resetData(arrayOf())
        perSeries.resetData(arrayOf())
        x = 0.0

        // Reset Fixed Line
        fixedLine.resetData(arrayOf())
        setupFixedLine()

        // Reset Graphs
        setupGraphs()
    }

    /**
     * Sets up a horizontal red fixed line on PER graph at
     * Y value 30.8 from start to end of visible graph.
     *
     * @see fixedLine
     */
    private fun setupFixedLine() {
        fixedLine.color = ContextCompat.getColor(app.applicationContext, R.color.red)

        // Setup Line to Reach End of Graph
        for (i in 0..maxX.toInt()) {
            fixedLine.appendData(DataPoint(i.toDouble(), fixedY), SCROLL_TO_END, MAX_DATA_POINTS)
        }

        binding.perGraph.addSeries(fixedLine)
    }

    /** Data */

    /**
     * Reads and converts the advertising data into live data values.
     *
     * @param result Scan Result from Scan Call Back
     * @see BLEManager.scanCallback
     * @see graphData
     */
    override fun readData(result: ScanResult) {
        val hexData = result.scanRecord?.bytes?.toHexString()?.removeWhiteSpace()?.uppercase()
        val mfrDataLength = hexData?.substring(2, 4)?.toInt(16) ?: 0
        val mfrData = hexData?.substring(4, 4 + (mfrDataLength * 2))

        // Result Contains Advertising Manufacturer Data
        if (mfrData?.substring(0, 2) == "FF") {
            val index = mfrData.substring(6, 8).toInt(16)

            // New Data Index (Don't Graph Data Advertised More Than Once)
            if (index != this.index) {
                this.index = index
                receivedPackets = mfrData.substring(8, 12).hexToBigEndian().toInt(16)
                per = mfrData.substring(12, 20).hexToBigEndian().hexToByteArray().toFloat()
                rssi = mfrData.substring(20, 24).hexToBigEndian().toInt(16) - 65536

                graphData()
                printData(mfrData, index, receivedPackets, per, rssi)
            }
        }
    }

    /**
     * Expands & scrolls graphs, updates data series used by graphs,
     * updates live data table under graphs, and saves data for the
     * All Data file.
     *
     * @see maxX
     * @see perMaxY
     * @see rssiSeries
     * @see perSeries
     * @see saveData
     */
    @SuppressLint("SetTextI18n")
    private fun graphData() {
        // X Value has Reached End of Graph
        if (x > maxX) {
            // Scroll Graph
            binding.rssiGraph.viewport.scrollToEnd()
            binding.perGraph.viewport.scrollToEnd()

            // Update Fixed Line Data (Graph will use Updated Data)
            fixedLine.appendData(DataPoint(x, fixedY), SCROLL_TO_END, MAX_DATA_POINTS)
        }
        // PER Y Value has Reached Top of Graph
        if (per > perMaxY) {
            val maxY = when {
                per > 80 -> 100.0
                per > 60 -> 80.0
                per > 40 -> 60.0
                else -> 40.0
            }
            binding.perGraph.viewport.setMaxY(maxY)
        }

        // Update Data Series (Graphs will use Updated Data)
        rssiSeries.appendData(DataPoint(x, rssi.toDouble()), SCROLL_TO_END, MAX_DATA_POINTS)
        perSeries.appendData(DataPoint(x, per.toDouble()), SCROLL_TO_END, MAX_DATA_POINTS)

        // Update Data Text
        with (binding) {
            rssiText.text = "$rssi"
            perText.text = String.format("%.2f", per)
            receivedPacketsText.text = "$receivedPackets"
        }

        // Save Data for All Data File
        saveData(true)

        x++
    }

    /**
     * Saves just current data or all data on graphs to a CSV file
     * in the documents folder. Picks one of two methods of creating
     * a data file based on Android version.
     *
     * Called on Save Data button click.
     *
     * @param allData True = PERAllTestData | False = PERTestData
     * @see PERViewModel.data
     * @see PERViewModel.allData
     * @see PERViewModel.writeFile
     */
    fun saveData(allData: Boolean) {
        // Save Data Button was Clicked
        if (!allData) viewModel.saveDataClicked = true

        val time = PERViewModel().getTime()
        var distance = binding.distanceEditText.text.toString()

        if (distance.isEmpty()) distance = "0"
        if (allData) distance = "N/A"

        // Update Last Data Point in All Data with Entered Distance
        if (!allData) {
            val lastIndex = viewModel.allData.lastIndexOf("N/A")
            if (lastIndex != -1) {
                viewModel.allData = viewModel.allData.replaceRange(lastIndex, lastIndex + 3, distance)
            }
        }

        val currentData = "$time, $distance, $rssi, $per\n"
        Timber.i("$time, $distance m, $rssi dBm, $per%")

        // Write Current Data to File
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // New Method of Writing to File (On Fragment Stop)
            if (allData) viewModel.allData += currentData else viewModel.data += currentData
        } else {
            // Old Deprecated Method of Writing to File
            viewModel.writeFile(currentData, allData)
        }
    }

    /**
     * Logs current live data.
     */
    private fun printData(
        mfrData: String, index: Int, receivedPackets: Int, per: Float, rssi: Int
    ) {
        Timber.i(
            "Manufacturer Data: $mfrData\n" +
            "Index: $index " +
            "Number of Packets Received: $receivedPackets " +
            "per: $per " +
            "rssi: $rssi"
        )
    }

    /** Helper Functions */

    /**
     * Closes Graph Fragment and returns to Scan Fragment.
     */
    override fun closeFragment() {
        activity?.supportFragmentManager?.popBackStack()
    }

}