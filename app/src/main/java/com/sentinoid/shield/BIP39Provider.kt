package com.sentinoid.shield

import cash.z.ecc.android.bip39.Mnemonics

fun generateRecoveryPhrase(): List<String> {
    // Generates 256 bits of entropy for a 24-word phrase
    val mnemonic = Mnemonics.MnemonicCode(Mnemonics.WordCount.COUNT_24)
    val phrase = mnemonic.words.map { String(it) } // This is your 24 words

    mnemonic.clear() // Clear sensitive data immediately
    return phrase
}
