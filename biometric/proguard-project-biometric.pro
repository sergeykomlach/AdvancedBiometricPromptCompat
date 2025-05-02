-ignorewarnings
-dontwarn android.hardware.biometrics.CryptoObject
-dontwarn android.hardware.face.FaceManager$AuthenticationCallback
-dontwarn android.hardware.face.FaceManager$AuthenticationResult
-dontwarn android.hardware.face.FaceManager
-dontwarn android.hardware.face.OppoMirrorFaceManager$AuthenticationCallback
-dontwarn android.hardware.face.OppoMirrorFaceManager$AuthenticationResult
-dontwarn android.hardware.face.OppoMirrorFaceManager
-dontwarn android.hardware.iris.IrisManager$AuthenticationCallback
-dontwarn android.hardware.iris.IrisManager$AuthenticationResult
-dontwarn android.hardware.iris.IrisManager
-dontwarn android.os.ServiceManager
-dontwarn com.hihonor.android.facerecognition.FaceRecognizeManager$FaceRecognitionAbility
-dontwarn com.hihonor.android.facerecognition.FaceRecognizeManager$FaceRecognizeCallback
-dontwarn com.hihonor.android.facerecognition.FaceRecognizeManager
-dontwarn com.huawei.facerecognition.FaceRecognizeManager$FaceRecognitionAbility
-dontwarn com.huawei.facerecognition.FaceRecognizeManager$FaceRecognizeCallback
-dontwarn com.huawei.facerecognition.FaceRecognizeManager
-dontwarn com.samsung.android.bio.face.SemBioFaceManager$AuthenticationCallback
-dontwarn com.samsung.android.bio.face.SemBioFaceManager$AuthenticationResult
-dontwarn com.samsung.android.bio.face.SemBioFaceManager$CryptoObject
-dontwarn com.samsung.android.bio.face.SemBioFaceManager
-dontwarn com.samsung.android.camera.iris.SemIrisManager$AuthenticationCallback
-dontwarn com.samsung.android.camera.iris.SemIrisManager$AuthenticationResult
-dontwarn com.samsung.android.camera.iris.SemIrisManager$CryptoObject
-dontwarn com.samsung.android.camera.iris.SemIrisManager
-dontwarn com.samsung.android.fingerprint.FingerprintEvent
-dontwarn com.samsung.android.fingerprint.FingerprintIdentifyDialog$FingerprintListener
-dontwarn com.samsung.android.fingerprint.FingerprintManager$EnrollFinishListener
-dontwarn com.samsung.android.fingerprint.FingerprintManager
-dontwarn com.samsung.android.fingerprint.IFingerprintClient$Stub
-dontwarn com.samsung.android.fingerprint.IFingerprintClient


-keep class com.samsung.** { *; }
-keep interface com.samsung.** { *; }

-keep class com.fingerprints.service.** { *; }
-keep interface com.fingerprints.service.** { *; }

-keep class com.huawei.** { *; }
-keep interface com.huawei.** { *; }

-keep class com.tencent.soter.** { *; }
-keep interface com.tencent.soter.** { *; }

-keep class dev.skomlach.biometric.compat.** { *; }
-keep interface dev.skomlach.biometric.compat.** { *; }
-keep class org.chickenhook.restrictionbypass.** { *; }
-keep interface org.chickenhook.restrictionbypass.** { *; }