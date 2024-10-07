package vn.vihat.omisample

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import vn.vihat.omicall.omisdk.OmiClient

class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val omiClient = OmiClient.getInstance(applicationContext, false)
        omiClient.configPushNotification(
            showUUID = false,
            showMissedCall = true,
            inboundChannelId = "omi-sdk-sample-inbound",
            inboundChannelName = "Inbound Calls",
            missedChannelId = "omi-sdk-sample-missed",
            missedChannelName = "Missed Calls",
            notificationIcon = "ic_call_status_inbound",
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
            fullScreenAvatar = "calling_face"
        )
        ProcessLifecycleOwner.get().lifecycle.addObserver(omiClient)
    }
}

