package com.example.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.example.data.AppDatabase
import com.example.data.FileItem
import com.example.data.FileRepository
import com.example.storage.StorageService
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val database = Room.databaseBuilder(
        application,
        AppDatabase::class.java, "storage-database"
    ).build()
    
    private val repository = FileRepository(database.fileDao())
    private val storageService = StorageService()

    val files: StateFlow<List<FileItem>> = repository.allFiles
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        syncWithFirebase()
    }

    fun syncWithFirebase() {
        viewModelScope.launch {
            try {
                val storage = storageService.getStorage() ?: return@launch
                val storageRef = storage.reference.child("uploads")
                val listResult = storageRef.listAll().await()
                
                val currentFiles = repository.allFiles.first()
                
                for (item in listResult.items) {
                    val downloadUrl = item.downloadUrl.await().toString()
                    val isExisting = currentFiles.any { 
                        it.downloadUrl == downloadUrl || item.name.contains(it.name) 
                    }
                    
                    if (!isExisting) {
                        try {
                            val metadata = item.metadata.await()
                            repository.insert(FileItem(
                                name = item.name,
                                mimeType = metadata.contentType ?: "application/octet-stream",
                                sizeBytes = metadata.sizeBytes,
                                fileUri = "",
                                downloadUrl = downloadUrl,
                                uploadStatus = "SUCCESS"
                            ))
                        } catch (e: Exception) {
                            Log.e("MainViewModel", "Failed to get metadata", e)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Failed to sync with Firebase", e)
            }
        }
    }

    fun uploadFile(context: Context, uri: Uri) {
        viewModelScope.launch {
            var fileName = "unknown_file"
            var mimeType = "application/octet-stream"
            var sizeBytes = 0L
            
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIndex != -1) fileName = cursor.getString(nameIndex)
                    if (sizeIndex != -1) sizeBytes = cursor.getLong(sizeIndex)
                }
            }
            mimeType = context.contentResolver.getType(uri) ?: mimeType

            val initialFile = FileItem(
                name = fileName,
                mimeType = mimeType,
                sizeBytes = sizeBytes,
                fileUri = uri.toString(),
                uploadStatus = "UPLOADING"
            )
            val id = repository.insert(initialFile).toInt()
            val fileWithId = initialFile.copy(id = id)

            val result = storageService.uploadFile(uri, "${System.currentTimeMillis()}_$fileName")
            
            result.onSuccess { url ->
                repository.update(fileWithId.copy(uploadStatus = "SUCCESS", downloadUrl = url))
            }.onFailure { error ->
                val status = if (error is IllegalStateException) "NOT_CONFIGURED" else "FAILED"
                repository.update(fileWithId.copy(uploadStatus = status))
            }
        }
    }

    fun scanAndUploadRecentFiles(context: Context) {
        viewModelScope.launch {
            try {
                val uri = android.provider.MediaStore.Files.getContentUri("external")
                val projection = arrayOf(
                    android.provider.MediaStore.MediaColumns._ID,
                    android.provider.MediaStore.MediaColumns.DISPLAY_NAME,
                    android.provider.MediaStore.MediaColumns.MIME_TYPE
                )
                val sortOrder = "${android.provider.MediaStore.MediaColumns.DATE_ADDED} DESC"
                
                context.contentResolver.query(
                    uri,
                    projection,
                    null,
                    null,
                    sortOrder
                )?.use { cursor ->
                    val idColumn = cursor.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns._ID)
                    val mimeTypeColumn = cursor.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns.MIME_TYPE)
                    
                    var count = 0
                    val currentDbFiles = repository.allFiles.first()

                    while (cursor.moveToNext() && count < 20) { // Get top 20 recent files
                        val id = cursor.getLong(idColumn)
                        val mimeType = cursor.getString(mimeTypeColumn) ?: ""
                        
                        val contentUri = android.content.ContentUris.withAppendedId(uri, id)
                        
                        // Avoid uploading if already in database
                        val existingFile = currentDbFiles.find { it.fileUri == contentUri.toString() }
                        if (existingFile == null) {
                            uploadFile(context, contentUri)
                            count++
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Failed to scan files", e)
            }
        }
    }
}
