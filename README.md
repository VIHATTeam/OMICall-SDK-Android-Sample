# Hướng dẫn tích hợp OmiSDK vào dự án Android

## Giới thiệu

OmiSDK là một SDK mạnh mẽ giúp bạn tích hợp các tính năng gọi điện vào ứng dụng Android của mình.
Dưới đây là các bước để tích hợp OmiSDK vào dự án của bạn.


## Bước 1: Thêm kho lưu trữ và phụ thuộc

### Mở tệp `app/build.gradle.kts` và thêm phụ thuộc vào OmiSDK:

```gradle
dependencies {
    // ...
    api "vn.vihat.omicall:omi-sdk:2.3.8"
    //...
}
```

Thêm các thư viện cần thiết (nếu khi run project bị lỗi thiếu thư viện):

```gradle
dependencies {
    //...

    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation "androidx.security:security-crypto:1.1.0-alpha06"
   
    implementation(platform("com.google.firebase:firebase-bom:33.1.0"))
    implementation "com.google.firebase:firebase-messaging"

    implementation "com.squareup.okhttp3:logging-interceptor:$okhttp_version"
    implementation "com.squareup.retrofit2:converter-gson:2.9.0"
    implementation("com.squareup.retrofit2:retrofit:2.9.0") {
        exclude module: "okhttp"
    }

    //...
}
```

### Mở tệp `settings.gradle.kts` và thêm kho lưu trữ của OmiSDK:

```gradle
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
  //...
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

Thêm quyền truy cập internet, camera và microphone vào tệp `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.CAMERA" />
```

Thêm intent filter cho activity hiển thị cuộc gọi:

```xml

<application>
    <!--Các phần khác-->
    <activity android:name=".CallingActivity" android:alwaysRetainTaskState="true"
        android:largeHeap="true" android:showOnLockScreen="true"
        android:theme="@style/Theme.OMICall" android:exported="true">
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
        android:enabled="true" android:exported="true"
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
        android:enabled="true" android:exported="false" />
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
- `needRegister`: Có cần kết nối tổng đài ngay khi khởi tạo hay không (mặc định là `true)
**Lưu ý:** Trong Application, bạn cần truyền vào needRegister = false

### Thêm OmiClient vào lifecycle trong Application để theo dõi trạng thái của ứng dụng:

```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        //...
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
    videoCallText = "Gọi Video",
    internalCallText = "Gọi nội bộ",
    inboundCallText = "Cuộc gọi đến",
    unknownContactText = "Không xác định",

    )
