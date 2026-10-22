package com.jarvis.assistant.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "jarvis_aliases",
    indices = [
        Index(value = ["relationship_tag"], unique = false),
        Index(value = ["actual_contact_name"], unique = false)
    ]
)
data class AliasEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    @ColumnInfo(name = "relationship_tag")
    val relationshipTag: String,

    @ColumnInfo(name = "actual_contact_name")
    val actualContactName: String,

    @ColumnInfo(name = "phone_number")
    val phoneNumber: String? = null
)
