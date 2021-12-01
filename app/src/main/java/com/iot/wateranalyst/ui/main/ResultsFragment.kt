package com.iot.wateranalyst.ui.main

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.iot.wateranalyst.MainActivity
import com.iot.wateranalyst.MainViewModel
import com.iot.wateranalyst.R
import com.iot.wateranalyst.databinding.ResultsFragmentBinding


/**
 * A placeholder fragment containing a simple view.
 */
class ResultsFragment(private val isDarkMode: Boolean = false) : Fragment(), DataUpdateListener{

    private lateinit var pageViewModel: PageViewModel
    private var _binding: ResultsFragmentBinding? = null
    private lateinit var viewModel: MainViewModel

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pageViewModel = ViewModelProvider(this).get(PageViewModel::class.java).apply {
            setIndex(arguments?.getInt(ARG_SECTION_NUMBER) ?: 1)
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        (activity as MainActivity).registerDataUpdateListener(this)
    }

    override fun onDestroy() {
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

        val textView: TextView = binding.sectionLabel
        textView.text = getString(R.string.tab_welcome_2)

        return root
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        viewModel = ViewModelProvider(this).get(MainViewModel::class.java)
        binding.viewModel = viewModel
        binding.googleLoginButton.setOnClickListener {
            (activity as MainActivity).loginOnClick()
        }
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

    override fun onDataUpdate(isDataReceived: Boolean, waterData: WaterData, rawData: ArrayList<Byte>) {
        viewModel.isDataReceived.postValue(isDataReceived)
        viewModel.pH.postValue(waterData.pH.toString())
        viewModel.hardness.postValue(waterData.hardness.toString())
        viewModel.solids.postValue(waterData.solids.toString())
        viewModel.chloramines.postValue(waterData.chloramines.toString())
        viewModel.sulfate.postValue(waterData.sulfate.toString())
        viewModel.conductivity.postValue(waterData.conductivity.toString())
        viewModel.organicCarbon.postValue(waterData.organicCarbon.toString())
        viewModel.trihalomethanes.postValue(waterData.trihalomethanes.toString())
        viewModel.turbidity.postValue(waterData.turbidity.toString())
        viewModel.waterData = waterData
        binding.sectionLabel.visibility = View.GONE
    }
}