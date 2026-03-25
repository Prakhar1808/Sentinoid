package com.sentinoid.shield

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.sentinoid.shield.data.SecurityEventDao

@Database(entities = [SecurityEvent::class], version = 1)
abstract class SovereignDatabase : RoomDatabase() {
    abstract fun securityEventDao(): SecurityEventDao

    companion object {
        @Volatile
        private var INSTANCE: SovereignDatabase? = null

        fun getDatabase(context: Context): SovereignDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SovereignDatabase::class.java,
                    "sovereign_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}