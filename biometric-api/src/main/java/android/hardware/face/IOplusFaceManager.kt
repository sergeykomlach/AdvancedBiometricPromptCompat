package android.hardware.face

import android.os.*
import androidx.annotation.RequiresApi

@RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
interface IOplusFaceManager : IInterface {
    companion object {
        val interfaceDescriptor: String = "android.hardware.face.IOplusFaceManager"
    }

    @get:Throws(RemoteException::class)
    val faceProcessMemory: Int

    @get:Throws(RemoteException::class)
    val failedAttempts: Int

    @Throws(RemoteException::class)
    fun getLockoutAttemptDeadline(i: Int): Long

    @Throws(RemoteException::class)
    fun regsiterFaceCmdCallback(iFaceCommandCallback: IFaceCommandCallback?): Int

    @Throws(RemoteException::class)
    fun resetFaceDaemon()

    @Throws(RemoteException::class)
    fun sendFaceCmd(i: Int, i2: Int, bArr: ByteArray?): Int

    @Throws(RemoteException::class)
    fun unregsiterFaceCmdCallback(iFaceCommandCallback: IFaceCommandCallback?): Int

    class Default : IOplusFaceManager {
        override fun asBinder(): IBinder? {
            return null
        }

        @get:Throws(RemoteException::class)
        override val faceProcessMemory: Int
            get() {
                return 0
            }

        @get:Throws(RemoteException::class)
        override val failedAttempts: Int
            get() {
                return 0
            }

        @Throws(RemoteException::class)
        override fun getLockoutAttemptDeadline(i: Int): Long {
            return 0
        }

        @Throws(RemoteException::class)
        override fun regsiterFaceCmdCallback(iFaceCommandCallback: IFaceCommandCallback?): Int {
            return 0
        }

        @Throws(RemoteException::class)
        override fun resetFaceDaemon() {
        }

        @Throws(RemoteException::class)
        override fun sendFaceCmd(i: Int, i2: Int, bArr: ByteArray?): Int {
            return 0
        }

        @Throws(RemoteException::class)
        override fun unregsiterFaceCmdCallback(iFaceCommandCallback: IFaceCommandCallback?): Int {
            return 0
        }
    }

    abstract class Stub : Binder(), IOplusFaceManager {
        init {
            attachInterface(this, IOplusFaceManager.interfaceDescriptor)
        }

        override fun asBinder(): IBinder {
            return this
        }

        @Throws(RemoteException::class)
        public override fun onTransact(i: Int, parcel: Parcel, parcel2: Parcel?, i2: Int): Boolean {
            if (i in 1..16777215) {
                parcel.enforceInterface(IOplusFaceManager.interfaceDescriptor)
            }
            return when (i) {
                1598968902 -> {
                    parcel2?.writeString(IOplusFaceManager.interfaceDescriptor)
                    true
                }

                else -> when (i) {
                    1 -> {
                        val readInt = parcel.readInt()
                        parcel.enforceNoDataAvail()
                        val lockoutAttemptDeadline = getLockoutAttemptDeadline(readInt)
                        parcel2?.writeNoException()
                        parcel2?.writeLong(lockoutAttemptDeadline)
                        true
                    }

                    2 -> {
                        val failedAttempts = failedAttempts
                        parcel2?.writeNoException()
                        parcel2?.writeInt(failedAttempts)
                        true
                    }

                    3 -> {
                        val readInt2 = parcel.readInt()
                        val readInt3 = parcel.readInt()
                        val createByteArray = parcel.createByteArray()
                        parcel.enforceNoDataAvail()
                        val sendFaceCmd = sendFaceCmd(readInt2, readInt3, createByteArray)
                        parcel2?.writeNoException()
                        parcel2?.writeInt(sendFaceCmd)
                        true
                    }

                    4 -> {
                        resetFaceDaemon()
                        parcel2?.writeNoException()
                        true
                    }

                    5 -> {
                        val faceProcessMemory = faceProcessMemory
                        parcel2?.writeNoException()
                        parcel2?.writeInt(faceProcessMemory)
                        true
                    }

                    6 -> {
                        val asInterface =
                            IFaceCommandCallback.Stub.asInterface(parcel.readStrongBinder())
                        parcel.enforceNoDataAvail()
                        val regsiterFaceCmdCallback = regsiterFaceCmdCallback(asInterface)
                        parcel2?.writeNoException()
                        parcel2?.writeInt(regsiterFaceCmdCallback)
                        true
                    }

                    7 -> {
                        val asInterface2 =
                            IFaceCommandCallback.Stub.asInterface(parcel.readStrongBinder())
                        parcel.enforceNoDataAvail()
                        val unregsiterFaceCmdCallback = unregsiterFaceCmdCallback(asInterface2)
                        parcel2?.writeNoException()
                        parcel2?.writeInt(unregsiterFaceCmdCallback)
                        true
                    }

                    else -> super.onTransact(i, parcel, parcel2, i2)
                }
            }
        }

