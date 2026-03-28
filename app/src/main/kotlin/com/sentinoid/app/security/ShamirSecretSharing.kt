package com.sentinoid.app.security

import java.math.BigInteger
import java.security.SecureRandom

class ShamirSecretSharing {

    companion object {
        private const val PRIME_BIT_LENGTH = 257
        private val PRIME = BigInteger(
            "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141", 16
        )
    }

    data class Share(
        val x: BigInteger,
        val y: BigInteger,
        val threshold: Int,
        val totalShares: Int
    ) {
        fun toStringRepresentation(): String {
            return "${x.toString(16)}:${y.toString(16)}:${threshold}:${totalShares}"
        }

        companion object {
            fun fromString(representation: String): Share {
                val parts = representation.split(":")
                return Share(
                    x = BigInteger(parts[0], 16),
                    y = BigInteger(parts[1], 16),
                    threshold = parts[2].toInt(),
                    totalShares = parts[3].toInt()
                )
            }
        }
    }

    fun splitSecret(
        secret: ByteArray,
        threshold: Int,
        totalShares: Int
    ): List<Share> {
        require(threshold in 2..totalShares) { "Threshold must be between 2 and totalShares" }

        val secretInt = BigInteger(1, secret)
        require(secretInt < PRIME) { "Secret too large" }

        // Generate random polynomial coefficients
        val coefficients = mutableListOf(secretInt)
        repeat(threshold - 1) {
            coefficients.add(generateRandomCoefficient())
        }

        // Generate shares at points x = 1, 2, ..., totalShares
        return (1..totalShares).map { x ->
            val xBig = BigInteger.valueOf(x.toLong())
            val y = evaluatePolynomial(coefficients, xBig)
            Share(xBig, y, threshold, totalShares)
        }
    }

    fun reconstructSecret(shares: List<Share>): ByteArray {
        require(shares.size >= shares.first().threshold) { 
            "Not enough shares to reconstruct" 
        }

        val threshold = shares.first().threshold
        val relevantShares = shares.take(threshold)

        var secret = BigInteger.ZERO

        for (i in relevantShares.indices) {
            val shareI = relevantShares[i]
            var numerator = BigInteger.ONE
            var denominator = BigInteger.ONE

            for (j in relevantShares.indices) {
                if (i != j) {
                    val shareJ = relevantShares[j]
                    numerator = numerator.multiply(shareJ.x.negate()).mod(PRIME)
                    denominator = denominator.multiply(shareI.x.subtract(shareJ.x)).mod(PRIME)
                }
            }

            val lagrangeBasis = numerator.multiply(denominator.modInverse(PRIME)).mod(PRIME)
            secret = secret.add(shareI.y.multiply(lagrangeBasis)).mod(PRIME)
        }

        // Convert back to byte array, maintaining original length
        val result = secret.toByteArray()
        return if (result.size > 32) {
            result.copyOfRange(result.size - 32, result.size)
        } else {
            result
        }
    }

    private fun generateRandomCoefficient(): BigInteger {
        val random = SecureRandom()
        var coefficient: BigInteger
        do {
            coefficient = BigInteger(PRIME_BIT_LENGTH, random)
        } while (coefficient >= PRIME || coefficient == BigInteger.ZERO)
        return coefficient
    }

    private fun evaluatePolynomial(coefficients: List<BigInteger>, x: BigInteger): BigInteger {
        var result = BigInteger.ZERO
        var power = BigInteger.ONE

        for (coefficient in coefficients) {
            result = result.add(coefficient.multiply(power)).mod(PRIME)
            power = power.multiply(x).mod(PRIME)
        }

        return result
    }

    fun validateShares(shares: List<Share>): Boolean {
        if (shares.isEmpty()) return false

        val threshold = shares.first().threshold
        val totalShares = shares.first().totalShares

        return shares.size >= threshold && 
               shares.all { it.threshold == threshold && it.totalShares == totalShares }
    }
}
