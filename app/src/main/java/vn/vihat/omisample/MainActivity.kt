package vn.vihat.omisample

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import vn.vihat.omicall.R
import vn.vihat.omicall.databinding.ActivityExampleBinding
import vn.vihat.omisample.utils.AppUtils

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration

    private lateinit var binding: ActivityExampleBinding

    private var lastPressDownX: Int = 0

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        AppUtils.keyboardDismiss(this, ev, lastPressDownX) { lastPressDownX = it }
        return super.dispatchTouchEvent(ev)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityExampleBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        checkPermission()
        val navController = findNavController(R.id.nav_host_fragment_content_example)
        appBarConfiguration = AppBarConfiguration(navController.graph)
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
        setupActionBarWithNavController(navController, appBarConfiguration)

        checkHasRegister()

        
    }

    private fun checkHasRegister() {
        if (AppUtils.checkSession(this)) {
            findNavController(R.id.nav_host_fragment_content_example).navigate(R.id.action_FirstFragment_to_SecondFragment)
        }
    }

    private fun checkPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.CALL_PHONE,
                    Manifest.permission.CAMERA,
                    Manifest.permission.MODIFY_AUDIO_SETTINGS,
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.POST_NOTIFICATIONS,
                ),
                0,
            )
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.CALL_PHONE,
                    Manifest.permission.CAMERA,
                    Manifest.permission.MODIFY_AUDIO_SETTINGS,
                    Manifest.permission.RECORD_AUDIO,
                ),
                0,
            )
        }
    }

}