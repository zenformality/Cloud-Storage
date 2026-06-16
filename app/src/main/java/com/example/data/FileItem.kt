package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "files")
data class FileItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
    val fileUri: String,
    val downloadUrl: String? = null,
    val uploadStatus: String = "PENDING"
)
