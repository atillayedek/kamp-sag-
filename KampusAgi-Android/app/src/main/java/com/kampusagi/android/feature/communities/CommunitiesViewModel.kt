package com.kampusagi.android.feature.communities

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kampusagi.android.core.ui.UiText
import com.kampusagi.android.core.ui.toUiText
import com.kampusagi.android.domain.common.AppError
import com.kampusagi.android.domain.community.CommunityScope
import com.kampusagi.android.domain.community.Post
import com.kampusagi.android.domain.community.PostRepository
import com.kampusagi.android.domain.profile.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Gönderi oluşturma/yorum gibi başka ekranlarda olan değişiklikleri akışa bildirir (akış kendini yeniler). */
@Singleton
class PostEvents @Inject constructor() {
    private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val changes: SharedFlow<Unit> = _changes.asSharedFlow()

    fun notifyChanged() {
        _changes.tryEmit(Unit)
    }
}

/** Bir kapsamdaki (Genel / Üniversitem) akışın durumu. */
data class FeedState(
    val items: List<Post> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val endReached: Boolean = false,
    /** İlk yükleme başarısız ve liste boş -> tam ekran hata + "Tekrar Dene". */
    val error: AppError? = null,
    /** Sonraki sayfa yüklenemedi -> liste altında satır içi hata + "Tekrar Dene". */
    val loadMoreError: AppError? = null,
)

data class CommunitiesUiState(
    val scope: CommunityScope = CommunityScope.GENERAL,
    val feeds: Map<CommunityScope, FeedState> = emptyMap(),
    val universityName: String? = null,
    val message: UiText? = null,
) {
    val feed: FeedState get() = feeds[scope] ?: FeedState()
}

@HiltViewModel
class CommunitiesViewModel @Inject constructor(
    private val postRepository: PostRepository,
    private val profileRepository: ProfileRepository,
    postEvents: PostEvents,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CommunitiesUiState())
    val uiState: StateFlow<CommunitiesUiState> = _uiState.asStateFlow()

    /** Şu an beğeni isteği süren gönderiler (çift dokunuş yarışını önler). */
    private val likesInFlight = mutableSetOf<String>()

    init {
        loadFirstPage(CommunityScope.GENERAL)
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(universityName = profileRepository.getMyProfile().universityName) }
            } catch (_: AppError) {
                // Yalnızca başlık için; akışın kendisi ayrı hata durumlarını gösterir.
            }
        }
        viewModelScope.launch {
            postEvents.changes.collect {
                // Bu ekranda gösterilen kapsam yenilenir; diğeri bir sonraki seçilişinde yeniden yüklenir.
                _uiState.update { state -> state.copy(feeds = state.feeds.filterKeys { key -> key == state.scope }) }
                refresh(silent = true)
            }
        }
    }

    fun selectScope(scope: CommunityScope) {
        if (_uiState.value.scope == scope) return
        _uiState.update { it.copy(scope = scope) }
        if (_uiState.value.feeds[scope] == null) loadFirstPage(scope)
    }

    fun refresh(silent: Boolean = false) {
        val scope = _uiState.value.scope
        updateFeed(scope) { it.copy(isRefreshing = !silent, error = null, loadMoreError = null) }
        viewModelScope.launch {
            try {
                val page = postRepository.getFeed(scope, PAGE_SIZE, before = null)
                updateFeed(scope) {
                    FeedState(items = page, isLoading = false, endReached = page.size < PAGE_SIZE)
                }
            } catch (e: AppError) {
                updateFeed(scope) { state ->
                    if (state.items.isEmpty()) state.copy(isLoading = false, isRefreshing = false, error = e)
                    else state.copy(isRefreshing = false)
                }
                if (_uiState.value.feed.items.isNotEmpty()) _uiState.update { it.copy(message = e.toUiText()) }
            }
        }
    }

    fun retry() {
        val scope = _uiState.value.scope
        updateFeed(scope) { it.copy(isLoading = true, error = null) }
        loadFirstPage(scope)
    }

    fun loadMore() {
        val scope = _uiState.value.scope
        val state = _uiState.value.feed
        if (state.isLoading || state.isLoadingMore || state.isRefreshing || state.endReached || state.items.isEmpty()) return
        updateFeed(scope) { it.copy(isLoadingMore = true, loadMoreError = null) }
        viewModelScope.launch {
            try {
                val page = postRepository.getFeed(scope, PAGE_SIZE, before = state.items.last().createdAt)
                updateFeed(scope) { current ->
                    val known = current.items.mapTo(HashSet()) { it.id }
                    current.copy(
                        items = current.items + page.filter { it.id !in known },
                        isLoadingMore = false,
                        endReached = page.size < PAGE_SIZE,
                    )
                }
            } catch (e: AppError) {
                updateFeed(scope) { it.copy(isLoadingMore = false, loadMoreError = e) }
            }
        }
    }

    fun toggleLike(post: Post) {
        if (!likesInFlight.add(post.id)) return
        val scope = _uiState.value.scope
        val target = !post.likedByMe
        replacePost(scope, post.id) { it.withLiked(target) }
        viewModelScope.launch {
            try {
                postRepository.setLiked(post.id, target)
            } catch (e: AppError) {
                // İyimser güncelleme geri alınır.
                replacePost(scope, post.id) { it.withLiked(!target) }
                _uiState.update { it.copy(message = e.toUiText()) }
            } finally {
                likesInFlight.remove(post.id)
            }
        }
    }

    fun consumeMessage() = _uiState.update { it.copy(message = null) }

    private fun loadFirstPage(scope: CommunityScope) {
        viewModelScope.launch {
            try {
                val page = postRepository.getFeed(scope, PAGE_SIZE, before = null)
                updateFeed(scope) { FeedState(items = page, isLoading = false, endReached = page.size < PAGE_SIZE) }
            } catch (e: AppError) {
                updateFeed(scope) { FeedState(isLoading = false, error = e) }
            }
        }
    }

    private fun updateFeed(scope: CommunityScope, transform: (FeedState) -> FeedState) {
        _uiState.update { state ->
            state.copy(feeds = state.feeds + (scope to transform(state.feeds[scope] ?: FeedState())))
        }
    }

    private fun replacePost(scope: CommunityScope, postId: String, transform: (Post) -> Post) {
        updateFeed(scope) { feed -> feed.copy(items = feed.items.map { if (it.id == postId) transform(it) else it }) }
    }

    companion object {
        const val PAGE_SIZE = 20
    }
}
