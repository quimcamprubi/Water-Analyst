package com.iot.wateranalyst.ui.main

import android.content.Context
import android.os.Bundle
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
        viewModel.isPredictionAvailable.postValue(true)
        binding.sendDataButton.setOnClickListener {
            getPrediction()
        }
        functions = FirebaseFunctions.getInstance("europe-west1")
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
        receivedWaterData: WaterData,
        rawData: ArrayList<Byte>
    ) {
        viewModel.isDataReceived.postValue(isDataReceived)
        if (viewModel.isLoggedIn.value == true) viewModel.isPredictionAvailable.postValue(true)
        viewModel.pH.postValue(receivedWaterData.pH.toString())
        viewModel.hardness.postValue(receivedWaterData.hardness.toString())
        viewModel.solids.postValue(receivedWaterData.solids.toString())
        viewModel.chloramines.postValue(receivedWaterData.chloramines.toString())
        viewModel.sulfate.postValue(receivedWaterData.sulfate.toString())
        viewModel.conductivity.postValue(receivedWaterData.conductivity.toString())
        viewModel.organicCarbon.postValue(receivedWaterData.organicCarbon.toString())
        viewModel.trihalomethanes.postValue(receivedWaterData.trihalomethanes.toString())
        viewModel.turbidity.postValue(receivedWaterData.turbidity.toString())
        this.waterData = receivedWaterData
        binding.sectionLabel.visibility = View.GONE
    }

    private fun getPrediction() {
        // You can uncomment some of these Water Data mocks in order to test the Cloud Function with different data than the one obtained from the board.
        // Very Good water quality:
        // this.waterData = WaterData(6.800119090315878,242.0080815075149,39143.40332881009,9.50169458771527,187.1707143624393,376.45659307467866,11.43246634722874,73.7772750262626,3.854939899721073)

        // Good water quality:
        // this.waterData = WaterData(4.68886106249815,234.89370257412287,28174.620516250765,10.850036483517218,187.42413089573415,444.8543208302114,11.784798842667612,89.01097411544157,2.8968521864425583)

        // Normal water quality:
        // this.waterData = WaterData(5.117390302624234,225.6574044119953,30914.111579007564,6.207729477942272,371.6493915314526,356.86228711669946,8.073420571182282,77.3881380783752,3.9371884418353353)

        // Slightly bad water quality:
        // this.waterData = WaterData(6.660212026118103,168.28374685651832,30944.363591242687,5.858769130547582,310.93085831787846,523.6712975009444,17.88423519296481,77.0423180517003,3.7497012410996176)

        // Bad water quality:
        // this.waterData = WaterData(4.961352728384606,166.25996162297542,22229.230089547444,9.922077892734912,295.131831185993,449.14719149056054,12.001547405946155,63.4279786441529,3.902837833888625)

        // Very bad water quality:
        this.waterData = WaterData(10.223862164528773,248.07173527013992,28749.716543528233,7.5134084658313025,393.66339551509645,283.6516335078445,13.789695317519886,84.60355617402357,2.672988736934779)

        // If you use one of these mocks, you must also uncomment line 72 of this file.

        var requestData = this.waterData.pH.toString().plus(",")
        requestData = requestData.plus(this.waterData.hardness.toString()).plus(",")
        requestData = requestData.plus(this.waterData.solids.toString()).plus(",")
        requestData = requestData.plus(this.waterData.chloramines.toString()).plus(",")
        requestData = requestData.plus(this.waterData.sulfate.toString()).plus(",")
        requestData = requestData.plus(this.waterData.conductivity.toString()).plus(",")
        requestData = requestData.plus(this.waterData.organicCarbon.toString()).plus(",")
        requestData = requestData.plus(this.waterData.trihalomethanes.toString()).plus(",")
        requestData = requestData.plus(this.waterData.turbidity.toString())

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
            // All code written here will be executed in this scope
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
                        if (responseList.size > 2) {
                            viewModel.relatedDiseases.postValue(responseList.drop(2).toStringList())
                            viewModel.isDiseasesDataAvailable.postValue(true)
                        }
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


    fun List<String>.toStringList(): String {
        var returnString = String()
        for (string in this) {
            returnString = returnString.plus(string).plus(", ")
        }
        returnString = returnString.dropLast(2)
        return returnString
    }
}