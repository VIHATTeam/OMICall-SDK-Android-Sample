# Hướng dẫn tích hợp OmiSDK vào dự án Android

## Giới thiệu

OmiSDK là một SDK mạnh mẽ giúp bạn tích hợp các tính năng gọi điện vào ứng dụng Android của mình.
Dưới đây là các bước để tích hợp OmiSDK vào dự án của bạn.

## Tổng quan kiến trúc

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              YOUR APPLICATION                                │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌─────────────┐    ┌─────────────┐    ┌─────────────┐    ┌─────────────┐  │
│  │ Application │    │  Activity   │    │  Fragment   │    │  Service    │  │
│  │  (MyApp)    │    │ (Calling)   │    │  (Login)    │    │   (FCM)     │  │
│  └──────┬──────┘    └──────┬──────┘    └──────┬──────┘    └──────┬──────┘  │
│         │                  │                  │                  │         │
│         └──────────────────┴──────────────────┴──────────────────┘         │
│                                    │                                        │
│                                    ▼                                        │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                           OmiClient                                  │   │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌────────────┐  │   │
│  │  │  register() │  │ startCall() │  │  pickUp()   │  │  hangUp()  │  │   │
│  │  │             │  │             │  │  decline()  │  │            │  │   │
│  │  └─────────────┘  └─────────────┘  └─────────────┘  └────────────┘  │   │
│  │                                                                      │   │
│  │  ┌─────────────────────────────────────────────────────────────┐    │   │
│  │  │                      OmiListener                             │    │   │
│  │  │  • onRegisterCompleted()  • incomingReceived()              │    │   │
│  │  │  • onCallEstablished()    • onCallEnd()                     │    │   │
│  │  │  • networkHealth()        • onVideoSize()                   │    │   │
│  │  └─────────────────────────────────────────────────────────────┘    │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                    │                                        │
└────────────────────────────────────┼────────────────────────────────────────┘
                                     │
                                     ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                               OmiSDK                                        │
├─────────────────────────────────────────────────────────────────────────────┤
│  ┌───────────────┐  ┌───────────────┐  ┌───────────────┐  ┌──────────────┐ │
│  │  SIP Service  │  │ Notification  │  │    PJSIP      │  │   Firebase   │ │
│  │   Manager     │  │   Service     │  │    Engine     │  │     FCM      │ │
│  └───────┬───────┘  └───────┬───────┘  └───────┬───────┘  └──────┬───────┘ │
│          │                  │                  │                  │         │
│          └──────────────────┴──────────────────┴──────────────────┘         │
│                                    │                                        │
└────────────────────────────────────┼────────────────────────────────────────┘
                                     │
                                     ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                            OMI Server (SIP)                                  │
└─────────────────────────────────────────────────────────────────────────────┘
```

## Luồng hoạt động

### 1. Đăng ký và Kết nối

```
┌──────────┐     ┌──────────┐     ┌──────────┐     ┌──────────┐     ┌──────────┐
│   App    │     │OmiClient │     │   SDK    │     │ Firebase │     │OMI Server│
└────┬─────┘     └────┬─────┘     └────┬─────┘     └────┬─────┘     └────┬─────┘
     │                │                │                │                │
     │ 1. register()  │                │                │                │
     │───────────────>│                │                │                │
     │                │ 2. Save credentials             │                │
     │                │───────────────>│                │                │
     │                │                │ 3. Register FCM token           │
     │                │                │───────────────────────────────>│
     │                │                │                │                │
     │                │                │ 4. Connect SIP │                │
     │                │                │───────────────────────────────>│
     │                │                │                │   5. 200 OK   │
     │                │                │<───────────────────────────────│
     │ 6. onRegisterCompleted(200)     │                │                │
     │<────────────────────────────────│                │                │
     │                │                │                │                │
```

### 2. Cuộc gọi đi (Outgoing Call)

```
┌──────────┐     ┌──────────┐     ┌──────────┐     ┌──────────┐
│   App    │     │OmiClient │     │   SDK    │     │OMI Server│
└────┬─────┘     └────┬─────┘     └────┬─────┘     └────┬─────┘
     │                │                │                │
     │ 1. startCall() │                │                │
     │───────────────>│                │                │
     │                │ 2. Check permissions            │
     │                │───────────────>│                │
     │                │                │ 3. INVITE      │
     │                │                │───────────────>│
     │ 4. onOutgoingStarted()          │                │
     │<────────────────────────────────│                │
     │                │                │  5. 180 Ringing│
     │                │                │<───────────────│
     │ 6. onRinging() │                │                │
     │<────────────────────────────────│                │
     │                │                │   7. 200 OK    │
     │                │                │<───────────────│
     │ 8. onCallEstablished()          │                │
     │<────────────────────────────────│                │
     │                │                │                │
     │   ═══════════ CALL IN PROGRESS ═══════════      │
     │                │                │                │
     │ 9. hangUp()    │                │                │
     │───────────────>│                │                │
     │                │                │  10. BYE       │
     │                │                │───────────────>│
     │ 11. onCallEnd()│                │                │
     │<────────────────────────────────│                │
