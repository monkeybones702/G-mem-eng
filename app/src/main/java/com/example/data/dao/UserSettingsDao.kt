package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.entity.UserSettingsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserSettingsDao {
    @Query("SELECT * FROM user_settings WHERE accountId = :accountId LIMIT 1")
    fun getSettingsForAccount(accountId: Long): Flow<UserSettingsEntity?>

    @Query("SELECT * FROM user_settings WHERE accountId = :accountId LIMIT 1")
    suspend fun getSettingsDirect(accountId: Long): UserSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateSettings(settings: UserSettingsEntity)

    @Update
    suspend fun updateSettings(settings: UserSettingsEntity)
}
