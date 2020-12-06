<p align="center">
<strong align="center">Buy me a beer, if you wish.</strong>
</p>
<p align="center">
  <a href="https://www.paypal.com/donate?hosted_button_id=53PV7V25NG292">
  <img src="https://www.paypalobjects.com/en_US/i/btn/btn_donateCC_LG.gif" alt="Donate" />
  </a>
</p>

## 


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
| Huiwei 3D FaceID | Android 10+ and Huawei devices |Huawei Mate 30 Pro (confirmation required)|  
| Samsung Face | Android 8+ and Samsung devices |Samsung Galaxy S10 (confirmation required)|
| Samsung Iris | Android 8+ and Samsung devices |Samsung Galaxy S10 (confirmation required)|  
| Oppo Face | Android 9+ and Oppo devices |Not tested yet|
| BiometricPrompt API | Android 9+ |Xiomi POCO F1|

## Setup
[![Download](https://api.bintray.com/packages/salat-cx65/Maven/dev.skomlach:biometric/images/download.svg) ](https://bintray.com/salat-cx65/Maven/dev.skomlach:biometric)


Include library in your app:

```groovy
allprojects {
    repositories {
        ...
        jcenter()
    }
}

dependencies {
     implementation 'dev.skomlach:biometric:X.X.X'
}
```
## Usage

- At first, you need to create the **BiometricPromptCompat**
```java
BiometricPromptCompat.Builder builder =
 new BiometricPromptCompat.Builder(getActivity())
 .setTitle("Biometric demo")
 .setNegativeButton("Cancel", null);  
BiometricPromptCompat biometricPromptCompat = builder.build();  
 ``` 
 ***Please note:**
 Methods `builder.setTitle()` and `builder.setNegativeButton()`   are mandatory.*



- You also able to specify the desired implementation use the next builder:
```java
BiometricPromptCompat.Builder builder =
 new BiometricPromptCompat.Builder(BiometricApi, getActivity());     
 ``` 
 

  **BiometricApi:**

  `BiometricApi.AUTO` - the library will peek at the best-matched API (default)
  
  `BiometricApi.LEGACY_API` - forced usage of legacy biometric APIs like Fingerprint or FaceUnlock, and custom UI
  
  `BiometricApi. BIOMETRIC_API` - forced usage of new BiometricPrompt API
  

 **BiometricPromptCompat:**
 
 `void authenticate(BiometricPromptCompat.Result resultCallback)` - start biometric auth workflow


 `void cancelAuthenticate()` - cancel active biometric auth workflow
 
 `boolean cancelAuthenticateBecauseOnPause()` - Useful if you need to allow biometric auth in Split-Screen mode; Recommended to call this method in `onResume()` and use returned value to avoid biometric auth restart. 
Returns `false` and keep biometric auth on display if the app in Split-Screen mode, returns `true` and cancel active biometric auth otherwise

  `@ColorRes int getDialogMainColor()`  - returns dialog background color
  
 `boolean hasEnrolled()`  - returns `true` if any biometric enrolled
 
 `boolean isBiometricSensorPermanentlyLocked()`  - returns `true` if biometric permanently locked; Device lock-unlock or reboot required from the user
 
 `boolean isHardwareDetected()`   - returns `true` if any biometric hardware available
 
 `boolean isLockOut()`   - returns `true` if biometric temporarily locked; Usually need to wait for 30 seconds and the system will reset this lock
 
 `boolean isNewBiometricApi()`   - returns `true` if  BiometricPrompt API used
 
 `void openSettings(Activity)`  - Attempting to open the "Enroll biometric" settings screen





**BiometricPromptCompat.Result**

`void onSucceeded()` - User successfully authenticated 
  
  `void onCanceled()` - Biometric authentification was canceled
  
  `void onFailed(AuthenticationFailureReason reason)`  - Error happens, see details in *AuthenticationFailureReason*
  
  `void onUIShown()` - Biometric UI on display


## Contact author

Telegram: [@SergeyKomlach](https://t.me/SergeyKomlach)

## License

Apache License 2.0
