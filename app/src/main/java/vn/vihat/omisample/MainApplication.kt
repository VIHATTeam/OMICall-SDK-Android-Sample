package vn.vihat.omisample

import android.app.Application
import android.util.Log
import androidx.lifecycle.ProcessLifecycleOwner
import vn.vihat.omicall.omisdk.OmiClient
import vn.vihat.omicall.omisdk.utils.DatabaseMaintenanceHelper

class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Log.d("App", "onCreate app")

        // CRASH FIX: Perform database maintenance on app start
        // This prevents SQLiteFullException crashes from WorkManager database bloat
        try {
            DatabaseMaintenanceHelper.performSafeMaintenance(applicationContext)
            Log.i("App", "Database maintenance completed successfully")
        } catch (e: Exception) {
            Log.e("App", "Database maintenance failed (non-critical)", e)
        }

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
            notificationColor = "#F95454",
            fullScreenAvatar = "calling_face"
        )
        ProcessLifecycleOwner.get().lifecycle.addObserver(omiClient)
    }
}
