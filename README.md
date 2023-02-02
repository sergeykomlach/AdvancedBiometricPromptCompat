Advanced BiometricPromptCompat
====  

[Help/Discussing chat](https://t.me/advancedbiometric)


## Introduction

#### What is `BiometricPrompt API`?

This is a new API that declares that the system takes care of a unified way to use different
biometric identification methods - fingerprint, face, iris, etc., as well as a unified way to
display the UI on all devices.

To learn more,
read [BiometricPrompt Reference](https://developer.android.com/reference/android/hardware/biometrics/BiometricPrompt)
on the Android Developers site.

Unfortunately, this simplification hides a number of problems.

- On Android 9, there is no way to simply get information about whether there is available biometric
  hardware and whether biometric data is enrolled. Android 10 provides BiometricManager that
  partially solves this problem.
- Some manufacturers have removed/do not display the biometric UI
- Biometric Auth solutions like Samsung Pass SDK or Meizu Fingerprint need to implement separately
- No way to identify what types of biometric auth available on the device.
- On Android 12 and devices with FaceUnlock (like Pixel 4), when user disable Camera via
  QuickSettings, Face setup and FaceUnlock stop working and no API to handle this case

#### How to use BiometricPromptCompat?

BiometricPromptCompat is designed to be compatible with the largest number of Android devices.  
Its interface is very close to the original `BiometricPrompt`.  

Minimal supported Android OS version: **Android 4.1 Jelly Bean**

Latest supported Android OS version: **Android 13 Tiramisu**

**NOTE: TargetSDK=33**


#### Key features

- Unified UI for all devices. Exception: some vendors (like Huawei or Samsung) provide custom UI
- Contains fix for devices WITHOUT system BiometricPrompt UI (like LG G8 or OnePlus 6T)
- Dark/Light themes supported; Also you able to get the background color of the current Biometric
  dialog
- Auth in Split-Screen Mode supported
- Wide range of supported biometrics
- Android 12+ microphone and camera toggles handling
- DynamicColors (MaterialYou/Monet) theming supported


#### Supported types of biometric authentication

| Type | Details | Tested on  
|--|--|--|  
| BiometricPrompt API | Android 9+ |Xiaomi POCO F1, OnePlus 8T|  
| Samsung IrisID | Android 7+ and Samsung devices |Samsung Galaxy S10|   
| Samsung Pass Fingerprint| Android 4.4-6.0 and Samsung devices | Samsung Galaxy S5 |  
| Fingerprint | Android 6+ |Xiaomi POCO F1|  
| In-screen Fingerprint | Android 8+ |OnePlus 6T/OnePlus 7 Pro| 
| Meizu Fingerprint | Android 5.0-5.1 and Meizu devices | Meizu Pro 5 | 
| Face Unlock (aka TrustedFaces) | Android 4.1+ |Prestigio PAP3400|  
| Huawei FaceID | Android 8+ and Huawei devices |Huawei MatePad T8, Huawei P30| 
| Huawei 3D FaceID | Android 10+ and Huawei devices |Huawei Mate 30 Pro|  
| Xiaomi FaceUnlock | Android 7+ and Xiaomi devices |Xiaomi POCO F1| 
| Samsung FaceID | Android 7+ and Samsung devices |Samsung Galaxy S10|  
| Oppo FaceID | Android 8+ and Oppo devices |Not tested yet|  
| Vivo FaceId | Android 8+ and Vivo devices |Not tested yet|
| Lava FaceId | Android (Unknown) and Lava devices |Not tested yet|
| Windows Subsystem for Android & Windows Hello | Doesn't work; Stubs in system API's | Acer Aspire 7 with fingerprint scanner & Windows 11 |


## Recent changes (last 3 month)
February 2, 2023

**Bugfix: FaceId doesn't work on Honor** check for camera access on Huawei/Honor devices

**Improvement: auth dismiss** more correct auth cancel for non-modern methods

December 28, 2022

Code cleanup and minor fixes

December 23, 2022

**Improvement: Soter-Core added to project** instead of downloading from jitpack.io 

November 14, 2022

**Bugfix: Crash on Android 4.1** - fixed crash when RenderScript executed

**Feature: Added Lava FaceID impl** - FaceID for Lava devices, not tested

November 8, 2022

**Bugfix: Cryptography** - wrong vector was used

**Feature: Silent Auth implemented** - added solution that allow to recognize the user without any UI (not all devices)




## Test app

You can check how the library works on your device using this [APK](https://github.com/sergeykomlach/AdvancedBiometricPromptCompat/blob/main/app/app-debug.apk)

## Screenshots:

**Xiaomi Pocophone F1**
<p align="center">  
  <img src="https://raw.githubusercontent.com/Salat-Cx65/AdvancedBiometricPromptCompat/main/screenshots/pocoF1.jpg" alt="Pocophone F1" width="500px" />  

</p>  


**Samsung Galaxy S5**
<p align="center">  
  <img src="https://raw.githubusercontent.com/Salat-Cx65/AdvancedBiometricPromptCompat/main/screenshots/samsungS5.png" alt="Samsung Galaxy S5" width="500px"  />  

</p>  

**Huawei Mate P40 Pro**
<p align="center">  
  <img src="https://raw.githubusercontent.com/Salat-Cx65/AdvancedBiometricPromptCompat/main/screenshots/huawei.jpg" alt="Huawei Mate P40 Pro" width="500px"  />  

</p>  

**Prestigio PAP3400**
<p align="center">  
  <img src="https://raw.githubusercontent.com/Salat-Cx65/AdvancedBiometricPromptCompat/main/screenshots/prestigio.png" alt="Prestigio PAP3400" width="500px"  />  

</p>  


**Video from Xiaomi Pocophone F1**  
[![Watch the video](https://img.youtube.com/vi/ttHroYJlgI0/maxresdefault.jpg)](https://youtu.be/ttHroYJlgI0)

## Setup

VERSION
= [![Maven Central](https://img.shields.io/maven-metadata/v?metadataUrl=https%3A%2F%2Frepo1.maven.org%2Fmaven2%2Fdev%2Fskomlach%2Fbiometric%2Fmaven-metadata.xml)](https://mvnrepository.com/artifact/dev.skomlach/biometric)

Add dependency to Gradle

```groovy  
 implementation 'dev.skomlach:biometric:${VERSION}' 
```  

## Usage

**BiometricPromptCompat API**

##

**BiometricPromptCompat.Companion:**


`fun getAvailableAuthRequests(): List<BiometricAuthRequest>` - return the list with all Biometrics, supported on this device


`var deviceInfo: DeviceInfo?` - return device hardware specifications


For development purpose only:

`fun logging(enabled: Boolean)` - allow to enable/disable logging


`fun apiEnabled(enabled: Boolean)`  - allow to enable/disable this library


##    

**BiometricAuthRequest**

Allows you to configure the type of target biometrics.  
It can be any combination of BiometricApi, BiometricConfirmation and BiometricType;  
Default
is `BiometricAuthRequest(BiometricApi.AUTO, BiometricType.BIOMETRIC_ANY, BiometricConfirmation.ANY)`

**BiometricConfirmation:**

`BiometricConfirmation.ANY` - any biometric confirm the user

`BiometricConfirmation.ALL` - all (one-by-one) biometrics confirm the user

**BiometricApi:**

`BiometricApi.AUTO` - the library will peek at the best-matched API

`BiometricApi.LEGACY_API` - forced usage of legacy biometric APIs like Fingerprint or FaceUnlock,
and custom UI

`BiometricApi.BIOMETRIC_API` - forced usage of new BiometricPrompt API

**BiometricType:**

`BiometricType.BIOMETRIC_FINGERPRINT` - Use only **Fingerprint** biometric, ignore others

`BiometricType.BIOMETRIC_FACE` - Use only **FaceId** biometric, ignore others

`BiometricType.BIOMETRIC_IRIS` - Use only **Iris** biometric, ignore others

`BiometricType.BIOMETRIC_ANY` - use any available biometric (multiple types supported)

##     

**BiometricManagerCompat**

`fun hasEnrolled(): Boolean` - returns `true` if specified biometric enrolled

`fun isBiometricSensorPermanentlyLocked(): Boolean` - returns `true` if:

a) specified biometric permanently locked; Device lock-unlock or reboot required from the user

b) hardware permanently [blocked by user](https://www.androidcentral.com/how-disable-microphone-and-camera-privacy-controls-android-12)

`fun isHardwareDetected(): Boolean` - returns `true` if specified biometric hardware available

`fun isLockOut(): Boolean` - returns `true` if

a) specified biometric temporarily locked (Usually need to wait for 30 seconds and the system will reset this lock) 

b) hardware temporary locked by 3rd party app

`fun openSettings(Activity): Boolean` - returns `true` if open the "Enroll biometric" settings
screen for specified biometric

`fun isBiometricEnrollChanged(): Boolean` - returns `true` if enrollment changed for specified
biometric.

**NOTE!!! Be careful using 'isBiometricEnrollChanged' - due to technical limitations, it can return
incorrect result in many cases**

`fun isSilentAuthAvailable(): Boolean` - returns `true` if silent auth available.

##
**BiometricPromptCompat.Builder**

Simplest builder:
```kotlin  
 val builder = BiometricPromptCompat.Builder(activity).setTitle("Biometric demo") .setNegativeButton("Cancel", null)
 val biometricPromptCompat = builder.build()
 ```   

**BiometricPromptCompat:**

`fun authenticate(BiometricPromptCompat.AuthenticationCallback)` - start biometric
auth workflow

`fun cancelAuthentication()` - cancel active biometric auth workflow

`@ColorRes fun getDialogMainColor(): Int` - returns dialog background color

**BiometricPromptCompat.AuthenticationCallback**

`fun onSucceeded(Set<BiometricType>)` - User successfully authenticated

`fun onCanceled()` - Biometric authentication was canceled

`fun onFailed(AuthenticationFailureReason)` - Error happens, see details in *
AuthenticationFailureReason*

`fun onUIOpened()/fun onUIClosed` - Biometric UI on display or closed



**DeviceInfoManager:**

Helper tool to check some biometric-related stuff in device specification

`fun hasFingerprint(DeviceInfo): Boolean` 

`fun hasUnderDisplayFingerprint(DeviceInfo): Boolean` 

`fun hasIrisScanner(DeviceInfo): Boolean`

`fun hasFaceID(DeviceInfo): Boolean`


## Minimal code example:

```kotlin
private fun startBioAuth() {
   val iris = BiometricAuthRequest(
       BiometricApi.AUTO,
       BiometricType.BIOMETRIC_IRIS,
       BiometricConfirmation.ANY
   )
   val faceId = BiometricAuthRequest(
       BiometricApi.AUTO,
       BiometricType.BIOMETRIC_FACE,
       BiometricConfirmation.ANY
   )
   val fingerprint = BiometricAuthRequest(
       BiometricApi.AUTO,
       BiometricType.BIOMETRIC_FINGERPRINT,
       BiometricConfirmation.ANY
   )
   var title = ""
   val currentBiometric =
       if (BiometricManagerCompat.isHardwareDetected(iris)
           && BiometricManagerCompat.hasEnrolled(iris)
       ) {
           title =
               "Your eyes are not only beautiful, but you can use them to unlock our app"
           iris
       } else
           if (BiometricManagerCompat.isHardwareDetected(faceId)
               && BiometricManagerCompat.hasEnrolled(faceId)
           ) {
               title = "Use your smiling face to enter the app"
               faceId
           } else if (BiometricManagerCompat.isHardwareDetected(fingerprint)
               && BiometricManagerCompat.hasEnrolled(fingerprint)
           ) {
               title = "Your unique fingerprints can unlock this app"
               fingerprint
           } else {
               null
           }

   currentBiometric?.let { biometricAuthRequest ->
       if (BiometricManagerCompat.isBiometricSensorPermanentlyLocked(biometricAuthRequest)
           || BiometricManagerCompat.isLockOut(biometricAuthRequest)
       ) {
           showToast("Biometric not available right now. Try again later")
           return
       }

       val prompt = BiometricPromptCompat.Builder(this).apply {
           this.setTitle(title)
           this.setNegativeButton("Cancel", null)
           this.setEnabledNotification(false)//hide notification
           this.setEnabledBackgroundBiometricIcons(false)//hide duplicate biometric icons above dialog
           this.setCryptographyPurpose(BiometricCryptographyPurpose(BiometricCryptographyPurpose.ENCRYPT))//request Cipher for encryption
       }
     if(!prompt.enableSilentAuth()){
       showToast("Unable to use Silent Auth on current device :|")
       return 
     }
       prompt.build().authenticate(object : BiometricPromptCompat.AuthenticationCallback {
           override fun onSucceeded(confirmed: Set<BiometricType>) {
               val encryptedData = CryptographyManager.encryptData(
                 "Hello, my friends".toByteArray(Charset.forName("UTF-8")),
                 confirmed
               )
             
               showToast("User authorized :)\n Biometric used for Encryption=${encryptedData.biometricType}\n EncryptedData=${encryptedData.data}; InitializationVector=${encryptedData.initializationVector};")
           }

           override fun onCanceled() {
               showToast("Auth canceled :|")
           }

           override fun onFailed(reason: AuthenticationFailureReason?) {
               showToast("Fatal error happens :(\nReason $reason")
           }

           override fun onUIOpened() {}

           override fun onUIClosed() {}
       })
   } ?: run {
       showToast("No available biometric on this device")
   }

}

```
## False-positive and/or False-negative detection

On **pure** API28 implementation (built-in BiometricPrompt API) is no way to get '
isBiometricEnrolled' results for specific biometric, like Iris/Face, etc. So, some tricks have used
that try to determine by indirect signs which biometric data are used (like "if NOT fingerprint, BUT
something enrolled in the System Settings").

There are edge cases where we cannot tell exactly what type of biometrics is enrolled - for example,
if it is Samsung with Face and Iris - in this case, the code can give a incorrect result. It can
happen if you set ```BiometricApi.BIOMETRIC_API + BiometricType.BIOMETRIC_FACE```
or ```BiometricApi.BIOMETRIC_API + BiometricType.BIOMETRIC_IRIS```

Fortunately, for Samsung with Face and Iris, the 'legacy' check should work correctly, so for
general cases when you
use ```BiometricApi.AUTO/LEGACY_API + BiometricType.BIOMETRIC_FACE/BIOMETRIC_IRIS``` all should work
fine.

## I have a device that can be unlocked using Fingerprint/Face/Iris and(or) I can use this biometric type in pre-installed apps. But it doesn't work on 3rd party apps. Can  you help?

Yes, this is, unfortunately, happening very often. Many functions demanded by the market are often
implemented by device manufacturers before the same API appears in the official Android SDK.

The device manufacturer has implemented biometric authentication via fingerprint/face/iris, but "
forgot" to provide access to this implementation for third-party developers. Therefore,
preinstalled (system) applications developed by the device manufacturer can use biometrics, while
banking applications, password managers, and other third-party applications cannot.

And unfortunately, sometimes manufacturers create such implementations that it is impossible to
access using any known technic.

Anyway, research and testing are required for each case, so feel free to create issues or contact
directly with me.

## Some docs

[DRAFT.md](https://github.com/sergeykomlach/AdvancedBiometricPromptCompat/blob/main/DRAFT.md)

## Code security checks

- FindBugs

- Find Security Bugs

- OWAPS dependencies check

- Snyk

- Sonatype-Lift

## License

Apache License 2.0

## Contact author

Telegram: [@SergeyKomlach](https://t.me/SergeyKomlach)

Twitter: [@SergejKomlach](https://twitter.com/SergejKomlach)

<p align="center">
  <a href="https://www.buymeacoffee.com/sergey.komlach" target="_blank"><img src="https://cdn.buymeacoffee.com/buttons/default-orange.png" alt="Buy Me A Coffee" height="41" width="174"></a>
  
</p>

