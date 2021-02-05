

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
| BiometricPrompt API | Android 9+ |Xiaomi POCO F1|  
| Samsung IrisID | Android 8+ and Samsung devices |Samsung Galaxy S10 (confirmation required)|   
| ~~HTC IrisID~~ | ~~Android 9+ and HTC devices~~| ~~???~~|
| Samsung Pass Fingerprint| Android 4.4+ and Samsung devices | Samsung Galaxy S5 |  
| Fingerprint | Android 6+ |Xiaomi POCO F1|  
| In-screen Fingerprint | Android 8+ |OnePlus 6T/OnePlus 7 Pro|
| Meizu Fingerprint | Android 5+ and Meizu devices | Not tested yet |
| Face Unlock (aka Trusted Faces) | Android 4.1+ |Prestigio PAP3400|  
| Huawei FaceID | Android 8+ and Huawei devices |Huawei MatePad T8, Huawei P30|
| Huawei 3D FaceID | Android 10+ and Huawei devices |Huawei Mate 30 Pro (confirmation required)|  
| Xiomi FaceUnlock | Android 8+ and Xiaomi devices |Xiaomi POCO F1| 
| Samsung FaceID | Android 8+ and Samsung devices |Samsung Galaxy S10 (confirmation required)|  
| Oppo FaceID | Android 8+ and Oppo devices |Not tested yet|  
| Vivo FaceId | Android 8+ and Vivo devices |Not tested yet|
| ~~OnePlus FaceId~~ | ~~Android 8+ and OnePlus devices~~ |~~One Plus 7 Pro~~|


## Screenshots:  
  
**Xiaomi Pocophone F1**  
<p align="center">  
  <img src="https://raw.githubusercontent.com/Salat-Cx65/AdvancedBiometricPromptCompat/main/screenshots/pocoF1.jpg" alt="Pocophone F1" width="500px" />  
  </a>  
</p>  
  
  
**Samsung Galaxy S5**  
<p align="center">  
  <img src="https://raw.githubusercontent.com/Salat-Cx65/AdvancedBiometricPromptCompat/main/screenshots/samsungS5.png" alt="Samsung Galaxy S5" width="500px"  />  
  </a>  
</p>  
  
**Huawei Mate P40 Pro**  
<p align="center">  
  <img src="https://raw.githubusercontent.com/Salat-Cx65/AdvancedBiometricPromptCompat/main/screenshots/huawei.jpg" alt="Huawei Mate P40 Pro" width="500px"  />  
  </a>  
</p>  
  
  **Prestigio PAP3400**  
<p align="center">  
  <img src="https://raw.githubusercontent.com/Salat-Cx65/AdvancedBiometricPromptCompat/main/screenshots/prestigio.png" alt="Prestigio PAP3400" width="500px"  />  
  </a>  
