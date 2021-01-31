package dev.skomlach.biometric.compat.engine.internal.face.miui.impl;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

import java.lang.ref.WeakReference;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import dev.skomlach.biometric.compat.engine.internal.face.miui.impl.wrapper.BiometricConnect;

public class BiometricClient {
    private static final String LOG_TAG = "BiometricClient";
    private static final int MSG_GET_VERSION = 5002;
    private static final int MSG_RELEASE_SERVICE = 5001;
    private static final int MSG_RELEASE_TIMEOUT = 5;
    private static final int MSG_REPLAY_TIMEOUT = 2;
    private static final int MSG_SEND_SERVEICE = 5003;
    private static final int MSG_START_SERVICE = 5000;
    private static final int SERVICE_READY_TIMEOUT = 2;
    private static final int SERVICE_STATUS_CONNECTED = 2;
    private static final int SERVICE_STATUS_CONNECTING = 1;
    private static final int SERVICE_STATUS_CONNECTING_ERROR = 12;
    private static final int SERVICE_STATUS_CONNECTING_TIME_OUT = 11;
    private static final int SERVICE_STATUS_DISCONNECT = 5;
    private static final int SERVICE_STATUS_DISCONNECTING = 3;
    private static final int SERVICE_STATUS_NONE = 0;
    private static final int SERVICE_STATUS_UNBIND = 4;
    private final ReentrantLock accessLock_ = new ReentrantLock();
    private HandlerThread mCallbackThread = null;
    private WeakReference<Context> mClientContext = null;
    private ClientLister mClientLister = null;
    private ConnectHandler mConnectHandler = null;
    private Handler mHandler = null;
    private HandlerThread mMainThread = null;
    private Messenger mReplyMessager = null;
    private Messenger mSendMessager = null;
    private ServiceCallback mServiceCallback = null;
    private int mServiceConnectStatus = 0;
    private MyServiceConnection mServiceConnection = null;
    private String mTagInfo = null;
    private CountDownLatch replayReadyLatch_ = null;
    private CountDownLatch serviceReadyLatch_ = null;

