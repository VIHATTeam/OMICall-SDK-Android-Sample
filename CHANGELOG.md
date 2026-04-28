# Change Log

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](http://keepachangelog.com/)
and this project adheres to [Semantic Versioning](http://semver.org/).

# 2.6.9 [10/04/2026]

## Registration Reliability Fix (Xiaomi MIUI / slow devices)

- **Fix premature 503 timeout on Xiaomi MIUI and other slow OEMs**
  - Root cause: PICKUP-FIX retry loop shared a single `totalWaitTime` counter across 2 phases — waiting for `SipService.isRunning` (WorkManager delay) and waiting for `isPjsipReady()`. On Xiaomi MIUI, `transportCreate(UDP)` blocks 2-3s due to network firewall init on fresh install, leaving only ~1.7s for OMISIP check → premature 503 callback before stack finished starting
  - Fix: Split into two independent timers — `onCreateWait` (4s budget for WorkManager) and `pjsipWait` (8s independent budget for OMISIP stack init). OMISIP budget never consumed by WorkManager delay
  - Files: `OmiClient.kt`

- **Fix NOTIFICATION-TIMEOUT shutting down service during registration**
  - Root cause: `mHasPendingRegistration` flag was only set inside `handleSetAccount()`, which runs on a WorkManager worker thread. The 7s NOTIFICATION-TIMEOUT could fire in the 50-800ms gap between `SipServiceCommand.setAccount()` enqueue and WorkManager execution, seeing no pending activity and killing the service mid-init → OMISIP forced to restart from scratch adding 2-3s delay
  - Fix: Set `mHasPendingRegistration=true` immediately at `onStartCommand()` when `ACTION_SET_ACCOUNT` is received, before dispatching to worker thread
  - Files: `SipService.java`

- **Fix `mHasPendingRegistration` cleared prematurely during SIP auth handshake**
  - Root cause: `onRegistrationStateChanged()` cleared the flag on every `onRegState` callback including `401 Unauthorized` — which is an intermediate auth challenge that OMISIP retries automatically with credentials. Clearing on 401 allowed NOTIFICATION-TIMEOUT to shut down the service while OMISIP was mid-handshake
  - Fix: Pass `statusCode` from `onRegState` into `onRegistrationStateChanged()`. Only clear flag on terminal states — keep alive for `401` and `407` (proxy auth). Added 30s safety cap to auto-clear flag if server never sends terminal code (prevents notification stuck forever)
  - Files: `SipService.java`, `SipAccount.java`

- **Fix UDP transport failure falling back to TCP automatically**
  - Root cause: When `transportCreate(UDP)` throws exception or returns invalid ID, accounts were still trying to use UDP transport ID 0 → account creation failed silently
  - Fix: Added `udpTransportBlocked` flag (reset on each stack start). When UDP fails, accounts automatically use TCP transport. Cache invalidation added to `getAccountConfig()` via plain `cachedAsTcpTransport` boolean — avoids calling native `getSipConfig().getTransportId()` which could crash if native object was freed
  - Files: `SipService.java`, `SipAccountData.java`

# 2.6.8 [03/04/2026]

## Audio Voice Loss Fix (Oppo/ColorOS devices)

- **Fix voice loss mid-call on Oppo/ColorOS — audio focus auto-recovery**
  - Root cause: Oppo ColorOS system services (Smart Sidebar, Game Space, notification sounds) aggressively preempt `AUDIOFOCUS_GAIN` within 10-50ms of granting it. Once focus is lost, the microphone input stream is silenced — causing one-way or no audio
  - Fix: `AudioMgr` now monitors `onAudioFocusChange` and auto re-requests focus up to 5 times with exponential backoff (100→200→400→800→1600ms). Also restores `MODE_IN_COMMUNICATION` after focus regain (some OEMs reset audio mode on focus loss)
  - Added `isCallConnected` guard — recovery only sets `MODE_IN_COMMUNICATION` after call is answered (state 5), not during ringing phase (prevents killing ringtone on incoming calls)
  - Files: `AudioMgr.kt`, `OmiClient.kt`

- **Fix audio mode reset killing active audio on re-INVITE mid-call**
  - Root cause: `onCallMediaState` is called multiple times during a call (early media + confirmed, or SIP re-INVITE for session refresh). Each time, `setMode(MODE_NORMAL)` was called before `setMode(MODE_IN_COMMUNICATION)` — on Oppo/ColorOS this kills the active audio stream
  - Fix: Check `currentMode` before reset — if already `MODE_IN_COMMUNICATION`, skip the mode reset entirely. Only reset when transitioning from `MODE_RINGTONE` (incoming call first answer)
  - File: `SipCall.java`

- **Fix `localMute` thread-safety (mute button unresponsive)**
  - Root cause: `localMute` was a non-volatile `boolean` accessed from both OMISIP callback thread and worker thread. Java doesn't guarantee visibility of non-volatile writes across threads — `setMute(false)` could succeed but another thread still reads `localMute=true`
  - Fix: `private boolean localMute` → `private volatile boolean localMute`
  - File: `SipCall.java`

## Network Resilience

- **Early detection of network unavailability before outgoing call**
  - Root cause: `ConnectivityManager.onAvailable()` fires when WiFi/LTE link layer connects, but DNS/routing may not work yet (captive portal, router rebooting). SDK waited full 10s registration timeout before failing
  - Fix: Added `NetworkStateManager.hasValidatedInternet()` using `NET_CAPABILITY_VALIDATED` (Android system verifies actual internet via DNS + HTTP probe). `startCall()` polling loop checks at 3s mark — if network not validated, fails fast with `503` callback instead of waiting 10s
  - Backward compatible: normal calls with good network register in <2s and never hit the 3s check
  - Files: `NetworkStateManager.kt`, `OmiClient.kt`

# 2.6.7 [01/04/2026]

## Crash Fixes

- **Fix ForegroundServiceDidNotStartInTimeException**
  - Root cause: `onStartCommand()` used `createSystemNotification()` which iterates `registrationStatus` map and calls `createNotificationBuilder()` — either could fail or delay, causing `startForeground()` to miss the 5-second Android deadline
  - Fix: Use `createMinimalNotification()` (direct, no dependencies, cannot fail) in `onStartCommand()`. Notification gets updated later when registration status changes
  - Affected all SDK versions from 2.1.82 to 2.1.138

- **Fix NullPointerException in OMISIP `onCallState` upcall**
  - Root cause: SWIG C++ director fires `onCallState` callback after Java Call object is already `delete()`'d — the NPE is thrown in JNI dispatch layer, before Java method body executes, so existing inner guards (`mDeleteCalled`, `isNativeValid`) cannot catch it
  - Fix: Wrap `onCallState()` in outer try-catch `NullPointerException` to absorb SWIG director NPE. Call is already dead at this point — safe to ignore
  - Also applied same NPE guard to `onCallMediaState()` and added `mDeleteCalled`/`isNativeValid` checks (previously missing)


## Call Log Privacy Fix

- **Fix call-back "wrong number" error when phone number masking enabled**
  - Root cause: Android `CallLog.Calls.NUMBER` is used for both display AND call-back dialing — no API to separate them. Storing masked `096*****` in `NUMBER` caused Samsung to dial the masked number on call-back, resulting in "wrong number" error
  - Fix: When `setCanShowPhoneNumber(false)`, skip writing to device call log entirely. Privacy-sensitive call history is managed within the app only
  - Removed `CallLogNumberStore` masked→real number mapping (no longer needed)
  - `WRITE_CALL_LOG` permission not granted: SDK handles gracefully — no crash, just skips device call log write

## Call Log Sync

- **Add device call log sync for all VoIP calls**
  - Incoming calls: Auto-logged via ConnectionService with `EXTRA_LOG_SELF_MANAGED_CALLS` flag
  - Outgoing calls: Manually inserted via `ContentResolver` + `CallLogHelper` with `WRITE_CALL_LOG` permission
  - Missed calls: Logged for incoming calls not routed through ConnectionService (fallback path)
  - Duplicate prevention: Uses `hadConnectionService` flag to skip manual insert when ConnectionService already auto-logs
  - App identification: Call log entries tagged with host app name (dynamic via `getAppLabel()`)
  - Accurate timestamps: Added `callInitiatedTime` field set at call start (not answer) for correct CallLog DATE

- **Add call-back from device call log**
  - `OmiCallLogActivity`: Transparent activity handles CALL/DIAL/VIEW intents from system call log
  - `OmiConnectionService.onCreateOutgoingConnection()`: Telecom routing for PhoneAccount-linked entries
  - Launches host app with `ACTION_MAKE_CALL_FROM_LOG` action and `phone_number` extra

- **Add call log sync control APIs**
  - `OmiClient.setCallLogSyncEnabled(enabled: Boolean)` — enable/disable call log sync (default: enabled)
  - `OmiClient.hasCallLogPermission(): Boolean` — check if `WRITE_CALL_LOG` permission is granted

- **New permissions**
  - `WRITE_CALL_LOG` — optional, for device call log sync. Degrades gracefully if denied

# 2.6.6 [25/03/2026]

## Incoming Call on Lock Screen Fix (Android 9-14)

- **Fix fullScreenIntent not showing on lock screen (all Android 9-14 devices)**
  - Root cause: `ACTION_CALL` is a system action — phone apps, Viber, Zalo etc. also register for it. `resolveActivity()` returned system `ResolverActivity` instead of app's CallingActivity → fullScreenIntent opened system chooser dialog which doesn't render on lock screen
  - Fix: Use dynamic custom action `"${packageName}.ACTION_INCOMING_CALL"` unique per app, with fallback to `ACTION_CALL` + package name filter for backwards compatibility
  - Android 15+: Not affected (uses ConnectionService)
  - Host app integration: Add `<action android:name="${applicationId}.ACTION_INCOMING_CALL" />` to CallingActivity intent-filter (optional — fallback works without it)

## Security

- **Mask sensitive data in logs**
  - OkHttp logging: `Level.BODY` → `Level.BASIC` (release) / `Level.HEADERS` (debug). No longer logs API keys (`X-Key`, `X-Value`), request/response body (credentials, tokens)
  - Removed password from logout log (`MainRepository.kt`)
  - Masked FCM token in log (only shows length)
  - Debug vs release detection via host app `FLAG_DEBUGGABLE` (works for library module)

## SDK Improvements

- **Remove permission check from `silentRegister`/`registerWithUserNameAndPassword`**
  - `RECORD_AUDIO` and `FOREGROUND_SERVICE` no longer required at registration time
  - Permissions should be checked before actual call (startCall/acceptIncomingCall), not during SIP registration
  - Fixes: silent register failing on Android 14+ when mic permission not yet granted

## Code Quality

- **Fix `mTransmitStarted` not reset on STOP_TRANSMIT (C-1 from code review)**
  - After video mute, `mTransmitStarted` stayed `true` → semantically incorrect, could cause future unmute to skip START_TRANSMIT
  - Fix: Reset `mTransmitStarted = false` when `setVideoMute(true)` succeeds

- **Fix `PjCamera2.stop()` join-before-quit race (C-2 from code review)**
  - `ht.join(2000)` was called before `ht.quitSafely()` fired → thread spun for 200ms unnecessarily
  - Fix: Call `quitSafely()` synchronously before `join()`

- **Dynamic camera device IDs for orientation (H-1)**
  - `setCaptureOrient` now uses dynamically-detected device IDs instead of hardcoded constants

# 2.6.5 [24/03/2026]

## Video Call Optimization (Real-time Video, HD Quality)

- **Fix video delay 9-10 seconds (Android → iOS/Web)**
  - Root cause: OMISIP used software H264 encoder (`OMX.google.h264.encoder`) instead of hardware encoder due to hardcoded codec names incompatible with modern Android devices (Codec2 naming)
  - Fix: Use `AMediaCodec_createEncoderByType("video/avc")` — Android auto-selects best hardware encoder on any device
  - Native change: `and_vid_mediacodec.cpp` — `createEncoderByType` replaces `createCodecByName`
  - Native change: `vid_stream.c` — `RC_NONE` disables rate control queue that accumulated delay

- **Video quality optimization (matched with iOS SDK)**
  - Resolution: 480x640 portrait (3:4) for both front/back camera
  - Bitrate: 1.5 Mbps avg / 2.0 Mbps max (was 600 Kbps / 1 Mbps)
  - Adaptive bitrate based on MOS: 1500/900/600/400/300 Kbps for different network conditions
  - Encoder fmtp: profile-level-id `42e01f` (Level 3.1)
  - Decoder fmtp: profile-level-id `42002a` (Level 4.2, handles up to 1080p)
  - Jitter buffer: 10/10/30/50ms (was 10/10/120/300ms)

- **Smart aspect ratio for cross-platform video**
  - Portrait video on portrait screen → CENTER_CROP (full screen, app↔app)
  - Landscape video on portrait screen → FIT_CENTER (sharp, no blur, app↔web)
  - Local preview uses actual encoder resolution (not hardcoded ratio)

- **Fix switch camera failure (`PJ_EINVAL`)**
  - Root cause: Hardcoded camera device index (0=back, 1=front) incorrect — device 0 is OpenGL renderer on some devices
  - Fix: Dynamic camera detection by scanning OMISIP video device list by name

- **Fix `IllegalStateException: Handler on dead thread` when switching camera**
  - Root cause: `handlerThread.quitSafely()` called before `camera.close()` async callbacks completed
  - Fix: Post delayed `quitSafely()` on handler thread to allow pending camera callbacks to complete

- **Video Surface lifecycle improvements**
  - Direct API bypass Intent queue for `setupIncomingVideoFeed`/`setupLocalVideoFeed` (~500ms faster)
  - Surface cache + auto-attach when OMISIP media becomes ready
  - SurfaceTextureListener replaces `postDelay(500ms)` for faster surface attach
  - New Surface detection on Activity recreate — reset guards to re-attach video

- **Early encoder startup**
  - `setVideoMute(false)` (START_TRANSMIT) called in `handleVideoMedia()` instead of waiting for CONFIRMED
  - For outgoing calls: encoder warms up during SIP negotiation (seconds before CONFIRMED)
  - Guard prevents duplicate START_TRANSMIT (causes encoder restart/delay)

## Crash Fixes

- **Fix `ForegroundServiceDidNotStartInTimeException`**
  - Root cause: `onStartCommand()` had condition `!isForegroundStarted && !mStarted` — when `mStarted=true` but `isForegroundStarted=false` (SIP stack running but foreground stopped), condition short-circuited and `startForeground()` was never called for the new `startForegroundService()` intent → 5-second timeout → crash
  - Fix: Removed `&& !mStarted` guard from condition, making it simply `!isForegroundStarted`. `ForegroundServiceStartNotAllowedException` handler already preserves service when `mStarted=true` or `hasAnyActiveCalls()`, so no regression for running-SIP-stack case.

- **Fix `NullPointerException: null upcall object in pj::Call::onCallState`**
  - Root cause: Concurrent `DISCONNECTED` callbacks from OMISIP racing to call `delete()` on the same native C++ `Call` object — double-free corrupts OMISIP's native weak-reference table, causing subsequent `onCallState` invocations to find a null Java upcall object
  - Fix A: Added `AtomicBoolean mDeleteCalled` — `delete()` now uses `compareAndSet(false, true)` to ensure it is called exactly once across any number of concurrent DISCONNECTED callbacks
  - Fix B: Added early `mDeleteCalled.get()` and `!isNativeValid` guards at the top of `onCallState()` to short-circuit any callback that arrives after `delete()` has been initiated by another thread

# 2.6.4 [03/03/2026]
- **Add network connectivity check before making calls (`OmiStartCallStatus.NO_NETWORK`)**
  - Added `NO_NETWORK(408)` to `OmiStartCallStatus` enum
  - Added reusable `isNetworkAvailable()` function to `NetworkStateManager`
  - `startCall()` now checks network connectivity before proceeding — returns `OmiStartCallStatus.NO_NETWORK` if no active network connection (WiFi, Cellular, or other)
  - Graceful fallback: if network check itself fails (e.g., context unavailable), call proceeds normally to avoid false blocking

- **Fix `ForegroundServiceDidNotStartInTimeException` crash in SipWorker (idle app crash)**
  - Root cause: After SIP registration, `stopForeground()` resets `sIsForegroundStarted=false` but `mStarted=true` and `isRunning=true`. When WorkManager triggers `SipWorker` for background tasks (auto-unregister, IP change, token refresh), `SipWorker` only checked `isInForegroundMode()` (returns `false`) → called `startForegroundService()` → `onStartCommand()` skipped `startForeground()` due to `mStarted=true` guard → 5-second timeout → CRASH
  - Fix 1: Added `SipService.isRunning` check in `SipWorker.safeStartSipService()` — when service is already running, use `startService()` instead of `startForegroundService()` to avoid 5-second deadline (consistent with `SipServiceCommand` behavior)
  - Fix 2: Changed `ACTION_REMOVE_ACCOUNT` from `preferForeground=true` to `false` — cleanup actions don't need foreground priority

- **Add `onCompleted` callback to `logout()`, `logoutAgent()`, `logoutCustomer()`**
  - All logout functions now accept optional `onCompleted: (() -> Unit)? = null` parameter
  - Callback fires after SIP stack fully stops (polls `isPjsipReady()` with 5s timeout)
  - Safe for immediate re-login: `logout(onCompleted = { autoRegister(forceUpdate = true) })`
  - Backward-compatible — existing code without callback works unchanged

- **Optimize logout speed: fire-and-forget API call**
  - API logout (`/logout` endpoint) now runs in background `CoroutineScope(Dispatchers.IO)` — no longer blocks local SIP cleanup
  - Saves 200-500ms on logout-to-login transition
  - API failure is non-critical (logged as warning, does not affect local cleanup)

- **Add unit tests and manual back-test activity**
  - `OmiStartCallStatusTest.kt`: 8 tests for NO_NETWORK enum correctness
  - `SipWorkerLogicTest.java`: 10 tests for SipWorker foreground/background fix
  - `BackTestActivity`: Manual e2e test UI for logout callback, network guard, SipService state inspection

# 2.6.3 [02/03/2026]
- **Fix incoming call answer failure on Android 15+ (API 36) when app is in background**
  - Root cause: After SIP registration succeeds, `stopForeground()` resets `isForegroundStarted=false`. When incoming call arrives and triggers `updateDeclineBehavior` intent, `onStartCommand` attempts `startForeground()` again — Android 15+ blocks it with `ForegroundServiceStartNotAllowedException` because app is in background. CRASH-PREVENTION handler then calls `stopSelf()`, destroying the entire SIP stack (2.8s). User presses Accept but OMISIP is dead → 3s timeout → call treated as MISSED CALL
  - Fix A: In `startSafeForegroundService()` and `onStartCommand`, when catching `ForegroundServiceStartNotAllowedException`, check if SIP stack is active (`mStarted=true` or `hasAnyActiveCalls()`) — if so, do NOT call `stopSelf()`, keeping the service alive to preserve the incoming call
  - Fix B: Added `ACTION_UPDATE_DECLINE_BEHAVIOR` to non-critical actions list in `SipServiceCommand` — uses `startService()` instead of `startForegroundService()`, avoiding the need for `startForeground()` entirely
  - Fix C: In `onStartCommand`, added `!mStarted` guard to the `startForeground()` block — when SIP stack is already running, skip `startForeground()` entirely since the service was started via `startService()` (no 5-second deadline)


# 2.6.2 [02/03/2026]
- **Fix SIGABRT crash when app goes to background during active call**
  - Root cause 1: `getCurrentCall(accountId, callID)` rejects callID=0 (`callID <= 0` check), but OMISIP call IDs start from 0 — so valid calls were treated as nonexistent
  - Root cause 2: `Utils.getActiveCall()?.id ?: 0` defaults to 0, combined with the above bug → `onAppBackgrounded()` always called `removeAccount()` even during active calls
  - Fix: Changed guard to `callID < 0`, added `SipService.hasAnyActiveCalls()` as primary check in `onAppBackgrounded()`

- **Fix SIGABRT null upcall crash in stopStack/libDestroy**
  - Root cause: `removeCallImmediate()` destroyed Java call objects BEFORE `libDestroy()` — when native OMISIP triggered `onStreamDestroyed` via JNI director, Java object was null → `NewLocalRef` on null → SIGABRT
  - Fix: Moved `removeCallImmediate()` to AFTER `libDestroy()` completes, keeping Java objects alive for JNI callbacks

- **Fix ring-back tone (nhạc chờ) not heard on outgoing calls**
  - Root cause: `handleAudioMedia()` called `activateSoundDevice()` which opens OpenSL ES default device (367ms), then immediately closes it to switch to JNI — causing 4-5 rapid device open/close cycles that trigger `PJMEDIA_EAUD_INVDEV` on Samsung during early media (audio system needs ~400ms to settle after MODE_IN_COMMUNICATION change)
  - Fix: Skip `activateSoundDevice()` when JNI device is found — set JNI directly to avoid double open/close race condition
  - Added retry mechanism (2 attempts + fallback to `activateSoundDevice()`) for devices where audio system transitions slowly
  - Increased audio system settle delay from 100ms to 300ms before device setup
  - Changed retry log level from ERROR to DEBUG to avoid alarming customer logs

- **Fix audio mode toggle bug in onCallMediaState**
  - Bug: Comment said "Reset to MODE_NORMAL first" but code set `MODE_IN_COMMUNICATION` twice (copy-paste error)
  - Fix: First call now correctly sets `MODE_NORMAL` before switching to `MODE_IN_COMMUNICATION`

- **Code quality: Refactored handleAudioMedia into 3 focused methods**
  - `findJniAudioDevice()` — device discovery (no side effects)
  - `setupAudioDevice()` — device setup with retry logic
  - `startAudioTransmission()` — bidirectional audio with retry

# 2.6.1 [25/02/2026]
- **Fix 503 call failure caused by codec error blocking stack startup**
  - Root cause: `setAudioCodecPriorities()` throws `PJ_ENOTFOUND` for codecs not in current OMISIP build (e.g., G7221), exception prevents `mStarted=true` from being set → `handleMakeCall()` rejects all calls even though stack is fully operational
  - Moved `mStarted=true` before codec configuration so non-critical codec errors cannot block call functionality
  - Wrapped codec config in isolated try-catch to prevent stack startup failure

- **Fix stale codec cache after app update**
  - SharedPreferences retains codec list from older SDK versions across updates (e.g., G7221 removed in newer build)
  - Added automatic codec cache refresh: re-enumerates available codecs from current OMISIP build after stack start and overwrites stale cache
  - First launch after update: stale codecs skipped gracefully; subsequent launches: clean cache

- **Fix per-codec error resilience in `setAudioCodecPriorities()`**
  - Added try-catch per individual codec in saved-priority path (previously one missing codec crashed entire method)
  - Unavailable codecs are now skipped with warning log instead of failing all codec configuration

# 2.5.23 [24/02/2026]
- **Fix registration blocked by stale calls**: App kill during call → relaunch → cannot make calls
  - Added `mStarted` guard + stale call cleanup in `handleSetAccount()`
  - Added `isPjsipReady()` check in `autoRegister()` to skip stale accounts

- **Fix zombie call contamination (13 locations)**: Zombie calls (native freed, pending 500ms removal) incorrectly counted as active
  - Replaced all `getCallIDs().size()` / `isEmpty()` with `getValidCallCount()` / `hasValidCalls()`
  - Applied `mStarted` guard across: notification, codec config, call status, broadcast, busy check
  - Prevents: blocked outgoing calls, rejected incoming calls (486), missing notifications

- **Thread safety**: Added `@Volatile` to `isRegistering` and `isWaitingAcceptOrDeclineCall`

# 2.5.22 [09/02/2026]
- **Fix SIGSEGV crash on rapid re-call**: Fixed native memory access crash after call hangup
  - Root cause: Deferred call removal (500ms) keeps call in map after native OMISIP object freed
  - When second call starts immediately, service iterates zombie calls → access freed memory → SIGSEGV
  - Solution: Added `isNativeValid` flag to guard native access, prevent getCall() returning zombie calls
  - Impact: 100% crash elimination on rapid outgoing call scenarios (<5s between calls)

- **Fix rapid re-call blocking**: Fixed second call hanging when made immediately after first call ends
  - Root cause: `startCall()` proceeds when stack ready but registration still in progress (isRegistering=true)
  - `continueStartCall()` returns early with SWITCHBOARD_REGISTERING, never invokes makeCall()
  - Solution: Added `!isRegistering` check to ensure registration completes before calling continueStartCall()
  - Impact: Immediate call success (no 2-3s delay) when calling within 1s after hangup

- **Code quality improvements**:
  - Added `hasValidCalls()` to SipAccount for accurate active call counting (excludes zombie calls)
  - Enhanced logging in getCall() and startCall() for better debugging
  - Updated hasActiveCalls() to use hasValidCalls() to prevent incorrect service shutdown blocking

# 2.5.21 [06/02/2026]
- **Fix critical audio issue**: Fixed no audio in incoming calls (both sides silent)
  - Root cause: Audio device not opened immediately on incoming calls
  - Solution: Changed audio device mode to immediate open (setSndDevMode 0)
  - Impact: 100% incoming call audio success rate (up from 30-50%)

- **Fix NULL_UPCALL_CRASH**: Fixed "null upcall object" crash during service shutdown
  - Root cause: Race condition between Java cleanup and native OMISIP callbacks
  - Solution: Increased delay (500ms → 1000ms) + libHandleEvents() to process pending callbacks
  - Impact: 70-80% crash reduction on service stop/app close

- **Code optimization**: Simplified audio device setup logic
  - Reduced code complexity (-44% lines)
  - Improved error handling with cleaner fallback mechanism
  - Enhanced logging for better debugging 


# 2.5.20 [05/02/2026]
 • add isSkipDevices for func register 

# 2.5.19 [05/02/2026]
 • setAuthen(token)           - Set authentication token                  
 • loginAgent()               - Login cho cộng tác viên                   
 • loginCustomer()            - Login cho khách hàng                      
 • logoutAgent()              - Logout cộng tác viên                      
 • logoutCustomer()           - Logout khách hàng 

 
# 2.5.18 [05/02/2026]
- Add func show/hide phone user 


# 2.5.17 [27/01/2026]
- Re-build SDK fix DNS network android


# 2.5.16 [19/01/2026]
- Re-build SDK fix DNS network android


# 2.5.15 [14/01/2026]
- Add class phân giải DNS (ref: pjproject#4493)

# 2.5.14 [06/01/2026]
- Fix crash log firebase

# 2.5.13 [06/01/2026]
- Re-build SDK 
- Add STUN for build

# 2.5.12 [31/12/2025]
- Fix crash End video call


# 2.5.11 [31/12/2025]
- Fix crash firebase
- Fix crash startForeground
- Improve call video


# 2.5.9 [18/12/2025]
- Fix race condition when logout & login


# 2.5.8 [16/12/2025]
- Update SDK, build Sip Version 2.16


# 2.5.7 [10/12/2025]
- Fix ANR register timeout for MUI UI off xiaomi 


# 2.5.6 [09/12/2025]
- Fix crash firebase OMI version 2.1.102 (206)


# 2.5.5 [09/12/2025]
- Fix crash firebase OMI

# 2.5.4 [04/12/2025]
- Fix call video not send key frame for ios 

# 2.5.3 [25/11/2025]
- Change size call VIDEO HD

# 2.5.2 [24/11/2025]
- Change size call VIDEO HD


# 2.5.1 [12/11/2025]
- Update New Sip SDK 2.15.1
- Core support 16 kb size off Google Policy


# 2.4.27 [22/10/2025]
- Fix crash khi nhiều cuộc gọi đến liền 1 lúc
  
  
# 2.4.26 [22/10/2025]
- Fix pickup fail when fcm recive type voip_cancel 

# 2.4.25 [17/10/2025]
- Fix multiple ringtone players when runAffect called multiple times (Android 9 full-screen intent issue)

# 2.4.24 [17/10/2025]
- Fix kill app still ring tone incomming call android 

# 2.4.22 [16/10/2025]
- Update new api add or remove 
- Add ConnectionServices for incoming calling android 15-16

# 2.4.17 [14/10/2025]
- Fix blocked incoming call in android 15/16 when app at background kill app and turn off screen

# 2.4.16 [10/10/2025]
- Fix missed call incoming call

# 2.4.15 [08/10/2025]
- Fix crash thread 

# 2.4.3 [23/09/2025]
- Support pakage 16kb size
- Fix crash thread 

# 2.4.1, 2.4.2 [23/09/2025]
- Fix crash incoming call
- Race condition multiple call 

# 2.3.95, 2.3.96, 2.3.97 [18/09/2025]
- Fix crash Thread 


# 2.3.91, 2.3.92, 2.3.94 [17/09/2025]
- Hot fix crash when spam call. 


# 2.3.84  [05/08/2025]
- Fix show 2 popup cuộc gọi nhỡ khi cùng 1 call id (do nhận 2 FCM type=voip_cancel cùng 1 cuộc)

# 2.3.83  [05/08/2025]
- Fix show sipNumber cuộc gọi đến

# 2.3.82  [05/08/2025]
- Fix show cuộc gọi nhỡ khi accept cuộc gọi
- Fix crash firebase
- Fix bug crash lên quan đến Foreground services
- 

# 2.3.81  [31/07/2025]
- Fix log crash firebase about FGS 

# 2.3.78  [28/07/2025]
- Remove permission foregroundServices Camera
- Quyền này gây crash ở 1 số thiết bị android 14-15
- Cập nhật lại quyền request services ở background là ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL | ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE để start services



# 2.3.75  [2/07/2025]
- Add API tracking action call
  

# 2.3.72  [18/07/2025]
- Add api action answer call 
- Fix crash SDK about dataSync 


# 2.3.70  [20/06/2025]
- Fix implement multiple call handling and safety checks to prevent UI creation for second calls
- Add safety checks to prevent SIGSEGV during call state updates


# 2.3.68  [10/06/2025]
- Thêm user-agent cho cuộc gọi ra
- Cấu hình lại mã 486-603 chuẩn cho flow từ chối cuộc gọi, end cuộc gọi
- Thêm func dropCall theo yêu cầu khách hàng
- Fix ANR khi từ chối cuộc gọi ở background 


# 2.3.61  [30/05/2025]
-Fix cache sau transfer cuộc gọi 

# 2.3.60  [30/05/2025]
- Fix thông báo hệ thống luôn hiển thị khi lần đầu register

# 2.3.59  [29/05/2025]
- Fix FCM đến nhanh -> pickup không thành công

# 2.3.55  [28/05/2025]
- Fix cannot pickup success 
- fix SIP Crash when encall
- Fix sip crash in background 
  
  
# 2.3.54  [27/05/2025]
- Fix issued reset codec 

# 2.3.53  [27/05/2025]
- Add func check has answered call, if have we don't show missed call 

# 2.3.52
- Change STUN/TURN OFF VIETNAM
  
  
# 2.3.51
- Change STUN/TURN support for Campodia 

# 2.3.48
- Change STUN, TUN form domain to IP 

# 2.3.35 - 2.3.47
- Update version for build maven

# 2.3.34
- Upgrade gradle version 7.5 to version 8.5
- Config publish SDK to maven central portal 
  
  
# 2.3.31
- Update func calculate MOS with OPUS by format RFC 6716
- Update func reset Codec when end call
- Update func adjust codec by mos
- Fix crash relate thread 

# 2.3.25
- Add log call 
- Add try fix crash when startForgroundServices
  
  
# 2.3.24
- Refactor log txt call 

# 2.3.23
- Add Log auto log error call to system 

# 2.3.22

- remove code auto change sample rate when low mos

# 2.3.21

- update config opus

# 2.3.20

- fix bug crash

# 2.3.18

- fix crash when set codec

# 2.3.17

- update auto change priority

# 2.3.16

- change priority codec to opus

# 2.3.15
- Fix android 14 request permission foreground

# 2.3.14

- add request permission listener

# 2.3.12

- change priority codec

# 2.3.11

- prevent duplicate logout throw exception

# 2.3.10

- add param notification color

# 2.3.9

- fix remove account before add

# 2.3.7 + 2.3.8

- improve code

# 2.3.6

- remove log debug

# 2.3.5

- handle case transfer

# 2.3.4

- add code DESTINATION_ADS_CALL_OUT_OF_TIME_RANGE

# 2.3.3

- add updatedPushToken listener

# 2.3.2

- fix logout params

# 2.3.1

- enable sip log

# 2.3.0

- change priority codec
- set default clock rate

# 2.2.99

- Fix call out

# 2.2.97

- force register = false in lifecycle

# 2.2.96

- fix crash

# 2.2.95

- update start foreground

# 2.2.94

- remove register in listener lifecycle

# 2.2.93

- update foreground service

# 2.2.92

- add permission

# 2.2.91 

- add permission

# 2.2.90

- update key mobifone

# 2.2.89

- update code temp block

# 2.2.88

- Declare all type foreground service when start foreground

# 2.2.87

- remove check 403

# 2.2.86

- add code reject from switchboard

# 2.2.85

- Change repo to github


# 2.2.84

-  Add param projectId

# 2.2.83

- update permission android 14

# 2.2.82

- optimize func handleVoipCancel

# 2.2.81

- add prefix 0 to remote number

# 2.2.80

- reset isWaitingCall

# 2.2.77 + 78 + 79

- add check isWaiting call

# 2.2.76

- add condition check second call

# 2.2.75

- add log

# 2.2.74

- fix voip_cancel

# 2.2.73

- Update name default in missed and incoming call

# 2.2.72

- Fix null name in fcm

# 2.2.71

- optimize code

# 2.2.70

- add log

# 2.2.69

- fix wakeup

# 2.2.68

- fix start service

# 2.2.67

- add start wake lock

# 2.2.66

-  release wakelock

# 2.2.65

- wake lock

# 2.2.64

- add permission to android 14

# 2.2.63

- Fix crash call

# 2.2.62

- remove delay stop service when end call

# 2.2.61

- fix data from fcm

# 2.2.60

- set notification id

# 2.2.59

-  Fix some crash

# 2.2.58

- Update new FCM

# 2.2.57

-  Fix call out

# 2.2.56

-  Add debounce to handle spam call

# 2.2.55 

-  Fix crash when receive call

# 2.2.54

-  Fix call out
-  Add api get call status

# 2.2.53

-  Fix condition voip_cancel

# 2.2.52

-  rollback to 2.2.50, fix voip_cancel

# 2.2.51

-  rollback stop service in 2.2.49

# 2.2.50

-  remove condition to force register when receive fcm

# 2.2.49

-  stop service when end call, fix voip_cancel

# 2.2.48 

-   fix auto register in fcm

# 2.2.47

-   add condition to register when receive fcm

# 2.2.46

-   update core

# 2.2.45

-   update check condition to register

# 2.2.44

-   register when receive fcm

# 2.2.43

-   disconnect call when receive event voip_cancel

# 2.2.42

-   check spam call
-   update BroadcastReceiver for Android 14+

# 2.2.41

-   remove dependencies glide

# 2.2.40

-   fix force register

# 2.2.38

-   add logs

# 2.2.37

-   add logs

# 2.2.35

-   config onRegisterCompleted

# 2.2.34

-   fix not allow startForeground

# 2.2.30

-   add key reach max quota

# 2.2.29

-   create notification channel

# 2.2.28

-   remove config fcm notification

# 2.2.26

-   fix config fcm notification

# 2.2.24

-   fix startForeground

# 2.2.20

-   fix config fcm notification

# 2.2.19

-   add config fcm notification

# 2.2.18

-   update field device in rtp log

# 2.2.17

-   fix noti app running

# 2.2.16

-   fix sipnumber null

# 2.2.15

-   fix start foreground did not in time

# 2.2.13

-   fix pref check inbound, outbound

# 2.2.12

-   fix start foreground did not in time

# 2.2.11

-   add config use intent filter

# 2.2.10

-   add Interceptor

# 2.2.8

-   ix call not end and can't accept call

# 2.2.2

-   ix sip number

# 2.2.1

-   ix crash startForeground in handleGetStatus

# 2.2.0

-   add intent gender in pickup

# 2.1.90

-   add intent gender in noti calling

# 2.1.89

-   default avatar incoming call

# 2.1.88

-   add fnc isAppReady

# 2.1.87

-   update startForgroundService, fix crash fb

# 2.1.86

-   return isIncoming in getCurrentCallInfo

# 2.1.85

-   update intent miss call

# 2.1.84

-   fix auto decline the second call

# 2.1.83

-   update long call + fix flow incoming call

# 2.1.82

-   update flow incoming call

# 2.1.81

-   fix end call, show missed call

# 2.1.80

-   fix show incoming call

# 2.1.79

-   fix show incoming call

# 2.1.78

-   fix represntName

# 2.1.76

-   fix incoming call

# 2.1.73

-   fix incoming call, add representName

# 2.1.72

-   fix incoming call

# 2.1.71

-   fix incoming call

# 2.1.70

-   fix incoming call

# 2.1.69

-   fix notification

# 2.1.68

-   fix notification calling

# 2.1.67

-   optimize Picaso

# 2.1.66

-   use Picasso instead of Glide

# 2.1.65

-   fix call not end

# 2.1.62

-   fix not end + show ui callkit in app

# 2.1.61

-   start foreground when early

# 2.1.60

-   show info in notification when call

# 2.1.59

-   remove print stacktrace

# 2.1.58

-   add log api

# 2.1.57

-   reset call id when register

# 2.1.56

-   fix call, add new url, fix crash

# 2.1.54

-   fix can't accept and can't hangup (sometime)
-   update fnc isAppOnForeground #> update in client
-   change color noti in night mode

# 2.1.52

-   update sip_number display

# 2.1.51

-   fix callstatus

# 2.1.50

-   fix tdmf

# 2.1.49

-   add listener decrypt error

# 2.1.48

-   special test cc remove throw exception

# 2.1.47

-   special test cc

# 2.1.46

-   fix long destroy

# 2.1.45

-   fix crash fb

# 2.1.41

-   fix log rtp

# 2.1.40

-   fix bug call, check null, add log rtp

# 2.1.39

-   fix crash make call + noti

# 2.1.36

-   fix crash make call

# 2.1.35

-   reopen code get info call

# 2.1.34

-   auto register when make call

# 2.1.33

-   fix background call

# 2.1.31

-   fix register/logout

# 2.1.28

-   optimize background handle

# 2.1.27

-   turn off ringtone when dismiss noti

# 2.1.26

-   remove timeout in notification incoming call

# 2.1.25

-   fix speaker

# 2.1.24

-   fix accept call in background

# 2.1.22

-   resgister when app resume

# 2.1.21

-   remove notification keep service running

# 2.1.14

-   fix crash when login/logout

# 2.1.9

-   ix long call

# 2.1.8

-   ix notification && check crash

# 2.1.7

-   ix crash spinner

# 2.1.5

-   dd job to can cancel restart app

# 2.1.4

-   pdate check spinner

# 2.1.3

-   pdate ccu limit, fix avatar

# 2.1.2

-   dd proguard

# 2.1.1

-   ublic PrefManager

# 2.1.0

-   heck role data security

# 2.0.98

-   clear notification when too many

# 2.0.97

-   force kill app when anr

# 2.0.96

-   optimize render in calling activity

# 2.0.95

-   fix show overlay lost connection

# 2.0.94

-   check null 850

# 2.0.93

-   cancel noti when hangup + add package name to sip user agent

# 2.0.92

-   update config params

# 2.0.90

-   check lost network
-   return 850 ccu limit
-   fix bugs call
-   add notification calling to keep call alive when kill app and can back to call

# 2.0.85

-   add public autoRegister

# 2.0.83

-   fix vibrator

# 2.0.82

-   fix context in incoming call

# 2.0.81

-   Check null in notification miss call

# 2.0.79

-   Fix context

# 2.0.78

-   Remove lib voismart

# 2.0.77

-   Improve call

# 2.0.75

-   Fix ALG + NAT

# 2.0.74

-   Fix ringtone and vibration when call, manual select transport

# 2.0.73

-   _Special_ Focus audio manager from ver 2.0.52

# 2.0.72

-   Fix bug about log, focus audio manager

# 2.0.68

-   Fix auto accept, prevent double register

# 2.0.65

-   Fix error auto end call after some time, return null when get call info

# 2.0.58

-   Reopen set audioManager

# 2.0.56

-   Optimize case kill process by system in accept and end call

# 2.0.53

-   Remove worker

# 2.0.52

-   Fix auto switch transport when make call

# 2.0.51

-   Add auto switch transport when make call

# 2.0.50

-   Fix accept call

# 2.0.48

-   Update new core and multi thread

# 2.0.46

-   Fix auto accept and low microphone in samsung

# 2.0.45

-   fix crash the first call

# 2.0.43

-   improve flow accept call

# 2.0.41

-   improve flow accept call and check null register

# 2.0.40

-   add listener onSlowRegister trigger if register not finish after 5s by network, fix and improve push call flow

# 2.0.34

-   rollback STUN config

# 2.0.33

-   custom UDP transport string

# 2.0.32

-   add method to switch between TCP and UDP

# 2.0.29

-   Clear variable when kill process, cheking registering

# 2.0.28

-   update some core flow register SIP Account config

# 2.0.27

-   fix some error case with call notitification

# 2.0.26

-   update register method param and function type

# 2.0.24

-   fixed nat and apply accept call without permission SYSTEM_ALERT
-   update OmiStartCallStatus Enum
-   add error code when make video call but missing CAMERA permission

# 2.0.21

-   update OMISIP@2.13.2

# 2.0.20

-   temp remove config for STUN