```

### 3. Cuộc gọi đến (Incoming Call)

```
┌──────────┐     ┌──────────┐     ┌──────────┐     ┌──────────┐     ┌──────────┐
│   App    │     │OmiClient │     │   SDK    │     │ Firebase │     │OMI Server│
└────┬─────┘     └────┬─────┘     └────┬─────┘     └────┬─────┘     └────┬─────┘
     │                │                │                │                │
     │                │                │                │  1. INVITE     │
     │                │                │                │<───────────────│
     │                │                │  2. FCM Push   │                │
     │                │                │<───────────────│                │
     │                │ 3. Show notification            │                │
     │                │<───────────────│                │                │
     │ 4. incomingReceived()           │                │                │
     │<────────────────────────────────│                │                │
     │                │                │                │                │
     │ 5. pickUp()    │                │                │                │
     │───────────────>│                │                │                │
     │                │                │  6. 200 OK     │                │
     │                │                │───────────────────────────────>│
     │ 7. onCallEstablished()          │                │                │
     │<────────────────────────────────│                │                │
     │                │                │                │                │
     │   ═══════════ CALL IN PROGRESS ═══════════      │                │
     │                │                │                │                │
     │                │                │   8. BYE       │                │
     │                │                │<───────────────────────────────│
     │ 9. onCallEnd() │                │                │                │
     │<────────────────────────────────│                │                │
```

### 4. Video Call Flow

```
┌──────────────────────────────────────────────────────────────────────────┐
│                           VIDEO CALL SETUP                                │
├──────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   ┌─────────────┐         ┌─────────────┐         ┌─────────────┐       │
│   │   LOCAL     │         │  OmiClient  │         │   REMOTE    │       │
│   │   VIDEO     │         │             │         │   VIDEO     │       │
│   └──────┬──────┘         └──────┬──────┘         └──────┬──────┘       │
│          │                       │                       │              │
│          │ setupLocalVideoFeed() │                       │              │
│          │──────────────────────>│                       │              │
│          │                       │                       │              │
│          │                       │ setupIncomingVideoFeed()             │
│          │                       │──────────────────────>│              │
│          │                       │                       │              │
│          │                       │    onVideoSize()      │              │
│          │                       │<──────────────────────│              │
│          │                       │                       │              │
│          │                       │ ScaleManager.adjustAspectRatio()     │
│          │                       │──────────────────────>│              │
│          │                       │                       │              │
│          │    toggleCamera()     │                       │              │
│          │──────────────────────>│                       │              │
│          │                       │                       │              │
│          │    switchCamera()     │                       │              │
│          │──────────────────────>│                       │              │
│                                                                          │
└──────────────────────────────────────────────────────────────────────────┘
```

## Các bước tích hợp nhanh

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        QUICK INTEGRATION CHECKLIST                           │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌─────┐                                                                    │
│  │  1  │  SETUP GRADLE                                                      │
│  └──┬──┘  • Add SDK dependency: io.omicrm.vihat:omi-sdk:2.5.7              │
│     │     • Add GitHub repository with credentials                          │
│     │     • Configure compileSdk=35, minSdk=24, Java 11                     │
│     ▼                                                                       │
│  ┌─────┐                                                                    │
│  │  2  │  CONFIGURE MANIFEST                                                │
│  └──┬──┘  • Add permissions (RECORD_AUDIO, CAMERA, FOREGROUND_SERVICE_*)   │
│     │     • Add CallingActivity with intent-filter                          │
│     │     • Add FirebaseMessageReceiver                                     │
│     ▼                                                                       │
│  ┌─────┐                                                                    │
│  │  3  │  SETUP APPLICATION                                                 │
│  └──┬──┘  • Call DatabaseMaintenanceHelper.performSafeMaintenance()        │
│     │     • Initialize OmiClient with needRegister=false                    │
│     │     • Add to ProcessLifecycleOwner                                    │
│     ▼                                                                       │
│  ┌─────┐                                                                    │
│  │  4  │  CONFIGURE NOTIFICATIONS                                           │
│  └──┬──┘  • Call omiClient.configPushNotification() with your settings     │
│     │     • Add google-services.json for Firebase                           │
│     ▼                                                                       │
│  ┌─────┐                                                                    │
│  │  5  │  IMPLEMENT REGISTRATION                                            │
│  └──┬──┘  • Request RECORD_AUDIO permission first                          │
│     │     • Call OmiClient.register() or registerWithApiKey()               │
│     │     • Handle result in coroutine                                      │
│     ▼                                                                       │
│  ┌─────┐                                                                    │
│  │  6  │  IMPLEMENT CALLING ACTIVITY                                        │
│  └──┬──┘  • Implement OmiListener interface                                │
│     │     • Handle incoming/outgoing call UI                                │
│     │     • Setup video feeds if video call                                 │
│     ▼                                                                       │
│  ┌─────┐                                                                    │
│  │  7  │  TEST & DEBUG                                                      │
│  └─────┘  • Test outgoing call                                             │
│           • Test incoming call (foreground & background)                    │
│           • Test video call                                                 │
│           • Check permissions handling                                      │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

## Yêu cầu hệ thống

- **minSdk**: 24 (Android 7.0)
- **targetSdk**: 35 (Android 15)
- **compileSdk**: 35
- **Java**: 11
- **Kotlin**: 1.9.0+

## Bước 1: Thêm kho lưu trữ và phụ thuộc

### Mở tệp `app/build.gradle.kts` và thêm phụ thuộc vào OmiSDK:

```gradle
android {
    compileSdk = 35

    defaultConfig {
        minSdk = 24
        targetSdk = 35
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    // OmiSDK - Phiên bản mới nhất
    implementation("io.omicrm.vihat:omi-sdk:2.5.7")
}
```

Thêm các thư viện cần thiết (nếu khi run project bị lỗi thiếu thư viện):

```gradle
dependencies {
    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-process:2.8.5")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.6.2")

    // WorkManager & Security
    implementation("androidx.work:work-runtime-ktx:2.8.1")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:32.2.0"))
    implementation("com.google.firebase:firebase-messaging-ktx:23.2.1")

    // Network
    implementation("com.squareup.okhttp3:logging-interceptor:5.0.0-alpha.11")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.retrofit2:retrofit:2.9.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.2")

    // Gson
    implementation("com.google.code.gson:gson:2.10.1")
}
```

### Mở tệp `settings.gradle.kts` và thêm kho lưu trữ của OmiSDK:

```gradle
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://maven.pkg.github.com/omicall/OMICall-SDK")
            credentials {
                username = "$username"
                password = "$password"
            }
            authentication {
                create<BasicAuthentication>("basic")
            }
        }
    }
}
```

Thông tin `username` và `password` sẽ được cung cấp khi bạn đăng ký sử dụng OmiSDK, nếu chưa có bạn
có thể liên hệ với chúng tôi để được hỗ trợ.

## Bước 2: Cấu hình và tích hợp OmiSDK

### Cấu hình tệp `AndroidManifest.xml`:

Thêm các quyền cần thiết vào tệp `AndroidManifest.xml`:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <!-- Telephony feature (optional) -->
    <uses-feature
        android:name="android.hardware.telephony"
        android:required="false" />

    <!-- Quyền cơ bản -->
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="com.google.android.c2dm.permission.RECEIVE" />
    <uses-permission android:name="android.permission.WAKE_LOCK" />

    <!-- Quyền cho cuộc gọi -->
    <uses-permission android:name="android.permission.RECORD_AUDIO" />
    <uses-permission android:name="android.permission.CAMERA" />
    <uses-permission android:name="android.permission.USE_SIP" />
    <uses-permission android:name="android.permission.CALL_PHONE" />
    <uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />

    <!-- Foreground service permissions (Android 14+) -->
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_PHONE_CALL" />

    <!-- Notification permission (Android 13+) -->
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

    <application>
        <!-- ... -->
    </application>
</manifest>
```