    public BiometricClient(Context context) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("BiometricMainThread_");
        stringBuilder.append(this.mTagInfo);
        this.mMainThread = new HandlerThread(stringBuilder.toString());
        this.mMainThread.start();
        this.mHandler = new MyHandler(this.mMainThread.getLooper());
        this.mTagInfo = context.toString();
        this.mClientContext = new WeakReference(context);
        stringBuilder = new StringBuilder();
        stringBuilder.append("BiometricClientCBThread_");
        stringBuilder.append(this.mTagInfo);
        this.mCallbackThread = new HandlerThread(stringBuilder.toString());
        this.mCallbackThread.start();
        this.mConnectHandler = new ConnectHandler(this.mCallbackThread.getLooper());
        this.mReplyMessager = new Messenger(this.mConnectHandler);
        BiometricConnect.syncDebugLog();
        StringBuilder stringBuilder2 = new StringBuilder();
        stringBuilder2.append(":");
        stringBuilder2.append(this.mTagInfo);
        stringBuilder2.append(":BiometricClient MainThread id:");
        stringBuilder2.append(this.mMainThread.getId());
        stringBuilder2.append(" CallbackThread id:");
        stringBuilder2.append(this.mCallbackThread.getId());
        Log.d(LOG_TAG, stringBuilder2.toString());
    }

    /* Access modifiers changed, original: protected */
    public void finalize() throws Throwable {
        super.finalize();
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(":");
        stringBuilder.append(this.mTagInfo);
        stringBuilder.append(":finalize");
        Log.d(LOG_TAG, stringBuilder.toString());
        release();
    }

    private void release() {
        if (this.mHandler != null) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(":");
            stringBuilder.append(this.mTagInfo);
            stringBuilder.append(":release");
            Log.d(LOG_TAG, stringBuilder.toString());
            HandlerThread handlerThread = this.mCallbackThread;
            if (handlerThread != null) {
                if (handlerThread.getLooper() != null) {
                    this.mCallbackThread.getLooper().quit();
                }
                this.mCallbackThread.quit();
                this.mCallbackThread = null;
            }
            this.mConnectHandler = null;
            this.mReplyMessager = null;
            handlerThread = this.mMainThread;
            if (handlerThread != null) {
                if (handlerThread.getLooper() != null) {
                    this.mMainThread.getLooper().quit();
                }
                this.mMainThread.quit();
                this.mMainThread = null;
            }
            this.mHandler = null;
        }
    }

    public void startService(ServiceCallback cb) {
        this.mServiceCallback = cb;
        this.mServiceConnectStatus = 0;
        this.mSendMessager = null;
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(":");
        stringBuilder.append(this.mTagInfo);
        stringBuilder.append(":startService");
        Log.d(LOG_TAG, stringBuilder.toString());
        this.mHandler.sendEmptyMessage(5000);
        Bundle bundle = new Bundle();
        bundle.putString("info", this.mTagInfo);
        sendBundle(0, bundle);
    }

    private void removeAllMessage() {
        if (this.mHandler != null) {
            StringBuilder stringBuilder = new StringBuilder();
            String str = ":";
            stringBuilder.append(str);
            stringBuilder.append(this.mTagInfo);
            stringBuilder.append(":removeAllMessage");
            String stringBuilder2 = stringBuilder.toString();
            String str2 = LOG_TAG;
            Log.d(str2, stringBuilder2);
            for (int arg = 5000; arg <= 5003; arg++) {
                while (this.mHandler.hasMessages(arg)) {
                    StringBuilder stringBuilder3 = new StringBuilder();
                    stringBuilder3.append(str);
                    stringBuilder3.append(this.mTagInfo);
                    stringBuilder3.append(":removeAllMessage arg：");
                    stringBuilder3.append(arg);
                    Log.d(str2, stringBuilder3.toString());
                    this.mHandler.removeMessages(arg);
                }
            }
        }
    }

    public void releaseService(ServiceCallback cb) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(":");
        stringBuilder.append(this.mTagInfo);
        stringBuilder.append(":releaseService  mServiceConnectStatus:");
        stringBuilder.append(this.mServiceConnectStatus);
        Log.d(LOG_TAG, stringBuilder.toString());
        removeAllMessage();
        sendCommand(2);
        this.mHandler.sendEmptyMessage(5001);
    }

    public void getServiceVersion(int module_id, ClientLister l) {
        if (BiometricConnect.DEBUG_LOG) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(":");
            stringBuilder.append(this.mTagInfo);
            stringBuilder.append(":getServiceVersion module_id:");
            stringBuilder.append(module_id);
            Log.d(LOG_TAG, stringBuilder.toString());
        }
        this.mClientLister = l;
        Message msg = Message.obtain(this.mHandler);
        msg.what = 5002;
        msg.arg1 = module_id;
        this.mHandler.sendMessage(msg);
    }

    private void onServiceBind(IBinder service) {
        StringBuilder stringBuilder = new StringBuilder();
        String str = ":";
        stringBuilder.append(str);
        stringBuilder.append(this.mTagInfo);
        stringBuilder.append(":onServiceBind begin");
        String stringBuilder2 = stringBuilder.toString();
        String str2 = LOG_TAG;
        Log.d(str2, stringBuilder2);
        this.accessLock_.lock();
        if (this.mTagInfo == null) {
            stringBuilder = new StringBuilder();
            stringBuilder.append(str);
            stringBuilder.append(this.mTagInfo);
            stringBuilder.append(":onServiceBind mTagInfo is null");
            Log.d(str2, stringBuilder.toString());
            this.accessLock_.unlock();
            return;
        }
        this.mServiceConnectStatus = 2;
        this.mSendMessager = new Messenger(service);
        this.serviceReadyLatch_.countDown();
        if (this.mServiceCallback != null) {
            Log.d(str2, "mServiceCallback yes");
            this.mServiceCallback.onBiometricServiceConnected();
        }
        this.accessLock_.unlock();
        stringBuilder = new StringBuilder();
        stringBuilder.append(str);
        stringBuilder.append(this.mTagInfo);
        stringBuilder.append(":onServiceBind end");
        Log.d(str2, stringBuilder.toString());
    }

    private void onServiceUnbind(boolean lock) {
        if (lock) {
            this.accessLock_.lock();
        }
        int i = this.mServiceConnectStatus;
        String str = ":";
        String str2 = LOG_TAG;
        StringBuilder stringBuilder;
        if (4 == i || 5 == i) {
            stringBuilder = new StringBuilder();
            stringBuilder.append(str);
            stringBuilder.append(this.mTagInfo);
            stringBuilder.append(":onServiceUnbind ignore mServiceConnectStatus:");
            stringBuilder.append(this.mServiceConnectStatus);
            Log.d(str2, stringBuilder.toString());
            if (lock) {
                this.accessLock_.unlock();
            }
            return;
        }
        stringBuilder = new StringBuilder();
        stringBuilder.append(str);
        stringBuilder.append(this.mTagInfo);
        stringBuilder.append(":onServiceUnbind mServiceConnectStatus:");
        stringBuilder.append(this.mServiceConnectStatus);
        Log.d(str2, stringBuilder.toString());
        this.mServiceConnectStatus = 4;
        ServiceCallback serviceCallback = this.mServiceCallback;
        if (serviceCallback != null) {
            serviceCallback.onBiometricServiceDisconnected();
        }
        if (lock) {
            this.accessLock_.unlock();
        }
    }

    public void sendCommand(int cmd) {
        sendCommand(cmd, 0);
    }

    public void sendCommand(int cmd, int arg) {
        boolean z = BiometricConnect.DEBUG_LOG;
        String str = ":Send MSG: sendCommand cmd:";
        String str2 = ":";
        String str3 = LOG_TAG;
        if (z) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(str2);
            stringBuilder.append(this.mTagInfo);
            stringBuilder.append(str);
            stringBuilder.append(cmd);
            stringBuilder.append(",arg:");
            stringBuilder.append(arg);
            Log.d(str3, stringBuilder.toString());
        }
        Message msg = Message.obtain();
        msg.what = 1001;
        msg.arg1 = cmd;
        msg.arg2 = arg;
        Message msg_cmd = Message.obtain(this.mHandler, 5003);
        msg_cmd.obj = msg;
        this.mHandler.sendMessage(msg_cmd);
        if (BiometricConnect.DEBUG_LOG) {
            StringBuilder stringBuilder2 = new StringBuilder();
            stringBuilder2.append(str2);
            stringBuilder2.append(this.mTagInfo);
            stringBuilder2.append(str);
            stringBuilder2.append(cmd);
            stringBuilder2.append(" ok");
            Log.d(str3, stringBuilder2.toString());
        }
    }

    public void sendBundle(int key, Bundle bundle) {
        if (BiometricConnect.DEBUG_LOG) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(":");
            stringBuilder.append(this.mTagInfo);
            stringBuilder.append(":Send MSG: sendBundle key:");
            stringBuilder.append(key);
            Log.d(LOG_TAG, stringBuilder.toString());
        }
        Message msg = Message.obtain();
        msg.what = 1002;
        bundle.putInt("tag", key);
        msg.setData(bundle);
        Message msg_cmd = Message.obtain(this.mHandler, 5003);
        msg_cmd.obj = msg;
        this.mHandler.sendMessage(msg_cmd);
    }

    @SuppressLint("WrongConstant")
    private void handle_startService() {
        StringBuilder stringBuilder;
        StringBuilder stringBuilder2;
        boolean z = BiometricConnect.DEBUG_LOG;
        String str = ":";
        String str2 = LOG_TAG;
        if (z) {
            StringBuilder stringBuilder3 = new StringBuilder();
            stringBuilder3.append(str);
            stringBuilder3.append(this.mTagInfo);
            stringBuilder3.append(":handle_startService");
            Log.d(str2, stringBuilder3.toString());
        }
        this.accessLock_.lock();
        this.mServiceConnectStatus = 1;
        this.serviceReadyLatch_ = new CountDownLatch(1);
        Intent intent = new Intent("com.xiaomi.biometric.BiometricService");
        intent.setPackage(BiometricConnect.SERVICE_PACKAGE_NAME);
        this.mServiceConnection = new MyServiceConnection();
        try {
            this.mClientContext.get().bindService(intent, this.mServiceConnection, 65);
        } catch (Exception e) {
            stringBuilder = new StringBuilder();
            stringBuilder.append(str);
            stringBuilder.append(this.mTagInfo);
            stringBuilder.append(":handle_startService - bindService Exception ERROR: ");
            stringBuilder.append(e);
            Log.d(str2, stringBuilder.toString());
            e.printStackTrace();
        }
        this.accessLock_.unlock();
        if (BiometricConnect.DEBUG_LOG) {
            stringBuilder2 = new StringBuilder();
            stringBuilder2.append(str);
            stringBuilder2.append(this.mTagInfo);
            stringBuilder2.append(":handle_startService await...");
            Log.d(str2, stringBuilder2.toString());
        }
        try {
            if (!this.serviceReadyLatch_.await(2, TimeUnit.SECONDS)) {
                stringBuilder2 = new StringBuilder();
                stringBuilder2.append(str);
                stringBuilder2.append(this.mTagInfo);
                stringBuilder2.append(":handle_startService - ERROR: tmeout!");
                Log.d(str2, stringBuilder2.toString());
                this.accessLock_.lock();
                this.mServiceConnectStatus = 11;
                this.accessLock_.unlock();
            }
        } catch (Exception e2) {
            stringBuilder = new StringBuilder();
            stringBuilder.append(str);
            stringBuilder.append(this.mTagInfo);
            stringBuilder.append(":handle_startService - ERROR: ");
            stringBuilder.append(e2);
            Log.d(str2, stringBuilder.toString());
            e2.printStackTrace();
            this.accessLock_.lock();
            this.mServiceConnectStatus = 12;
            this.accessLock_.unlock();
        }
        if (BiometricConnect.DEBUG_LOG) {
            stringBuilder2 = new StringBuilder();
            stringBuilder2.append(str);
            stringBuilder2.append(this.mTagInfo);
            stringBuilder2.append(":handle_startService ok");
            Log.d(str2, stringBuilder2.toString());
        }
    }

    private void handle_releaseService() {
        StringBuilder stringBuilder;
        this.accessLock_.lock();
        String str = this.mTagInfo;
        String str2 = ":";
        String str3 = LOG_TAG;
        StringBuilder stringBuilder2;
        if (str == null) {
            stringBuilder2 = new StringBuilder();
            stringBuilder2.append(str2);
            stringBuilder2.append(this.mTagInfo);
            stringBuilder2.append(":handle_releaseService mClientInfoList is null");
            Log.e(str3, stringBuilder2.toString());
            this.accessLock_.unlock();
            return;
        }
        stringBuilder2 = new StringBuilder();
        stringBuilder2.append(str2);
        stringBuilder2.append(this.mTagInfo);
        stringBuilder2.append(":handle_releaseService mServiceConnectStatus:");
        stringBuilder2.append(this.mServiceConnectStatus);
        stringBuilder2.append(" ");
        Log.d(str3, stringBuilder2.toString());
        if (4 != this.mServiceConnectStatus) {
            onServiceUnbind(false);
        }
        try {
            this.mClientContext.get().unbindService(this.mServiceConnection);
        } catch (IllegalArgumentException e) {
            stringBuilder = new StringBuilder();
            stringBuilder.append(str2);
            stringBuilder.append(this.mTagInfo);
            stringBuilder.append(":handle_releaseService IllegalArgumentException:");
            stringBuilder.append(e.toString());
            Log.d(str3, stringBuilder.toString());
        } catch (NullPointerException e2) {
            stringBuilder = new StringBuilder();
            stringBuilder.append(str2);
            stringBuilder.append(this.mTagInfo);
            stringBuilder.append(":handle_releaseService NullPointerException:");
            stringBuilder.append(e2.toString());
            Log.d(str3, stringBuilder.toString());
        } catch (Exception e3) {
            stringBuilder = new StringBuilder();
            stringBuilder.append(str2);
            stringBuilder.append(this.mTagInfo);
            stringBuilder.append(":handle_releaseService Exception:");
            stringBuilder.append(e3.toString());
            Log.d(str3, stringBuilder.toString());
        }
        this.mServiceConnectStatus = 5;
        this.mServiceConnection = null;
        release();
        this.accessLock_.unlock();
        if (BiometricConnect.DEBUG_LOG) {
            stringBuilder2 = new StringBuilder();
            stringBuilder2.append(str2);
            stringBuilder2.append(this.mTagInfo);
            stringBuilder2.append(":handle_releaseService end");
            Log.d(str3, stringBuilder2.toString());
        }
    }

    private void handle_getServiceVersion(int module_id) {
        this.accessLock_.lock();
        int i = this.mServiceConnectStatus;
        String str = ":";
        String str2 = LOG_TAG;
        StringBuilder stringBuilder;
        if (i != 2) {
            stringBuilder = new StringBuilder();
            stringBuilder.append(str);
            stringBuilder.append(this.mTagInfo);
            stringBuilder.append(":handle_getServiceVersion error: service not Connected");
            Log.d(str2, stringBuilder.toString());
            this.accessLock_.unlock();
            return;
        }
        StringBuilder stringBuilder2;
        try {
            if (BiometricConnect.DEBUG_LOG) {
                stringBuilder = new StringBuilder();
                stringBuilder.append(str);
                stringBuilder.append(this.mTagInfo);
                stringBuilder.append(":handle_getServiceVersion");
                Log.d(str2, stringBuilder.toString());
            }
            Message msg = Message.obtain(null, 1000, module_id, 0);
            msg.replyTo = this.mReplyMessager;
            this.replayReadyLatch_ = new CountDownLatch(1);
            this.mSendMessager.send(msg);
            this.accessLock_.unlock();
            try {
                if (!this.replayReadyLatch_.await(2, TimeUnit.SECONDS)) {
                    stringBuilder2 = new StringBuilder();
                    stringBuilder2.append(str);
                    stringBuilder2.append(this.mTagInfo);
                    stringBuilder2.append(":handle_getServiceVersion - ERROR: timeout!");
                    Log.d(str2, stringBuilder2.toString());
                }
            } catch (InterruptedException ex) {
                StringBuilder stringBuilder3 = new StringBuilder();
                stringBuilder3.append(str);
                stringBuilder3.append(this.mTagInfo);
                stringBuilder3.append(":handle_getServiceVersion - ERROR: ");
                stringBuilder3.append(ex);
                Log.d(str2, stringBuilder3.toString());
                ex.printStackTrace();
            }
        } catch (RemoteException e) {
            stringBuilder2 = new StringBuilder();
            stringBuilder2.append(str);
            stringBuilder2.append(this.mTagInfo);
            stringBuilder2.append(":handle_getServiceVersion - RemoteException ERROR: ");
            stringBuilder2.append(e);
            Log.d(str2, stringBuilder2.toString());
            e.printStackTrace();
        } catch (Exception e2) {
            stringBuilder2 = new StringBuilder();
            stringBuilder2.append(str);
            stringBuilder2.append(this.mTagInfo);
            stringBuilder2.append(":handle_getServiceVersion - Exception ERROR: ");
            stringBuilder2.append(e2);
            Log.d(str2, stringBuilder2.toString());
            e2.printStackTrace();
        }
        if (BiometricConnect.DEBUG_LOG) {
            stringBuilder = new StringBuilder();
            stringBuilder.append(str);
            stringBuilder.append(this.mTagInfo);
            stringBuilder.append(":handle_getServiceVersion end");
            Log.d(str2, stringBuilder.toString());
        }
    }

    private void handle_sendService(Message msg) {
        StringBuilder stringBuilder;
        String str = ":handle_sendService - ERROR: ";
        this.accessLock_.lock();
        int i = this.mServiceConnectStatus;
        String str2 = ":";
        String str3 = LOG_TAG;
        StringBuilder stringBuilder2;
        if (i != 2) {
            stringBuilder2 = new StringBuilder();
            stringBuilder2.append(str2);
            stringBuilder2.append(this.mTagInfo);
            stringBuilder2.append(":handle_sendService error: service not Connected");
            Log.d(str3, stringBuilder2.toString());
            this.accessLock_.unlock();
            return;
        }
        StringBuilder stringBuilder3;
        if (BiometricConnect.DEBUG_LOG) {
            stringBuilder3 = new StringBuilder();
            stringBuilder3.append(str2);
            stringBuilder3.append(this.mTagInfo);
            stringBuilder3.append(":handle_sendService");
            Log.d(str3, stringBuilder3.toString());
        }
        try {
            msg.replyTo = this.mReplyMessager;
            this.replayReadyLatch_ = new CountDownLatch(1);
            this.mSendMessager.send(msg);
            this.accessLock_.unlock();
            try {
                if (!this.replayReadyLatch_.await(2, TimeUnit.SECONDS)) {
                    stringBuilder3 = new StringBuilder();
                    stringBuilder3.append(str2);
                    stringBuilder3.append(this.mTagInfo);
                    stringBuilder3.append(":handle_sendService - ERROR: timeout! cmd:");
                    stringBuilder3.append(msg.arg1);
                    stringBuilder3.append(" arg:");
                    stringBuilder3.append(msg.arg2);
                    Log.d(str3, stringBuilder3.toString());
                }
            } catch (InterruptedException ex) {
                stringBuilder = new StringBuilder();
                stringBuilder.append(str2);
                stringBuilder.append(this.mTagInfo);
                stringBuilder.append(str);
                stringBuilder.append(ex);
                Log.d(str3, stringBuilder.toString());
                ex.printStackTrace();
            }
        } catch (RemoteException ex2) {
            stringBuilder = new StringBuilder();
            stringBuilder.append(str2);
            stringBuilder.append(this.mTagInfo);
            stringBuilder.append(str);
            stringBuilder.append(ex2);
            Log.d(str3, stringBuilder.toString());
            ex2.printStackTrace();
        } catch (Exception e) {
            stringBuilder3 = new StringBuilder();
            stringBuilder3.append(str2);
            stringBuilder3.append(this.mTagInfo);
            stringBuilder3.append(":handle_sendService - Exception ERROR: ");
            stringBuilder3.append(e);
            Log.d(str3, stringBuilder3.toString());
            e.printStackTrace();
        }
        if (BiometricConnect.DEBUG_LOG) {
            stringBuilder2 = new StringBuilder();
            stringBuilder2.append(str2);
            stringBuilder2.append(this.mTagInfo);
            stringBuilder2.append(":handle_sendService end");
            Log.d(str3, stringBuilder2.toString());
        }
    }

    public interface ClientLister {
        void onVersion(float f, float f2);
    }

    interface ServiceCallback {
        void onBiometricBundleCallback(int i, int i2, Bundle bundle);

        void onBiometricEventCallback(int i, int i2, int i3, int i4);

        void onBiometricEventClassLoader(Bundle bundle);

        void onBiometricServiceConnected();

        void onBiometricServiceDisconnected();
    }

    private class ConnectHandler extends Handler {
        public ConnectHandler(Looper looper) {
            super(looper);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(":");
            stringBuilder.append(BiometricClient.this.mTagInfo);
            stringBuilder.append(":ConnectHandler");
            Log.d(BiometricClient.LOG_TAG, stringBuilder.toString());
        }

        public void handleMessage(Message msg) {
            StringBuilder stringBuilder;
            boolean z = BiometricConnect.DEBUG_LOG;
            String str = ", arg1:";
            String str2 = ":";
            String str3 = BiometricClient.LOG_TAG;
            if (z) {
                stringBuilder = new StringBuilder();
                stringBuilder.append(str2);
                stringBuilder.append(BiometricClient.this.mTagInfo);
                stringBuilder.append(":handleMessage cb - what:");
                stringBuilder.append(msg.what);
                stringBuilder.append(str);
                stringBuilder.append(msg.arg1);
                Log.d(str3, stringBuilder.toString());
            }
            if (msg.what == 9999) {
                Log.d(str3, "MSG_CONNECT_TEST ok");
                if (BiometricClient.this.replayReadyLatch_ != null) {
                    BiometricClient.this.replayReadyLatch_.countDown();
                }
                return;
            }
            BiometricClient.this.accessLock_.lock();
            if (BiometricClient.this.mServiceCallback == null) {
                stringBuilder = new StringBuilder();
                stringBuilder.append(str2);
                stringBuilder.append(BiometricClient.this.mTagInfo);
                stringBuilder.append(":handleMessage cb ignore- what:");
                stringBuilder.append(msg.what);
                stringBuilder.append(str);
                stringBuilder.append(msg.arg1);
                Log.e(str3, stringBuilder.toString());
                BiometricClient.this.accessLock_.unlock();
                return;
            }
            Bundle in_bundle = msg.getData();
            in_bundle.setClassLoader(BiometricConnect.class.getClassLoader());
            boolean result = in_bundle.getBoolean("result");
            int i = msg.what;
            if (i == 1000) {
                int serviceVerMaj_ = in_bundle.getInt(BiometricConnect.MSG_VER_SER_MAJ);
                i = in_bundle.getInt(BiometricConnect.MSG_VER_SER_MIN);
                int moduleVerMaj_ = in_bundle.getInt(BiometricConnect.MSG_VER_MODULE_MAJ);
                int moduleVerMin_ = in_bundle.getInt(BiometricConnect.MSG_VER_MODULE_MIN);
                StringBuilder stringBuilder2 = new StringBuilder();
                stringBuilder2.append(str2);
                stringBuilder2.append(BiometricClient.this.mTagInfo);
                stringBuilder2.append(":handleMessage cb - MSG_SEVICE_VERSION:SVC: v");
                stringBuilder2.append(serviceVerMaj_);
                str2 = ".";
                stringBuilder2.append(str2);
                stringBuilder2.append(i);
                stringBuilder2.append(", Module: v");
                stringBuilder2.append(moduleVerMaj_);
                stringBuilder2.append(str2);
                stringBuilder2.append(moduleVerMin_);
                Log.d(str3, stringBuilder2.toString());
                if (BiometricClient.this.mClientLister != null) {
                    BiometricClient.this.mClientLister.onVersion(((float) ((serviceVerMaj_ * 100) + i)) / 100.0f, ((float) ((moduleVerMaj_ * 100) + moduleVerMin_)) / 100.0f);
                }
                BiometricClient.this.accessLock_.unlock();
            } else if (i != 1001) {
                String str4 = BiometricConnect.MSG_REPLY_MODULE_ID;
                StringBuilder stringBuilder3;
                if (i == 1004) {
                    if (!result || BiometricClient.this.mServiceCallback == null) {
                        stringBuilder3 = new StringBuilder();
                        stringBuilder3.append(str2);
                        stringBuilder3.append(BiometricClient.this.mTagInfo);
                        stringBuilder3.append(":handleMessage cb MSG_CALLBACK_EVENT ignore- what:");
                        stringBuilder3.append(msg.what);
                        stringBuilder3.append(str);
                        stringBuilder3.append(msg.arg1);
                        Log.e(str3, stringBuilder3.toString());
                    } else {
                        BiometricClient.this.mServiceCallback.onBiometricEventCallback(in_bundle.getInt(str4), in_bundle.getInt("event"), in_bundle.getInt(BiometricConnect.MSG_REPLY_ARG1), in_bundle.getInt(BiometricConnect.MSG_REPLY_ARG2));
                    }
                    BiometricClient.this.accessLock_.unlock();
                } else if (i != 1005) {
                    BiometricClient.this.accessLock_.unlock();
                } else {
                    if (!result || BiometricClient.this.mServiceCallback == null) {
                        stringBuilder3 = new StringBuilder();
                        stringBuilder3.append(str2);
                        stringBuilder3.append(BiometricClient.this.mTagInfo);
                        stringBuilder3.append(":handleMessage cb MSG_CALLBACK_BUNDLE ignore- what:");
                        stringBuilder3.append(msg.what);
                        stringBuilder3.append(str);
                        stringBuilder3.append(msg.arg1);
                        Log.e(str3, stringBuilder3.toString());
                    } else {
                        BiometricClient.this.mServiceCallback.onBiometricBundleCallback(in_bundle.getInt(str4), in_bundle.getInt("key"), in_bundle);
                    }
                    BiometricClient.this.accessLock_.unlock();
                }
            } else {
                StringBuilder stringBuilder4;
                if (2 == msg.arg1) {
                    if (BiometricConnect.DEBUG_LOG) {
                        stringBuilder4 = new StringBuilder();
                        stringBuilder4.append(str2);
                        stringBuilder4.append(BiometricClient.this.mTagInfo);
                        stringBuilder4.append(":handleMessage cb - MSG_COMMAND_DEINIT_CALLBACK");
                        Log.d(str3, stringBuilder4.toString());
                    }
                    BiometricClient.this.onServiceUnbind(false);
                } else if (1 == msg.arg1 && BiometricConnect.DEBUG_LOG) {
                    stringBuilder4 = new StringBuilder();
                    stringBuilder4.append(str2);
                    stringBuilder4.append(BiometricClient.this.mTagInfo);
                    stringBuilder4.append(":handleMessage cb - MSG_COMMAND_INIT_CALLBACK");
                    Log.d(str3, stringBuilder4.toString());
                }
                BiometricClient.this.accessLock_.unlock();
            }
            if (!in_bundle.getBoolean(BiometricConnect.MSG_REPLY_NO_SEND_WAIT)) {
                BiometricClient.this.replayReadyLatch_.countDown();
            }
        }
    }

    private class MyHandler extends Handler {
        public MyHandler(Looper looper) {
            super(looper);
        }

        public void handleMessage(Message msg) {
            if (BiometricConnect.DEBUG_LOG) {
                StringBuilder stringBuilder = new StringBuilder();
                stringBuilder.append(":");
                stringBuilder.append(BiometricClient.this.mTagInfo);
                stringBuilder.append(":handleMessage main what:");
                stringBuilder.append(msg.what);
                Log.d(BiometricClient.LOG_TAG, stringBuilder.toString());
            }
            switch (msg.what) {
                case 5000:
                    BiometricClient.this.handle_startService();
                    return;
                case 5001:
                    BiometricClient.this.handle_releaseService();
                    return;
                case 5002:
                    BiometricClient.this.handle_getServiceVersion(msg.arg1);
                    return;
                case 5003:
                    BiometricClient.this.handle_sendService((Message) msg.obj);
                    return;
                default:
                    return;
            }
        }
    }

    private class MyServiceConnection implements ServiceConnection {
        private MyServiceConnection() {
        }

        public void onServiceConnected(ComponentName name, IBinder service) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(":");
            stringBuilder.append(BiometricClient.this.mTagInfo);
            stringBuilder.append(":onServiceConnected mServiceConnectStatus:");
            stringBuilder.append(BiometricClient.this.mServiceConnectStatus);
            Log.d(BiometricClient.LOG_TAG, stringBuilder.toString());
            BiometricClient.this.onServiceBind(service);
        }

        public void onServiceDisconnected(ComponentName name) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(":");
            stringBuilder.append(BiometricClient.this.mTagInfo);
            stringBuilder.append(":onServiceDisconnected mServiceConnectStatus:");
            stringBuilder.append(BiometricClient.this.mServiceConnectStatus);
            Log.d(BiometricClient.LOG_TAG, stringBuilder.toString());
            BiometricClient.this.onServiceUnbind(true);
            BiometricClient.this.handle_releaseService();
        }
    }
}
