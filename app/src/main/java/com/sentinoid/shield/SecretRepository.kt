package com.sentinoid.shield

import kotlinx.coroutines.flow.Flow

class SecretRepository(private val secretDao: SecretDao) {

    fun getAllSecrets(): Flow<List<Secret>> = secretDao.getAllSecrets()

    suspend fun insert(secret: Secret) {
        secretDao.insert(secret)
    }

    suspend fun delete(id: Int) {
        secretDao.delete(id)
    }
}