```

Các tham số truyền vào:

- `showUUID`: Hiển thị UUID của cuộc gọi
- `showMissedCall`: Hiển thị thông báo cuộc gọi nhỡ
- `buttonAccept`: Tên của icon hiển thị ở nút chấp nhận cuộc gọi, được đặt trong thư
  mục `res/drawable`
- `buttonDecline`: Tên của icon hiển thị ở nút từ chối cuộc gọi, được đặt trong thư
  mục `res/drawable`
- `notificationIcon`: Tên của icon hiển thị ở thông báo, được đặt trong thư mục `res/drawable`
- `notificationMissedCallPrefix`: Tiền tố của thông báo cuộc gọi nhỡ
- `inboundChannelId`: ID của channel hiển thị thông báo cuộc gọi đến
- `inboundChannelName`: Tên của channel hiển thị thông báo cuộc gọi đến
- `missedChannelId`: ID của channel hiển thị thông báo cuộc gọi nhỡ
- `missedChannelName`: Tên của channel hiển thị thông báo cuộc gọi nhỡ
- `videoCallText`: Text hiển thị cho cuộc gọi video trên thông báo cuộc gọi đến
- `internalCallText`: Text hiển thị cho cuộc gọi nội bộ trên thông báo cuộc gọi đến
- `inboundCallText`: Text hiển thị cho cuộc gọi đến trên thông báo cuộc gọi đến
- `unknownContactText`: Text hiển thị cho cuộc gọi từ số không xác định trên thông báo cuộc gọi đến
- `representName`: Tên đại diện cho người gọi, nếu truyền vào sẽ hiển thị tên đại diện thay vì tên
  người gọi / số điện thoại
- `useIntentFilter`: Sử dụng intent filter để nhận intent vào activity của bạn

- `notificationAvatar`: Deprecated
- `receiverText`: Deprecated
- `speakerText`: Deprecated
- `headsetText`: Deprecated
- `callingText`: Deprecated
- `incomingCallText`: Deprecated
- `ringingText`: Deprecated
- `connectingText`: Deprecated
- `endCallText`: Deprecated
- `lostConnectionText`: Deprecated
- `callTerminatedText`: Deprecated
- `oldAvatarBaseUrl`: Deprecated
- `newAvatarBaseUrl`: Deprecated
- `ringtone`: Deprecated
- `displayNameType`: Deprecated
- `fullScreenAvatar`: Deprecated
- `fullScreenUserImageSize`: Deprecated
- `fullScreenTextColor`: Deprecated
- `fullscreenBackgroundColor`: Deprecated

## Bước 3: Sử dụng OmiSDK

Bạn có thể sử dụng OmiSDK ở bất kỳ nơi nào bạn muốn, nhưng trước tiên cần [khởi tạo OmiClient](#khởi-tạo-omiclient) như đã
nói ở trên, sau đó bạn có thể sử dụng các API của OmiClient để thực hiện các chức năng như gọi điện, nhận cuộc gọi, kết thúc cuộc gọi, ...

Ngoài ra, bạn cũng cần implement interface `OmiListener` để nhận các sự kiện từ OmiSDK.


### Đăng ký thông tin thiết bị

Khi đăng nhập app, bạn cần phải đăng ký thông tin thiết bị, fcm token và project id của firebase để nhận thông báo từ OmiSDK.

**Lưu ý 1:** Đăng ký thông tin thiết bị khác với [Kết nối tổng đài](#kết-nối-tổng-đài), bạn chỉ cần đăng ký thông tin thiết bị 1 lần duy nhất khi đăng nhập app.

**Lưu ý 2:** Bạn nên cấp quyền ghi âm và camera trước khi đăng ký thông tin thiết bị để tránh việc bị lỗi khi khởi tạo service do chính sách mới của Google.

Có 2 cách để đăng ký thông tin thiết bị:

#### Đăng ký bằng api key:

```kotlin
OmiClient.registerWithApiKey(
    apiKey,
    userName,
    userPhone,
    sipUuid,
    isVideoCall,
    firebaseToken,
    projectId
)
```
Các tham số truyền vào:
- `apiKey`: API key 
- `userName`: Số nội bộ của người dùng
- `uuid`: UUID của người dùng
- `phone`: Số điện thoại cần login (dùng để định danh người dùng)
- `isVideo`: Có dùng video call hay không
- `firebaseToken`: Token của Firebase
- `projectId`: ID của project Firebase

Trả về:
- `true`: Đăng ký thành công
- `false`: Đăng ký thất bại

#### Đăng ký bằng password:

```kotlin
OmiClient.register(
  sipUser,
  sipPassword,
  sipRealm,
  isVideoCall,
  firebaseToken,
  projectId
)
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

**Lưu ý:** cả 2 phương thức đăng ký đều lá suspend function, bạn cần gọi nó trong 1 coroutine hoặc 1 thread khác để tránh block main thread.

### Kết nối tổng đài

Việc kết nối tổng đài sẽ được tự động thực hiện khi bạn thực hiện cuộc gọi hoặc nhận được thông báo có cuộc gọi đến thông qua FCM.
OmiSDK tự quản lý việc khởi tạo service và kết nối tổng đài, và sẽ tự động ngắt kết nối khi kết thúc cuộc gọi để tiết kiệm tài nguyên.
Kết quả kết nối tổng đài sẽ được trả về thông qua interface listener `OmiListener` mà bạn đã implement với phương thức `onRegisterCompleted`.

### Lăng nghe sự kiện từ OmiSDK

Để lăng nghe sự kiện từ OmiSDK, bạn cần implement interface `OmiListener` và gán nó cho OmiClient:
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
  
  //override các phương thức của OmiListener
  
}
```

#### Sử dụng lambda:
```kotlin
class CallingActivity : AppCompatActivity() {

