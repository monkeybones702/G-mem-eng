package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val email: String,
    val avatarColorHex: String = "#1E40AF",
    val isActive: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
