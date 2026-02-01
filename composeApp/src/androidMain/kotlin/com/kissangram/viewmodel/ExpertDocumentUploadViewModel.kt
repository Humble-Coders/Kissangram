package com.kissangram.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.storage.FirebaseStorage
import com.kissangram.model.VerificationStatus
import com.kissangram.model.UserRole
import com.kissangram.repository.AndroidAuthRepository
import com.kissangram.repository.AndroidPreferencesRepository
import com.kissangram.repository.FirestoreUserRepository
import com.kissangram.usecase.CreateUserProfileUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class ExpertDocumentUploadUiState(
    val uploadedDocumentURL: String? = null,
    val uploadedFileName: String? = null,
    val isUploading: Boolean = false,
    val uploadProgress: Float = 0f,
    val error: String? = null
)

class ExpertDocumentUploadViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val prefs = AndroidPreferencesRepository(appContext)
    private val authRepository = AndroidAuthRepository(
        context = appContext,
        activity = null,
        preferencesRepository = prefs
    )
    private val userRepository = FirestoreUserRepository(authRepository = authRepository)
    private val createUserProfileUseCase = CreateUserProfileUseCase(
        authRepository = authRepository,
        preferencesRepository = prefs,
        userRepository = userRepository
    )

    private val _uiState = MutableStateFlow(ExpertDocumentUploadUiState())
    val uiState: StateFlow<ExpertDocumentUploadUiState> = _uiState.asStateFlow()

    private val storage = FirebaseStorage.getInstance("gs://kissangram-19531.firebasestorage.app")

    fun uploadDocument(uri: Uri, context: Context, userId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isUploading = true,
                uploadProgress = 0f,
                error = null
            )

            try {
                val fileName = getFileName(uri, context) ?: "document_${System.currentTimeMillis()}"
                val fileExtension = fileName.substringAfterLast('.', "")
                val storageRef = storage.reference
                val documentRef = storageRef.child("expert-documents/$userId/${System.currentTimeMillis()}.$fileExtension")
                val metadata = com.google.firebase.storage.StorageMetadata.Builder()
                    .setContentType(getContentType(fileExtension))
                    .build()
                val uploadTask = documentRef.putFile(uri, metadata)

                uploadTask.addOnProgressListener { taskSnapshot ->
                    val progress = (100.0 * taskSnapshot.bytesTransferred / taskSnapshot.totalByteCount).toFloat()
                    _uiState.value = _uiState.value.copy(uploadProgress = progress / 100f)
                }

                val task = uploadTask.await()
                val downloadUrl = task.storage.downloadUrl.await()

                _uiState.value = _uiState.value.copy(
                    uploadedDocumentURL = downloadUrl.toString(),
                    uploadedFileName = fileName,
                    isUploading = false,
                    uploadProgress = 1f
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = e.message ?: "Upload failed",
                    isUploading = false
                )
            }
        }
    }

    private fun getFileName(uri: Uri, context: Context): String? {
        var result: String? = null
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) {
                    result = it.getString(nameIndex)
                }
            }
        }
        return result
    }

    private fun getContentType(fileExtension: String): String {
        return when (fileExtension.lowercase()) {
            "pdf" -> "application/pdf"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "doc" -> "application/msword"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            else -> "application/octet-stream"
        }
    }

    fun completeSetup(onSuccess: () -> Unit, onError: (String) -> Unit = {}) {
        viewModelScope.launch {
            try {
                val docUrl = _uiState.value.uploadedDocumentURL
                createUserProfileUseCase(
                    role = UserRole.EXPERT,
                    verificationDocUrl = docUrl,
                    verificationStatus = if (docUrl != null) VerificationStatus.PENDING else VerificationStatus.UNVERIFIED
                )
                prefs.setAuthCompleted()
                _uiState.value = _uiState.value.copy(error = null)
                onSuccess()
            } catch (e: Exception) {
                val msg = e.message ?: "Failed to save"
                _uiState.value = _uiState.value.copy(error = msg)
                onError(msg)
            }
        }
    }

    fun skipVerification(onSuccess: () -> Unit, onError: (String) -> Unit = {}) {
        viewModelScope.launch {
            try {
                createUserProfileUseCase(
                    role = UserRole.EXPERT,
                    verificationDocUrl = null,
                    verificationStatus = VerificationStatus.UNVERIFIED
                )
                prefs.setAuthCompleted()
                _uiState.value = _uiState.value.copy(error = null)
                onSuccess()
            } catch (e: Exception) {
                val msg = e.message ?: "Failed to save"
                _uiState.value = _uiState.value.copy(error = msg)
                onError(msg)
            }
        }
    }
}