    private val omiListener = object : OmiListener {

      override fun onUpdatedPushToken(isSuccess: Boolean) {
        TODO("Not yet implemented")
      }

      override fun onSwitchBoardAnswer(sip: String) {
        TODO("Not yet implemented")
      }
        
      override fun onRegisterCompleted(statusCode: Int) {
        TODO("Not yet implemented")
      }

      override fun onFcmReceived(uuid: String, userName: String, avatar: String) {
        TODO("Not yet implemented")
      }
        
      override fun incomingReceived(callerId: Int?, phoneNumber: String?, isVideo: Boolean?) {
        TODO("Not yet implemented")
      }

      override fun onOutgoingStarted(callerId: Int, phoneNumber: String?, isVideo: Boolean?) {
        TODO("Not yet implemented")
      }

      override fun onRinging(callerId: Int, transactionId: String?) {
        TODO("Not yet implemented")
      }
      override fun onConnecting() {
        TODO("Not yet implemented")
      }

      override fun onCallEstablished(
        callerId: Int,
        phoneNumber: String?,
        isVideo: Boolean?,
        startTime: Long,
        transactionId: String?
      ) {
        TODO("Not yet implemented")
      }
      
      override fun networkHealth(stat: Map<String, *>, quality: Int) {
        TODO("Not yet implemented")
      }

      override fun onHold(isHold: Boolean) {
        TODO("Not yet implemented")
      }

      override fun onMuted(isMuted: Boolean) {
        TODO("Not yet implemented")
      }
  
      override fun onAudioChanged(audioInfo: Map<String, Any>) {
        TODO("Not yet implemented")
      }

      override fun onVideoSize(width: Int, height: Int) {
        TODO("Not yet implemented")
      }
  
      override fun onCallEnd(callInfo: MutableMap<String, Any?>, statusCode: Int) {
        TODO("Not yet implemented")
      }
  
      override fun onDescriptionError() {
        TODO("Not yet implemented")
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

- `onRegisterCompleted(statusCode: Int)`: Kết quả kêt nối tổng đài
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
        - int `lcn`: Số lần trả về mos giống nhau liên tiếp (nếu > 3 thì có thể mạng không ổn định)
    - `quality`: Chất lượng mạng (0: Tốt, 1: Trung bình, 2: Kém)

- `onHold(isHold: Boolean)`: Trạng thái hold
- `onMuted(isMuted: Boolean)`: Trạng thái mute
- `onAudioChanged(audioInfo: Map<String, Any>)`: Thông tin audio
  - `audioInfo`: Thông tin audio
    - int `type`: Loại audio. Xem class `AudioDeviceInfo`
    - string `name`: Tên audio
  
- `onVideoSize(width: Int, height: Int)`: Kích thước video

- `onCallEnd(callInfo: MutableMap<String, Any?>, statusCode: Int)`: Kết thúc cuộc gọi
  - `callInfo`: Thông tin cuộc gọi
    - string `transaction_id`: ID của cuộc gọi
    - string `direction`: Hướng cuộc gọi
    - string `destination_number`: Số điện thoại đích
    - long `time_start_to_answer`: Thời gian bắt đầu cuộc gọi (tính bằng giây)
    - long `time_end`: Thời gian kết thúc cuộc gọi (tính bằng giây)
    - string `disposition`: Trạng thái cuộc gọi (`answered`, `no_answer`)
  - `statusCode`: Mã lỗi kết thúc cuộc gọi
    - `200`: Kết thúc cuộc gọi bình thường
    - `408`: Hết thời gian cuộc gọi
    - `480`: Tạm thời không khả dụng
    - `486`: Bận
    - `487`: Cuộc gọi bị hủy
    - `500`: Lỗi server
    - `503`: Server không khả dụng
    - `600`: Cuộc gọi bị từ chối
    - `601`: Cuộc gọi bị kết thúc bởi khách hàng
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
  
#### Các phương thức không còn sử dụng:
Chúng tôi sẽ loại bỏ các phương thức sau trong các phiên bản sau vì chúng không còn cần thiết:
- `onDescriptionError()`
- `onSwitchBoardAnswer(sip: String)`

### Gọi điện

Khi bạn gọi hàm `startCall`, OmiSDK sẽ kiểm tra các điều kiện trước khi thực hiện cuộc gọi, nếu có lỗi sẽ trả về enum `OmiStartCallStatus` tương ứng.
Nếu chưa kết nối tổng đài, OmiSDK sẽ tự động kết nối và thực hiện cuộc gọi sau khi kết nối thành công.
Khi việc kết nối tổng đài hoàn tất, OmiSDK sẽ gọi lại phương thức `onRegisterCompleted` của interface `OmiListener` mà bạn đã implement.
Sau đó, OmiSDK sẽ thực hiện cuộc gọi và trả về kết quả thông qua interface `OmiListener` với các phương thức `onOutgoingStarted` và `onRinging

```kotlin
omiClient.startCall(
  phoneNumber = "",
  isVideo = false,
  name = "",
  avatar = ""
)
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
      val intent = intent
      if (intent.action == "android.intent.action.CALL" && intent.data?.scheme == "omisdk" && intent.data?.host == "incoming_call") {
        isIncoming = intent!!.getBooleanExtra(SipServiceConstants.ACTION_IS_INCOMING_CALL, false)
        remoteNumber = intent.getStringExtra(SipServiceConstants.PARAM_NUMBER) ?: ""
        remoteName = intent.getStringExtra(SipServiceConstants.PARAM_USERNAME) ?: ""
        remoteAvatar = intent.getStringExtra(SipServiceConstants.PARAM_AVATAR) ?: ""
        isVideoCall = intent.getBooleanExtra(SipServiceConstants.PARAM_IS_VIDEO, false)
        transactionId = intent.getStringExtra(SipServiceConstants.PARAM_UUID) ?: ""
        isAcceptedCall = intent.getBooleanExtra(SipServiceConstants.ACTION_ACCEPT_INCOMING_CALL, false)
      }
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
  - Nếu `isAcceptedCall` là `true`, bạn cần chủ động gọi hàm `acceptCall` để chấp nhận cuộc gọi ngay lập tức.
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

Khi bạn nhận được cuộc gọi đến, bạn cần chấp nhận cuộc gọi bằng cách gọi hàm `pickup()`:

```kotlin
omiClient.pickup()
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
  - `callerNumber`: Số điện thoại của khách hàng
  - `status`: Trạng thái cuộc gọi (xem [Call Status](#status-call))
  - `sipNumber`: Số nội bộ của người gọi
  - `muted`: Trạng thái mute
  - `isVideo`: Có phải cuộc gọi video hay không
  - `startTime`: Thời gian bắt đầu cuộc gọi
  - `cameraStatus`: Trạng thái camera
  - `isIncoming`: Có phải cuộc gọi đến hay không
  Cũng có thể sử dụng util [getActiveCall](#một-số-hàm-tiện-ích) để lấy thông tin cuộc gọi đang diễn ra

### Status call
- 0: Cuộc gọi chưa bắt đầu
- 1: Đang gọi đi
- 2: Cuộc gọi đến
- 3: Cuộc gọi đang đổ chuông
- 4: Đang kết nối
- 5: Cuộc gọi đã thiết lập
- 6: Cuộc gọi kết thúc

### Một số hàm tiện ích
- `AppUtils.isInternalPhoneNumber(remoteNumber)`: Kiểm tra xem số điện thoại có phải là số nội bộ hay không
- `AppUtils.mapOutputs(
     context: Context,
     outputs: List<Map<String, Any>>,
     stringReceiver: String,
     stringSpeaker: String,
     stringHeadset: String)`: Map danh sách audio output thành list `MenuSelectorModel` để hiển thị lên giao diện chọn audio output
  - `context`: Context
  - `outputs`: Danh sách audio output, được lấy từ `omiClient.getAudioOutputs()`
  - `stringReceiver`: Tên audio output cho loa ngoài
  - `stringSpeaker`: Tên audio output cho loa trong
  - `stringHeadset`: Tên audio output cho tai nghe
  - Trả về: List `MenuSelectorModel` chứa danh sách audio output 
- `Utils.getActiveCall(applicationContext)`: Lấy thông tin cuộc gọi đang diễn ra
  - `applicationContext`: Context của ứng dụng
  - Trả về: Object `OmiActiveCall` chứa thông tin cuộc gọi đang diễn ra
- `Utils.securityCustomerData(remoteNumber, canSeePhoneNumber)`: Ẩn/mã hóa số điện thoại
  - `remoteNumber`: Số điện thoại cần ẩn/mã hóa
  - `canSeePhoneNumber`: Có thể xem số điện thoại hay không
  - Trả về: String số điện thoại đã ẩn/mã hóa















