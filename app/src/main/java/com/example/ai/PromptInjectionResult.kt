package com.example.ai

import com.example.data.entity.MemoryEntity

data class MemoryScore(
    val memory: MemoryEntity,
    val score: Float,
    val matchedKeywords: List<String>
)

data class PromptInjectionResult(
    val rawPrompt: String,
    val matchedMemories: List<MemoryEntity>,
    val formattedMemoryContext: String,
    val effectiveSystemInstruction: String,
    val effectiveUserPrompt: String,
    val estimatedTokensInjected: Int,
    val strategyUsed: String
)
