package android.hardware.face;

import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;

import androidx.annotation.RequiresApi;

@RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
public interface IOplusFaceManager extends IInterface {
    String DESCRIPTOR = "android.hardware.face.IOplusFaceManager";

    int getFaceProcessMemory() throws RemoteException;

    int getFailedAttempts() throws RemoteException;

    long getLockoutAttemptDeadline(int i) throws RemoteException;

    int regsiterFaceCmdCallback(IFaceCommandCallback iFaceCommandCallback) throws RemoteException;

    void resetFaceDaemon() throws RemoteException;

    int sendFaceCmd(int i, int i2, byte[] bArr) throws RemoteException;

    int unregsiterFaceCmdCallback(IFaceCommandCallback iFaceCommandCallback) throws RemoteException;

    class Default implements IOplusFaceManager {
        @Override
        public IBinder asBinder() {
            return null;
        }
        @Override
        public int getFaceProcessMemory() throws RemoteException {
            return 0;
        }
        @Override
        public int getFailedAttempts() throws RemoteException {
            return 0;
        }
        @Override
        public long getLockoutAttemptDeadline(int i) throws RemoteException {
            return 0;
        }
        @Override
        public int regsiterFaceCmdCallback(IFaceCommandCallback iFaceCommandCallback) throws RemoteException {
            return 0;
        }
        @Override
        public void resetFaceDaemon() throws RemoteException {
        }
        @Override
        public int sendFaceCmd(int i, int i2, byte[] bArr) throws RemoteException {
            return 0;
        }
        @Override
        public int unregsiterFaceCmdCallback(IFaceCommandCallback iFaceCommandCallback) throws RemoteException {
            return 0;
        }
    }

    abstract class Stub extends Binder implements IOplusFaceManager {
        static final int TRANSACTION_getFaceProcessMemory = 5;
        static final int TRANSACTION_getFailedAttempts = 2;
        static final int TRANSACTION_getLockoutAttemptDeadline = 1;
        static final int TRANSACTION_regsiterFaceCmdCallback = 6;
        static final int TRANSACTION_resetFaceDaemon = 4;
        static final int TRANSACTION_sendFaceCmd = 3;
        static final int TRANSACTION_unregsiterFaceCmdCallback = 7;

        public Stub() {
            attachInterface(this, IOplusFaceManager.DESCRIPTOR);
        }

        public static IOplusFaceManager asInterface(IBinder iBinder) {
            if (iBinder == null) {
                return null;
            }
            IInterface queryLocalInterface = iBinder.queryLocalInterface(IOplusFaceManager.DESCRIPTOR);
            return (queryLocalInterface == null || !(queryLocalInterface instanceof IOplusFaceManager)) ? new Proxy(iBinder) : (IOplusFaceManager) queryLocalInterface;
        }
        @Override
        public IBinder asBinder() {
            return this;
        }
        @Override
        public boolean onTransact(int i, Parcel parcel, Parcel parcel2, int i2) throws RemoteException {
            if (i >= 1 && i <= 16777215) {
                parcel.enforceInterface(IOplusFaceManager.DESCRIPTOR);
            }
            switch (i) {
                case 1598968902:
                    parcel2.writeString(IOplusFaceManager.DESCRIPTOR);
                    return true;
                default:
                    switch (i) {
                        case 1:
                            int readInt = parcel.readInt();
                            parcel.enforceNoDataAvail();
                            long lockoutAttemptDeadline = getLockoutAttemptDeadline(readInt);
                            parcel2.writeNoException();
                            parcel2.writeLong(lockoutAttemptDeadline);
                            return true;
                        case 2:
                            int failedAttempts = getFailedAttempts();
                            parcel2.writeNoException();
                            parcel2.writeInt(failedAttempts);
                            return true;
                        case 3:
                            int readInt2 = parcel.readInt();
                            int readInt3 = parcel.readInt();
                            byte[] createByteArray = parcel.createByteArray();
                            parcel.enforceNoDataAvail();
                            int sendFaceCmd = sendFaceCmd(readInt2, readInt3, createByteArray);
                            parcel2.writeNoException();
                            parcel2.writeInt(sendFaceCmd);
                            return true;
                        case 4:
                            resetFaceDaemon();
                            parcel2.writeNoException();
                            return true;
                        case 5:
                            int faceProcessMemory = getFaceProcessMemory();
                            parcel2.writeNoException();
                            parcel2.writeInt(faceProcessMemory);
                            return true;
                        case 6:
                            IFaceCommandCallback asInterface = IFaceCommandCallback.Stub.asInterface(parcel.readStrongBinder());
                            parcel.enforceNoDataAvail();
                            int regsiterFaceCmdCallback = regsiterFaceCmdCallback(asInterface);
                            parcel2.writeNoException();
                            parcel2.writeInt(regsiterFaceCmdCallback);
                            return true;
                        case 7:
                            IFaceCommandCallback asInterface2 = IFaceCommandCallback.Stub.asInterface(parcel.readStrongBinder());
                            parcel.enforceNoDataAvail();
                            int unregsiterFaceCmdCallback = unregsiterFaceCmdCallback(asInterface2);
                            parcel2.writeNoException();
                            parcel2.writeInt(unregsiterFaceCmdCallback);
                            return true;
                        default:
                            return super.onTransact(i, parcel, parcel2, i2);
                    }
            }
        }

