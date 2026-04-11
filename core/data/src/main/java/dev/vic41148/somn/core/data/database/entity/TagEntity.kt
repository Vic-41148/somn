package dev.vic41148.somn.core.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tags")
data class TagEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val category: String = "",
    val color: Long = 0xFF6200EE,
    val icon: String = "label",
    val isArchived: Boolean = false
)
