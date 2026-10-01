package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.GeminiApiBridge
import com.example.ai.GeminiResult
import com.example.ai.MemoryRetrievalEngine
import com.example.ai.MemoryScore
import com.example.ai.PromptInjectionResult
import com.example.data.database.AppDatabase
import com.example.data.entity.AccountEntity
import com.example.data.entity.ChatMessageEntity
import com.example.data.entity.MemoryEntity
import com.example.data.entity.UserSettingsEntity
import com.example.data.repository.MemoryRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class UiNotice(
    val message: String,
    val isError: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: MemoryRepository

    init {
        val db = AppDatabase.getDatabase(application)
        repository = MemoryRepository(db)
        viewModelScope.launch {
            repository.initializeDefaultsIfNeeded()
        }
    }

    // Accounts
    val allAccounts: StateFlow<List<AccountEntity>> = repository.allAccounts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeAccount: StateFlow<AccountEntity?> = repository.activeAccount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Current Account Settings
    @OptIn(ExperimentalCoroutinesApi::class)
    val userSettings: StateFlow<UserSettingsEntity?> = activeAccount
        .flatMapLatest { account ->
            if (account != null) repository.getSettingsForAccount(account.id)
            else flowOf(null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Memory Search & Filter
    private val _memorySearchQuery = MutableStateFlow("")
    val memorySearchQuery: StateFlow<String> = _memorySearchQuery.asStateFlow()

    private val _selectedTagFilter = MutableStateFlow("All")
    val selectedTagFilter: StateFlow<String> = _selectedTagFilter.asStateFlow()

    // Memories for active account
    @OptIn(ExperimentalCoroutinesApi::class)
    val memories: StateFlow<List<MemoryEntity>> = combine(
        activeAccount,
        _memorySearchQuery,
        _selectedTagFilter
    ) { account, query, tag ->
        Triple(account, query, tag)
    }.flatMapLatest { (account, query, tag) ->
        if (account == null) return@flatMapLatest flowOf(emptyList())

        val sourceFlow = if (query.isBlank()) {
            repository.getMemoriesForAccount(account.id)
        } else {
            repository.searchMemories(account.id, query.trim())
        }

        sourceFlow
    }.combine(_selectedTagFilter) { list, tag ->
        if (tag == "All") list
        else list.filter { it.tag.equals(tag, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Chat Messages
    @OptIn(ExperimentalCoroutinesApi::class)
    val chatMessages: StateFlow<List<ChatMessageEntity>> = activeAccount
        .flatMapLatest { account ->
            if (account != null) repository.getMessagesForAccount(account.id)
            else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Live Draft & Injection Preview
    private val _chatDraft = MutableStateFlow("")
    val chatDraft: StateFlow<String> = _chatDraft.asStateFlow()

    private val _liveMatchedMemories = MutableStateFlow<List<MemoryScore>>(emptyList())
    val liveMatchedMemories: StateFlow<List<MemoryScore>> = _liveMatchedMemories.asStateFlow()

    // Inspect Outbound Prompt Sheet state
    private val _inspectInjectionResult = MutableStateFlow<PromptInjectionResult?>(null)
    val inspectInjectionResult: StateFlow<PromptInjectionResult?> = _inspectInjectionResult.asStateFlow()

    // UI State flags
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _notice = MutableStateFlow<UiNotice?>(null)
    val notice: StateFlow<UiNotice?> = _notice.asStateFlow()

    // Candidate Extracted Fact for prompt
    private val _extractedFactNotice = MutableStateFlow<Pair<String, String>?>(null)
    val extractedFactNotice: StateFlow<Pair<String, String>?> = _extractedFactNotice.asStateFlow()

    fun updateDraft(text: String) {
        _chatDraft.value = text
        updateLiveMatching(text)
    }

    private fun updateLiveMatching(text: String) {
        val account = activeAccount.value ?: return
        val currentSettings = userSettings.value ?: return
        if (!currentSettings.memoryRetrievalEnabled) {
            _liveMatchedMemories.value = emptyList()
            return
        }

        viewModelScope.launch {
            val candidateMemories = repository.getActiveMemoriesList(account.id)
            val scored = MemoryRetrievalEngine.findRelevantMemories(
                prompt = text,
                candidateMemories = candidateMemories,
                topK = currentSettings.topK,
                minScoreThreshold = currentSettings.minRelevanceScore
            )
            _liveMatchedMemories.value = scored
        }
    }

    fun preparePromptInspection(promptText: String) {
        val account = activeAccount.value ?: return
        val currentSettings = userSettings.value ?: return

        viewModelScope.launch {
            val candidateMemories = repository.getActiveMemoriesList(account.id)
            val scored = MemoryRetrievalEngine.findRelevantMemories(
                prompt = promptText,
                candidateMemories = candidateMemories,
                topK = currentSettings.topK,
                minScoreThreshold = currentSettings.minRelevanceScore
            )
            val matchedMemories = scored.map { it.memory }
            val injection = MemoryRetrievalEngine.buildInjectedPrompt(
                rawPrompt = promptText,
                matchedMemories = matchedMemories,
                settings = currentSettings
            )
            _inspectInjectionResult.value = injection
        }
    }

    fun clearPromptInspection() {
        _inspectInjectionResult.value = null
    }

    fun sendMessage(userPromptText: String) {
        val text = userPromptText.trim()
        if (text.isEmpty() || _isLoading.value) return

        val account = activeAccount.value ?: return
        val currentSettings = userSettings.value ?: return

        _isLoading.value = true
        _chatDraft.value = ""
        _liveMatchedMemories.value = emptyList()

        viewModelScope.launch {
            try {
                // 1. Retrieve relevant memories (only enabled ones)
                val candidateMemories = repository.getActiveMemoriesList(account.id)
                val scored = MemoryRetrievalEngine.findRelevantMemories(
                    prompt = text,
                    candidateMemories = candidateMemories,
                    topK = currentSettings.topK,
                    minScoreThreshold = currentSettings.minRelevanceScore
                )
                val matchedMemories = scored.map { it.memory }

                // 2. Build structured injection
                val injection = MemoryRetrievalEngine.buildInjectedPrompt(
                    rawPrompt = text,
                    matchedMemories = matchedMemories,
                    settings = currentSettings
                )

                // 3. Save User Message into Room
                val injectedIds = matchedMemories.joinToString(",") { it.id.toString() }
                val injectedSnippet = matchedMemories.joinToString("; ") { it.content }
                repository.saveChatMessage(
                    ChatMessageEntity(
                        accountId = account.id,
                        role = "user",
                        content = text,
                        injectedMemoryIds = injectedIds,
                        injectedMemorySnippets = injectedSnippet,
                        tokenCountEstimate = (text.length / 4) + injection.estimatedTokensInjected
                    )
                )

                // 4. Update memory hit statistics
                if (matchedMemories.isNotEmpty()) {
                    repository.recordMemoryHits(matchedMemories.map { it.id })
                }

                // 5. Send to Gemini API Bridge
                when (val result = GeminiApiBridge.generateContent(injection, currentSettings)) {
                    is GeminiResult.Success -> {
                        repository.saveChatMessage(
                            ChatMessageEntity(
                                accountId = account.id,
                                role = "model",
                                content = result.responseText,
                                tokenCountEstimate = result.candidateTokens
                            )
                        )

                        // 6. Check for automatic fact extraction
                        if (currentSettings.autoExtractMemories) {
                            val candidateFacts = GeminiApiBridge.extractCandidateFacts(text)
                            if (candidateFacts.isNotEmpty()) {
                                _extractedFactNotice.value = candidateFacts.first()
                            }
                        }
                    }
                    is GeminiResult.Error -> {
                        _notice.value = UiNotice(
                            message = result.message,
                            isError = true
                        )
                    }
                }
            } catch (e: Exception) {
                _notice.value = UiNotice(
                    message = "Error sending message: ${e.localizedMessage}",
                    isError = true
                )
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun acceptExtractedFact() {
        val fact = _extractedFactNotice.value ?: return
        val account = activeAccount.value ?: return
        viewModelScope.launch {
            repository.addMemory(
                accountId = account.id,
                tag = fact.second,
                content = fact.first,
                importance = 3,
                isEnabled = true
            )
            _extractedFactNotice.value = null
            _notice.value = UiNotice("Memory auto-saved to on-device layer: \"${fact.first}\"")
        }
    }

    fun dismissExtractedFact() {
        _extractedFactNotice.value = null
    }

    fun clearChat() {
        val account = activeAccount.value ?: return
        viewModelScope.launch {
            repository.clearChatHistory(account.id)
            _notice.value = UiNotice("Chat history cleared.")
        }
    }

    // --- Memories & Checkbox Management ---
    fun toggleMemoryCheckbox(memoryId: Long, isEnabled: Boolean) {
        viewModelScope.launch {
            repository.toggleMemoryEnabled(memoryId, isEnabled)
            // Refresh draft preview if currently typing
            updateLiveMatching(_chatDraft.value)
        }
    }

    fun setAllMemoriesEnabled(isEnabled: Boolean) {
        val account = activeAccount.value ?: return
        viewModelScope.launch {
            repository.setAllMemoriesEnabled(account.id, isEnabled)
            updateLiveMatching(_chatDraft.value)
            _notice.value = UiNotice(if (isEnabled) "All memories enabled for injection" else "All memories disabled")
        }
    }

    fun addMemory(tag: String, content: String, importance: Int, isEnabled: Boolean) {
        val account = activeAccount.value ?: return
        viewModelScope.launch {
            repository.addMemory(account.id, tag, content, importance, isEnabled)
            _notice.value = UiNotice("Memory saved to local SQLite.")
            updateLiveMatching(_chatDraft.value)
        }
    }

    fun updateMemory(memory: MemoryEntity) {
        viewModelScope.launch {
            repository.updateMemory(memory)
            _notice.value = UiNotice("Memory updated.")
            updateLiveMatching(_chatDraft.value)
        }
    }

    fun deleteMemory(memoryId: Long) {
        viewModelScope.launch {
            repository.deleteMemory(memoryId)
            _notice.value = UiNotice("Memory deleted from device.")
            updateLiveMatching(_chatDraft.value)
        }
    }

    fun setMemorySearch(query: String) {
        _memorySearchQuery.value = query
    }

    fun setTagFilter(tag: String) {
        _selectedTagFilter.value = tag
    }

    // --- Account Switching ---
    fun switchAccount(accountId: Long) {
        viewModelScope.launch {
            repository.switchAccount(accountId)
            _notice.value = UiNotice("Switched profile.")
        }
    }

    fun createAccount(name: String, email: String, colorHex: String) {
        viewModelScope.launch {
            val newId = repository.createAccount(name, email, colorHex)
            repository.switchAccount(newId)
            _notice.value = UiNotice("Created and switched to account '$name'.")
        }
    }

    fun deleteAccount(account: AccountEntity) {
        viewModelScope.launch {
            repository.deleteAccount(account)
            _notice.value = UiNotice("Account removed.")
        }
    }

    // --- Settings Updates ---
    fun saveSettings(updated: UserSettingsEntity) {
        viewModelScope.launch {
            repository.saveSettings(updated)
            _notice.value = UiNotice("Settings saved.")
            updateLiveMatching(_chatDraft.value)
        }
    }

    fun testConnection() {
        val currentSettings = userSettings.value ?: return
        _isLoading.value = true
        viewModelScope.launch {
            val dummyInjection = PromptInjectionResult(
                rawPrompt = "Respond with one word: 'CONNECTED'",
                matchedMemories = emptyList(),
                formattedMemoryContext = "",
                effectiveSystemInstruction = "Be ultra concise.",
                effectiveUserPrompt = "Respond with one word: 'CONNECTED'",
                estimatedTokensInjected = 0,
                strategyUsed = "none"
            )
            when (val res = GeminiApiBridge.generateContent(dummyInjection, currentSettings)) {
                is GeminiResult.Success -> {
                    _notice.value = UiNotice("API Connection Successful! Model: ${res.modelUsed}")
                }
                is GeminiResult.Error -> {
                    _notice.value = UiNotice("Connection Failed: ${res.message}", isError = true)
                }
            }
            _isLoading.value = false
        }
    }

    fun dismissNotice() {
        _notice.value = null
    }
}
