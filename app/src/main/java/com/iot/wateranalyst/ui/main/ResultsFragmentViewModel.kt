package com.iot.wateranalyst.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData

class ResultsFragmentViewModel(application: Application, ) : AndroidViewModel(application) {

    private val context = getApplication<Application>().applicationContext
    val isDataReceived = MutableLiveData<Boolean>(false)

}
