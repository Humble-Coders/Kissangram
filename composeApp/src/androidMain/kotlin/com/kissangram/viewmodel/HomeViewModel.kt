package com.kissangram.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kissangram.model.Post
import com.kissangram.model.UserStories
import com.kissangram.repository.AndroidAuthRepository
import com.kissangram.repository.AndroidPreferencesRepository
import com.kissangram.repository.FirestoreFeedRepository
import com.kissangram.repository.FirestoreStoryRepository
import com.kissangram.repository.FirestorePostRepository
import com.kissangram.repository.FirestoreUserRepository
import com.kissangram.usecase.GetHomeFeedUseCase
import com.kissangram.usecase.GetStoryBarUseCase
import com.kissangram.usecase.LikePostUseCase
import com.kissangram.usecase.SavePostUseCase
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.Timestamp
import com.kissangram.data.LocationData
import com.kissangram.data.CropsData

class HomeViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val authRepository = AndroidAuthRepository(
        context = application,
        activity = null,
        preferencesRepository = AndroidPreferencesRepository(application)
    )
    private val userRepository = FirestoreUserRepository(authRepository = authRepository)
    private val feedRepository = FirestoreFeedRepository(authRepository = authRepository)
    private val postRepository = FirestorePostRepository(
        authRepository = authRepository,
        userRepository = userRepository
    )
    private val storyRepository = FirestoreStoryRepository()
    
    // Use cases
    private val getHomeFeedUseCase = GetHomeFeedUseCase(feedRepository)
    private val getStoryBarUseCase = GetStoryBarUseCase(storyRepository)
    private val likePostUseCase = LikePostUseCase(postRepository)
    private val savePostUseCase = SavePostUseCase(postRepository)
    
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    
    // Track posts currently being processed to prevent race conditions
    private val postsBeingProcessed = mutableSetOf<String>()
    
    init {
        Log.d(TAG, "init: HomeViewModel created, calling loadContent()")
        loadContent()
    }
    
    fun loadContent() {
        viewModelScope.launch {
            Log.d(TAG, "loadContent: start, setting isLoading=true")
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val userId = authRepository.getCurrentUserId()
            Log.d(TAG, "loadContent: currentUserId=${userId?.take(8)?.plus("...") ?: "null"}")
            try {
                val stories = getStoryBarUseCase()
                Log.d(TAG, "loadContent: stories count=${stories.size}")
                val posts = getHomeFeedUseCase(page = 0)
                Log.d(TAG, "loadContent: posts count=${posts.size}, firstId=${posts.firstOrNull()?.id}")
                _uiState.value = _uiState.value.copy(
                    stories = stories,
                    posts = posts,
                    isLoading = false
                )
                Log.d(TAG, "loadContent: success, uiState.posts.size=${_uiState.value.posts.size}")
            } catch (e: Exception) {
                Log.e(TAG, "loadContent: failed", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load content"
                )
            }
        }
    }
    
    fun refreshFeed() {
        viewModelScope.launch {
            Log.d(TAG, "refreshFeed: start")
            _uiState.value = _uiState.value.copy(isRefreshing = true)
            try {
                val stories = getStoryBarUseCase()
                val posts = getHomeFeedUseCase(page = 0)
                Log.d(TAG, "refreshFeed: stories=${stories.size}, posts=${posts.size}")
                _uiState.value = _uiState.value.copy(
                    stories = stories,
                    posts = posts,
                    isRefreshing = false
                )
            } catch (e: Exception) {
                Log.e(TAG, "refreshFeed: failed", e)
                _uiState.value = _uiState.value.copy(
                    isRefreshing = false,
                    error = e.message
                )
            }
        }
    }
    
    fun loadMorePosts() {
        if (_uiState.value.isLoadingMore) return
        viewModelScope.launch {
            Log.d(TAG, "loadMorePosts: start, currentPage=${_uiState.value.currentPage}")
            _uiState.value = _uiState.value.copy(isLoadingMore = true)
            try {
                val currentPage = _uiState.value.currentPage
                val newPosts = getHomeFeedUseCase(page = currentPage + 1)
                Log.d(TAG, "loadMorePosts: got ${newPosts.size} new posts")
                if (newPosts.isNotEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        posts = _uiState.value.posts + newPosts,
                        currentPage = currentPage + 1,
                        isLoadingMore = false
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        hasMorePosts = false,
                        isLoadingMore = false
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "loadMorePosts: failed", e)
                _uiState.value = _uiState.value.copy(isLoadingMore = false)
            }
        }
    }
    
    /**
     * Handles like/unlike action for a post.
     * @return true if the request was accepted and processed, false if it was ignored (e.g., already processing)
     */
    fun onLikePost(postId: String): Boolean {
        // Prevent multiple simultaneous requests for the same post
        if (postsBeingProcessed.contains(postId)) {
            Log.d(TAG, "onLikePost: post $postId is already being processed, ignoring")
            return false
        }
        
        val currentPosts = _uiState.value.posts
        val postIndex = currentPosts.indexOfFirst { it.id == postId }
        
        if (postIndex != -1) {
            val post = currentPosts[postIndex]
            val newLikedState = !post.isLikedByMe
            val newLikesCount = if (newLikedState) post.likesCount + 1 else post.likesCount - 1
            
            // Mark as being processed
            postsBeingProcessed.add(postId)
            
            // ⚡ INSTAGRAM APPROACH: Update UI IMMEDIATELY (synchronous, main thread)
            // This happens before any async work, so user sees instant feedback
            val updatedPost = post.copy(
                isLikedByMe = newLikedState,
                likesCount = newLikesCount
            )
            val updatedPosts = currentPosts.toMutableList()
            updatedPosts[postIndex] = updatedPost
            _uiState.value = _uiState.value.copy(posts = updatedPosts)
            
            // Fire network request in background (non-blocking)
            viewModelScope.launch {
                try {
                    likePostUseCase(postId, post.isLikedByMe)
                } catch (e: Exception) {
                    Log.e(TAG, "onLikePost: failed for postId=$postId", e)
                    // Revert on failure
                    val revertedPosts = currentPosts.toMutableList()
                    _uiState.value = _uiState.value.copy(posts = revertedPosts)
                } finally {
                    // Always remove from processing set
                    postsBeingProcessed.remove(postId)
                }
            }
            return true
        }
        return false
    }
    
    fun onSavePost(postId: String) {
        // Prevent multiple simultaneous requests for the same post
        val saveKey = "save_$postId"
        if (postsBeingProcessed.contains(saveKey)) {
            Log.d(TAG, "onSavePost: post $postId is already being processed, ignoring")
            return
        }
        
        val currentPosts = _uiState.value.posts
        val postIndex = currentPosts.indexOfFirst { it.id == postId }
        
        if (postIndex != -1) {
            val post = currentPosts[postIndex]
            val newSavedState = !post.isSavedByMe
            
            // Mark as being processed
            postsBeingProcessed.add(saveKey)
            
            // ⚡ INSTAGRAM APPROACH: Update UI IMMEDIATELY (synchronous, main thread)
            // This happens before any async work, so user sees instant feedback
            val updatedPost = post.copy(isSavedByMe = newSavedState)
            val updatedPosts = currentPosts.toMutableList()
            updatedPosts[postIndex] = updatedPost
            _uiState.value = _uiState.value.copy(posts = updatedPosts)
            
            // Fire network request in background (non-blocking)
            viewModelScope.launch {
                try {
                    savePostUseCase(postId, post.isSavedByMe)
                } catch (e: Exception) {
                    Log.e(TAG, "onSavePost: failed for postId=$postId", e)
                    // Revert on failure
                    val revertedPosts = currentPosts.toMutableList()
                    _uiState.value = _uiState.value.copy(posts = revertedPosts)
                } finally {
                    // Always remove from processing set
                    postsBeingProcessed.remove(saveKey)
                }
            }
        }
    }
    
    /**
     * Upload India states and districts data to Firestore.
     * This is a one-time operation for seeding reference data.
     * Call this from a dev button, then remove the button after use.
     */
    fun uploadLocationsToFirestore(
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val firestore = FirebaseFirestore.getInstance(
        com.google.firebase.FirebaseApp.getInstance(),
       "kissangram"
    )
                
                val locationData = hashMapOf(
                    "country" to "India",
                    "statesAndDistricts" to LocationData.indiaStatesAndDistricts,
                    "stateNames" to LocationData.stateNames,
                    "version" to 1,
                    "updatedAt" to Timestamp.now()
                )
                
                firestore.collection("appConfig")
                    .document("locations")
                    .set(locationData)
                    .addOnSuccessListener {
                        onSuccess()
                    }
                    .addOnFailureListener { e ->
                        onError(e.message ?: "Upload failed")
                    }
            } catch (e: Exception) {
                onError(e.message ?: "Failed to upload locations")
            }
        }
    }
    
    /**
     * Upload crops data to Firestore.
     * This is a one-time operation for seeding reference data.
     * Call this from a dev button, then remove the button after use.
     */
    fun uploadCropsToFirestore(
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val firestore = FirebaseFirestore.getInstance(
                    com.google.firebase.FirebaseApp.getInstance(),
                    "kissangram"
                )
                
                val cropsData = hashMapOf(
                    "categories" to CropsData.categories,
                    "categoryNames" to CropsData.categoryNames,
                    "allCrops" to CropsData.allCrops,
                    "totalCrops" to CropsData.totalCrops,
                    "version" to 1,
                    "updatedAt" to Timestamp.now()
                )
                
                firestore.collection("appConfig")
                    .document("crops")
                    .set(cropsData)
                    .addOnSuccessListener {
                        onSuccess()
                    }
                    .addOnFailureListener { e ->
                        onError(e.message ?: "Upload failed")
                    }
            } catch (e: Exception) {
                onError(e.message ?: "Failed to upload crops")
            }
        }
    }

    companion object {
        private const val TAG = "HomeViewModel"
    }
}

data class HomeUiState(
    val stories: List<UserStories> = emptyList(),
    val posts: List<Post> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMorePosts: Boolean = true,
    val currentPage: Int = 0,
    val error: String? = null
)
