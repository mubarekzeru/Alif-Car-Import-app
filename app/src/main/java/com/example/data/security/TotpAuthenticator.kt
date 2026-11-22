package com.example.data.security

import java.net.URLEncoder
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.pow

/**
 * Standard RFC 6238 / RFC 4226 Time-Based One-Time Password (TOTP) engine
 * compatible with Google Authenticator (HMAC-SHA1, 6 digits, 30-second window).
 */
object TotpAuthenticator {

    const val DEFAULT_ISSUER = "Alif Car Import"
    const val DEFAULT_DIGITS = 6
    const val DEFAULT_PERIOD_SECONDS = 30L
    private const val BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    private val secureRandom = SecureRandom()

    fun generateSecretBase32(byteCount: Int = 20): String {
        val bytes = ByteArray(byteCount.coerceIn(10, 40))
        secureRandom.nextBytes(bytes)
        return encodeBase32(bytes)
    }

    fun formatSecretForDisplay(secretBase32: String): String {
        val clean = normalizeBase32(secretBase32)
        return clean.chunked(4).joinToString(" ")
    }

    fun normalizeBase32(raw: String): String =
        raw.uppercase(Locale.US).filter { it in BASE32_ALPHABET }

    fun sanitizeCodeInput(raw: String): String =
        raw.filter { it.isDigit() }

    fun isValidSecretBase32(secretBase32: String): Boolean {
        val clean = normalizeBase32(secretBase32)
        return clean.length in 16..64 && clean == secretBase32
    }

    fun buildOtpAuthUri(
        accountEmail: String,
        secretBase32: String,
        issuer: String = DEFAULT_ISSUER,
        digits: Int = DEFAULT_DIGITS,
        periodSeconds: Long = DEFAULT_PERIOD_SECONDS
    ): String {
        val cleanSecret = normalizeBase32(secretBase32)
        val cleanIssuer = issuer.trim().ifBlank { DEFAULT_ISSUER }
        val cleanAccount = accountEmail.trim().ifBlank { "admin" }
        val encodedIssuer = urlEncodePathSegment(cleanIssuer)
        val encodedAccount = urlEncodePathSegment(cleanAccount)
        val label = "$encodedIssuer:$encodedAccount"
        return "otpauth://totp/$label" +
            "?secret=$cleanSecret" +
            "&issuer=$encodedIssuer" +
            "&algorithm=SHA1" +
            "&digits=$digits" +
            "&period=$periodSeconds"
    }

    fun generateCode(
        secretBase32: String,
        timeMillis: Long = System.currentTimeMillis(),
        periodSeconds: Long = DEFAULT_PERIOD_SECONDS,
        digits: Int = DEFAULT_DIGITS
    ): String {
        val keyBytes = decodeBase32(secretBase32)
        require(keyBytes.isNotEmpty()) { "Invalid Base32 secret key." }
        val counter = Math.floorDiv(timeMillis / 1000L, periodSeconds)
        return generateHotp(keyBytes, counter, digits)
    }

    fun verifyCode(
        secretBase32: String,
        userCode: String,
        timeMillis: Long = System.currentTimeMillis(),
        allowedDriftSteps: Int = 1,
        periodSeconds: Long = DEFAULT_PERIOD_SECONDS,
        digits: Int = DEFAULT_DIGITS
    ): Boolean {
        val cleanCode = sanitizeCodeInput(userCode)
        if (cleanCode.length != digits) return false

        val keyBytes = runCatching { decodeBase32(secretBase32) }.getOrNull() ?: return false
        if (keyBytes.isEmpty()) return false

        val currentStep = Math.floorDiv(timeMillis / 1000L, periodSeconds)
        val expectedBytes = cleanCode.toByteArray(StandardCharsets.US_ASCII)

        var matched = false
        for (offset in -allowedDriftSteps..allowedDriftSteps) {
            val candidate = generateHotp(keyBytes, currentStep + offset, digits)
            val candidateBytes = candidate.toByteArray(StandardCharsets.US_ASCII)
            if (MessageDigest.isEqual(expectedBytes, candidateBytes)) {
                matched = true
            }
        }
        return matched
    }

    private fun generateHotp(keyBytes: ByteArray, counter: Long, digits: Int): String {
        val counterBytes = ByteBuffer.allocate(8).putLong(counter).array()
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(keyBytes, "HmacSHA1"))
        val hash = mac.doFinal(counterBytes)

        val offset = hash[hash.size - 1].toInt() and 0x0F
        val binary =
            ((hash[offset].toInt() and 0x7F) shl 24) or
                ((hash[offset + 1].toInt() and 0xFF) shl 16) or
                ((hash[offset + 2].toInt() and 0xFF) shl 8) or
                (hash[offset + 3].toInt() and 0xFF)

        val modulo = 10.0.pow(digits).toInt()
        val otp = binary % modulo
        return otp.toString().padStart(digits, '0')
    }

    fun encodeBase32(data: ByteArray): String {
        if (data.isEmpty()) return ""
        val out = StringBuilder((data.size * 8 + 4) / 5)
        var buffer = 0
        var bitsLeft = 0
        for (b in data) {
            buffer = (buffer shl 8) or (b.toInt() and 0xFF)
            bitsLeft += 8
            while (bitsLeft >= 5) {
                val index = (buffer shr (bitsLeft - 5)) and 0x1F
                out.append(BASE32_ALPHABET[index])
                bitsLeft -= 5
            }
        }
        if (bitsLeft > 0) {
            val index = (buffer shl (5 - bitsLeft)) and 0x1F
            out.append(BASE32_ALPHABET[index])
        }
        return out.toString()
    }

    fun decodeBase32(encoded: String): ByteArray {
        val clean = encoded
            .uppercase(Locale.US)
            .replace("=", "")
            .replace(" ", "")
            .replace("-", "")
        if (clean.isEmpty()) return ByteArray(0)

        val out = ByteArray((clean.length * 5) / 8)
        var buffer = 0
        var bitsLeft = 0
        var outIndex = 0
        for (ch in clean) {
            val value = BASE32_ALPHABET.indexOf(ch)
            require(value >= 0) { "Invalid Base32 character: $ch" }
            buffer = (buffer shl 5) or value
            bitsLeft += 5
            if (bitsLeft >= 8) {
                out[outIndex++] = ((buffer shr (bitsLeft - 8)) and 0xFF).toByte()
                bitsLeft -= 8
            }
        }
        return if (outIndex == out.size) out else out.copyOf(outIndex)
    }

    private fun urlEncodePathSegment(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name())
            .replace("+", "%20")
}
