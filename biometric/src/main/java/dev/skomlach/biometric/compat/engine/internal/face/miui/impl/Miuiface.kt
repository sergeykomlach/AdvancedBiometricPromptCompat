package dev.skomlach.biometric.compat.engine.internal.face.miui.impl

import android.os.Parcel
import android.os.Parcelable
import android.os.Parcelable.Creator

class Miuiface : Parcelable {

    companion object {
        @JvmField val CREATOR: Creator<Miuiface> = object : Creator<Miuiface> {
            override fun createFromParcel(p: Parcel): Miuiface {
                return Miuiface(p)
            }

            override fun newArray(size: Int): Array<Miuiface?> {
                return arrayOfNulls(size)
            }
        }
    }

    val deviceId: Long
    val groupId: Int
    val miuifaceId: Int
    val name: CharSequence?

    constructor(name: CharSequence?, groupId: Int, miuifaceId: Int, deviceId: Long) {
        this.name = name
        this.groupId = groupId
        this.miuifaceId = miuifaceId
        this.deviceId = deviceId
    }

    private constructor(p: Parcel) {
        name = p.readString()
        groupId = p.readInt()
        miuifaceId = p.readInt()
        deviceId = p.readLong()
    }

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(out: Parcel, flags: Int) {
        out.writeString(name.toString())
        out.writeInt(groupId)
        out.writeInt(miuifaceId)
        out.writeLong(deviceId)
    }
}