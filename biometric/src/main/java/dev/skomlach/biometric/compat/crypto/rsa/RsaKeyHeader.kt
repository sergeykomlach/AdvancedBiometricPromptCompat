package dev.skomlach.biometric.compat.crypto.rsa

import java.nio.ByteBuffer
import java.nio.ByteOrder

class RsaKeyHeader(//length of hoeder
    //public static final int BYTES_LENGTH = 6 * Integer.SIZE/ Byte.SIZE;//Integer.BYTES available only on Android N+
    //bytes of header
    val magic // RSAKEY_PUBLIC or RSAKEY_PRIVATE
    : Int, val bitLength: Int, // size of public exponent in bytes
    val cbPublicExp: Int, // size of modulus in bytes
    val cbModulus: Int, // size of prime1 in bytes
    val cbPrime1: Int, // size of prime2 in bytes
    val cbPrime2: Int
) {

    //exponent1
    //exponent2
    //coefficient
    //privateExponent
    val expectedDataLength: Int
        get() = when (magic) {
            MAGIC_RSAKEY_PUBLIC -> cbPublicExp + cbModulus

            MAGIC_RSAKEY_PRIVATE -> cbPublicExp +
                    cbModulus + cbPrime1 + cbPrime2 +
                    cbPrime1 /*exponent1*/ +
                    cbPrime2 /*exponent2*/ +
                    cbPrime1 /*coefficient*/ +
                    cbModulus //privateExponent
            else -> throw IllegalStateException("Cannot determine expected key length for magic $magic")
        }

    fun toByteArray32(): ByteArray {
        val buffer = ByteBuffer.allocate(getHeaderLength(4))
        //header is written in native order
        buffer.order(ByteOrder.nativeOrder())
        buffer.putInt(magic)
        buffer.putInt(bitLength)
        buffer.putInt(cbPublicExp)
        buffer.putInt(cbModulus)
        buffer.putInt(cbPrime1)
        buffer.putInt(cbPrime2)
        return buffer.array()
    }

    fun toByteArray64(): ByteArray {
        val buffer = ByteBuffer.allocate(getHeaderLength(8))
        //header is written in native order
        buffer.order(ByteOrder.nativeOrder())
        buffer.putLong(magic.toLong())
        buffer.putLong(bitLength.toLong())
        buffer.putLong(cbPublicExp.toLong())
        buffer.putLong(cbModulus.toLong())
        buffer.putLong(cbPrime1.toLong())
        buffer.putLong(cbPrime2.toLong())
        return buffer.array()
    }

    /**
     * to Byte array is called from C code
     *
     * @param sizeOfLong
     * @return
     */
    fun toByteArray(sizeOfLong: Int): ByteArray {
        return when (sizeOfLong) {
            4 -> toByteArray32()
            8 -> toByteArray64()
            else -> throw IllegalStateException("Unexpected size of int $sizeOfLong")
        }
    }

    companion object {
        const val KEY_SIZE = 2048
        const val MAGIC_RSAKEY_PUBLIC = 826364754
        const val MAGIC_RSAKEY_PRIVATE = 859919186

        @JvmStatic
        @Throws(RsaKeyDataException::class)
        fun fromByteArray(bytes: ByteArray, sizeOfLong: Int): RsaKeyHeader {
            return when (sizeOfLong) {
                4 -> fromByteArray32(bytes)
                8 -> fromByteArray64(bytes)
                else -> throw IllegalStateException("Unexpected size of int $sizeOfLong")
            }
        }

        @Throws(RsaKeyDataException::class)
        fun fromByteArray32(bytes: ByteArray): RsaKeyHeader {
            if (bytes.size != getHeaderLength(4)) {
                throw RsaKeyDataException(
                    "RsaKeyHeader unexpected data length " + bytes.size + ". Expected " + getHeaderLength(
                        4
                    )
                )
            }
            val buffer = ByteBuffer.wrap(bytes)
            buffer.order(ByteOrder.nativeOrder())
            return RsaKeyHeader(
                buffer.int,
                buffer.int,
                buffer.int,
                buffer.int,
                buffer.int,
                buffer.int
            )
        }

        @Throws(RsaKeyDataException::class)
        fun fromByteArray64(bytes: ByteArray): RsaKeyHeader {
            if (bytes.size != getHeaderLength(8)) {
                throw RsaKeyDataException(
                    "RsaKeyHeader unexpected data length " + bytes.size + ". Expected " + getHeaderLength(
                        8
                    )
                )
            }
            val buffer = ByteBuffer.wrap(bytes)
            buffer.order(ByteOrder.nativeOrder())
            return RsaKeyHeader(
                buffer.long.toInt(),
                buffer.long.toInt(),
                buffer.long.toInt(),
                buffer.long.toInt(),
                buffer.long.toInt(),
                buffer.long.toInt()
            )
        }

        @JvmStatic
        fun getHeaderLength(sizeOfLong: Int): Int {
            return sizeOfLong * 6
        }
    }
}