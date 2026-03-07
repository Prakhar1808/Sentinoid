package com.sentinoid.shield

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SecretDao {
    @Insert
    suspend fun insert(secret: Secret)

    @Query("SELECT * FROM secrets ORDER BY creationTimestamp DESC")
    fun getAllSecrets(): Flow<List<Secret>>

    @Query("DELETE FROM secrets WHERE id = :id")
    suspend fun delete(id: Int)
}