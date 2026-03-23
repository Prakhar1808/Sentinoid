package com.sentinoid.shield

import android.util.Base64
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cash.z.ecc.android.bip39.Mnemonics
import java.security.SecureRandom
import java.util.*

object OfflineRecovery {

    private val random = SecureRandom()

    /**
     * Generates a 24-word recovery phrase.
     */
    fun generateMnemonic(): String {
        val mnemonic = Mnemonics.MnemonicCode(Mnemonics.WordCount.COUNT_24)
        val phrase = mnemonic.words.joinToString(" ") { String(it) }
        mnemonic.clear()
        return phrase
    }

    /**
     * Splits a secret string into N shares, requiring T shares to reconstruct.
     * Uses a Shamir Secret Sharing implementation over GF(256).
     */
    fun splitSecret(secret: String, shares: Int, threshold: Int): List<String> {
        if (threshold > shares) throw IllegalArgumentException("Threshold cannot be greater than shares")
        val secretBytes = secret.toByteArray()
        val results = Array(shares) { ByteArray(secretBytes.size) }

        for (i in secretBytes.indices) {
            val poly = ByteArray(threshold)
            poly[0] = secretBytes[i]
            for (j in 1 until threshold) {
                poly[j] = random.nextInt(256).toByte()
            }

            for (x in 1..shares) {
                results[x - 1][i] = evaluatePolynomial(poly, x)
            }
        }

        return results.mapIndexed { index, bytes ->
            "${index + 1}:" + Base64.encodeToString(bytes, Base64.NO_WRAP)
        }
    }

    /**
     * Reconstructs the secret from a list of shares.
     */
    fun recoverSecret(shares: List<String>): String {
        if (shares.isEmpty()) return ""
        
        val decodedShares = try {
            shares.map {
                val parts = it.split(":")
                val x = parts[0].toInt()
                val y = Base64.decode(parts[1], Base64.DEFAULT)
                x to y
            }
        } catch (e: Exception) {
            return ""
        }

        val secretSize = decodedShares[0].second.size
        val secretBytes = ByteArray(secretSize)

        for (i in 0 until secretSize) {
            val points = decodedShares.map { it.first to (it.second[i].toInt() and 0xFF) }
            secretBytes[i] = interpolate(points).toByte()
        }

        return String(secretBytes)
    }

    // GF(256) implementation details
    private val exp = IntArray(512)
    private val log = IntArray(256)

    init {
        var x = 1
        for (i in 0 until 255) {
            exp[i] = x
            exp[i + 255] = x
            log[x] = i
            x = x shl 1
            if (x >= 256) x = x xor 0x11D
        }
    }

    private fun mul(a: Int, b: Int): Int {
        if (a == 0 || b == 0) return 0
        return exp[log[a] + log[b]]
    }

    private fun div(a: Int, b: Int): Int {
        if (a == 0) return 0
        if (b == 0) throw ArithmeticException("Division by zero")
        return exp[log[a] + 255 - log[b]]
    }

    private fun evaluatePolynomial(poly: ByteArray, x: Int): Byte {
        var result = 0
        var xPow = 1
        for (coeff in poly) {
            result = result xor mul(coeff.toInt() and 0xFF, xPow)
            xPow = mul(xPow, x)
        }
        return result.toByte()
    }

    private fun interpolate(points: List<Pair<Int, Int>>): Int {
        var result = 0
        for (i in points.indices) {
            var basis = 1
            for (j in points.indices) {
                if (i == j) continue
                val num = points[j].first
                val den = points[j].first xor points[i].first
                basis = mul(basis, div(num, den))
            }
            result = result xor mul(basis, points[i].second)
        }
        return result
    }
}

@Composable
fun BIP39Words(mnemonic: String, onProceed: () -> Unit) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text("Write down your 24-word recovery phrase:")
        Spacer(modifier = Modifier.height(16.dp))
        Text(mnemonic)
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onProceed) {
            Text("Proceed")
        }
    }
}
