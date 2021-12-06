package com.iot.wateranalyst.ui.main

import android.Manifest
import android.Manifest.permission.*
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.*
import android.bluetooth.BluetoothDevice.TRANSPORT_LE
import android.bluetooth.BluetoothGattCharacteristic.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
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
import com.iot.wateranalyst.MainActivity
import com.iot.wateranalyst.MainViewModel
import com.iot.wateranalyst.databinding.BleFragmentLayoutBinding
import kotlinx.android.synthetic.main.ble_fragment_layout.*
import timber.log.Timber
import java.util.*


private const val ENABLE_BLUETOOTH_REQUEST_CODE = 1
private const val LOCATION_PERMISSION_REQUEST_CODE = 2
private const val GATT_MAX_MTU_SIZE = 517
private const val NORDIC_UART_SERVICE_UUID = "6e400001-b5a3-f393-e0a9-e50e24dcca9e"
private const val RX_CHARACTERISTIC_UUID = "6e400002-b5a3-f393-e0a9-e50e24dcca9e"
private const val TX_CHARACTERISTIC_UUID = "6e400003-b5a3-f393-e0a9-e50e24dcca9e"
private const val TX_DESCRIPTOR_UUID = "00002902-0000-1000-8000-00805f9b34fb"

class BLEFragment(private val isDarkMode: Boolean = false) : Fragment() {

