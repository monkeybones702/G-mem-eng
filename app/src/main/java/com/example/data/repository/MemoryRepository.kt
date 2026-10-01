package com.example.data.repository

import com.example.data.database.AppDatabase
import com.example.data.entity.AccountEntity
import com.example.data.entity.ChatMessageEntity
import com.example.data.entity.MemoryEntity
import com.example.data.entity.UserSettingsEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext

class MemoryRepository(private val database: AppDatabase) {

    private val accountDao = database.accountDao()
    private val memoryDao = database.memoryDao()
    private val chatDao = database.chatMessageDao()
    private val settingsDao = database.userSettingsDao()

    val allAccounts: Flow<List<AccountEntity>> = accountDao.getAllAccounts()
    val activeAccount: Flow<AccountEntity?> = accountDao.getActiveAccount()

    suspend fun initializeDefaultsIfNeeded() = withContext(Dispatchers.IO) {
        val existingAccounts = allAccounts.firstOrNull()
        if (existingAccounts.isNullOrEmpty()) {
            // Create default active account
            val defaultAccountId = accountDao.insertAccount(
                AccountEntity(
                    name = "Primary User",
                    email = "user@geminimemory.local",
                    avatarColorHex = "#1E40AF",
                    isActive = true
                )
            )

            // Seed default settings
            settingsDao.insertOrUpdateSettings(
                UserSettingsEntity(
                    accountId = defaultAccountId,
                    customApiKey = "",
                    modelName = "gemini-2.5-flash",
                    systemInstructions = "You are a concise, helpful assistant. Incorporate retained user context seamlessly without repeating it verbatim.",
                    injectionStrategy = "system_instruction",
                    memoryRetrievalEnabled = true,
                    topK = 3,
                    minRelevanceScore = 0.15f,
                    ultraLowTokenMode = true,
                    autoExtractMemories = true
                )
            )

            // Seed default memories demonstrating various tags and checkboxes
            val sampleMemories = listOf(
                MemoryEntity(
                    accountId = defaultAccountId,
                    tag = "Preference",
                    content = "Prefers Kotlin and modern Jetpack Compose for Android",
                    importance = 5,
                    isEnabled = true
                ),
                MemoryEntity(
                    accountId = defaultAccountId,
                    tag = "Constraint",
                    content = "Always give brief, direct code examples with least tokens possible",
                    importance = 4,
                    isEnabled = true
                ),
                MemoryEntity(
                    accountId = defaultAccountId,
                    tag = "Project",
                    content = "Building an on-device Gemini memory layer with Room SQLite database",
                    importance = 4,
                    isEnabled = true
                ),
                MemoryEntity(
                    accountId = defaultAccountId,
                    tag = "Fact",
                    content = "App uses Gemini 2.5 Flash / 1.5 Flash for high performance and low tokens",
                    importance = 3,
                    isEnabled = true
                ),
                MemoryEntity(
                    accountId = defaultAccountId,
                    tag = "Preference",
                    content = "Prefers Material 3 dark/dynamic colors and clean MVVM architecture",
                    importance = 3,
                    isEnabled = false // Checkbox starts unchecked to showcase toggle feature!
                )
            )
            memoryDao.insertMemories(sampleMemories)
        }
    }

    // --- Accounts ---
    suspend fun createAccount(name: String, email: String, colorHex: String): Long = withContext(Dispatchers.IO) {
        val id = accountDao.insertAccount(
            AccountEntity(
                name = name,
                email = email,
                avatarColorHex = colorHex,
                isActive = false
            )
        )
        // Create initial settings for this account
        settingsDao.insertOrUpdateSettings(
            UserSettingsEntity(
                accountId = id,
                modelName = "gemini-2.5-flash"
            )
        )
        id
    }

    suspend fun switchAccount(accountId: Long) = withContext(Dispatchers.IO) {
        accountDao.clearActiveAccounts()
        accountDao.setActiveAccount(accountId)
    }

    suspend fun deleteAccount(account: AccountEntity) = withContext(Dispatchers.IO) {
        accountDao.deleteAccount(account)
        chatDao.clearHistoryForAccount(account.id)
        val remaining = allAccounts.firstOrNull()?.filter { it.id != account.id }
        if (!remaining.isNullOrEmpty()) {
            switchAccount(remaining.first().id)
        }
    }

    // --- Memories ---
    fun getMemoriesForAccount(accountId: Long): Flow<List<MemoryEntity>> =
        memoryDao.getMemoriesForAccount(accountId)

    fun searchMemories(accountId: Long, query: String): Flow<List<MemoryEntity>> =
        memoryDao.searchMemories(accountId, query)

    suspend fun getActiveMemoriesList(accountId: Long): List<MemoryEntity> = withContext(Dispatchers.IO) {
        memoryDao.getActiveMemoriesList(accountId)
    }

    suspend fun addMemory(
        accountId: Long,
        tag: String,
        content: String,
        importance: Int = 3,
        isEnabled: Boolean = true
    ) = withContext(Dispatchers.IO) {
        memoryDao.insertMemory(
            MemoryEntity(
                accountId = accountId,
                tag = tag.trim().ifEmpty { "General" },
                content = content.trim(),
                importance = importance.coerceIn(1, 5),
                isEnabled = isEnabled
            )
        )
    }

    suspend fun updateMemory(memory: MemoryEntity) = withContext(Dispatchers.IO) {
        memoryDao.updateMemory(memory)
    }

    suspend fun toggleMemoryEnabled(id: Long, isEnabled: Boolean) = withContext(Dispatchers.IO) {
        memoryDao.updateMemoryEnabled(id, isEnabled)
    }

    suspend fun setAllMemoriesEnabled(accountId: Long, isEnabled: Boolean) = withContext(Dispatchers.IO) {
        memoryDao.setAllMemoriesEnabled(accountId, isEnabled)
    }

    suspend fun deleteMemory(id: Long) = withContext(Dispatchers.IO) {
        memoryDao.deleteMemoryById(id)
    }

    suspend fun recordMemoryHits(memoryIds: List<Long>) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        for (id in memoryIds) {
            memoryDao.recordMemoryHit(id, now)
        }
    }

    // --- Chat Messages ---
    fun getMessagesForAccount(accountId: Long): Flow<List<ChatMessageEntity>> =
        chatDao.getMessagesForAccount(accountId)

    suspend fun saveChatMessage(message: ChatMessageEntity): Long = withContext(Dispatchers.IO) {
        chatDao.insertMessage(message)
    }

    suspend fun clearChatHistory(accountId: Long) = withContext(Dispatchers.IO) {
        chatDao.clearHistoryForAccount(accountId)
    }

    // --- Settings ---
    fun getSettingsForAccount(accountId: Long): Flow<UserSettingsEntity?> =
        settingsDao.getSettingsForAccount(accountId)

    suspend fun getSettingsDirect(accountId: Long): UserSettingsEntity = withContext(Dispatchers.IO) {
        settingsDao.getSettingsDirect(accountId) ?: UserSettingsEntity(accountId = accountId)
    }

    suspend fun saveSettings(settings: UserSettingsEntity) = withContext(Dispatchers.IO) {
        settingsDao.insertOrUpdateSettings(settings)
    }
}
