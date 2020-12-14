
## PLEASE NOTE:
If your project has minSDK 23 and should support only ***basic Fingerprint Authorization on most devices*** - take a look first at the  [AndroidX's Biometric ](https://developer.android.com/jetpack/androidx/releases/biometric).

If you still need advanced Biometric authentication use **Fingerpint, Face or Iris** on the wide range of devices - see doc below.



Advanced BiometricPromptCompat
====


## Introduction

#### What is `BiometricPrompt API`?


This is a new API that declares that the system takes care of a unified way to use different biometric identification methods - fingerprint, face, iris, etc., as well as a unified way to display the UI on all devices.

To learn more, read [BiometricPrompt Reference](https://developer.android.com/reference/android/hardware/biometrics/BiometricPrompt) in Android Developers site.

Unfortunately, this simplification hides a number of problems.
- On Android 9, there is no way to simply get information about whether there is available biometric hardware and whether biometric data is enrolled. Android 10 provides BiometricManager that partially solves this problem.
- Some manufacturers have removed/do not display the biometric UI
- Biometric Auth solutions like Samsung Pass SDK or Meizu Fingerprint need to implement separately
- No way to identify what types of biometric auth available on the device.



#### How to use BiometricPromptCompat in old devices?

BiometricPromptCompat is designed to be compatible with the largest number of Android devices.
Its interface is very close to the original `BiometricPrompt`.
Minimal supported SDK -  **Android 4.1  JellyBean (API 16)**

#### Key features
- Unified UI for all devices - starts from Android 4.1 and to Android 11
- Contains fix for devices WITHOUT system BiometricPrompt UI (like LG G8 or OnePlus 6T)
- Dark/Light themes supported; Also you able to get the background color of the current Biometric dialog
- Auth in Split-Screen Mode supported
- Wide range of supported biometrics

#### Supported types of biometric authentication

|  Type | Details | Tested on
|--|--|--|
| Face Unlock (aka Trusted Faces) | Android 4.1+ |Prestigio PAP3400|
| Fingerprint (Samsung Pass)  | Android 4.4+ and Samsung devices | Samsung Galaxy S5 |
| Meizu Fingerprint | Android 5+ and Meizu devices | Not tested yet |
| Fingerprint | Android 6+ |Xiomi POCO F1|
| Fingerprint with In-screen scanner | Android 8+ |OnePlus 6T|
| Huawei 3D FaceID | Android 10+ and Huawei devices |Huawei Mate 30 Pro (confirmation required)|  
| Samsung FaceID | Android 8+ and Samsung devices |Samsung Galaxy S10 (confirmation required)|
| Samsung IrisID | Android 8+ and Samsung devices |Samsung Galaxy S10 (confirmation required)|  
| Oppo FaceID | Android 9+ and Oppo devices |Not tested yet|
| BiometricPrompt API | Android 9+ |Xiomi POCO F1|

## Setup
[![Download](https://api.bintray.com/packages/salat-cx65/Maven/dev.skomlach:biometric/images/download.svg) ](https://bintray.com/salat-cx65/Maven/dev.skomlach:biometric)


Include library in your app:

```groovy

    repositories {
         maven { url 'https://developer.huawei.com/repo/' }
         maven { url "https://dl.bintray.com/tencent-soter/maven/" }
         maven { url "https://pkgs.dev.azure.com/MicrosoftDeviceSDK/DuoSDK-Public/_packaging/Duo-SDK-Feed/maven/v1" }
    }


dependencies {
     implementation 'dev.skomlach:biometric:X.X.X'
}
```
## Usage
- In `Application.onCreate()` need to call

```java
BiometricPromptCompat.init(null);  
 ``` 
This will start the initialization process. 
If you want to do something after initialization - you can also pass a Runnable object - it will be called when initialization is complete.





 **BiometricApi:**

  `BiometricApi.AUTO` - the library will peek at the best-matched API
  
  `BiometricApi.LEGACY_API` - forced usage of legacy biometric APIs like Fingerprint or FaceUnlock, and custom UI
  
  `BiometricApi.BIOMETRIC_API` - forced usage of new BiometricPrompt API
  
 **BiometricType:**

  `BiometricType.BIOMETRIC_FINGERPRINT` - Use only **Fingerprint** biometric, ignore others
  
  `BiometricType.BIOMETRIC_FACE` -  Use only **FaceId** biometric, ignore others
  
  `BiometricType.BIOMETRIC_IRIS` -  Use only **Iris** biometric, ignore others
  
  `BiometricType.BIOMETRIC_UNDEFINED` - use any available biometric (multiple types supported)


**BiometricAuthRequest** 


Allows you to configure the type of target biometrics.
It can be any combination of BiometricApi and BiometricType;
Default is `BiometricAuthRequest(BiometricApi.AUTO, BiometricType.BIOMETRIC_UNDEFINED)` - means any available BiometricApi and BiometricType


**BiometricPromptCompat API**
##
 
 `static boolean hasEnrolled(BiometricAuthRequest)`  - returns `true` if specified biometric enrolled
 
 `static boolean isBiometricSensorPermanentlyLocked(BiometricAuthRequest)`  - returns `true` if specified biometric permanently locked; Device lock-unlock or reboot required from the user
 
 `static boolean isHardwareDetected(BiometricAuthRequest)`   - returns `true` if specified biometric hardware available
 
 `static boolean isLockOut(BiometricAuthRequest)`   - returns `true` if specified biometric temporarily locked; Usually need to wait for 30 seconds and the system will reset this lock
 
 `static boolean isNewBiometricApi(BiometricAuthRequest)`   - returns `true` if BiometricPrompt API used for specified BiometricAuthRequest 
 
 `static void openSettings(Activity, BiometricAuthRequest)`  - Attempting to open the "Enroll biometric" settings screen for specified BiometricAuthRequest 
##

**BiometricPromptCompat.Builder**

```java
BiometricPromptCompat.Builder builder =
 new BiometricPromptCompat.Builder(getActivity())
 .setTitle("Biometric demo")
 .setNegativeButton("Cancel", null);  
BiometricPromptCompat biometricPromptCompat = builder.build();  
 ``` 
 ***Please note:***
 Methods `builder.setTitle()` and `builder.setNegativeButton()`   are mandatory.


- You also able to specify desired BiometricAuthRequest use the next builder:
```java
BiometricPromptCompat.Builder builder =
 new BiometricPromptCompat.Builder(BiometricAuthRequest, getActivity());     
 ``` 
 
 **BiometricPromptCompat:**
 
 
 `void authenticate(BiometricPromptCompat.Result resultCallback)` - start biometric auth workflow

 `void cancelAuthenticate()` - cancel active biometric auth workflow
 
 `boolean cancelAuthenticateBecauseOnPause()` - Useful if you need to allow biometric auth in Split-Screen mode; Recommended to call this method in `onResume()` and use returned value to avoid biometric auth restart. 
Returns `false` and keep biometric auth on display if the app in Split-Screen mode, returns `true` and cancel active biometric auth otherwise

  `@ColorRes int getDialogMainColor()`  - returns dialog background color
 

**BiometricPromptCompat.Result**

`void onSucceeded()` - User successfully authenticated 
  
  `void onCanceled()` - Biometric authentification was canceled
  
  `void onFailed(AuthenticationFailureReason reason)`  - Error happens, see details in *AuthenticationFailureReason*
  
  `void onUIShown()` - Biometric UI on display



## I have a device that can be unlocked using Fingerprint/Face/Iris and(or) I can use this biometric type in pre-installed apps. But it doesn't work on 3rd party apps. Can  you help?

Yes, this is unfortunately happens very often. Many functions demanded by the market are often implemented by device manufacturers before the same API appears in the official Android SDK.

In short:
The device manufacturer has implemented biometric authentication via fingerprint/face/iris, but "forgot" to provide access to this implementation for third-party developers. Therefore, preinstalled (system) applications developed by the device manufacturer can use biometrics, while banking applications, password managers, and other third-party applications cannot.

Full answer:
It all depends on the specific case. I have come across several options that can be divided into several groups.
1) We know how the "Manager" defined and can add "dummy manager" into the `biometric-api` module, for example [MiuiFaceManagerImpl](https://github.com/shivatejapeddi/miuiframework/blob/cd456214274c046663aefce4d282bea0151f1f89/sources/android/hardware/miuiface/MiuiFaceManagerImpl.java). Unfortunately, this approach is not always possible to use.

First, starting with Android 9 appears [restrictions](
https://developer.android.com/distribute/best-practices/develop/restrictions-non-sdk-interfaces) on the use of non-public APIs. This limitation can be [circumvented](https://github.com/tiann/FreeReflection), BUT…

Secondly - anyway, the manager considered here uses system permissions, and as a result, a SecurityException will be thrown when we tried to get "Manager"  instance.

2) We know how the "Manager" defined and can add "dummy manager" into the `biometric-api` module, like `FaceID` on [EMUI 8.0](https://github.com/SivanLiu/HwFrameWorkSource/blob/5a49561c33945b022dcecbd25453c88cf6eec9e5/P9_8_0_0/src/main/java/com/huawei/facerecognition/FaceRecognizeManager.java)  , [EMUI 8.1](https://github.com/SivanLiu/HwFrameWorkSource/blob/fe9e34e997762d44e832b1022c14c36c10dd714f/Mate10_8_1_0/src/main/java/huawei/android/security/facerecognition/FaceRecognizeManagerImpl.java) or [EMUI 9.0](https://github.com/SivanLiu/HwFrameWorkSource/blob/54f8e90b0aa54196b39ea89c7cbf0aa6ce4ccf1f/Mate20_9_0_0/src/main/java/com/huawei/hardware/face/FaceManager.java) , and even able to get the “manager” instance. In this case, we will face the situation when working, for the first look, API, will not work in 3rd party apps properly.
3) Biometric auth implemented in the system `com.android.keyguard` package. For example, Meizu uses [Face++](https://github.com/FacePlusPlus) solution. Dependence on implementation, exists a chance to bind to the service as we do for [FaceUnlock](https://github.com/Salat-Cx65/AdvancedBiometricPromptCompat/tree/main/biometric/src/main/java/dev/skomlach/biometric/compat/engine/internal/face/facelock)

Anyway, research and testing required for each case, so feel free to create issues or contact directly with me.

## TODO
- Simplify setup **(IN PROGRESS)**
- Add more devices/manufacturers
- ~~Check for the way to start BiometricAuth with specified BiometricType~~
- Cleanup project and README
- Migrate to Kotlin

## Contact author

Telegram: [@SergeyKomlach](https://t.me/SergeyKomlach)

Twitter: [@SergejKomlach](https://twitter.com/SergejKomlach)

## License

Apache License 2.0

