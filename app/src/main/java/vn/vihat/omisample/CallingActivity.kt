package vn.vihat.omisample

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.WindowManager
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.omicrm.omisdk.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import vn.vihat.omicall.databinding.ActivityCallingBinding
import vn.vihat.omicall.omisdk.OmiClient
import vn.vihat.omicall.omisdk.OmiListener
import vn.vihat.omicall.omisdk.component.ButtonView
import vn.vihat.omicall.omisdk.component.NumpadDialog
import vn.vihat.omicall.omisdk.component.OverlayLoading
import vn.vihat.omicall.omisdk.data.model.MenuSelectorModel
import vn.vihat.omicall.omisdk.dialog.MenuSelector
import vn.vihat.omicall.omisdk.service.NotificationService.Companion.isVideo
import vn.vihat.omicall.omisdk.utils.AnimUtils
import vn.vihat.omicall.omisdk.utils.AppUtils
import vn.vihat.omicall.omisdk.utils.AppUtils.forceKillApp
import vn.vihat.omicall.omisdk.utils.SipServiceConstants
import vn.vihat.omicall.omisdk.utils.Utils
import vn.vihat.omicall.omisdk.videoutils.ScaleManager
import vn.vihat.omicall.omisdk.videoutils.Size

//Implementation of the OmiListener interface
class CallingActivity : AppCompatActivity(), OmiListener {

    private lateinit var binding: ActivityCallingBinding

    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    private var countDownJob: Job? = null
    private val supervisorJob = SupervisorJob()

    private var overlayLoading: OverlayLoading? = null

    private lateinit var omiClient: OmiClient

    // call state
    private var transactionId = ""
    private var isIncoming = false
    private var startTime: Long = 0
    private var isAcceptedCall = false
    private var isReopenCall = false
    private var isMuteCall = false
    private var isVideoCall = false
    private var remoteNumber = ""
    private var remoteName = ""
    private var remoteAvatar = ""
    private var isInternalCall = false
    private var callDirectionTxt = ""
    private var callDTMFs = ""
    private var numpadDialog: NumpadDialog? = null
    private var outputs: ArrayList<MenuSelectorModel>? = null
    private var outputSelected = 1
    private var sipNumber = ""

    // sensor state
    private lateinit var wakeLock: PowerManager.WakeLock
    private lateinit var sensorManager: SensorManager
    private var proximitySensor: Sensor? = null
    private var proximitySensorListener: SensorEventListener? = null

    //state video call
    private var autoHideHandler = Handler(Looper.getMainLooper())
    private var isShowActionButton = true
    private var isOffCamera = false

    //state config
    private var backgroundColor = "#FF1E3150"
    private var defaultAvatar = "calling_face"
    private var avatarSize = 72
    private var textColor = "#FFFFFFFF"
    private var videoCallText = "Gọi video"
    private var internalCallText = "Gọi nội bộ"
    private var inboundCallText = "Cuộc gọi đến"
    private var unknownContactText = "Không xác định"
    private var callingText = "Đang gọi..."
    private var ringingText = "Đang đổ chuông..."
    private var connectingText = "Đang kết nối..."
    private var endCallText = "Kết thúc"
    private var canSeePhoneNumber = true
    private var showUUID = true

    companion object {
        var instance: CallingActivity? = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityCallingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )
        setShowWhenLocked(true)
        setTurnScreenOn(true)

        instance = this

        // init omi client
        omiClient = OmiClient.getInstance(applicationContext)

        // add listener
        omiClient.addCallStateListener(this)

        // intent from notification incoming or other activity (such as call from dialer)
        isIncoming = intent!!.getBooleanExtra(SipServiceConstants.ACTION_IS_INCOMING_CALL, false)
        remoteNumber = intent.getStringExtra(SipServiceConstants.PARAM_NUMBER) ?: ""
        remoteName = intent.getStringExtra(SipServiceConstants.PARAM_USERNAME) ?: ""
        remoteAvatar = intent.getStringExtra(SipServiceConstants.PARAM_AVATAR) ?: ""
        isVideoCall = intent.getBooleanExtra(SipServiceConstants.PARAM_IS_VIDEO, false)
        transactionId = intent.getStringExtra(SipServiceConstants.PARAM_UUID) ?: ""
        isAcceptedCall =
            intent.getBooleanExtra(SipServiceConstants.ACTION_ACCEPT_INCOMING_CALL, false)

