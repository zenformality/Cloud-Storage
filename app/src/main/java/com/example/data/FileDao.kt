package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FileDao {
    @Query("SELECT * FROM files ORDER BY id DESC")
    fun getAllFiles(): Flow<List<FileItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFile(file: FileItem): Long

    @Update
    suspend fun updateFile(file: FileItem)

    @Query("DELETE FROM files WHERE id = :id")
    suspend fun deleteFileById(id: Int)
}
