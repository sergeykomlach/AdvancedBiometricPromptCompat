// IFaceVerifyService.aidl
package dev.skomlach.biometric.compat.engine.internal.face.lava;
import dev.skomlach.biometric.compat.engine.internal.face.lava.IFaceVerifyServiceCallback;

// Declare any non-default types here with import statements
interface IFaceVerifyService
{
    void unregisterCallback(IFaceVerifyServiceCallback mCallback);
    void registerCallback(IFaceVerifyServiceCallback mCallback);
    void startVerify();
    void stopVerify();
}