        //intent from notification reopen call
        isReopenCall = intent.getBooleanExtra(SipServiceConstants.ACTION_REOPEN_CALL, false)
        startTime = intent!!.getLongExtra(
            SipServiceConstants.PARAM_CONNECT_TIMESTAMP,
            System.currentTimeMillis()
        )

        isInternalCall =
            AppUtils.isInternalPhoneNumber(remoteNumber) //utils method to check internal call provide by SDK
        callDirectionTxt =
            if (isVideoCall) videoCallText
            else if (isInternalCall) internalCallText else inboundCallText
        outputs =
            AppUtils.mapOutputs(
                applicationContext,
                omiClient.getAudioOutputs(),
                "Receiver",
                "Speaker",
                "Headset"
            ) //utils method to map output provide by SDK
        outputSelected =
            when (true) {
                checkHaveOutput(8) -> 8
                checkHaveOutput(7) -> 7
                checkHaveOutput(3) -> 3
                checkHaveOutput(4) -> 4
                else -> 1
            }

        getSipNumber()
        setupViews()
        initBindingEvent()
        acquireWakeLock()
        setupProximitySensor()

        if (!isIncoming) {
            //this is case user call from dialer
            omiClient.startCall(
                phoneNumber = remoteNumber,
                isVideo = isVideo,
                name = "",
                avatar = ""
            )
        } else {
            if (isAcceptedCall) {
                //this is case user accept call from notification, so we need to accept call
                acceptCall()
            }

            if (isReopenCall) {
                //this is case user reopen call from notification after you close app, so we need to rerender view
                val activeCall =
                    Utils.getActiveCall(applicationContext) // This util method to get active call provide by SDK
                onCallEstablished(
                    activeCall?.id ?: 0,
                    activeCall?.remoteNumber,
                    activeCall?.isVideo,
                    startTime,
                    activeCall?.uuid
                )
            }
        }
    }

    override fun onStop() {
        instance = null
        omiClient.stopVideoPreview()
        super.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        supervisorJob.cancel()
        omiClient.removeCallStateListener(this)
        releaseWakeLock()
        releaseProximitySensor()
    }

    //-----------------------------------Render method-----------------------------------//
    private fun renderRingingView() {
        if (isIncoming) {
            binding.btnAcceptCallAnim.isVisible = true
            binding.btnAcceptCallSeek.isVisible = true
            binding.btnAcceptCallAnim.startPulse()
        } else {
            binding.btnAcceptCallWrapper.isVisible = false
        }
    }

    private fun renderInAudioCallView(startTime: Long) {
        binding.btnAcceptCallWrapper.isVisible = false
        binding.btnAcceptCallAnim.stopPulse()

        binding.audioExtraButtons.isVisible = true
        binding.callPingWrapper.isVisible = true
        renderTimeCall(startTime)
    }

    private fun renderInVideoCall() {
        binding.callVideoWrapper.isVisible = true
        binding.callAudioWrapper.isVisible = false
    }

    private fun renderTimeCall(startTime: Long) {
        val handler = Handler()
        handler.postDelayed(
            object : Runnable {
                override fun run() {
                    val time = System.currentTimeMillis() - startTime
                    val seconds = (time / 1000).toInt()
                    val minutes = seconds / 60
                    val hours = minutes / 60
                    val textSeconds =
                        if (seconds % 60 < 10) "0${seconds % 60}" else "${seconds % 60}"
                    val textMinutes =
                        if (minutes % 60 < 10) "0${minutes % 60}" else "${minutes % 60}"
                    val displayText =
                        if (hours > 0) {
                            "$hours:$textMinutes:$textSeconds"
                        } else {
                            "$textMinutes:$textSeconds"
                        }

                    coroutineScope.launch(Dispatchers.Main) {
                        binding.stateCallStatus.text = displayText
                    }

                    handler.postDelayed(this, 1000)
                }
            },
            1000
        )
    }

    private fun renderInfo() {
        val defaultUserImage =
            resources.getIdentifier("${packageName}:drawable/$defaultAvatar", null, null)
        if (remoteAvatar.isNotEmpty() && remoteAvatar != "null") {
            binding.audioRemoteAvatar.bind(
                remoteAvatar,
                iconSize = avatarSize,
                defaultAvatar = defaultUserImage,
                name = remoteName,
            )
        } else {
            binding.audioRemoteAvatar.bind(defaultAvatar = defaultUserImage, iconSize = avatarSize)
        }
        binding.audioRemoteName.text = remoteName.ifBlank { unknownContactText }
    }

    @SuppressLint("SetTextI18n")
    private fun setupViews() {
        val isShow = isAcceptedCall
        binding.audioExtraButtons.isVisible = !isVideoCall && isShow
        binding.videoExtraButtons.isVisible = isVideoCall && isShow
        binding.stateSipNumber.text = if (isInternalCall) "" else sipNumber
        binding.stateDirectionIcon.setImageResource(
            if (isInternalCall) R.drawable.ic_call_status_local
            else R.drawable.ic_call_status_inbound
        )

        binding.stateStartAt.text =
            "$callDirectionTxt ${AppUtils.getFormatDate("HH:mm:ss", System.currentTimeMillis())}"
        binding.backgroundView.setBackgroundColor(Color.parseColor(backgroundColor))
        if (remoteAvatar.isNotEmpty()) {
            binding.audioRemoteAvatar.bind(remoteAvatar, iconSize = avatarSize)
        } else {
            val defaultUserImage =
                resources.getIdentifier("${packageName}:drawable/$defaultAvatar", null, null)
            binding.audioRemoteAvatar.bind(defaultAvatar = defaultUserImage, iconSize = avatarSize)
        }
        binding.audioRemoteName.setTextColor(Color.parseColor(textColor))
        binding.audioRemoteNumber.setTextColor(Color.parseColor(textColor))
        binding.uuid.setTextColor(Color.parseColor(textColor))
        binding.audioRemoteNumber.text = Utils.securityCustomerData(remoteNumber, canSeePhoneNumber)
        binding.audioRemoteName.text = remoteName.ifBlank { unknownContactText }
        if (!isAcceptedCall && !isReopenCall) {
            renderRingingView()
        } else {
            binding.btnAcceptCallWrapper.isVisible = false
            binding.btnAcceptCallAnim.stopPulse()
            binding.stateCallStatus.text = connectingText
        }
        binding.uuid.visibility = if (showUUID) View.VISIBLE else View.GONE
        binding.uuid.text = transactionId

        handleToggleActionButton(
            binding.btnToggleSpeaker,
            outputs?.find { it.value.toInt() == outputSelected }?.icon
                ?: R.drawable.ic_voice_volume,
            false
        )
    }

    //-----------------------------------Handle method-----------------------------------//
    private fun checkHaveOutput(type: Int): Boolean {
        return outputs?.find { it.value.toInt() == type } != null
    }

    private fun initBindingEvent() {
        binding.btnEndAudioCall.onClick {
            if (!isAcceptedCall && !isReopenCall && isIncoming) {
                omiClient.decline()
            } else {
                omiClient.hangUp()
            }
            Utils.saveActiveCall(applicationContext, null)
            finishAndRemoveTask()
        }
        binding.btnEndVideoCall.onClick {
            if (!isAcceptedCall && !isReopenCall && isIncoming) {
                omiClient.decline()
            } else {
                omiClient.hangUp()
            }
            Utils.saveActiveCall(applicationContext, null)
            autoHideHandler.removeCallbacks(autoHideRunnable)
            finishAndRemoveTask()
        }
        binding.btnAcceptCallSeek.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {

                override fun onStartTrackingTouch(seekbar: SeekBar) {}

                override fun onProgressChanged(
                    seekBar: SeekBar?,
                    progress: Int,
                    fromUser: Boolean
                ) {
                    if (progress > 0) {
                        binding.btnAcceptCallAnim.stopPulse()

                        if ( progress > 50) {
                            acceptCall()
                            binding.btnAcceptCallWrapper.isVisible = false
                        }
                    }
                }

                override fun onStopTrackingTouch(seekbar: SeekBar) {
                    if (seekbar.progress < 40) {
                        seekbar.progress = 0
                        binding.btnAcceptCallAnim.startPulse()
                    }
                }
            }
        )

        binding.btnShowNumpad.onClick {
            numpadDialog =
                NumpadDialog(
                    callDTMFs,
                    {
                        callDTMFs += it
                        omiClient.sendDtmf(it)
                    }
                ) { numpadDialog = null }
            numpadDialog!!.show(supportFragmentManager, NumpadDialog.TAG)
        }
        binding.btnToggleAudioMute.onClick { handleToggleMute() }
        binding.btnToggleVideoMute.onClick {
            // handleEnterPictureInPictureMode()
            resetAutoHideVideoActionButtons()
            handleToggleMute()
            binding.videoLocalMicStatus.isVisible = isMuteCall
        }

        binding.btnToggleCamera.onClick {
            resetAutoHideVideoActionButtons()
            isOffCamera = !isOffCamera
            omiClient.toggleCamera()
            binding.videoLocalWrapper.visibility = if (isOffCamera) View.INVISIBLE else View.VISIBLE
            binding.btnSwitchCamera.setDisable(isOffCamera)
            handleToggleActionButton(binding.btnToggleCamera, R.drawable.ic_no_video, isOffCamera)
        }
        binding.btnSwitchCamera.onClick {
            resetAutoHideVideoActionButtons()
            omiClient.switchCamera()
        }
        binding.videoLocalWrapper.setOnClickListener {
            resetAutoHideVideoActionButtons()
            if (!isShowActionButton) {
                isShowActionButton = true
                toggleShowVideoActionButtons()
            }
        }
        binding.callVideoContainer.setOnClickListener { toggleShowVideoActionButtons(true) }
        binding.btnToggleSpeaker.onClick {
            if (outputs!!.size > 2) {
                MenuSelector.show(
                    supportFragmentManager,
                    selections = outputs,
                    listSelected = arrayListOf(outputSelected.toString()),
                    onSelected = { list ->
                        outputSelected = list[0].toInt()
                        handleToggleActionButton(
                            binding.btnToggleSpeaker,
                            outputs?.find { it.value.toInt() == outputSelected }?.icon
                                ?: R.drawable.ic_voice_volume,
                            false
                        )
                        omiClient.setAudio(list[0].toInt())
                    },
                    type = MenuSelector.TYPE_SINGLE,
                )
            } else {
                outputSelected = if (outputSelected == 2) 1 else 2
                omiClient.toggleSpeaker()
                handleToggleActionButton(
                    binding.btnToggleSpeaker,
                    outputs?.find { it.value.toInt() == outputSelected }?.icon
                        ?: R.drawable.ic_voice_volume,
                    outputSelected == 2
                )
            }
        }

        binding.btnTransferCall.onClick {
            omiClient.forwardCallTo("103")
        }
    }

    private fun acceptCall() {
        omiClient.pickUp()
        isAcceptedCall = true
    }

    private fun getSipNumber(): String {
        val callInfo = omiClient.getCurrentCallInfo()
        return (callInfo?.get("sipNumber") as String?) ?: ""
    }

    private fun handleToggleActionButton(btnView: ButtonView, icon: Int, isActive: Boolean) {
        btnView.setColorBg(if (isActive) R.color.white else R.color.white10)
        btnView.setIcon(
            ButtonView.ICON_POSITION_START,
            icon,
            if (isActive) R.color.primary else R.color.white
        )
    }

    private fun handleToggleMute() {
        isMuteCall = !isMuteCall
        coroutineScope.launch { omiClient.toggleMute() }
        handleToggleActionButton(
            if (isVideoCall) binding.btnToggleVideoMute else binding.btnToggleAudioMute,
            R.drawable.ic_block_microphone,
            isMuteCall
        )
    }

    private var autoHideRunnable = Runnable {
        isShowActionButton = false
        toggleShowVideoActionButtons()
    }

    private fun toggleShowVideoActionButtons(isToggle: Boolean = false) {
        if (isToggle) isShowActionButton = !isShowActionButton
        if (isShowActionButton) {
            autoHideHandler.postDelayed(autoHideRunnable, 3000)
            AnimUtils.slideDown(binding.videoExtraButtons)
        } else {
            AnimUtils.slideUp(binding.videoExtraButtons)
            autoHideHandler.removeCallbacks(autoHideRunnable)
        }
    }

    private fun resetAutoHideVideoActionButtons() {
        autoHideHandler.removeCallbacks(autoHideRunnable)
        autoHideHandler.postDelayed(autoHideRunnable, 3000)
    }

    private fun handleSetVideoStream(isLocal: Boolean, textureView: TextureView) {
        try {
            val surface = Surface(textureView.surfaceTexture)
            if (isLocal) {
                omiClient.setupLocalVideoFeed(surface)
            } else {
                omiClient.setupIncomingVideoFeed(surface)
                binding.videoRemoteInfo.isVisible = false
            }
            ScaleManager.adjustAspectRatio(
                textureView,
                Size(textureView.width, textureView.height),
                Size(1280, 720),
            )
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }


    private fun startCountDown(isShowed: Boolean) {
        lifecycleScope.launch {
            if (!supportFragmentManager.isStateSaved) {
                if (isShowed) return@launch
                overlayLoading = OverlayLoading.show(supportFragmentManager)

                countDownJob =
                    CoroutineScope(Dispatchers.Main + supervisorJob).launch {
                        delay(15000)
                        if (overlayLoading != null) {
                            overlayLoading?.dismiss()
                            overlayLoading = null
                            omiClient.onDisconnected(0, 502)
                            forceKillApp(applicationContext)
                        }
                    }
            }
        }
    }

    private fun cancelCountDown() {
        countDownJob?.cancel()
        countDownJob = null
        if (overlayLoading != null) {
            overlayLoading?.dismiss()
            overlayLoading = null
        }
    }


    //-----------------------------------handle sensor-----------------------------------//
    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock =
            powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MyApp::CallWakeLockTag")
        wakeLock.acquire(10 * 60 * 1000L /*10 minutes*/)
    }

    private fun releaseWakeLock() {
        if (wakeLock.isHeld) {
            wakeLock.release()
        }
    }

    private fun setupProximitySensor() {
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)

        proximitySensorListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (proximitySensor == null) return
                if (event.values[0] < proximitySensor!!.maximumRange) {
                    // Tắt màn hình khi gần tai
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                } else {
                    // Mở lại màn hình khi rời xa
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                }
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
                // Không cần xử lý
            }
        }

        sensorManager.registerListener(
            proximitySensorListener,
            proximitySensor,
            SensorManager.SENSOR_DELAY_NORMAL
        )
    }

    private fun releaseProximitySensor() {
        sensorManager.unregisterListener(proximitySensorListener)
    }

