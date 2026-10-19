package com.mejdi14.kmpstartupmvp.metadata

import com.mejdi14.kmpstartupmvp.auth.AsyncValue
import com.mejdi14.kmpstartupmvp.auth.AuthSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Port of the Flutter `Metadata` notifier (PORTING.md §1.2).
 *
 * Refetches on login and logout (the signed-in user id changes) and on [refresh].
 * Token refreshes keep the same user id, so they do not refetch (rough edge #5, D-002).
 * [AsyncValue.Data] holds null when signed out.
 */
class MetadataStore(
    private val repository: MetadataRepository,
    authState: Flow<AsyncValue<AuthSession?>>,
    scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<AsyncValue<UserMetadata?>>(AsyncValue.Loading)
    val state: StateFlow<AsyncValue<UserMetadata?>> = _state.asStateFlow()

    private val refreshTicks = MutableStateFlow(0)

    /** Auth input reduced to what matters here: still loading, or the (possibly null) user id. */
    private data class Key(val loading: Boolean, val userId: String?)

    init {
        scope.launch {
            combine(
                authState.map { Key(it is AsyncValue.Loading, (it as? AsyncValue.Data)?.value?.userId) }
                    .distinctUntilChanged(),
                refreshTicks,
            ) { key, _ -> key }.collectLatest { key ->
                when {
                    key.loading -> _state.value = AsyncValue.Loading
                    key.userId == null -> _state.value = AsyncValue.Data(null)
                    else -> {
                        _state.value = AsyncValue.Loading
                        _state.value = try {
                            AsyncValue.Data(load())
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Throwable) {
                            AsyncValue.Error(e)
                        }
                    }
                }
            }
        }
    }

    /** Re-run the fetch for the current user (e.g. after returning from Stripe, task 11). */
    fun refresh() {
        refreshTicks.value++
    }

    /**
     * Saves the profile then refetches so [state] reflects what the server stored.
     * Blank values are stored as null; input is checked by [ProfileValidation] first.
     */
    suspend fun updateProfile(fullName: String, avatarUrl: String) {
        repository.updateProfile(fullName.trim().ifEmpty { null }, avatarUrl.trim().ifEmpty { null })
        refresh()
    }

    /** Fetches profile and subscription concurrently, as `(getUserDetails(), getSubscription()).wait`. */
    private suspend fun load(): UserMetadata = coroutineScope {
        val details = async { repository.fetchUserDetails() }
        val subscription = async { repository.fetchSubscription() }
        val user = details.await() ?: throw MissingProfileException()
        user.copy(subscription = subscription.await())
    }
}
