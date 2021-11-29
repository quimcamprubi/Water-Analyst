package com.iot.wateranalyst.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData

class ResultsFragmentViewModel(application: Application, ) : AndroidViewModel(application) {

    private val context = getApplication<Application>().applicationContext
    lateinit var waterData: WaterData
    val isDataReceived = MutableLiveData<Boolean>(false)
    val pH = MutableLiveData<String>()
    val hardness = MutableLiveData<String>()
    val solids = MutableLiveData<String>()
    val chloramines = MutableLiveData<String>()
    val sulfate = MutableLiveData<String>()
    val conductivity = MutableLiveData<String>()
    val organicCarbon = MutableLiveData<String>()
    val trihalomethanes = MutableLiveData<String>()
    val turbidity = MutableLiveData<String>()
}