Thêm intent filter cho activity hiển thị cuộc gọi:

```xml
<application>
    <!--Các phần khác-->
    <activity android:name=".CallingActivity"
        android:alwaysRetainTaskState="true"
        android:largeHeap="true"
        android:showOnLockScreen="true"
        android:theme="@style/Theme.OMICall"
        android:exported="true">
        <intent-filter>
            <action android:name="android.intent.action.CALL" />
            <category android:name="android.intent.category.DEFAULT" />
            <data android:host="incoming_call" android:scheme="omisdk" />
        </intent-filter>
    </activity>
</application>
```

Thêm receiver để nhận thông báo từ Firebase:

```xml
<application>
    <!--Các phần khác-->
    <receiver android:name="vn.vihat.omicall.omisdk.receiver.FirebaseMessageReceiver"
        android:enabled="true"
        android:exported="true"
        android:foregroundServiceType="remoteMessaging"
        android:permission="com.google.android.c2dm.permission.SEND"
        tools:replace="android:exported">
        <intent-filter>
            <action android:name="com.google.android.c2dm.intent.RECEIVE" />
        </intent-filter>
    </receiver>
</application>
```

Thêm service để hiển thị thông báo:

```xml
<application>
    <!--Các phần khác-->
    <service android:name="vn.vihat.omicall.omisdk.service.NotificationService"
        android:enabled="true"
        android:exported="false" />
</application>
```

### Cấu hình firebase:

Thêm file `google-services.json` vào thư mục `app` của dự án.

### Khởi tạo OmiClient:

Bạn có thể khởi tạo OmiClient trong `onCreate` của `Application` và các `Activity` hoặc bất kỳ nơi nào bạn
muốn gọi API của OmiSDK.

```kotlin
val omiClient = OmiClient.getInstance(applicationContext)
```
Tham số truyền vào:
- `context`: Context của ứng dụng (thường là `applicationContext`)
- `needRegister`: Có cần kết nối tổng đài ngay khi khởi tạo hay không (mặc định là `true`)

**Lưu ý:** Trong Application, bạn cần truyền vào needRegister = false

### Thêm OmiClient vào lifecycle trong Application để theo dõi trạng thái của ứng dụng:

```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // QUAN TRỌNG: Thực hiện database maintenance để tránh crash SQLiteFullException
        try {
            DatabaseMaintenanceHelper.performSafeMaintenance(applicationContext)
            Log.i("App", "Database maintenance completed successfully")
        } catch (e: Exception) {
            Log.e("App", "Database maintenance failed (non-critical)", e)
        }

        val omiClient = OmiClient.getInstance(applicationContext, false)
        ProcessLifecycleOwner.get().lifecycle.addObserver(omiClient)
    }
}
```

### Config push notification:

Config push notification 1 lần hoặc bất kỳ lúc nào bạn muốn cập nhật cấu hình

```kotlin
omiClient.configPushNotification(
    showUUID = false,
    showMissedCall = true,
    inboundChannelId = "inbound_calls_channel",
    inboundChannelName = "Inbound Calls Channel",
    missedChannelId = "missed_calls_channel",
    missedChannelName = "Missed Calls Channel",
    notificationIcon = "ic_call_status_inbound",
    notificationColor = "#F95454",
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
```

Các tham số truyền vào:

- `showUUID`: Hiển thị UUID của cuộc gọi
- `showMissedCall`: Hiển thị thông báo cuộc gọi nhỡ
- `buttonAccept`: Tên của icon hiển thị ở nút chấp nhận cuộc gọi, được đặt trong thư mục `res/drawable`
- `buttonDecline`: Tên của icon hiển thị ở nút từ chối cuộc gọi, được đặt trong thư mục `res/drawable`
- `notificationIcon`: Tên của icon hiển thị ở thông báo, được đặt trong thư mục `res/drawable`
- `notificationColor`: Màu của icon thông báo (hex color, ví dụ: "#F95454")
- `notificationMissedCallPrefix`: Tiền tố của thông báo cuộc gọi nhỡ
- `inboundChannelId`: ID của channel hiển thị thông báo cuộc gọi đến
- `inboundChannelName`: Tên của channel hiển thị thông báo cuộc gọi đến
- `missedChannelId`: ID của channel hiển thị thông báo cuộc gọi nhỡ
- `missedChannelName`: Tên của channel hiển thị thông báo cuộc gọi nhỡ
- `videoCallText`: Text hiển thị cho cuộc gọi video trên thông báo cuộc gọi đến
- `internalCallText`: Text hiển thị cho cuộc gọi nội bộ trên thông báo cuộc gọi đến
- `inboundCallText`: Text hiển thị cho cuộc gọi đến trên thông báo cuộc gọi đến
- `unknownContactText`: Text hiển thị cho cuộc gọi từ số không xác định trên thông báo cuộc gọi đến
- `representName`: Tên đại diện cho người gọi, nếu truyền vào sẽ hiển thị tên đại diện thay vì tên người gọi / số điện thoại
- `useIntentFilter`: Sử dụng intent filter để nhận intent vào activity của bạn

## Bước 3: Sử dụng OmiSDK