        private class Proxy(private val mRemote: IBinder) : IOplusFaceManager {
            override fun asBinder(): IBinder? {
                return mRemote
            }

            @get:Throws(RemoteException::class)
            override val faceProcessMemory: Int
                get() {
                    val obtain = Parcel.obtain()
                    val obtain2 = Parcel.obtain()
                    return try {
                        obtain.writeInterfaceToken(interfaceDescriptor)
                        mRemote.transact(5, obtain, obtain2, 0)
                        obtain2.readException()
                        obtain2.readInt()
                    } finally {
                        obtain2.recycle()
                        obtain.recycle()
                    }
                }

            @get:Throws(RemoteException::class)
            override val failedAttempts: Int
                get() {
                    val obtain = Parcel.obtain()
                    val obtain2 = Parcel.obtain()
                    return try {
                        obtain.writeInterfaceToken(interfaceDescriptor)
                        mRemote.transact(2, obtain, obtain2, 0)
                        obtain2.readException()
                        obtain2.readInt()
                    } finally {
                        obtain2.recycle()
                        obtain.recycle()
                    }
                }

            @Throws(RemoteException::class)
            override fun getLockoutAttemptDeadline(i: Int): Long {
                val obtain = Parcel.obtain()
                val obtain2 = Parcel.obtain()
                return try {
                    obtain.writeInterfaceToken(interfaceDescriptor)
                    obtain.writeInt(i)
                    mRemote.transact(1, obtain, obtain2, 0)
                    obtain2.readException()
                    obtain2.readLong()
                } finally {
                    obtain2.recycle()
                    obtain.recycle()
                }
            }

            @Throws(RemoteException::class)
            override fun regsiterFaceCmdCallback(iFaceCommandCallback: IFaceCommandCallback?): Int {
                val obtain = Parcel.obtain()
                val obtain2 = Parcel.obtain()
                return try {
                    obtain.writeInterfaceToken(interfaceDescriptor)
                    obtain.writeStrongInterface(iFaceCommandCallback)
                    mRemote.transact(6, obtain, obtain2, 0)
                    obtain2.readException()
                    obtain2.readInt()
                } finally {
                    obtain2.recycle()
                    obtain.recycle()
                }
            }

            @Throws(RemoteException::class)
            override fun resetFaceDaemon() {
                val obtain = Parcel.obtain()
                val obtain2 = Parcel.obtain()
                try {
                    obtain.writeInterfaceToken(interfaceDescriptor)
                    mRemote.transact(4, obtain, obtain2, 0)
                    obtain2.readException()
                } finally {
                    obtain2.recycle()
                    obtain.recycle()
                }
            }

            @Throws(RemoteException::class)
            override fun sendFaceCmd(i: Int, i2: Int, bArr: ByteArray?): Int {
                val obtain = Parcel.obtain()
                val obtain2 = Parcel.obtain()
                return try {
                    obtain.writeInterfaceToken(interfaceDescriptor)
                    obtain.writeInt(i)
                    obtain.writeInt(i2)
                    obtain.writeByteArray(bArr)
                    mRemote.transact(3, obtain, obtain2, 0)
                    obtain2.readException()
                    obtain2.readInt()
                } finally {
                    obtain2.recycle()
                    obtain.recycle()
                }
            }

            @Throws(RemoteException::class)
            override fun unregsiterFaceCmdCallback(iFaceCommandCallback: IFaceCommandCallback?): Int {
                val obtain = Parcel.obtain()
                val obtain2 = Parcel.obtain()
                return try {
                    obtain.writeInterfaceToken(interfaceDescriptor)
                    obtain.writeStrongInterface(iFaceCommandCallback)
                    mRemote.transact(7, obtain, obtain2, 0)
                    obtain2.readException()
                    obtain2.readInt()
                } finally {
                    obtain2.recycle()
                    obtain.recycle()
                }
            }
        }

        companion object {
            const val TRANSACTION_getFaceProcessMemory = 5
            const val TRANSACTION_getFailedAttempts = 2
            const val TRANSACTION_getLockoutAttemptDeadline = 1
            const val TRANSACTION_regsiterFaceCmdCallback = 6
            const val TRANSACTION_resetFaceDaemon = 4
            const val TRANSACTION_sendFaceCmd = 3
            const val TRANSACTION_unregsiterFaceCmdCallback = 7
            fun asInterface(iBinder: IBinder?): IOplusFaceManager? {
                if (iBinder == null) {
                    return null
                }
                val queryLocalInterface = iBinder.queryLocalInterface(interfaceDescriptor)
                return if (queryLocalInterface == null || queryLocalInterface !is IOplusFaceManager) Proxy(
                    iBinder
                ) else queryLocalInterface
            }
        }
    }

}