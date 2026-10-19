package com.mejdi14.kmpstartupmvp.auth

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

private class FakeAuthRepository : AuthRepository {
    val status = MutableStateFlow<AuthStatus>(AuthStatus.Initializing)
    override val authStatus: Flow<AuthStatus> = status
    val calls = mutableListOf<String>()

    override suspend fun signInWithPassword(email: String, password: String) {
        calls += "signIn:$email"
        status.value = AuthStatus.SignedIn(session("tok1"))
    }

    override suspend fun signUp(email: String, password: String) {
        calls += "signUp:$email" // email confirmation on: no session
    }

    override suspend fun recoverPassword(email: String, redirectUrl: String?) {
        calls += "recover:$email:$redirectUrl"
    }

    override suspend fun updatePassword(password: String) {
        calls += "updatePassword"
    }

    override suspend fun exchangeCodeForSession(code: String) {
        calls += "exchange:$code"
    }

    override suspend fun signOut() {
        calls += "signOut"
        status.value = AuthStatus.SignedOut
    }
}

private fun session(token: String) = AuthSession("u1", "a@b.co", token)

@OptIn(ExperimentalCoroutinesApi::class)
class AuthStoreTest {
    @Test
    fun loadingUntilRestoreThenNullSession() = runTest {
        val repo = FakeAuthRepository()
        val store = AuthStore(repo, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        assertEquals(AsyncValue.Loading, store.state.value)
        assertEquals(AuthGate.Unknown, store.authGate.value)
        repo.status.value = AuthStatus.SignedOut
        assertEquals(AsyncValue.Data(null), store.state.value)
        assertEquals(AuthGate.LoggedOut, store.authGate.value)
    }

    @Test
    fun restoresStoredSession() = runTest {
        val repo = FakeAuthRepository()
        val store = AuthStore(repo, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        repo.status.value = AuthStatus.SignedIn(session("restored"))
        assertEquals(AsyncValue.Data<AuthSession?>(session("restored")), store.state.value)
        assertEquals(AuthGate.LoggedIn, store.authGate.value)
    }

    @Test
    fun signInSignOutDriveGate() = runTest {
        val repo = FakeAuthRepository()
        val store = AuthStore(repo, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        repo.status.value = AuthStatus.SignedOut
        store.signInWithPassword("a@b.co", "secret1")
        assertEquals(AuthGate.LoggedIn, store.authGate.value)
        store.signOut()
        assertEquals(AuthGate.LoggedOut, store.authGate.value)
        assertEquals(listOf("signIn:a@b.co", "signOut"), repo.calls)
    }

    @Test
    fun signUpLeavesUserLoggedOut() = runTest {
        val repo = FakeAuthRepository()
        val store = AuthStore(repo, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        repo.status.value = AuthStatus.SignedOut
        store.signUp("a@b.co", "secret1")
        assertEquals(AuthGate.LoggedOut, store.authGate.value)
    }

    @Test
    fun tokenRefreshUpdatesSessionButNotGate() = runTest {
        val repo = FakeAuthRepository()
        val store = AuthStore(repo, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        repo.status.value = AuthStatus.SignedIn(session("t1"))
        val gates = mutableListOf<AuthGate>()
        val job: Job = launch(UnconfinedTestDispatcher(testScheduler)) { store.authGate.toList(gates) }
        repo.status.value = AuthStatus.SignedIn(session("t2"))
        repo.status.value = AuthStatus.SignedIn(session("t3"))
        assertEquals(listOf(AuthGate.LoggedIn), gates)
        assertEquals(AsyncValue.Data<AuthSession?>(session("t3")), store.state.value)
        job.cancel()
    }

    @Test
    fun streamErrorBecomesErrorStateAndLoggedOutGate() = runTest {
        val failing = object : AuthRepository by FakeAuthRepository() {
            override val authStatus: Flow<AuthStatus> = kotlinx.coroutines.flow.flow { throw IllegalStateException("boom") }
        }
        val store = AuthStore(failing, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        assertIs<AsyncValue.Error>(store.state.value)
        assertEquals(AuthGate.LoggedOut, store.authGate.value)
    }

    @Test
    fun resetLinkExchangesCodeAndRaisesPendingFlag() = runTest {
        val repo = FakeAuthRepository()
        val store = AuthStore(repo, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        store.handleDeepLink("com.devtodollars.app://login-callback/reset?code=c1")
        assertEquals(listOf("exchange:c1"), repo.calls)
        assertEquals(true, store.pendingPasswordReset.value)
        store.dismissPasswordReset()
        assertEquals(false, store.pendingPasswordReset.value)
    }

    @Test
    fun foreignLinkIsIgnoredAndErrorLinkThrows() = runTest {
        val repo = FakeAuthRepository()
        val store = AuthStore(repo, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        store.handleDeepLink("https://example.com/x")
        assertEquals(emptyList(), repo.calls)
        assertFailsWith<IllegalStateException> {
            store.handleDeepLink("com.devtodollars.app://login-callback/reset#error=access_denied")
        }
        assertEquals(false, store.pendingPasswordReset.value)
    }
}
