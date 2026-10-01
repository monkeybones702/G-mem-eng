package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.dao.AccountDao
import com.example.data.dao.ChatMessageDao
import com.example.data.dao.MemoryDao
import com.example.data.dao.UserSettingsDao
import com.example.data.entity.AccountEntity
import com.example.data.entity.ChatMessageEntity
import com.example.data.entity.MemoryEntity
import com.example.data.entity.UserSettingsEntity

@Database(
    entities = [
        AccountEntity::class,
        MemoryEntity::class,
        ChatMessageEntity::class,
        UserSettingsEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun memoryDao(): MemoryDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun userSettingsDao(): UserSettingsDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "gemini_memory_database.db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
