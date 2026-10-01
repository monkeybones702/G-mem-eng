package com.example.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.entity.MemoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memories WHERE accountId = :accountId ORDER BY importance DESC, lastUsedAt DESC, createdAt DESC")
    fun getMemoriesForAccount(accountId: Long): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories WHERE accountId = :accountId AND isEnabled = 1")
    suspend fun getActiveMemoriesList(accountId: Long): List<MemoryEntity>

    @Query("SELECT * FROM memories WHERE accountId = :accountId AND isEnabled = 1")
    fun getActiveMemoriesFlow(accountId: Long): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories WHERE accountId = :accountId AND (content LIKE '%' || :query || '%' OR tag LIKE '%' || :query || '%') ORDER BY importance DESC")
    fun searchMemories(accountId: Long, query: String): Flow<List<MemoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(memory: MemoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemories(memories: List<MemoryEntity>)

    @Update
    suspend fun updateMemory(memory: MemoryEntity)

    @Delete
    suspend fun deleteMemory(memory: MemoryEntity)

    @Query("DELETE FROM memories WHERE id = :id")
    suspend fun deleteMemoryById(id: Long)

    @Query("UPDATE memories SET isEnabled = :isEnabled WHERE id = :id")
    suspend fun updateMemoryEnabled(id: Long, isEnabled: Boolean)

    @Query("UPDATE memories SET isEnabled = :isEnabled WHERE accountId = :accountId")
    suspend fun setAllMemoriesEnabled(accountId: Long, isEnabled: Boolean)

    @Query("UPDATE memories SET hitCount = hitCount + 1, lastUsedAt = :timestamp WHERE id = :id")
    suspend fun recordMemoryHit(id: Long, timestamp: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM memories WHERE accountId = :accountId")
    fun getMemoryCount(accountId: Long): Flow<Int>
}
