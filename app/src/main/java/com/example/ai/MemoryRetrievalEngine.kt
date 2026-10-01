package com.example.ai

import com.example.data.entity.MemoryEntity
import com.example.data.entity.UserSettingsEntity

object MemoryRetrievalEngine {

    private val STOP_WORDS = setOf(
        "a", "about", "above", "after", "again", "against", "all", "am", "an", "and",
        "any", "are", "aren't", "as", "at", "be", "because", "been", "before", "being",
        "below", "between", "both", "but", "by", "can't", "cannot", "could", "couldn't",
        "did", "didn't", "do", "does", "doesn't", "doing", "don't", "down", "during",
        "each", "few", "for", "from", "further", "had", "hadn't", "has", "hasn't",
        "have", "haven't", "having", "he", "he'd", "he'll", "he's", "her", "here",
        "here's", "hers", "herself", "him", "himself", "his", "how", "how's", "i",
        "i'd", "i'll", "i'm", "i've", "if", "in", "into", "is", "isn't", "it",
        "it's", "its", "itself", "let's", "me", "more", "most", "mustn't", "my",
        "myself", "no", "nor", "not", "of", "off", "on", "once", "only", "or",
        "other", "ought", "our", "ours", "ourselves", "out", "over", "own", "same",
        "shan't", "she", "she'd", "she'll", "she's", "should", "shouldn't", "so",
        "some", "such", "than", "that", "that's", "the", "their", "theirs", "them",
        "themselves", "then", "there", "there's", "these", "they", "they'd", "they'll",
        "they're", "they've", "this", "those", "through", "to", "too", "under", "until",
        "up", "very", "was", "wasn't", "we", "we'd", "we'll", "we're", "we've", "were",
        "weren't", "what", "what's", "when", "when's", "where", "where's", "which",
        "while", "who", "who's", "whom", "why", "why's", "with", "won't", "would",
        "wouldn't", "you", "you'd", "you'll", "you're", "you've", "your", "yours",
        "yourself", "yourselves", "please", "tell", "give", "write", "show", "can"
    )

    /**
     * Extracts tokenized keywords from text.
     */
    fun extractKeywords(text: String): List<String> {
        return text.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .split("\\s+".toRegex())
            .filter { it.length > 2 && it !in STOP_WORDS }
    }

    /**
     * Performs keyword and relevance search over active enabled memories.
     */
    fun findRelevantMemories(
        prompt: String,
        candidateMemories: List<MemoryEntity>,
        topK: Int = 3,
        minScoreThreshold: Float = 0.10f
    ): List<MemoryScore> {
        val queryKeywords = extractKeywords(prompt)
        if (queryKeywords.isEmpty() && prompt.isBlank()) {
            return emptyList()
        }

        val promptLower = prompt.lowercase()

        val scoredMemories = candidateMemories
            .filter { it.isEnabled } // Only enabled memories (checkbox filter)
            .map { memory ->
                val memoryText = memory.content.lowercase()
                val memoryTag = memory.tag.lowercase()
                val memoryWords = extractKeywords(memory.content).toSet()

                var rawScore = 0f
                val matchedWords = mutableListOf<String>()

                // 1. Keyword overlap
                for (kw in queryKeywords) {
                    if (memoryWords.contains(kw)) {
                        rawScore += 1.0f
                        matchedWords.add(kw)
                    } else if (memoryText.contains(kw)) {
                        rawScore += 0.6f
                        matchedWords.add(kw)
                    }
                }

                // 2. Tag match boost (e.g. tag is 'Kotlin' or 'Preference')
                if (queryKeywords.contains(memoryTag) || promptLower.contains(memoryTag)) {
                    rawScore += 1.2f
                    matchedWords.add("tag:${memory.tag}")
                }

                // 3. Exact phrase match bonus
                if (memoryText.length in 5..40 && promptLower.contains(memoryText)) {
                    rawScore += 2.0f
                }

                // 4. Importance scaling (1 = 0.8x, 3 = 1.0x, 5 = 1.3x)
                val importanceFactor = 0.8f + (memory.importance * 0.1f)
                val hitCountBonus = (memory.hitCount.coerceAtMost(10) * 0.03f)
                val finalScore = (rawScore * importanceFactor) + hitCountBonus

                MemoryScore(
                    memory = memory,
                    score = finalScore,
                    matchedKeywords = matchedWords
                )
            }
            .filter { it.score >= minScoreThreshold }
            .sortedByDescending { it.score }
            .take(topK)

        return scoredMemories
    }

    /**
     * Formats retrieved memories into a minimal token footprint representation.
     */
    fun formatMemoriesForInjection(
        memories: List<MemoryEntity>,
        ultraLowTokenMode: Boolean
    ): String {
        if (memories.isEmpty()) return ""

        return if (ultraLowTokenMode) {
            // Ultra-compact syntax to minimize prompt tokens
            val items = memories.joinToString("; ") { it.content.trim() }
            "[USER MEMORY: $items]"
        } else {
            val items = memories.joinToString("\n") { "- [${it.tag}] ${it.content.trim()}" }
            "User Context & Retained Memories:\n$items"
        }
    }

    /**
     * Structures outbound prompt injection based on settings.
     */
    fun buildInjectedPrompt(
        rawPrompt: String,
        matchedMemories: List<MemoryEntity>,
        settings: UserSettingsEntity
    ): PromptInjectionResult {
        if (!settings.memoryRetrievalEnabled || matchedMemories.isEmpty()) {
            return PromptInjectionResult(
                rawPrompt = rawPrompt,
                matchedMemories = emptyList(),
                formattedMemoryContext = "",
                effectiveSystemInstruction = settings.systemInstructions,
                effectiveUserPrompt = rawPrompt,
                estimatedTokensInjected = 0,
                strategyUsed = "none"
            )
        }

        val formattedContext = formatMemoriesForInjection(
            memories = matchedMemories,
            ultraLowTokenMode = settings.ultraLowTokenMode
        )

        // Calculate token estimate (~4 chars per token heuristic)
        val estimatedTokens = (formattedContext.length / 4).coerceAtLeast(1)

        return when (settings.injectionStrategy) {
            "prefix" -> {
                val userPromptWithPrefix = "$formattedContext\n\n$rawPrompt"
                PromptInjectionResult(
                    rawPrompt = rawPrompt,
                    matchedMemories = matchedMemories,
                    formattedMemoryContext = formattedContext,
                    effectiveSystemInstruction = settings.systemInstructions,
                    effectiveUserPrompt = userPromptWithPrefix,
                    estimatedTokensInjected = estimatedTokens,
                    strategyUsed = "prefix"
                )
            }
            else -> {
                // "system_instruction" is default
                val enhancedSystemInstruction = if (settings.systemInstructions.isBlank()) {
                    formattedContext
                } else {
                    "${settings.systemInstructions}\n\n$formattedContext"
                }

                PromptInjectionResult(
                    rawPrompt = rawPrompt,
                    matchedMemories = matchedMemories,
                    formattedMemoryContext = formattedContext,
                    effectiveSystemInstruction = enhancedSystemInstruction,
                    effectiveUserPrompt = rawPrompt,
                    estimatedTokensInjected = estimatedTokens,
                    strategyUsed = "system_instruction"
                )
            }
        }
    }
}
