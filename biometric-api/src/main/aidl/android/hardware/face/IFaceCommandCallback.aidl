// IFaceCommandCallback.aidl
package android.hardware.face;

// Declare any non-default types here with import statements

interface IFaceCommandCallback {
   void onFaceCmd(int i, in byte[] bArr);
}