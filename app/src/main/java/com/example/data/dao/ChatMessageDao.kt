package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.entity.ChatMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM chat_messages WHERE accountId = :accountId ORDER BY timestamp ASC")
    fun getMessagesForAccount(accountId: Long): Flow<List<ChatMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity): Long

    @Query("DELETE FROM chat_messages WHERE accountId = :accountId")
    suspend fun clearHistoryForAccount(accountId: Long)

    @Query("SELECT COUNT(*) FROM chat_messages WHERE accountId = :accountId")
    fun getMessageCount(accountId: Long): Flow<Int>
}
