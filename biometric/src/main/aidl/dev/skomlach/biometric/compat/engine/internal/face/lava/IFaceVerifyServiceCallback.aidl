// IFaceVerifyServiceCallback.aidl
package dev.skomlach.biometric.compat.engine.internal.face.lava;

// Declare any non-default types here with import statements

interface IFaceVerifyServiceCallback {
   void sendRecognizeResult(int resultId, String commandStr);
}