</p>  
## Setup  
[![Download](https://api.bintray.com/packages/salat-cx65/Maven/dev.skomlach:biometric/images/download.svg) ](https://bintray.com/salat-cx65/Maven/dev.skomlach:biometric)  
  
  
Add dependency to Gradle:  
```groovy  
dependencies {
 implementation 'dev.skomlach:biometric:X.X.X' 
}
```  


## Usage  
  
  
  
**BiometricPromptCompat API**  
##  
At first, better in `Application.onCreate()`, call  
  
```java  
BiometricPromptCompat.init(callback);//Callback - null or Runnable{ do_something_after_init(); }     
```   


**BiometricManagerCompat**

##  
  
 `static boolean hasEnrolled()` - returns `true` if specified biometric enrolled  
   
 `static boolean isBiometricSensorPermanentlyLocked()` - returns `true` if specified biometric permanently locked; Device lock-unlock or reboot required from the user  
   
 `static boolean isHardwareDetected()` - returns `true` if specified biometric hardware available  
   
 `static boolean isLockOut()` - returns `true` if specified biometric temporarily locked; Usually need to wait for 30 seconds and the system will reset this lock  
   
 `static boolean isNewBiometricApi()` - returns `true` if BiometricPrompt API used for specified biometric  
   
 `static boolean openSettings(Activity)` -  returns `true` if open the "Enroll biometric" settings screen for specified biometric  
##  
  
  
  
  **BiometricAuthRequest**   
  
Allows you to configure the type of target biometrics.  
It can be any combination of BiometricApi and BiometricType;  
Default is `BiometricAuthRequest(BiometricApi.AUTO, BiometricType.BIOMETRIC_ANY)` - means any available BiometricApi and BiometricType  
  
  
 **BiometricApi:**  
  
  `BiometricApi.AUTO` - the library will peek at the best-matched API  
    
  `BiometricApi.LEGACY_API` - forced usage of legacy biometric APIs like Fingerprint or FaceUnlock, and custom UI  
    
  `BiometricApi.BIOMETRIC_API` - forced usage of new BiometricPrompt API  
    
 **BiometricType:**  
  
  `BiometricType.BIOMETRIC_FINGERPRINT` - Use only **Fingerprint** biometric, ignore others  
    
  `BiometricType.BIOMETRIC_FACE` -  Use only **FaceId** biometric, ignore others  
    
  `BiometricType.BIOMETRIC_IRIS` -  Use only **Iris** biometric, ignore others  
    
  `BiometricType.BIOMETRIC_ANY` - use any available biometric (multiple types supported)  
  
##  
  
**BiometricPromptCompat.Builder**  
  
```java  
BiometricPromptCompat.Builder builder =  
 new BiometricPromptCompat.Builder(getActivity()) .setTitle("Biometric demo") .setNegativeButton("Cancel", null); BiometricPromptCompat biometricPromptCompat = builder.build();    
 ```   
 ***Please note:***  
  Methods `builder.setTitle()` and `builder.setNegativeButton()` are mandatory.  
  
   
 **BiometricPromptCompat:**  
    
  `void authenticate(BiometricPromptCompat.Result resultCallback)` - start biometric auth workflow  
  
 `void cancelAuthenticate()` - cancel active biometric auth workflow  
   
 `boolean cancelAuthenticateBecauseOnPause()` - Useful if you need to allow biometric auth in Split-Screen mode; Recommended to call this method in `onPause()` and use returned value to avoid biometric auth restart.   
Returns `false` and keep biometric auth on display if the app in Split-Screen mode, returns `true` and cancel active biometric auth otherwise  
  
  `@ColorRes int getDialogMainColor()` - returns dialog background color  
   
  
**BiometricPromptCompat.Result**  
  
  `void onSucceeded()` - User successfully authenticated   
    
  `void onCanceled()` - Biometric authentication was canceled  
    
  `void onFailed(AuthenticationFailureReason reason)` - Error happens, see details in *AuthenticationFailureReason*  
  
  `void onUIShown()` - Biometric UI on display  
  
  
  
## I have a device that can be unlocked using Fingerprint/Face/Iris and(or) I can use this biometric type in pre-installed apps. But it doesn't work on 3rd party apps. Can  you help?  
  
Yes, this is, unfortunately, happening very often. Many functions demanded by the market are often implemented by device manufacturers before the same API appears in the official Android SDK.  
  
The device manufacturer has implemented biometric authentication via fingerprint/face/iris, but "forgot" to provide access to this implementation for third-party developers. Therefore, preinstalled (system) applications developed by the device manufacturer can use biometrics, while banking applications, password managers, and other third-party applications cannot.  

And unfortunately, sometimes manufacturers create such implementations that it is impossible to access using any known technic.

Anyway, research and testing required for each case, so feel free to create issues or contact directly with me.

  
## Types of Biometrics
https://www.biometricsinstitute.org/what-is-biometrics/types-of-biometrics/



## HOWTO SETUP 

FaceUnlock: https://www.xda-developers.com/face-unlock/

Fingerprint: https://www.wikihow.com/Set-Up-the-Fingerprint-Scanner-on-an-Android-Device

IrisUnlock: https://www.samsung.com/ph/support/mobile-devices/what-is-iris-scanning-and-how-to-use-it-on-my-samsung-galaxy-device/

  
## TODO  
- ~~Simplify setup~~
- Add more devices/manufacturers  
- ~~Check for the way to start BiometricAuth with specified BiometricType~~
- Cleanup project and README  
- Migrate to Kotlin  
  
  


## Contact author  
  
Telegram: [@SergeyKomlach](https://t.me/SergeyKomlach)  
  
Twitter: [@SergejKomlach](https://twitter.com/SergejKomlach)  
  
## License  
  
Apache License 2.0
