package com.iot.wateranalyst

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.viewpager.widget.ViewPager
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.material.tabs.TabLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.iot.wateranalyst.databinding.ActivityMainBinding
import com.iot.wateranalyst.ui.main.DataUpdateListener
import com.iot.wateranalyst.ui.main.SectionsPagerAdapter
import com.iot.wateranalyst.ui.main.WaterData
import java.util.*

const val GOOGLE_SIGN_IN = 100
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var isDarkMode = false
    var rawList = arrayListOf<Byte>()
    private lateinit var viewPager: ViewPager
    private var listeners = mutableListOf<DataUpdateListener>()
    private lateinit var viewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isDarkMode = when (this.resources.configuration.uiMode.and(
            Configuration.UI_MODE_NIGHT_MASK)) {
            Configuration.UI_MODE_NIGHT_YES -> true
            else -> false
        }
        viewModel = ViewModelProvider(this).get(MainViewModel::class.java)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val sectionsPagerAdapter = SectionsPagerAdapter(this, supportFragmentManager, isDarkMode)
        viewPager = binding.viewPager
        viewPager.adapter = sectionsPagerAdapter
        val tabs: TabLayout = binding.tabs
        tabs.setupWithViewPager(viewPager)

        session()
    }

    private fun session(){
        val prefs = getSharedPreferences(getString(R.string.prefs_file), Context.MODE_PRIVATE)
        val email = prefs.getString("email", null)
        val name = prefs.getString("name", null)
        val provider = prefs.getString("provider", null)

        if (email != null && provider != null && name != null){
            viewModel.isLoggedIn.postValue(true)
        }
    }

    fun loginOnClick() {
        val googleConf = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        val googleClient = GoogleSignIn.getClient(this, googleConf)
        googleClient.signOut()

        startActivityForResult(googleClient.signInIntent, GOOGLE_SIGN_IN)
    }

    override fun onResume() {
        isDarkMode = when (this.resources.configuration.uiMode.and(
            Configuration.UI_MODE_NIGHT_MASK)) {
            Configuration.UI_MODE_NIGHT_YES -> true
            else -> false
        }
        super.onResume()
    }

    override fun onRestart() {
        isDarkMode = when (this.resources.configuration.uiMode.and(
            Configuration.UI_MODE_NIGHT_MASK)) {
            Configuration.UI_MODE_NIGHT_YES -> true
            else -> false
        }
        super.onRestart()
    }

    fun nextFragment(componentList: ArrayList<Byte>) {
        rawList = componentList
        val waterData = rawList.toByteArray().decodeToString().toWaterData()
        for (listener in listeners) {
            listener.onDataUpdate(isDataReceived(), waterData, rawList)
        }
        viewPager.currentItem = viewPager.currentItem + 1
    }

    fun isDataReceived() = rawList.size > 0

    @Synchronized
    fun registerDataUpdateListener(listener: DataUpdateListener) {
        listeners.add(listener)
    }

    @Synchronized
    fun unregisterDataUpdateListener(listener: DataUpdateListener) {
        listeners.remove(listener)
    }

    fun String.toWaterData(): WaterData {
        val strs = this.split(";")
        strs.dropLast(1)
        return WaterData(strs.get(0).toFloat(), strs.get(1).toFloat(), strs.get(2).toFloat(), strs.get(3).toFloat(),
            strs.get(4).toFloat(), strs.get(5).toFloat(), strs.get(6).toFloat(), strs.get(7).toFloat(),
            strs.get(8).toFloat())
    }

    private fun showAlert() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Error")
        builder.setMessage("There was an error in the authentication")
        builder.setPositiveButton("Accept", null)
        val dialog: AlertDialog = builder.create()
        dialog.show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?){
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == GOOGLE_SIGN_IN){
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try{
                val account = task.getResult(ApiException::class.java)
                if (account != null){
                    val credential = GoogleAuthProvider.getCredential(account.idToken, null)
                    FirebaseAuth.getInstance().signInWithCredential(credential).addOnCompleteListener{
                        if (it.isSuccessful){
                            viewModel.isLoggedIn.postValue(true)
                        } else {
                            showAlert()
                        }
                    }
                }
            } catch(e: ApiException) {
                showAlert()
            }
        }
    }
}