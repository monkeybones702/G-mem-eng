package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "memories")
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val accountId: Long,
    val tag: String, // e.g. "Preference", "Fact", "Constraint", "Project"
    val content: String,
    val importance: Int = 3, // 1 to 5
    val isEnabled: Boolean = true, // Checkbox toggle for dynamic retrieval injection
    val hitCount: Int = 0,
    val lastUsedAt: Long = 0,
    val createdAt: Long = System.currentTimeMillis()
)