//-----------------------------------OmiListener-----------------------------------//

    override fun onRegisterCompleted(statusCode: Int) {
        Log.d("App", "onRegisterCompleted: statusCode: $statusCode")
    }

    override fun onFcmReceived(uuid: String, userName: String, avatar: String) {
        Log.d("App", "onFcmReceived: uuid: $uuid, userName: $userName, avatar: $avatar")
        remoteName = userName
        remoteAvatar = avatar
        this.transactionId = uuid
        renderInfo()
    }

    override fun incomingReceived(callerId: Int?, phoneNumber: String?, isVideo: Boolean?) {
        Log.d("App", "incomingReceived: callerId: $callerId, phoneNumber: $phoneNumber, isVideo: $isVideo")
        binding.stateCallStatus.text = ""
    }

    override fun onOutgoingStarted(callerId: Int, phoneNumber: String?, isVideo: Boolean?) {
        Log.d("App", "onOutgoingStarted: callerId: $callerId, phoneNumber: $phoneNumber, isVideo: $isVideo")
        binding.stateCallStatus.text = callingText
    }

    override fun onRinging(callerId: Int, transactionId: String?) {
        Log.d("App", "onRinging: callerId: $callerId, transactionId: $transactionId")
        getSipNumber()
        binding.stateSipNumber.text = if (isInternalCall) "" else sipNumber
        binding.stateCallStatus.text = if (isIncoming) "" else ringingText
    }

    override fun onConnecting() {
        Log.d("App", "onConnecting: ")
        binding.stateCallStatus.text = connectingText
    }

    override fun onCallEstablished(
        callerId: Int,
        phoneNumber: String?,
        isVideo: Boolean?,
        startTime: Long,
        transactionId: String?
    ) {
        Log.d("App", "onCallEstablished: $callerId, $phoneNumber, $isVideo, $startTime, $transactionId")
        this.startTime = startTime
        if (isVideoCall) {
            renderInVideoCall()
            autoHideHandler.postDelayed(autoHideRunnable, 3000)
            AppUtils.postDelay {
                handleSetVideoStream(true, binding.videoLocalStream)
                handleSetVideoStream(false, binding.videoRemoteStream)
            }
        } else {
            renderInAudioCallView(startTime)
        }
    }

    override fun onHold(isHold: Boolean) {
        Log.d("App", "onHold: $isHold")
    }

    override fun onMuted(isMuted: Boolean) {
        Log.d("App", "onMuted: $isMuted")
    }

    override fun onAudioChanged(audioInfo: Map<String, Any>) {
        Log.d("App", "onAudioChanged: $audioInfo")
    }
    override fun onVideoSize(width: Int, height: Int) {
        Log.d("App", "onVideoSize: $width, $height")
    }

    override fun onSwitchBoardAnswer(sip: String) {
        binding.stateSipNumber.text = sip
    }

    override fun networkHealth(stat: Map<String, *>, quality: Int) {
        Log.d("App", "networkHealth: $stat, $quality")
        lifecycleScope.launch(Dispatchers.Main) {
            try {
                val numSameMode = stat["lcn"] as? Int ?: 0

                if (numSameMode != 0 && numSameMode % 3 == 0) {
                    startCountDown(overlayLoading != null)
                } else {
                    if (numSameMode == 0) {
                        cancelCountDown()
                    }
                }

                val mos = stat["mos"] as? Float
                val pingColor =
                    ContextCompat.getColor(
                        applicationContext,
                        when (quality) {
                            0 -> R.color.success
                            1 -> R.color.warning
                            else -> R.color.error
                        }
                    )

                binding.apply {
                    callPingWrapper.isVisible = true
                    callPingValue.text = mos?.let { String.format("%.2f", it) } ?: ""
                    callPingIcon.setColorFilter(pingColor)
                    callPingValue.setTextColor(pingColor)
                }
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }

    override fun onCallEnd(callInfo: MutableMap<String, Any?>, statusCode: Int) {
        Log.d("App", "onCallEnd: callInfo: $callInfo, statusCode: $statusCode")
        runOnUiThread { binding.stateCallStatus.text = endCallText }
        releaseWakeLock()
        releaseProximitySensor()
        finishAndRemoveTask()
    }

    override fun onDescriptionError() {
        Log.d("App", "onDescriptionError: ")
    }

    override fun onUpdatedPushToken(isSuccess: Boolean) {
        Log.d("App", "onUpdatedPushToken: ")
    }


    // method 2

//    private val omiListener = object : OmiListener {
//        override fun incomingReceived(callerId: Int?, phoneNumber: String?, isVideo: Boolean?) {
//            TODO("Not yet implemented")
//        }
//
//        override fun networkHealth(stat: Map<String, *>, quality: Int) {
//            TODO("Not yet implemented")
//        }
//
//        override fun onAudioChanged(audioInfo: Map<String, Any>) {
//            TODO("Not yet implemented")
//        }
//
//        override fun onCallEnd(callInfo: MutableMap<String, Any?>, statusCode: Int) {
//            TODO("Not yet implemented")
//        }
//
//        override fun onCallEstablished(
//            callerId: Int,
//            phoneNumber: String?,
//            isVideo: Boolean?,
//            startTime: Long,
//            transactionId: String?
//        ) {
//            TODO("Not yet implemented")
//        }
//
//        override fun onConnecting() {
//            TODO("Not yet implemented")
//        }
//
//        override fun onDescriptionError() {
//            TODO("Not yet implemented")
//        }
//
//        override fun onFcmReceived(uuid: String, userName: String, avatar: String) {
//            TODO("Not yet implemented")
//        }
//
//        override fun onHold(isHold: Boolean) {
//            TODO("Not yet implemented")
//        }
//
//        override fun onMuted(isMuted: Boolean) {
//            TODO("Not yet implemented")
//        }
//
//        override fun onOutgoingStarted(callerId: Int, phoneNumber: String?, isVideo: Boolean?) {
//            TODO("Not yet implemented")
//        }
//
//        override fun onRegisterCompleted(statusCode: Int) {
//            TODO("Not yet implemented")
//        }
//
//        override fun onRinging(callerId: Int, transactionId: String?) {
//            TODO("Not yet implemented")
//        }
//
//        override fun onSwitchBoardAnswer(sip: String) {
//            TODO("Not yet implemented")
//        }
//
//        override fun onUpdatedPushToken(isSuccess: Boolean) {
//            TODO("Not yet implemented")
//        }
//
//        override fun onVideoSize(width: Int, height: Int) {
//            TODO("Not yet implemented")
//        }
//    }

}
