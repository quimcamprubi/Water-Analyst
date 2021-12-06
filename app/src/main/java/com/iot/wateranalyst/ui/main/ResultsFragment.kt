package com.iot.wateranalyst.ui.main

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.google.android.gms.tasks.Task
import com.google.firebase.functions.FirebaseFunctions
import com.google.gson.*
import com.iot.wateranalyst.MainActivity
import com.iot.wateranalyst.MainViewModel
import com.iot.wateranalyst.databinding.ResultsFragmentBinding
import kotlinx.coroutines.*
import org.json.JSONObject
import timber.log.Timber
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * A placeholder fragment containing a simple view.
 */
class ResultsFragment(private val isDarkMode: Boolean = false) : Fragment(), DataUpdateListener {
    private lateinit var functions: FirebaseFunctions
    private lateinit var pageViewModel: PageViewModel
    private var _binding: ResultsFragmentBinding? = null
    private lateinit var viewModel: MainViewModel
    private lateinit var waterData: WaterData
    private lateinit var requestReturnValue: String
    private lateinit var job: Job

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        job = Job()
        pageViewModel = ViewModelProvider(this).get(PageViewModel::class.java).apply {
            setIndex(arguments?.getInt(ARG_SECTION_NUMBER) ?: 1)
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        (activity as MainActivity).registerDataUpdateListener(this)
    }

    override fun onDestroy() {
        job.cancel()
        super.onDestroy()
        (activity as MainActivity).unregisterDataUpdateListener(this)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = ResultsFragmentBinding.inflate(inflater, container, false)
        binding.lifecycleOwner = this
        val root = binding.root

        viewModel = ViewModelProvider(activity!!).get(MainViewModel::class.java)
        binding.viewModel = viewModel
        binding.googleLoginButton.setOnClickListener {
            (activity as MainActivity).loginOnClick()
        }
        binding.sendDataButton.setOnClickListener {
            getPrediction()
        }
        functions = FirebaseFunctions.getInstance("europe-west2")
        return root
    }

    companion object {
        private const val ARG_SECTION_NUMBER = "section_number"

        @JvmStatic
        fun newInstance(sectionNumber: Int): ResultsFragment {
            return ResultsFragment().apply {
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

    override fun onDataUpdate(
        isDataReceived: Boolean,
        waterData: WaterData,
        rawData: ArrayList<Byte>
    ) {
        viewModel.isDataReceived.postValue(isDataReceived)
        if (viewModel.isLoggedIn.value == true) viewModel.isPredictionAvailable.postValue(true)
        viewModel.pH.postValue(waterData.pH.toString())
        viewModel.hardness.postValue(waterData.hardness.toString())
        viewModel.solids.postValue(waterData.solids.toString())
        viewModel.chloramines.postValue(waterData.chloramines.toString())
        viewModel.sulfate.postValue(waterData.sulfate.toString())
        viewModel.conductivity.postValue(waterData.conductivity.toString())
        viewModel.organicCarbon.postValue(waterData.organicCarbon.toString())
        viewModel.trihalomethanes.postValue(waterData.trihalomethanes.toString())
        viewModel.turbidity.postValue(waterData.turbidity.toString())
        this.waterData = waterData
        binding.sectionLabel.visibility = View.GONE
    }

    private fun getPrediction() {
        var requestData = waterData.pH.toString().plus(",")
        requestData = requestData.plus(waterData.hardness.toString()).plus(",")
        requestData = requestData.plus(waterData.solids.toString()).plus(",")
        requestData = requestData.plus(waterData.chloramines.toString()).plus(",")
        requestData = requestData.plus(waterData.sulfate.toString()).plus(",")
        requestData = requestData.plus(waterData.conductivity.toString()).plus(",")
        requestData = requestData.plus(waterData.organicCarbon.toString()).plus(",")
        requestData = requestData.plus(waterData.trihalomethanes.toString()).plus(",")
        requestData = requestData.plus(waterData.turbidity.toString())

        sendPostRequest(requestData)
    }

    // HTTPS POST network call
    private fun sendPostRequest(requestData: String) {
        val jsonObject = JSONObject()
        jsonObject.put("water_data", requestData)
        val jsonObjectString = jsonObject.toString()
        val uiScope = CoroutineScope(Dispatchers.Main + job)
        // We are using a Coroutine to not freeze up the main UI thread. This way, the app continues
        // to run while the network call is being processed
        uiScope.launch(Dispatchers.IO) {
            val url = URL("https://europe-west2-water-analyst-328009.cloudfunctions.net/make_water_prediction")
            val httpURLConnection = url.openConnection() as HttpURLConnection
            httpURLConnection.requestMethod = "POST"
            httpURLConnection.setRequestProperty("Content-Type", "application/json") // The format of the content we're sending to the server
            httpURLConnection.setRequestProperty("Accept", "application/json") // The format of response we want to get from the server
            httpURLConnection.doInput = true
            httpURLConnection.doOutput = true

            // Send the JSON we created
            val outputStreamWriter = OutputStreamWriter(httpURLConnection.outputStream)
            outputStreamWriter.write(jsonObjectString)
            outputStreamWriter.flush()

            // Check if the connection is successful
            val responseCode = httpURLConnection.responseCode

            if (responseCode == HttpURLConnection.HTTP_OK) {
                withContext(Dispatchers.Main) {
                    val response = httpURLConnection.inputStream.bufferedReader().use { it.readText() }  // defaults to UTF-8
                    // Convert raw JSON to pretty JSON using GSON library
                    val responseList = response.split(",")
                    if (responseList.size <= 1) {
                        Toast.makeText(activity, "Cloud function error, please try again", Toast.LENGTH_SHORT).show()
                    } else {
                        viewModel.rawPotability.postValue(responseList[0].toDouble())
                        val potabilityPercentage = String.format("%.2f", responseList[0].toFloat() * 100)
                        viewModel.potabilityIndex.postValue(potabilityPercentage.plus("%"))
                        viewModel.waterQuality.postValue(responseList[1])
                        viewModel.relatedDiseases.postValue(responseList[2])
                        viewModel.isResponseReceived.postValue(true)
                    }
                    Timber.e(response)
                }
            } else {
                Timber.e("Cloud function returned an error%s", responseCode.toString())
            }
        }
    }

    // This is the Firebase Functions call. It does not work because it does not send the JSON parameter
    // in the proper format (application/json). There is no way of changing the call's header, as well
    // as the other necessary https headers.
    private fun makeWaterPrediction(requestData: String): Task<JsonElement> {
        val request = mapOf(
            "water_data" to requestData
        )
        val finalRequest = JSONObject(request)
        return functions.getHttpsCallable("make_water_prediction")
            .call(finalRequest)
            .continueWith { task ->
                val result = task.result?.data
                JsonParser.parseString(Gson().toJson(result))
            }
    }
}