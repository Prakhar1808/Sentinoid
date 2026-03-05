package com.sentinoid.shield

import android.content.Context
import java.io.File

/**
 * High-level helper for encrypting and decrypting files using the VaultService.
 */
class VaultFileManager(private val context: Context, private val vaultService: VaultService) {

    /**
     * Encrypts a file and returns the Base64 encoded IV.
     */
    fun encryptFile(inputFileName: String, outputFileName: String): String? {
        val inputFile = File(context.filesDir, inputFileName)
        if (!inputFile.exists()) return null
        
        val outputFile = File(context.filesDir, outputFileName)
        return try {
            vaultService.encryptFile(inputFile, outputFile)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Decrypts a file using the provided Base64 encoded IV.
     */
    fun decryptFile(inputFileName: String, outputFileName: String, iv: String): Boolean {
        val inputFile = File(context.filesDir, inputFileName)
        if (!inputFile.exists()) return false
        
        val outputFile = File(context.filesDir, outputFileName)
        return try {
            vaultService.decryptFile(inputFile, outputFile, iv)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Deletes a file from the internal storage.
     */
    fun deleteFile(fileName: String): Boolean {
        val file = File(context.filesDir, fileName)
        return if (file.exists()) file.delete() else false
    }
}