    private lateinit var pageViewModel: PageViewModel
    private var _binding: BleFragmentLayoutBinding? = null
    private lateinit var viewModel: MainViewModel
    private lateinit var bluetoothGatt: BluetoothGatt
    private var isBluetoothConnected: Boolean = false
    private var resultArray = arrayListOf<Byte>()
    private var receivedData = 0
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
            if (!isBluetoothConnected) bluetoothGatt = this.connectGatt(context, false, gattCallback, TRANSPORT_LE)
            else bluetoothGatt.disconnect()
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
        viewModel = ViewModelProvider(activity!!).get(MainViewModel::class.java)
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
                    isBluetoothConnected = true
                    bluetoothGatt = gatt
                    viewModel.isBluetoothConnected.postValue(true)
                    Handler(Looper.getMainLooper()).post {
                        binding.readDataButton.visibility=View.VISIBLE
                        gatt.discoverServices()
                        gatt.requestMtu(GATT_MAX_MTU_SIZE)
                        binding.readDataButton.setOnClickListener {
                            setNotificationsAndRead(gatt)
                            viewModel.isResponseReceived.postValue(false)
                        }
                    }
                }
                else if (newState == BluetoothProfile.STATE_DISCONNECTED){
                    Timber.i("BluetoothGattCallback: Successfully disconnected from $showableName!")
                    activity?.runOnUiThread{ Toast.makeText(context, "Successfully disconnected from $showableName!", Toast.LENGTH_SHORT).show() }
                    isBluetoothConnected = false
                    viewModel.isBluetoothConnected.postValue(false)
                    gatt.close()
                }
                else {
                    Timber.i("BluetoothGattCallback: Error $status encountered for $showableName. Disconnecting...")
                    activity?.runOnUiThread{ Toast.makeText(activity?.applicationContext, "Error $status encountered for $showableName. Disconnecting...", Toast.LENGTH_SHORT).show() }
                    isBluetoothConnected = false
                    viewModel.isBluetoothConnected.postValue(false)
                    gatt.disconnect()
                    gatt.close()
                }
            }
        }


        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            super.onServicesDiscovered(gatt, status)
            gatt?.printGattTable()
        }

        override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
            super.onMtuChanged(gatt, mtu, status)
            Timber.i("ATT MTU changed to $mtu, success: ${status == BluetoothGatt.GATT_SUCCESS}")
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

        private fun setNotificationsAndRead(gattObject: BluetoothGatt?) {
            val serviceUuid = UUID.fromString(NORDIC_UART_SERVICE_UUID)
            val notifyCharacteristicUuid = UUID.fromString(TX_CHARACTERISTIC_UUID)
            val notifyCharacteristic = gattObject?.getService(serviceUuid)?.getCharacteristic(notifyCharacteristicUuid)
            gattObject?.setCharacteristicNotification(notifyCharacteristic, true)
            val descriptor = notifyCharacteristic?.getDescriptor(UUID.fromString(TX_DESCRIPTOR_UUID))
            descriptor?.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            gattObject?.writeDescriptor(descriptor)

            val writeCharacteristicUuid = UUID.fromString(RX_CHARACTERISTIC_UUID)
            val writeCharacteristic = gattObject?.getService(serviceUuid)?.getCharacteristic(writeCharacteristicUuid)
            writeCharacteristic?.setValue("0")
            writeCharacteristic?.writeType = WRITE_TYPE_NO_RESPONSE
            if (writeCharacteristic?.isWritableWithoutResponse() == true)
                gattObject.writeCharacteristic(writeCharacteristic)
            else
                activity?.runOnUiThread { Log.i("writeCharacteristic","This device doesn't support the default write BLE characteristic.") }

            Thread.sleep(500)
            (activity as MainActivity).nextFragment(resultArray)
            resultArray.clear()
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            super.onCharacteristicWrite(gatt, characteristic, status)
            when(status) {
                BluetoothGatt.GATT_SUCCESS -> Log.i("onCharacteristicWrite","Response: ${String(characteristic!!.value)}")
                BluetoothGatt.GATT_READ_NOT_PERMITTED -> Log.i("onCharacteristicWrite","Response: ${String(characteristic!!.value)}")
                else -> Log.i("onCharacteristicWrite","Error: $status")
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt?,
            descriptor: BluetoothGattDescriptor?,
            status: Int
        ) {
            super.onDescriptorWrite(gatt, descriptor, status)
            if(descriptor?.uuid?.equals(UUID.fromString(TX_DESCRIPTOR_UUID)) == true) {
                with(descriptor) {
                    when(status) {
                        BluetoothGatt.GATT_SUCCESS -> {
                             Log.i("onDescriptorWrite", "Notify descriptor set correctly")
                            val serviceUuid = UUID.fromString(NORDIC_UART_SERVICE_UUID)
                            val writeCharacteristicUuid = UUID.fromString(RX_CHARACTERISTIC_UUID)
                            val writeCharacteristic = gatt?.getService(serviceUuid)?.getCharacteristic(writeCharacteristicUuid)
                            writeCharacteristic?.setValue("0")
                            writeCharacteristic?.writeType = WRITE_TYPE_NO_RESPONSE
                            if (writeCharacteristic?.isWritableWithoutResponse() == true) gatt.writeCharacteristic(writeCharacteristic)
                            else Log.i("writeCharacteristic","This device doesn't support the default write BLE characteristic.")
                        }
                        BluetoothGatt.GATT_READ_NOT_PERMITTED -> Log.i("onDescriptorWrite","Notify descriptor set not permitted ${this?.value.toString()}")
                        else -> Log.i("onDescriptorWrite","Error: $status")
                    }
                }
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?
        ) {
            super.onCharacteristicChanged(gatt, characteristic)
            val newValue = characteristic!!.value
            resultArray.addAll(newValue.asList())
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            super.onCharacteristicRead(gatt, characteristic, status)
            Log.i("onCharacteristicRead","Returned value = ${characteristic?.value}")
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

    fun BluetoothGattCharacteristic.isReadable(): Boolean = containsProperty(BluetoothGattCharacteristic.PROPERTY_READ)
    fun BluetoothGattCharacteristic.isWritable(): Boolean = containsProperty(BluetoothGattCharacteristic.PROPERTY_WRITE)
    fun BluetoothGattCharacteristic.isWritableWithoutResponse(): Boolean = containsProperty(BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)
    private fun BluetoothGattCharacteristic.containsProperty(property: Int): Boolean { return properties and property != 0 }

    private fun Activity.requestPermission(permission: String, requestCode: Int) {
        ActivityCompat.requestPermissions(this, arrayOf(permission), requestCode)
    }

    private fun Context.hasPermission(permissionType: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permissionType) == PackageManager.PERMISSION_GRANTED
    }
}