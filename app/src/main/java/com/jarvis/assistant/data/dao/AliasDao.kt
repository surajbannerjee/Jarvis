package com.jarvis.assistant.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.jarvis.assistant.data.entity.AliasEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AliasDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(alias: AliasEntity): Long

    @Update
    suspend fun update(alias: AliasEntity)

    @Delete
    suspend fun delete(alias: AliasEntity)

    @Query("DELETE FROM jarvis_aliases WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM jarvis_aliases ORDER BY relationship_tag ASC")
    fun getAllAliasesFlow(): Flow<List<AliasEntity>>

    @Query("SELECT * FROM jarvis_aliases ORDER BY relationship_tag ASC")
    suspend fun getAllAliases(): List<AliasEntity>

    @Query("SELECT * FROM jarvis_aliases WHERE LOWER(relationship_tag) = LOWER(:tag) LIMIT 1")
    suspend fun findByTag(tag: String): AliasEntity?

    @Query("SELECT * FROM jarvis_aliases WHERE LOWER(actual_contact_name) = LOWER(:contactName) LIMIT 1")
    suspend fun findByContactName(contactName: String): AliasEntity?

    @Query("SELECT * FROM jarvis_aliases WHERE phone_number = :number LIMIT 1")
    suspend fun findByPhoneNumber(number: String): AliasEntity?

    @Query("DELETE FROM jarvis_aliases WHERE LOWER(relationship_tag) = LOWER(:tag)")
    suspend fun deleteByTag(tag: String)
}
