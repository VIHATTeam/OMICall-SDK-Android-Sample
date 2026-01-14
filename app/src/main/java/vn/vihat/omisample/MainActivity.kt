package vn.vihat.omisample

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import vn.vihat.omicall.R
import vn.vihat.omicall.databinding.ActivityExampleBinding
import vn.vihat.omicall.omisdk.OmiClient
import vn.vihat.omicall.omisdk.OmiListener
import vn.vihat.omisample.utils.AppUtils

class MainActivity : AppCompatActivity(), OmiListener {

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
        OmiClient.getInstance(this.applicationContext, false).addCallStateListener(this)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        val am = getSystemService(AUDIO_SERVICE) as AudioManager
        am.mode = AudioManager.MODE_NORMAL

        // Request permissions immediately on app start
        requestRequiredPermissions()

        val navController = findNavController(R.id.nav_host_fragment_content_example)
        appBarConfiguration = AppBarConfiguration(navController.graph)
        // Hide back button on toolbar
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
        setupActionBarWithNavController(navController, appBarConfiguration)

        OmiClient.getInstance(this.applicationContext).configPushNotification(
            showUUID = false,
            showMissedCall = true,
            inboundChannelId = "omi-sdk-sample-inbound",
            inboundChannelName = "Inbound Calls",
            missedChannelId = "omi-sdk-sample-missed",
            missedChannelName = "Missed Calls",
            notificationIcon = "",
            videoCallText = "Gọi Video",
            internalCallText = "Gọi nội bộ",
            inboundCallText = "Cuộc gọi đến",
            unknownContactText = "Không xác định",
            callingText = "Đang gọi...",
            incomingCallText = "Cuộc gọi đến",
            ringingText = "Đang đổ chuông...",
            connectingText = "Đang kết nối...",
            endCallText = "Kết thúc",
            lostConnectionText = "Mất kết nối",
            callTerminatedText = "Cuộc gọi kết thúc",
            notificationColor = "#F95454",
        )

        checkHasRegister()
    }

    override fun onRegisterCompleted(statusCode: Int) {
        if (statusCode == 200) {
            findNavController(R.id.nav_host_fragment_content_example).navigate(R.id.action_FirstFragment_to_SecondFragment)
        }
    }

    override fun onStop() {
        super.onStop()
        OmiClient.getInstance(this.applicationContext).removeCallStateListener(this)
    }

    private fun checkHasRegister() {
        if (AppUtils.checkSession(this)) {
            findNavController(R.id.nav_host_fragment_content_example).navigate(R.id.action_FirstFragment_to_SecondFragment)
        }
    }

    private fun requestRequiredPermissions() {
        val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.USE_SIP,
                Manifest.permission.CALL_PHONE,
                Manifest.permission.CAMERA,
                Manifest.permission.MODIFY_AUDIO_SETTINGS,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.POST_NOTIFICATIONS,
            )
        } else {
            arrayOf(
                Manifest.permission.USE_SIP,
                Manifest.permission.CALL_PHONE,
                Manifest.permission.CAMERA,
                Manifest.permission.MODIFY_AUDIO_SETTINGS,
                Manifest.permission.RECORD_AUDIO,
            )
        }

        // Check if RECORD_AUDIO permission is already granted
        val recordAudioGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!recordAudioGranted) {
            Log.w("MainActivity", "RECORD_AUDIO permission not granted - requesting permissions")
            Toast.makeText(
                this,
                "App cần quyền microphone để thực hiện cuộc gọi. Vui lòng cấp quyền.",
                Toast.LENGTH_LONG
            ).show()
        } else {
            Log.d("MainActivity", "RECORD_AUDIO permission already granted")
        }

        // Request all required permissions
        ActivityCompat.requestPermissions(this, requiredPermissions, PERMISSION_REQUEST_CODE)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == PERMISSION_REQUEST_CODE) {
            val recordAudioGranted = permissions.indexOf(Manifest.permission.RECORD_AUDIO).let { index ->
                index >= 0 && grantResults[index] == PackageManager.PERMISSION_GRANTED
            }

            if (recordAudioGranted) {
                Log.d("MainActivity", "RECORD_AUDIO permission granted - SIP service can now use foreground service with microphone")
                Toast.makeText(this, "Quyền microphone đã được cấp", Toast.LENGTH_SHORT).show()
            } else {
                Log.w("MainActivity", "RECORD_AUDIO permission denied - SIP service will have limited functionality")
                Toast.makeText(
                    this,
                    "Quyền microphone bị từ chối - App có thể không nhận được cuộc gọi khi chạy nền",
                    Toast.LENGTH_LONG
                ).show()
            }

            // Log all permission results for debugging
            permissions.forEachIndexed { index, permission ->
                val granted = grantResults[index] == PackageManager.PERMISSION_GRANTED
                Log.d("MainActivity", "Permission $permission: ${if (granted) "GRANTED" else "DENIED"}")
            }
        }
    }

    companion object {
        private const val PERMISSION_REQUEST_CODE = 123
    }
}
