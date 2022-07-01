package dev.skomlach.biometric.compat.crypto.rsa

import dev.skomlach.biometric.compat.crypto.rsa.BigIntHelper.bigInt2Bytes
import dev.skomlach.biometric.compat.crypto.rsa.BigIntHelper.divAndRoundUp
import dev.skomlach.biometric.compat.crypto.rsa.RsaKeyHeader.Companion.getHeaderLength
import java.math.BigInteger
import java.nio.ByteBuffer
import java.security.KeyFactory
import java.security.NoSuchAlgorithmException
import java.security.NoSuchProviderException
import java.security.interfaces.RSAPrivateCrtKey
import java.security.spec.InvalidKeySpecException
import java.security.spec.RSAPrivateCrtKeySpec

class RsaPrivateKey private constructor(
    private val rsaKeyHeader: RsaKeyHeader,
    private val publicExponent: BigInteger,
    private val modulus: BigInteger,
    private val prime1: BigInteger,
    private val prime2: BigInteger,
    private val exponent1: BigInteger,
    private val exponent2: BigInteger,
    private val coefficient: BigInteger,
    private val privateExponent: BigInteger
) {
    private val rsaPrivateCrtKeySpec: RSAPrivateCrtKeySpec = RSAPrivateCrtKeySpec(
        modulus,
        publicExponent,
        privateExponent,
        prime1,
        prime2,
        exponent1,
        exponent2,
        coefficient
    )
    private lateinit var factory: KeyFactory

    /*
        private static BigInteger getPrivateExponent(BigInteger e, BigInteger p, BigInteger q) {
            BigInteger pSub1 = p.subtract(BigInteger.ONE);
            BigInteger qSub1 = q.subtract(BigInteger.ONE);
            BigInteger phi = pSub1.multiply(qSub1);
            return e.modInverse(phi);
        }
    */
    @Throws(
        NoSuchAlgorithmException::class,
        InvalidKeySpecException::class,
        NoSuchProviderException::class
    )
    fun toRsaKey(): RSAPrivateCrtKey {
        if (!::factory.isInitialized) {
            synchronized(RsaPrivateKey::class.java) {
                if (!::factory.isInitialized) {
                    factory = KeyFactory.getInstance("RSA")
                }
            }
        }
        return factory.generatePrivate(rsaPrivateCrtKeySpec) as RSAPrivateCrtKey
    }

    fun toByteArray(sizeOfLong: Int): ByteArray {
        val headerLength = getHeaderLength(sizeOfLong)
        val data = ByteBuffer.allocate(headerLength + rsaKeyHeader.expectedDataLength)
        data.put(rsaKeyHeader.toByteArray(sizeOfLong))
        data.put(bigInt2Bytes(publicExponent, rsaKeyHeader.cbPublicExp))
        data.put(bigInt2Bytes(modulus, rsaKeyHeader.cbModulus))
        data.put(bigInt2Bytes(prime1, rsaKeyHeader.cbPrime1))
        data.put(bigInt2Bytes(prime2, rsaKeyHeader.cbPrime2))
        data.put(bigInt2Bytes(exponent1, rsaKeyHeader.cbPrime1))
        data.put(bigInt2Bytes(exponent2, rsaKeyHeader.cbPrime2))
        data.put(bigInt2Bytes(coefficient, rsaKeyHeader.cbPrime1))
        data.put(bigInt2Bytes(privateExponent, rsaKeyHeader.cbModulus))
        if (data.hasRemaining()) {
            throw RuntimeException("Not all data has been written to output")
        }
        return data.array()
    }

    companion object {
        fun fromRsaKey(rsaPrivateCrtKey: RSAPrivateCrtKey): RsaPrivateKey {
            val header = RsaKeyHeader(
                RsaKeyHeader.MAGIC_RSAKEY_PRIVATE,
                rsaPrivateCrtKey.modulus.bitLength(),
                divAndRoundUp(rsaPrivateCrtKey.publicExponent.bitLength(), 8),
                divAndRoundUp(rsaPrivateCrtKey.modulus.bitLength(), 8),
                divAndRoundUp(rsaPrivateCrtKey.primeP.bitLength(), 8),
                divAndRoundUp(rsaPrivateCrtKey.primeQ.bitLength(), 8)
            )
            return RsaPrivateKey(
                header,
                rsaPrivateCrtKey.publicExponent,
                rsaPrivateCrtKey.modulus,
                rsaPrivateCrtKey.primeP,
                rsaPrivateCrtKey.primeQ,
                rsaPrivateCrtKey.primeExponentP,
                rsaPrivateCrtKey.primeExponentQ,
                rsaPrivateCrtKey.crtCoefficient,
                rsaPrivateCrtKey.privateExponent
            )
        }

        @Throws(RsaKeyDataException::class)
        fun fromByteArray(bytes: ByteArray, sizeOfLong: Int): RsaPrivateKey {
            val headerSize = getHeaderLength(sizeOfLong)
            if (bytes.size < headerSize) {
                throw RsaKeyDataException("RsaPrivateKey: Unexpected bytes length, expected at least: $headerSize")
            }
            val data = ByteBuffer.wrap(bytes)
            val headerBytes = ByteArray(headerSize)
            data[headerBytes]
            val header = RsaKeyHeader.fromByteArray(headerBytes, sizeOfLong)
            if (header.magic != RsaKeyHeader.MAGIC_RSAKEY_PRIVATE) {
                throw RsaKeyDataException("RsaPrivateKey: Unexpected magic byte in header: " + header.magic + ". Expected " + RsaKeyHeader.MAGIC_RSAKEY_PRIVATE)
            }
            val expectedLength = header.expectedDataLength
            if (expectedLength != bytes.size - headerSize) {
                throw RsaKeyDataException(
                    "RsaPrivateKey: Unexpected bytes length: " + (bytes.size - headerSize)
                            + ". Expected " + header.expectedDataLength
                )
            }
            var bArr = ByteArray(header.cbPublicExp)
            data[bArr]
            val publicExp = BigInteger(1, bArr)
            bArr = ByteArray(header.cbModulus)
            data[bArr]
            val modulus = BigInteger(1, bArr)
            bArr = ByteArray(header.cbPrime1)
            data[bArr]
            val prime1 = BigInteger(1, bArr)
            bArr = ByteArray(header.cbPrime2)
            data[bArr]
            val prime2 = BigInteger(1, bArr)
            bArr = ByteArray(header.cbPrime1)
            data[bArr]
            val exponent1 = BigInteger(1, bArr)
            bArr = ByteArray(header.cbPrime2)
            data[bArr]
            val exponent2 = BigInteger(1, bArr)
            bArr = ByteArray(header.cbPrime1)
            data[bArr]
            val coefficient = BigInteger(1, bArr)
            bArr = ByteArray(header.cbModulus)
            data[bArr]
            val privateExponent = BigInteger(1, bArr)
            if (data.hasRemaining()) {
                throw RuntimeException("RsaPrivateKey: Not all bytes has been read from input")
            }
            return RsaPrivateKey(
                header,
                publicExp,
                modulus,
                prime1,
                prime2,
                exponent1,
                exponent2,
                coefficient,
                privateExponent
            )
        }
    }

}