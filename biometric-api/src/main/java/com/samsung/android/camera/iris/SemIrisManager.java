package com.samsung.android.camera.iris;

import android.content.Context;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.util.SparseArray;
import android.view.View;

import java.security.Signature;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.Mac;

public class SemIrisManager {
    public static synchronized SemIrisManager getSemIrisManager(Context context) {
        return null;
    }

    public void authenticate(CryptoObject cryptoObject, CancellationSignal cancellationSignal, int i, AuthenticationCallback authenticationCallback, Handler handler, int i2, Bundle bundle, View view) {

    }

    public void authenticate(CryptoObject cryptoObject, CancellationSignal cancellationSignal, int i, AuthenticationCallback authenticationCallback, Handler handler, View view) {

    }

    public void authenticate(CryptoObject cryptoObject, CancellationSignal cancellationSignal, int i, AuthenticationCallback authenticationCallback, Handler handler, View view, int i2) {
        authenticate(cryptoObject, cancellationSignal, i, authenticationCallback, handler, i2, null, view);
    }

    public SparseArray getEnrolledIrisUniqueID() {

        return null;
    }

    public List<Iris> getEnrolledIrises() {
        return null;
    }

    public List<Iris> getEnrolledIrises(int i) {

        return null;
    }

    public boolean hasEnrolledIrises() {

        return false;
    }

    public boolean hasEnrolledIrises(int i) {

        return false;
    }

    public boolean isHardwareDetected() {
        return false;
    }

    public static abstract class AuthenticationCallback {
        public void onAuthenticationAcquired(int i) {
        }

        public void onAuthenticationError(int i, CharSequence charSequence) {
        }

        public void onAuthenticationFailed() {
        }

        public void onAuthenticationHelp(int i, CharSequence charSequence) {
        }

        public void onAuthenticationSucceeded(AuthenticationResult authenticationResult) {
        }

        public void onIRImage(byte[] bArr, int i, int i2) {
        }
    }

    public static class AuthenticationResult {
        private final CryptoObject mCryptoObject;
        private final Iris mIris;

        public AuthenticationResult(CryptoObject cryptoObject, Iris iris) {
            this.mCryptoObject = cryptoObject;
            this.mIris = iris;
        }

        public CryptoObject getCryptoObject() {
            return this.mCryptoObject;
        }

        public Iris getIris() {
            return this.mIris;
        }
    }

    public static final class CryptoObject {
        private final Object mCrypto;
        private final byte[] mFidoRequestData;
        private byte[] mFidoResultData = null;

        public CryptoObject(Signature signature, byte[] bArr) {
            this.mCrypto = signature;
            this.mFidoRequestData = bArr;
        }

        public CryptoObject(Cipher cipher, byte[] bArr) {
            this.mCrypto = cipher;
            this.mFidoRequestData = bArr;
        }

        public CryptoObject(Mac mac, byte[] bArr) {
            this.mCrypto = mac;
            this.mFidoRequestData = bArr;
        }

        public Cipher getCipher() {
            return this.mCrypto instanceof Cipher ? (Cipher) this.mCrypto : null;
        }

        public byte[] getFidoRequestData() {
            return this.mFidoRequestData;
        }

        public byte[] getFidoResultData() {
            return this.mFidoResultData;
        }

        private void setFidoResultData(byte[] bArr) {
            this.mFidoResultData = bArr;
        }

        public Mac getMac() {
            return this.mCrypto instanceof Mac ? (Mac) this.mCrypto : null;
        }

        public long getOpId() {
            return 0;
        }

        public Signature getSignature() {
            return this.mCrypto instanceof Signature ? (Signature) this.mCrypto : null;
        }
    }
}