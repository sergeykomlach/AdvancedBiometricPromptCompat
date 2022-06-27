package dev.skomlach.biometric.compat.crypto.rsa

import dev.skomlach.biometric.compat.crypto.rsa.BigIntHelper.bigInt2Bytes
import dev.skomlach.biometric.compat.crypto.rsa.BigIntHelper.divAndRoundUp
import dev.skomlach.biometric.compat.crypto.rsa.RsaKeyHeader.Companion.getHeaderLength
import java.math.BigInteger
import java.nio.ByteBuffer
import java.security.KeyFactory
import java.security.NoSuchAlgorithmException
import java.security.NoSuchProviderException
import java.security.interfaces.RSAPublicKey
import java.security.spec.InvalidKeySpecException
import java.security.spec.RSAPublicKeySpec

class RsaPublicKey(
    private val rsaKeyHeader: RsaKeyHeader,
    private val publicExponent: BigInteger,
    private val modulus: BigInteger
) {
    private val rsaPublicKeySpec: RSAPublicKeySpec = RSAPublicKeySpec(
        modulus,
        publicExponent
    )
    private lateinit var factory: KeyFactory

    @Throws(
        NoSuchAlgorithmException::class,
        InvalidKeySpecException::class,
        NoSuchProviderException::class
    )
    fun toRsaKey(): RSAPublicKey {
        if (!::factory.isInitialized) {
            synchronized(RsaPublicKey::class.java) {
                if (!::factory.isInitialized) {
                    factory = KeyFactory.getInstance("RSA")

                }
            }
        }
        return factory.generatePublic(rsaPublicKeySpec) as RSAPublicKey
    }

    fun toByteArray(sizeOfLong: Int): ByteArray {
        val data =
            ByteBuffer.allocate(getHeaderLength(sizeOfLong) + rsaKeyHeader.expectedDataLength)
        data.put(rsaKeyHeader.toByteArray(sizeOfLong))
        data.put(bigInt2Bytes(publicExponent, rsaKeyHeader.cbPublicExp))
        data.put(bigInt2Bytes(modulus, rsaKeyHeader.cbModulus))
        if (data.hasRemaining()) {
            throw RuntimeException("Some unexpected bytes remaining when converting public key to byte array")
        }
        return data.array()
    }

    companion object {
        fun fromRsaKey(rsaPublicKey: RSAPublicKey): RsaPublicKey {
            val header = RsaKeyHeader(
                RsaKeyHeader.MAGIC_RSAKEY_PUBLIC,
                rsaPublicKey.modulus.bitLength(),
                divAndRoundUp(rsaPublicKey.publicExponent.bitLength(), 8),
                divAndRoundUp(rsaPublicKey.modulus.bitLength(), 8),
                0,
                0
            )
            return RsaPublicKey(
                header,
                rsaPublicKey.publicExponent,
                rsaPublicKey.modulus
            )
        }

        @Throws(RsaKeyDataException::class)
        fun fromByteArray(bytes: ByteArray, sizeOfLong: Int): RsaPublicKey {
            val headerLength = getHeaderLength(sizeOfLong)
            if (bytes.size < headerLength) {
                throw RsaKeyDataException("RsaPrivateKey: Unexpected bytes length, expected at least: $headerLength")
            }
            val data = ByteBuffer.wrap(bytes)
            val headerBytes = ByteArray(headerLength)
            data[headerBytes]
            val header = RsaKeyHeader.fromByteArray(headerBytes, sizeOfLong)
            if (header.magic != RsaKeyHeader.MAGIC_RSAKEY_PUBLIC) {
                throw RsaKeyDataException("RsaPublicKey: Unexpected magic byte in header: " + header.magic + ". Expected " + RsaKeyHeader.MAGIC_RSAKEY_PUBLIC)
            }
            val expectedLength = header.expectedDataLength
            if (expectedLength != bytes.size - headerLength) {
                throw RsaKeyDataException(
                    "RsaPublicKey: Unexpected bytes length: " + (bytes.size - headerLength)
                            + ". Expected " + header.expectedDataLength
                )
            }
            var bArr = ByteArray(header.cbPublicExp)
            data[bArr]
            val publicExp = BigInteger(1, bArr)
            bArr = ByteArray(header.cbModulus)
            data[bArr]
            val modulus = BigInteger(1, bArr)
            if (data.hasRemaining()) {
                throw RuntimeException("RsaPublicKey: Not all bytes has been read from input")
            }
            return RsaPublicKey(header, publicExp, modulus)
        }
    }

}