Bạn có thể sử dụng OmiSDK ở bất kỳ nơi nào bạn muốn, nhưng trước tiên cần [khởi tạo OmiClient](#khởi-tạo-omiclient) như đã
nói ở trên, sau đó bạn có thể sử dụng các API của OmiClient để thực hiện các chức năng như gọi điện, nhận cuộc gọi, kết thúc cuộc gọi, ...

Ngoài ra, bạn cũng cần implement interface `OmiListener` để nhận các sự kiện từ OmiSDK.


### Đăng ký thông tin thiết bị

Khi đăng nhập app, bạn cần phải đăng ký thông tin thiết bị, fcm token và project id của firebase để nhận thông báo từ OmiSDK.

**Lưu ý 1:** Đăng ký thông tin thiết bị khác với [Kết nối tổng đài](#kết-nối-tổng-đài), bạn chỉ cần đăng ký thông tin thiết bị 1 lần duy nhất khi đăng nhập app.

**Lưu ý 2:** Bạn nên cấp quyền ghi âm và camera trước khi đăng ký thông tin thiết bị để tránh việc bị lỗi khi khởi tạo service do chính sách mới của Google (Android 14+).

Có 2 cách để đăng ký thông tin thiết bị:

#### Đăng ký bằng api key:

```kotlin
lifecycleScope.launch {
    val result = OmiClient.registerWithApiKey(
        apiKey,
        userName,
        userPhone,
        sipUuid,
        isVideoCall,
        firebaseToken,
    )
    if (result) {
        // Đăng ký thành công
    } else {
        // Đăng ký thất bại
    }
}
```
Các tham số truyền vào:
- `apiKey`: API key
- `userName`: Số nội bộ của người dùng
- `uuid`: UUID của người dùng
- `phone`: Số điện thoại cần login (dùng để định danh người dùng)
- `isVideo`: Có dùng video call hay không
- `firebaseToken`: Token của Firebase

Trả về:
- `true`: Đăng ký thành công
- `false`: Đăng ký thất bại

#### Đăng ký bằng password:

```kotlin
lifecycleScope.launch {
    val result = OmiClient.register(
        sipUser,
        sipPassword,
        sipRealm,
        isVideoCall,
        firebaseToken,
        projectId = "your_firebase_project_id"
    )
    if (result) {
        // Đăng ký thành công
    } else {
        // Đăng ký thất bại
    }
}
```

Các tham số truyền vào:
- `sipUser`: Số nội bộ của người dùng
- `sipPassword`: Mật khẩu của người dùng
- `sipRealm`: Realm của người dùng
- `isVideo`: Có dùng video call hay không
- `firebaseToken`: Token của Firebase
- `projectId`: ID của project Firebase

Trả về:
- `true`: Đăng ký thành công
- `false`: Đăng ký thất bại

**Lưu ý:** cả 2 phương thức đăng ký đều là suspend function, bạn cần gọi nó trong 1 coroutine hoặc 1 thread khác để tránh block main thread.

### Kết nối tổng đài

Việc kết nối tổng đài sẽ được tự động thực hiện khi bạn thực hiện cuộc gọi hoặc nhận được thông báo có cuộc gọi đến thông qua FCM.
OmiSDK tự quản lý việc khởi tạo service và kết nối tổng đài, và sẽ tự động ngắt kết nối khi kết thúc cuộc gọi để tiết kiệm tài nguyên.
Kết quả kết nối tổng đài sẽ được trả về thông qua interface listener `OmiListener` mà bạn đã implement với phương thức `onRegisterCompleted`.

### Lắng nghe sự kiện từ OmiSDK

Để lắng nghe sự kiện từ OmiSDK, bạn cần implement interface `OmiListener` và gán nó cho OmiClient:
Có 2 cách để đăng ký lắng nghe sự kiện từ OmiSDK:

#### implement interface `OmiListener`:
```kotlin
class CallingActivity : AppCompatActivity(), OmiListener {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // init omi client
        omiClient = OmiClient.getInstance(applicationContext)
        // add listener
        omiClient.addCallStateListener(this)
    }

    // override các phương thức của OmiListener
}
```

#### Sử dụng lambda:
```kotlin
class CallingActivity : AppCompatActivity() {

    private val omiListener = object : OmiListener {

        override fun onUpdatedPushToken(isSuccess: Boolean) {
            // Handle push token update
        }

        override fun onSwitchBoardAnswer(sip: String) {
            // Handle switchboard answer
        }

        override fun onRegisterCompleted(statusCode: Int) {
            // Handle register completion
        }

        override fun onFcmReceived(uuid: String, userName: String, avatar: String) {
            // Handle FCM received
        }

        override fun incomingReceived(callerId: Int?, phoneNumber: String?, isVideo: Boolean?) {
            // Handle incoming call
        }

        override fun onOutgoingStarted(callerId: Int, phoneNumber: String?, isVideo: Boolean?) {
            // Handle outgoing started
        }

        override fun onRinging(callerId: Int, transactionId: String?) {
            // Handle ringing
        }

        override fun onConnecting() {
            // Handle connecting
        }

        override fun onCallEstablished(
            callerId: Int,
            phoneNumber: String?,
            isVideo: Boolean?,
            startTime: Long,
            transactionId: String?
        ) {
            // Handle call established
        }

        override fun networkHealth(stat: Map<String, *>, quality: Int) {
            // Handle network health
        }

        override fun onHold(isHold: Boolean) {
            // Handle hold
        }

        override fun onMuted(isMuted: Boolean) {
            // Handle mute
        }

        override fun onAudioChanged(audioInfo: Map<String, Any>) {
            // Handle audio changed
        }

        override fun onVideoSize(width: Int, height: Int) {
            // Handle video size changed
        }

        override fun onCallEnd(callInfo: MutableMap<String, Any?>, statusCode: Int) {
            // Handle call end
        }

        override fun onDescriptionError() {
            // Handle error
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // init omi client
        omiClient = OmiClient.getInstance(applicationContext)
        // add listener
        omiClient.addCallStateListener(omiListener)
    }
}
```

#### Huỷ lắng nghe sự kiện từ OmiSDK:
```kotlin
override fun onDestroy() {
    super.onDestroy()
    omiClient.removeCallStateListener(this)
}
```

#### Các phương thức của OmiListener:
- `onUpdatedPushToken(isSuccess: Boolean)`: Kết quả đăng ký thông tin thiết bị
  - `isSuccess`: Cập nhật token thành công hay không

- `onRegisterCompleted(statusCode: Int)`: Kết quả kết nối tổng đài
  - `statusCode`: Trạng thái đăng ký (200: Đăng ký thành công, khác: Đăng ký thất bại)

- `onFcmReceived(uuid: String, userName: String, avatar: String)`: Nhận thông tin cuộc gọi từ FCM
  - `uuid`: UUID của người gọi
  - `userName`: Tên của người gọi
  - `avatar`: Avatar của người gọi

- `incomingReceived(callerId: Int?, phoneNumber: String?, isVideo: Boolean?)`: Nhận cuộc gọi đến
  - `callerId`: ID của người gọi
  - `phoneNumber`: Số điện thoại của người gọi
  - `isVideo`: Có phải cuộc gọi video hay không

- `onOutgoingStarted(callerId: Int, phoneNumber: String?, isVideo: Boolean?)`: Bắt đầu cuộc gọi đi
  - `callerId`: ID của người gọi
  - `phoneNumber`: Số điện thoại của người gọi
  - `isVideo`: Có phải cuộc gọi video hay không

- `onRinging(callerId: Int, transactionId: String?)`: Bắt đầu đổ chuông
  - `callerId`: ID của người gọi
  - `transactionId`: ID của cuộc gọi

- `onConnecting()`: Đang kết nối

- `onCallEstablished(callerId: Int, phoneNumber: String?, isVideo: Boolean?, startTime: Long, transactionId: String?)`: Cuộc gọi đã được thiết lập
  - `callerId`: ID của người gọi
  - `phoneNumber`: Số điện thoại của người gọi
  - `isVideo`: Có phải cuộc gọi video hay không
  - `startTime`: Thời gian bắt đầu cuộc gọi
  - `transactionId`: ID của cuộc gọi

- `networkHealth(stat: Map<String, *>, quality: Int)`: Trạng thái và chất lượng mạng của cuộc gọi
    - `stat`: Thông tin trạng thái mạng
        - long `req`: Thời gian request
        - float `mos`: Mean Opinion Score
        - float `jitter`: Jitter
        - float `latency`: Latency
        - float `ppl`: Packet loss percentage
        - int `lcn`: Số lần trả về mos giống nhau liên tiếp (nếu >= 3 thì có thể mạng không ổn định)
    - `quality`: Chất lượng mạng (0: Tốt, 1: Trung bình, 2: Kém)

- `onHold(isHold: Boolean)`: Trạng thái hold
- `onMuted(isMuted: Boolean)`: Trạng thái mute
- `onAudioChanged(audioInfo: Map<String, Any>)`: Thông tin audio
  - `audioInfo`: Thông tin audio
    - int `type`: Loại audio. Xem class `AudioDeviceInfo`
    - string `name`: Tên audio

- `onVideoSize(width: Int, height: Int)`: Kích thước video remote thay đổi (dùng để điều chỉnh aspect ratio)

- `onCallEnd(callInfo: MutableMap<String, Any?>, statusCode: Int)`: Kết thúc cuộc gọi
  - `callInfo`: Thông tin cuộc gọi
    - string `transaction_id`: ID của cuộc gọi
    - string `direction`: Hướng cuộc gọi
    - string `destination_number`: Số điện thoại đích
    - long `time_start_to_answer`: Thời gian bắt đầu cuộc gọi (tính bằng giây)
    - long `time_end`: Thời gian kết thúc cuộc gọi (tính bằng giây)
    - string `disposition`: Trạng thái cuộc gọi (`answered`, `no_answer`)
  - `statusCode`: Mã lỗi kết thúc cuộc gọi (xem [Mã lỗi kết thúc cuộc gọi](#mã-lỗi-kết-thúc-cuộc-gọi))

### Gọi điện

Khi bạn gọi hàm `startCall`, OmiSDK sẽ kiểm tra các điều kiện trước khi thực hiện cuộc gọi, nếu có lỗi sẽ trả về enum `OmiStartCallStatus` tương ứng.
Nếu chưa kết nối tổng đài, OmiSDK sẽ tự động kết nối và thực hiện cuộc gọi sau khi kết nối thành công.

**Lưu ý:** Hàm `startCall` là suspend function, bạn cần gọi nó trong 1 coroutine.

```kotlin
lifecycleScope.launch {
    val result = omiClient.startCall(
        phoneNumber = "0123456789",
        isVideo = false,
        name = "",
        avatar = ""
    )

    when (result) {
        OmiStartCallStatus.SUCCESS -> {
            // Gọi thành công, chuyển đến CallingActivity
            val intent = Intent(context, CallingActivity::class.java)
            intent.putExtra(SipServiceConstants.PARAM_NUMBER, phoneNumber)
            intent.putExtra(SipServiceConstants.PARAM_IS_VIDEO, isVideo)
            startActivity(intent)
        }
        OmiStartCallStatus.SWITCHBOARD_REGISTERING -> {
            // Đang kết nối tổng đài, cuộc gọi sẽ được thực hiện sau khi kết nối thành công
            // Có thể chuyển đến CallingActivity để hiển thị trạng thái connecting
        }
        else -> {
            // Xử lý lỗi
            Toast.makeText(context, "Start call have some errors: $result", Toast.LENGTH_SHORT).show()
        }
    }
}
```

Các tham số truyền vào:
- `phoneNumber`: Số điện thoại hoặc số nội bộ của người cần gọi
- `isVideo`: Có gọi video hay không
- `name`: Tên của người cần gọi nếu có (hiển thị trên giao diện)
- `avatar`: Avatar của người cần gọi nếu có (hiển thị trên giao diện)

Trả về: enum `OmiStartCallStatus`

- `ALREADY_IN_CALL`: Đang trong cuộc gọi khác
- `INVALID_UUID`: UUID không hợp lệ
- `EMPTY_REMOTE_NUMBER`: Số điện thoại không hợp lệ
- `CAN_NOT_CALL_YOURSELF`: Không thể gọi cho chính mình
- `SWITCHBOARD_NOT_CONNECTED`: Chưa kết nối tổng đài
- `MISSING_AUDIO_PERMISSION`: Thiếu quyền ghi âm
- `MISSING_VIDEO_PERMISSION`: Thiếu quyền camera
- `SWITCHBOARD_REGISTERING`: Đang kết nối tổng đài
- `SUCCESS`: Gọi thành công

### Nhận cuộc gọi

Khi có cuộc gọi đến, nếu ứng dụng đang chạy, OmiSDK sẽ tự động hiển thị giao diện cuộc gọi đến (Activity Calling mà bạn đặt intent filter trong [Cấu hình tệp Manifest](#cấu-hình-tệp-androidmanifestxml)), nếu ứng dụng không chạy, OmiSDK sẽ hiển thị thông báo.
Khi bạn nhấn vào thông báo, Omi SDK sẽ tự động mở ứng dụng và hiển thị giao diện cuộc gọi đến.

Tại activity hiển thị cuộc gọi đến, bạn sẽ nhận được intent với action `android.intent.action.CALL` và data `omisdk://incoming_call`:

```kotlin
class CallingActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_calling)

        // Lấy thông tin từ intent
        isIncoming = intent!!.getBooleanExtra(SipServiceConstants.ACTION_IS_INCOMING_CALL, false)
        remoteNumber = intent.getStringExtra(SipServiceConstants.PARAM_NUMBER) ?: ""
        remoteName = intent.getStringExtra(SipServiceConstants.PARAM_USERNAME) ?: ""
        remoteAvatar = intent.getStringExtra(SipServiceConstants.PARAM_AVATAR) ?: ""
        isVideoCall = intent.getBooleanExtra(SipServiceConstants.PARAM_IS_VIDEO, false)
        transactionId = intent.getStringExtra(SipServiceConstants.PARAM_UUID) ?: ""
        isAcceptedCall = intent.getBooleanExtra(SipServiceConstants.ACTION_ACCEPT_INCOMING_CALL, false)
    }
}
```
Trong đó:
- `isIncoming`: Có phải cuộc gọi đến hay không
- `remoteNumber`: Số điện thoại của người gọi
- `remoteName`: Tên của người gọi (nếu có)
- `remoteAvatar`: Avatar của người gọi (nếu có)
- `isVideoCall`: Có phải cuộc gọi video hay không
- `transactionId`: ID của cuộc gọi
- `isAcceptedCall`: Có phải cuộc gọi đã được chấp nhận hay không (Khi người gọi nhấn nút chấp nhận cuộc gọi từ thông báo)
  - Nếu `isAcceptedCall` là `true`, bạn cần chủ động gọi hàm `pickup` để chấp nhận cuộc gọi ngay lập tức.
  - Nếu `isAcceptedCall` là `false`, bạn có thể chờ người dùng chấp nhận cuộc gọi hoặc từ chối cuộc gọi.

### Mở lại giao diện cuộc gọi sau khi ứng dụng bị kill

Khi bắt đầu cuộc gọi, OmiSDK sẽ hiển thị 1 thông báo "Cuộc gọi đang diễn ra" để có thể kích hoạt được foreground service, giúp ứng dụng không bị kill khi bị kill bởi hệ thống.
Trong trường hợp ứng dụng bị kill bởi người dùng (xoá app khỏi stack recent app), cuộc gọi vẫn được duy trì, lúc này nếu muốn, người dùng có thể mở lại giao diện cuộc gọi bằng cách click vào thông báo "Cuộc gọi đang diễn ra".

Khi click vào thông báo "Cuộc gọi đang diễn ra", OmiSDK sẽ tự động mở lại giao diện cuộc gọi, đồng thời bạn cũng có thể lấy thông tin cuộc gọi từ intent:

```kotlin
isReopenCall = intent.getBooleanExtra(SipServiceConstants.ACTION_REOPEN_CALL, false)
startTime = intent!!.getLongExtra(
    SipServiceConstants.PARAM_CONNECT_TIMESTAMP,
    System.currentTimeMillis()
)
```

Trong đó:
- `isReopenCall`: Có phải mở lại cuộc gọi sau khi ứng dụng bị kill hay không
- `startTime`: Thời gian bắt đầu cuộc gọi

Bạn có thể dùng util [getActiveCall](#một-số-hàm-tiện-ích) để lấy thông tin cuộc gọi đang diễn ra.


### Chấp nhận cuộc gọi

Khi bạn nhận được cuộc gọi đến, bạn cần chấp nhận cuộc gọi bằng cách gọi hàm `pickUp()`:

```kotlin
omiClient.pickUp()
```
Sau khi gọi hàm này, OmiSDK sẽ thực hiện cuộc gọi và trả về kết quả thông qua interface `OmiListener` với các phương thức `onConnecting` và `onCallEstablished`.

### Kết thúc cuộc gọi

Khi bạn muốn kết thúc cuộc gọi, nếu là cuộc gọi đến và chưa chấp nhận cuộc gọi, bạn có thể từ chối cuộc gọi bằng cách gọi hàm `decline()`, nếu cuộc gọi đã được chấp nhận hoặc là cuộc gọi đi, bạn có thể kết thúc cuộc gọi bằng cách gọi hàm `hangUp()`:

```kotlin
omiClient.decline()
omiClient.hangUp()
```

Sau khi gọi hàm này, OmiSDK sẽ kết thúc cuộc gọi và trả về kết quả thông qua interface `OmiListener` với phương thức `onCallEnd`.

### Các phương thức khác
- `omiClient.getAudioOutputs()`: Lấy danh sách audio output
  - Trả về: List<String, map> danh sách audio output gồm type và name, xem class `AudioDeviceInfo`
- `omiClient.setupLocalVideoFeed(surface)`: Setup local video feed, truyền vào surface của view
- `omiClient.setupIncomingVideoFeed(surface)`: Setup incoming video feed, truyền vào surface của view
- `omiClient.stopVideoPreview()`: Dừng video preview
- `omiClient.sendDtmf(string)`: Gửi DTMF, chỉ hỗ trợ 0-9, *, #
- `omiClient.toggleCamera()`: Tắt/mở camera
- `omiClient.switchCamera()`: Chuyển camera trước/sau
- `omiClient.setAudio(type)`: Chọn audio output (type: Int, xem class `AudioDeviceInfo`)
- `omiClient.toggleSpeaker()`: Chuyển loa ngoài/loa trong
- `omiClient.toggleMute()`: Mute/Unmute
- `omiClient.forwardCallTo(sip)`: Chuyển cuộc gọi đến số nội bộ khác
- `omiClient.getCurrentCallInfo()`: Lấy thông tin cuộc gọi hiện tại, trả về map chứa thông tin cuộc gọi
  - `callerNumber`: Số điện thoại của khách hàng
  - `status`: Trạng thái cuộc gọi (xem [Status Call](#status-call))
  - `sipNumber`: Số nội bộ của người gọi
  - `muted`: Trạng thái mute
  - `isVideo`: Có phải cuộc gọi video hay không
  - `startTime`: Thời gian bắt đầu cuộc gọi
  - `cameraStatus`: Trạng thái camera
  - `isIncoming`: Có phải cuộc gọi đến hay không

  Cũng có thể sử dụng util [getActiveCall](#một-số-hàm-tiện-ích) để lấy thông tin cuộc gọi đang diễn ra
- `omiClient.getSipRealm()`: Lấy sip realm hiện tại
- `omiClient.getSipUser()`: Lấy sip user hiện tại
- `omiClient.getSipTransport()`: Lấy transport hiện tại (AUTO, TCP, UDP)
- `omiClient.updateSipTransport(transport)`: Cập nhật transport (OmiSipTransport.AUTO, OmiSipTransport.TCP, OmiSipTransport.UDP)
- `omiClient.logout()`: Đăng xuất (clear session)

### Status Call
- 0: Cuộc gọi chưa bắt đầu
- 1: Đang gọi đi
- 2: Cuộc gọi đến
- 3: Cuộc gọi đang đổ chuông
- 4: Đang kết nối
- 5: Cuộc gọi đã thiết lập
- 6: Cuộc gọi kết thúc

### Mã lỗi kết thúc cuộc gọi
- `200`: Kết thúc cuộc gọi bình thường
- `408`: Hết thời gian cuộc gọi
- `480`: Tạm thời không khả dụng
- `486`: Bận
- `487`: Cuộc gọi bị hủy
- `500`: Lỗi server
- `503`: Server không khả dụng
- `600`: Cuộc gọi bị từ chối
- `601`: Cuộc gọi bị kết thúc bởi khách hàng
- `602`: Cuộc gọi đã được nghe / kết thúc bởi nhân viên khác
- `603`: Cuộc gọi bị từ chối
- `850`: Vượt quá hạn mức cuộc gọi đồng thời
- `851`: Vượt quá hạn mức cuộc gọi
- `852`: Chưa được gán gói dịch vụ, vui lòng liên hệ nhà cung cấp
- `853`: Số nội bộ đã bị tắt hoạt động
- `854`: Thuê bao này trong danh sách DNC
- `855`: Vượt quá số lượng cuộc gọi cho phép của gói dùng thử
- `856`: Vượt quá số phút cho phép của gói dùng thử
- `857`: Thuê bao đã bị chặn trong cấu hình
- `858`: Đầu số không xác định hoặc chưa được thiết lập
- `859`: Không có đầu số khả dụng cho hướng Viettel, vui lòng liên hệ nhà cung cấp.
- `860`: Không có đầu số khả dụng cho hướng Vinaphone, vui lòng liên hệ nhà cung cấp.
- `861`: Không có đầu số khả dụng cho hướng Mobifone, vui lòng liên hệ nhà cung cấp.
- `862`: Đầu số tạm khóa hướng Viettel
- `863`: Đầu số tạm khóa hướng Vinaphone
- `864`: Đầu số tạm khóa hướng Mobifone
- `865`: Cuộc gọi quảng cáo ngoài khung giờ cho phép, vui lòng gọi lại sau

### Một số hàm tiện ích
- `AppUtils.isInternalPhoneNumber(remoteNumber)`: Kiểm tra xem số điện thoại có phải là số nội bộ hay không
- `AppUtils.mapOutputs(context, outputs, stringReceiver, stringSpeaker, stringHeadset)`: Map danh sách audio output thành list `MenuSelectorModel` để hiển thị lên giao diện chọn audio output
  - `context`: Context
  - `outputs`: Danh sách audio output, được lấy từ `omiClient.getAudioOutputs()`
  - `stringReceiver`: Tên audio output cho loa ngoài
  - `stringSpeaker`: Tên audio output cho loa trong
  - `stringHeadset`: Tên audio output cho tai nghe
  - Trả về: List `MenuSelectorModel` chứa danh sách audio output
- `AppUtils.getFormatDate(format, timestamp)`: Format timestamp thành string theo format
- `AppUtils.postDelay(callback)`: Gọi callback sau 1 khoảng thời gian
- `Utils.getActiveCall(applicationContext)`: Lấy thông tin cuộc gọi đang diễn ra
  - `applicationContext`: Context của ứng dụng
  - Trả về: Object `OmiActiveCall` chứa thông tin cuộc gọi đang diễn ra
- `Utils.securityCustomerData(remoteNumber, canSeePhoneNumber)`: Ẩn/mã hóa số điện thoại
  - `remoteNumber`: Số điện thoại cần ẩn/mã hóa
  - `canSeePhoneNumber`: Có thể xem số điện thoại hay không
  - Trả về: String số điện thoại đã ẩn/mã hóa
- `Utils.saveActiveCall(context, call)`: Lưu thông tin cuộc gọi đang diễn ra (truyền null để xóa)
- `DatabaseMaintenanceHelper.performSafeMaintenance(context)`: Thực hiện database maintenance để tránh crash SQLiteFullException
- `ScaleManager.adjustAspectRatio(textureView, viewSize, videoSize)`: Điều chỉnh aspect ratio cho video view

## Troubleshooting

### Lỗi SQLiteFullException
Nếu gặp lỗi `SQLiteFullException` hoặc database bị đầy, hãy đảm bảo gọi `DatabaseMaintenanceHelper.performSafeMaintenance(applicationContext)` trong `Application.onCreate()`.

### Lỗi ForegroundServiceStartNotAllowedException (Android 14+)
Từ Android 14, Google yêu cầu phải có quyền `RECORD_AUDIO` trước khi khởi tạo foreground service với type `microphone`. Hãy đảm bảo yêu cầu quyền `RECORD_AUDIO` trước khi đăng ký thông tin thiết bị.

### Không nhận được cuộc gọi khi app ở background
Kiểm tra các quyền sau đã được cấp:
- `FOREGROUND_SERVICE_MICROPHONE`
- `FOREGROUND_SERVICE_PHONE_CALL`
- `RECORD_AUDIO`
- `POST_NOTIFICATIONS` (Android 13+)

### Video call không hiển thị đúng aspect ratio
Sử dụng `ScaleManager.adjustAspectRatio()` trong callback `onVideoSize()` để điều chỉnh aspect ratio động khi kích thước video thay đổi.
