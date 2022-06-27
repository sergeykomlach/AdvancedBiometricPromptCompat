package dev.skomlach.biometric.compat.crypto.rsa

import java.math.BigInteger
import java.nio.ByteBuffer

object BigIntHelper {
    @JvmStatic
    fun bigInt2Bytes(bigInteger: BigInteger, resultLength: Int): ByteArray {
        val bb = ByteBuffer.allocate(resultLength)
        val bArr = bigInt2Bytes(bigInteger)
        bb.position(resultLength - bArr.size)
        bb.put(bArr)
        return bb.array()
    }

    //no sign
    private fun bigInt2Bytes(bigInt: BigInteger): ByteArray {
        var bitLength = bigInt.bitLength()
        // round bitLength
        bitLength = bitLength + 7 shr 3 shl 3
        val bigBytes = bigInt.toByteArray()
        if (bigInt.bitLength() % 8 != 0 && bigInt.bitLength() / 8 + 1 == bitLength / 8) {
            return bigBytes
        }
        // set up params for copying everything but sign bit
        var startSrc = 0
        var len = bigBytes.size

        // if bigInt is exactly byte-aligned, just skip signbit in copy
        if (bigInt.bitLength() % 8 == 0) {
            startSrc = 1
            len--
        }
        val startDst = bitLength / 8 - len // to pad w/ nulls as per spec
        val resizedBytes = ByteArray(bitLength / 8)
        System.arraycopy(bigBytes, startSrc, resizedBytes, startDst, len)
        return resizedBytes
    }

    @JvmStatic
    fun divAndRoundUp(num: Int, divisor: Int): Int {
        return (num + divisor - 1) / divisor
    }
}