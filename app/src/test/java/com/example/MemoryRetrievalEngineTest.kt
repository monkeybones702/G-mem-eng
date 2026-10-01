package com.example

import com.example.ai.MemoryRetrievalEngine
import com.example.data.entity.MemoryEntity
import com.example.data.entity.UserSettingsEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryRetrievalEngineTest {

    @Test
    fun `keyword search matches relevant memories`() {
        val memories = listOf(
            MemoryEntity(
                id = 1,
                accountId = 1,
                tag = "Preference",
                content = "Always write code in Kotlin using Jetpack Compose",
                importance = 5,
                isEnabled = true
            ),
            MemoryEntity(
                id = 2,
                accountId = 1,
                tag = "Fact",
                content = "User lives in Seattle and likes cycling",
                importance = 2,
                isEnabled = true
            )
        )

        val results = MemoryRetrievalEngine.findRelevantMemories(
            prompt = "Can you help me design an Android Compose screen with Kotlin?",
            candidateMemories = memories,
            topK = 3
        )

        assertEquals(1, results.size)
        assertEquals(1L, results[0].memory.id)
    }

    @Test
    fun `disabled memories are excluded by checkbox setting`() {
        val memories = listOf(
            MemoryEntity(
                id = 1,
                accountId = 1,
                tag = "Preference",
                content = "Prefers Python over Kotlin",
                importance = 5,
                isEnabled = false // Checkbox disabled!
            ),
            MemoryEntity(
                id = 2,
                accountId = 1,
                tag = "Preference",
                content = "Prefers Kotlin and Jetpack Compose",
                importance = 4,
                isEnabled = true // Checkbox enabled!
            )
        )

        val results = MemoryRetrievalEngine.findRelevantMemories(
            prompt = "Which language should we use: Python or Kotlin?",
            candidateMemories = memories,
            topK = 5
        )

        assertEquals(1, results.size)
        assertEquals(2L, results[0].memory.id)
        assertFalse(results.any { it.memory.id == 1L })
    }

    @Test
    fun `ultra-low token formatting produces concise representation`() {
        val memories = listOf(
            MemoryEntity(
                id = 1,
                accountId = 1,
                tag = "Constraint",
                content = "No boilerplate code",
                importance = 4,
                isEnabled = true
            ),
            MemoryEntity(
                id = 2,
                accountId = 1,
                tag = "Fact",
                content = "Name is Alex",
                importance = 3,
                isEnabled = true
            )
        )

        val formatted = MemoryRetrievalEngine.formatMemoriesForInjection(
            memories = memories,
            ultraLowTokenMode = true
        )

        assertTrue(formatted.startsWith("[USER MEMORY:"))
        assertTrue(formatted.contains("No boilerplate code"))
        assertTrue(formatted.contains("Name is Alex"))
    }

    @Test
    fun `buildInjectedPrompt creates proper system instruction injection`() {
        val memory = MemoryEntity(
            id = 1,
            accountId = 1,
            tag = "Preference",
            content = "Prefers brief answers",
            importance = 5,
            isEnabled = true
        )
        val settings = UserSettingsEntity(
            accountId = 1,
            systemInstructions = "Base assistant instruction.",
            injectionStrategy = "system_instruction",
            memoryRetrievalEnabled = true,
            ultraLowTokenMode = true
        )

        val result = MemoryRetrievalEngine.buildInjectedPrompt(
            rawPrompt = "How do coroutines work?",
            matchedMemories = listOf(memory),
            settings = settings
        )

        assertEquals("How do coroutines work?", result.effectiveUserPrompt)
        assertTrue(result.effectiveSystemInstruction.contains("Base assistant instruction."))
        assertTrue(result.effectiveSystemInstruction.contains("[USER MEMORY: Prefers brief answers]"))
        assertEquals("system_instruction", result.strategyUsed)
        assertTrue(result.estimatedTokensInjected > 0)
    }
}
