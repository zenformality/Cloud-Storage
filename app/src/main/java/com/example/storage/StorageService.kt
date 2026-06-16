package com.example.storage

import android.net.Uri
import android.util.Log
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await

class StorageService {
    fun getStorage(): FirebaseStorage? {
        return try {
            FirebaseStorage.getInstance()
        } catch (e: Exception) {
            Log.e("StorageService", "Firebase App not initialized. Did you add google-services.json?", e)
            null
        }
    }

    suspend fun uploadFile(uri: Uri, filename: String): Result<String> {
        val storage = getStorage()
            ?: return Result.failure(IllegalStateException("Firebase is not configured. Please add your google-services.json file."))
            
        val storageRef = storage.reference
        val fileRef = storageRef.child("uploads/$filename")
        
        return try {
            fileRef.putFile(uri).await()
            val downloadUrl = fileRef.downloadUrl.await()
            Result.success(downloadUrl.toString())
        } catch (e: Exception) {
            Log.e("StorageService", "Upload failed", e)
            Result.failure(e)
        }
    }
}
