package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_settings")
data class UserSettingsEntity(
    @PrimaryKey
    val accountId: Long,
    val customApiKey: String = "",
    val modelName: String = "gemini-2.5-flash",
    val systemInstructions: String = "You are an intelligent, helpful AI assistant. Always respect the provided user memories and preferences.",
    val injectionStrategy: String = "system_instruction", // "system_instruction" or "prefix"
    val memoryRetrievalEnabled: Boolean = true,
    val topK: Int = 3,
    val minRelevanceScore: Float = 0.15f,
    val ultraLowTokenMode: Boolean = true, // Keeps prompt prefixes extremely compact to minimize token usage
    val autoExtractMemories: Boolean = true // Analyzes prompts to suggest or save key user facts
)