        private static class Proxy implements IOplusFaceManager {
            private final IBinder mRemote;

            Proxy(IBinder iBinder) {
                this.mRemote = iBinder;
            }

            @Override
            public IBinder asBinder() {
                return this.mRemote;
            }
            @Override
            public int getFaceProcessMemory() throws RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IOplusFaceManager.DESCRIPTOR);
                    this.mRemote.transact(5, obtain, obtain2, 0);
                    obtain2.readException();
                    return obtain2.readInt();
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }
            @Override
            public int getFailedAttempts() throws RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IOplusFaceManager.DESCRIPTOR);
                    this.mRemote.transact(2, obtain, obtain2, 0);
                    obtain2.readException();
                    return obtain2.readInt();
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            public String getInterfaceDescriptor() {
                return IOplusFaceManager.DESCRIPTOR;
            }
            @Override
            public long getLockoutAttemptDeadline(int i) throws RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IOplusFaceManager.DESCRIPTOR);
                    obtain.writeInt(i);
                    this.mRemote.transact(1, obtain, obtain2, 0);
                    obtain2.readException();
                    return obtain2.readLong();
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }
            @Override
            public int regsiterFaceCmdCallback(IFaceCommandCallback iFaceCommandCallback) throws RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IOplusFaceManager.DESCRIPTOR);
                    obtain.writeStrongInterface(iFaceCommandCallback);
                    this.mRemote.transact(6, obtain, obtain2, 0);
                    obtain2.readException();
                    return obtain2.readInt();
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }
            @Override
            public void resetFaceDaemon() throws RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IOplusFaceManager.DESCRIPTOR);
                    this.mRemote.transact(4, obtain, obtain2, 0);
                    obtain2.readException();
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }
            @Override
            public int sendFaceCmd(int i, int i2, byte[] bArr) throws RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IOplusFaceManager.DESCRIPTOR);
                    obtain.writeInt(i);
                    obtain.writeInt(i2);
                    obtain.writeByteArray(bArr);
                    this.mRemote.transact(3, obtain, obtain2, 0);
                    obtain2.readException();
                    return obtain2.readInt();
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }

            @Override
            public int unregsiterFaceCmdCallback(IFaceCommandCallback iFaceCommandCallback) throws RemoteException {
                Parcel obtain = Parcel.obtain();
                Parcel obtain2 = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken(IOplusFaceManager.DESCRIPTOR);
                    obtain.writeStrongInterface(iFaceCommandCallback);
                    this.mRemote.transact(7, obtain, obtain2, 0);
                    obtain2.readException();
                    return obtain2.readInt();
                } finally {
                    obtain2.recycle();
                    obtain.recycle();
                }
            }
        }
    }
}
