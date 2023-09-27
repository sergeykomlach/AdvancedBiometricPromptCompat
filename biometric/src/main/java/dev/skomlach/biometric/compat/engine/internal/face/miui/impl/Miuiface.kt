/*
 *  Copyright (c) 2023 Sergey Komlach aka Salat-Cx65; Original project https://github.com/Salat-Cx65/AdvancedBiometricPromptCompat
 *  All rights reserved.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package dev.skomlach.biometric.compat.engine.internal.face.miui.impl

import android.os.Parcel
import android.os.Parcelable
import android.os.Parcelable.Creator

class Miuiface : Parcelable {

    companion object {
        @JvmField
        val CREATOR: Creator<Miuiface> = object : Creator<Miuiface> {
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