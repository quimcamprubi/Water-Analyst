package com.iot.wateranalyst.ui.main

import android.Manifest
import android.Manifest.permission.*
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import com.google.android.material.button.MaterialButton
import com.iot.wateranalyst.R
import com.iot.wateranalyst.databinding.BleFragmentLayoutBinding
import kotlinx.android.synthetic.main.ble_fragment_layout.*
import timber.log.Timber

private const val ENABLE_BLUETOOTH_REQUEST_CODE = 1
private const val LOCATION_PERMISSION_REQUEST_CODE = 2
private const val GATT_MAX_MTU_SIZE = 517

class BLEFragment(private val isDarkMode: Boolean = false) : Fragment() {

    private lateinit var pageViewModel: PageViewModel
    private var _binding: BleFragmentLayoutBinding? = null
    private lateinit var viewModel: BLEFragmentViewModel
    private lateinit var gattObject: BluetoothGatt
    private lateinit var bluetoothGatt: BluetoothGatt
    private var isBluetoothConnected: Boolean = false
    private val bluetoothAdapter: BluetoothAdapter by lazy {
        val bluetoothManager = activity?.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    /*private val scanFilter = ScanFilter.Builder().setServiceUuid(
        ParcelUuid.fromString(ENVIRONMENTAL_SERVICE_UUID.toString())
    ).build()*/

    private val bleScanner by lazy {
        bluetoothAdapter.bluetoothLeScanner
    }

    private val scanSettings = ScanSettings.Builder()
        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
        .build()

    private val isLocationPermissionGranted
        get() = activity?.hasPermission(ACCESS_FINE_LOCATION)

    private val binding get() = _binding!!

    private var isScanning = false
        set(value) {
            field = value
        }

    private val scanResults = mutableListOf<ScanResult>()
    private val scanResultAdapter = ScanResultAdapter(scanResults, isDarkMode) { result ->
        if (isScanning) stopBleScan()
        with(result.device) {
            if (!isBluetoothConnected) gattObject = connectGatt(context, false, gattCallback)
            else gattObject.disconnect()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = BleFragmentLayoutBinding.inflate(inflater, container, false)
        binding.lifecycleOwner = this
        val root = binding.root
        val scanButton: MaterialButton = binding.bleScanStopButton
        scanButton.setOnClickListener {
            if (isScanning) stopBleScan()
            else startBleScan()
        }
        //setupRecyclerView()
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        viewModel = ViewModelProvider(this).get(BLEFragmentViewModel::class.java)
        binding.viewModel = viewModel

    }

    companion object {
        private const val ARG_SECTION_NUMBER = "section_number"
        @JvmStatic
        fun newInstance(sectionNumber: Int): BLEFragment {
            return BLEFragment().apply {
                arguments = Bundle().apply {
                    putInt(ARG_SECTION_NUMBER, sectionNumber)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pageViewModel = ViewModelProvider(this).get(PageViewModel::class.java).apply {
            setIndex(arguments?.getInt(BLEFragment.ARG_SECTION_NUMBER) ?: 1)
        }
    }

    override fun onResume() {
        super.onResume()
        if (!bluetoothAdapter.isEnabled) {
            promptEnableBluetooth()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            ENABLE_BLUETOOTH_REQUEST_CODE -> if (resultCode != Activity.RESULT_OK) promptEnableBluetooth()
        }
    }

    private fun setupRecyclerView() {
        scan_results_recycler_view.adapter = scanResultAdapter
        scan_results_recycler_view.layoutManager = LinearLayoutManager(activity, RecyclerView.VERTICAL, false)
        scan_results_recycler_view.isNestedScrollingEnabled = false

        val animator = scan_results_recycler_view.itemAnimator
        if (animator is SimpleItemAnimator) {
            animator.supportsChangeAnimations = false
        }
    }

    private fun promptEnableBluetooth() {
        if (!bluetoothAdapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, ENABLE_BLUETOOTH_REQUEST_CODE)
        }
    }

    private fun startBleScan() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && isLocationPermissionGranted == false) requestLocationPermission()
        else {
            scanResults.clear()
            scanResultAdapter.notifyDataSetChanged()
            bleScanner.startScan(null, scanSettings, scanCallback) //TODO change filters to listOf(scanFilter) when device UUID is known
            isScanning = true
            viewModel.isScanning.value = true
        }
    }

    private fun stopBleScan() {
        bleScanner.stopScan(scanCallback)
        viewModel.isScanning.value = false
        isScanning = false
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val indexQuery = scanResults.indexOfFirst { it.device.address == result.device.address }
            if (indexQuery != -1) { // A scan result already exists with the same address
                scanResults[indexQuery] = result
                scanResultAdapter.notifyItemChanged(indexQuery)
            } else {
                with(result.device) {
                    Timber.i("Found BLE device! Name: ${name ?: "Unnamed"}, address: $address")
                }
                scanResults.add(result)
                scanResultAdapter.notifyItemInserted(scanResults.size - 1)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Timber.e("onScanFailed: code $errorCode")
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)
            val showableName = when(gatt.device.name) {
                null -> gatt.device.address
                else -> gatt.device.name
            }
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Timber.i("BluetoothGattCallback: Successfully connected to $showableName!")
                    activity?.runOnUiThread{ Toast.makeText(context, "Successfully connected to $showableName!", Toast.LENGTH_SHORT).show() }
                    bluetoothGatt = gatt
                    Handler(Looper.getMainLooper()).post {
                        bluetoothGatt.discoverServices()
                    }
                    isBluetoothConnected = true
                }
                else if (newState == BluetoothProfile.STATE_DISCONNECTED){
                    Timber.i("BluetoothGattCallback: Successfully disconnected from $showableName!")
                    activity?.runOnUiThread{ Toast.makeText(context, "Successfully disconnected from $showableName!", Toast.LENGTH_SHORT).show() }
                    isBluetoothConnected = false
                    gatt.close()
                }
                else {
                    Timber.i("BluetoothGattCallback: Error $status encountered for $showableName. Disconnecting...")
                    activity?.runOnUiThread{ Toast.makeText(activity?.applicationContext, "Error $status encountered for $showableName. Disconnecting...", Toast.LENGTH_SHORT).show() }
                    isBluetoothConnected = false
                    gatt.close()
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            super.onServicesDiscovered(gatt, status)
            gatt?.printGattTable()
            // Connection complete
        }

        private fun BluetoothGatt.printGattTable() {
            if (services.isEmpty()) {
                Log.i("printGattTable", "No service and characteristic available, call discoverServices() first?")
                return
            }
            services.forEach { service ->
                val characteristicsTable = service.characteristics.joinToString(
                    separator = "\n|--",
                    prefix = "|--"
                ) { it.uuid.toString() }
                Log.i("printGattTable", "\nService ${service.uuid}\nCharacteristics:\n$characteristicsTable")
            }
        }
    }

    // PERMISSIONS REQUESTS

    private fun requestLocationPermission() {
        if (isLocationPermissionGranted == true) {
            return
        }
        val builder = AlertDialog.Builder(context)
        builder.setTitle("Location permission required")
            .setMessage(
                "Starting from Android M (6.0), the system requires apps to be granted " +
                        "location access in order to scan for BLE devices."
            )
            .setCancelable(false)
            .setPositiveButton(android.R.string.ok,
                DialogInterface.OnClickListener { dialog, id ->
                    activity?.requestPermission(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        LOCATION_PERMISSION_REQUEST_CODE
                    )
                }
            )
            .create()
        builder.show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            LOCATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.firstOrNull() == PackageManager.PERMISSION_DENIED) requestLocationPermission()
                else startBleScan()
            }
        }
    }

    // PERMISSIONS CHECK

    private fun Activity.requestPermission(permission: String, requestCode: Int) {
        ActivityCompat.requestPermissions(this, arrayOf(permission), requestCode)
    }

    private fun Context.hasPermission(permissionType: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permissionType) == PackageManager.PERMISSION_GRANTED
    }
}