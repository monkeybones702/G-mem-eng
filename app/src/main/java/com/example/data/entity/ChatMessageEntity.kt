package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val accountId: Long,
    val role: String, // "user" or "model"
    val content: String,
    val injectedMemoryIds: String = "", // Comma-separated memory IDs injected
    val injectedMemorySnippets: String = "", // Condensed summary of memories injected
    val tokenCountEstimate: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
)
