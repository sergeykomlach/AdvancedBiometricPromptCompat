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

package dev.skomlach.biometric.app.utils

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.iot.cbor.CborMap

/*
[IANA] https://www.iana.org/assignments/uri-schemes/prov/fido

[DEMO] https://webauthn.io/

[CHROME] https://github.com/chromium/chromium/blob/50479218fc94681552b7ba2c526069141182a143/device/fido/cable/v2_handshake.cc#L441-L469
and
https://github.com/chromium/chromium/blob/50479218fc94681552b7ba2c526069141182a143/device/fido/cable/v2_handshake.h#L107-L127

[CBOR] https://www.rfc-editor.org/rfc/rfc8949.txt

[CBOR2] https://cbor.me/

[CTAP] https://fidoalliance.org/specs/fido-v2.1-ps-20210615/fido-client-to-authenticator-protocol-v2.1-ps-errata-20220621.html
Note that this is only version 2.1. Our QR code will only be specified in version 2.2 which is unfortunately not yet publicly available.

[WEBAUTHN] https://www.w3.org/TR/webauthn-2/

[BTC] https://btcinformation.org/en/developer-guide#public-key-formats
* */
object CborDecoder {
    private const val FIDO_SCHEME = "FIDO:/"
//    val input =
//        "530041661895476865661273834128964621807669533344792382326710152372905169673070313819044473172216635730196413921514565990072242051134369305604289772389122107096654083332"
//        "13086400838107303667332719012595115747821895775708323189557153075146383351399743589971313508078026948312026786722471666005727649643501784024544726574771401798171307406596245"
//    val link =
//        "FIDO:/$input"

    fun decodeFidoUrl(fidoUrl: String?): CborPayload? {
        try {
            if (fidoUrl == null || !fidoUrl.startsWith(FIDO_SCHEME, ignoreCase = true)) return null

            val input = fidoUrl.substring(FIDO_SCHEME.length, fidoUrl.length)
            decodeToMap(input)?.let {
                return Gson().fromJson(it.toJsonString(), CborPayload::class.java)
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
        return null
    }

    private fun decodeToMap(input: String): CborMap? {
        try {
            // and then read/write data as usual
            val byteArray = input
                .toCharArray()
                .toList()
                .chunked(17).flatMap { c ->
                    val s = c.joinToString("")
                    val n = when (s.length) {
                        3 -> 1
                        5 -> 2
                        8 -> 3
                        10 -> 4
                        13 -> 5
                        15 -> 6
                        17 -> 7
                        else -> throw Exception()
                    }
                    s.toULong().toByteArray().toList().take(n)
                }
                .toByteArray()

            val cborMap =
                CborMap.createFromCborByteArray(byteArray)
            // Prints out the line `toString: 55799({"a":1,"b":[2,3]})`
            println("toString: $cborMap")

            // Prints out the line `toJsonString: {"a":1,"b":[2,3]}`
            println("toJsonString: " + cborMap.toJsonString())

            return cborMap
        } catch (e: Throwable) {
            e.printStackTrace()
        }
        return null
    }

    private fun ULong.toByteArray(): ByteArray {
        val result = ByteArray(ULong.SIZE_BYTES)
        (0 until ULong.SIZE_BYTES).forEach {
            result[it] = this.shr(Byte.SIZE_BITS * it).toByte()
        }
        return result
    }

    data class CborPayload(
        @SerializedName("0") var compressed_public_key: String? = null,
        @SerializedName("1") var qr_secret: String? = null,
        @SerializedName("2") var num_known_domains: Int? = null,
        @SerializedName("3") var timestampt: Long? = null,
        @SerializedName("4") var supports_linking: Boolean? = null,
        @SerializedName("5") var request_type: String? = null,
        @SerializedName("6") var webAuthNonDiscoverable: Boolean? = null,
    )
}