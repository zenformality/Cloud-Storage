package com.example.data

import kotlinx.coroutines.flow.Flow

class FileRepository(private val fileDao: FileDao) {
    val allFiles: Flow<List<FileItem>> = fileDao.getAllFiles()

    suspend fun insert(file: FileItem): Long = fileDao.insertFile(file)
    suspend fun update(file: FileItem) = fileDao.updateFile(file)
    suspend fun deleteById(id: Int) = fileDao.deleteFileById(id)
}
