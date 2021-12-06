package com.iot.wateranalyst

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import com.iot.wateranalyst.ui.main.WaterData

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val context = getApplication<Application>().applicationContext
    val isScanning = MutableLiveData<Boolean>(false)
    val isBluetoothConnected = MutableLiveData<Boolean>(false)
    val isDataReceived = MutableLiveData<Boolean>(false)
    val isLoggedIn = MutableLiveData<Boolean>(false)
    val isPredictionAvailable = MutableLiveData<Boolean>(false)
    val pH = MutableLiveData<String>()
    val hardness = MutableLiveData<String>()
    val solids = MutableLiveData<String>()
    val chloramines = MutableLiveData<String>()
    val sulfate = MutableLiveData<String>()
    val conductivity = MutableLiveData<String>()
    val organicCarbon = MutableLiveData<String>()
    val trihalomethanes = MutableLiveData<String>()
    val turbidity = MutableLiveData<String>()
    val potabilityIndex = MutableLiveData<String>()
    val rawPotability = MutableLiveData<Double>()
    val waterQuality = MutableLiveData<String>()
    val relatedDiseases = MutableLiveData<String>()
    val isResponseReceived = MutableLiveData<Boolean>(false)
    val isDiseasesDataAvailable = MutableLiveData<Boolean>(false)
}