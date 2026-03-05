package com.sentinoid.shield

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [Secret::class], version = 1)
abstract class SovereignDatabase : RoomDatabase() {
    abstract fun secretDao(): SecretDao
}