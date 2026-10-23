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
import com.mejdi14.kmpstartupmvp.errors.AuthLinkException
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

    override suspend fun signInWithIdToken(provider: IdTokenProvider, idToken: String, nonce: String?) {
        calls += "idToken:$provider:$idToken:$nonce"
        status.value = AuthStatus.SignedIn(session("tok2"))
    }

    override suspend fun signInWithOAuth(provider: OAuthProvider, redirectUrl: String) {
        calls += "oauth:$provider:$redirectUrl"
    }

    override suspend fun signInAnonymously() {
        calls += "signInAnonymously"
        status.value = AuthStatus.SignedIn(guestSession)
    }

    /** What the next conversion does: succeed with this outcome, or fail with [conversionFailure]. */
    var conversionOutcome: AccountConversion = AccountConversion.Complete
    var conversionFailure: Throwable? = null

    override suspend fun convertAnonymousUser(
        email: String,
        password: String,
        redirectUrl: String?,
    ): AccountConversion {
        calls += "convert:$email:$redirectUrl"
        conversionFailure?.let { throw it }
        val current = (status.value as? AuthStatus.SignedIn)?.session ?: error("not signed in")
        // supabase-kt writes the updated user back into the stored session (same id, same token).
        // Only a completed conversion clears `isAnonymous`; a pending confirmation changes nothing.
        if (conversionOutcome is AccountConversion.Complete) {
            status.value = AuthStatus.SignedIn(current.copy(email = email, isAnonymous = false))
        }
        return conversionOutcome
    }

    override suspend fun signOut() {
        calls += "signOut"
        status.value = AuthStatus.SignedOut
    }
}

private fun session(token: String) = AuthSession("u1", "a@b.co", token)

private val guestSession = AuthSession("guest1", null, "tok3", isAnonymous = true)

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
    fun continueAsGuestDrivesGateLikeAnySignIn() = runTest {
        val repo = FakeAuthRepository()
        val store = AuthStore(repo, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        repo.status.value = AuthStatus.SignedOut
        store.continueAsGuest()
        // The whole point of D-009: no new gate state, no guest special-casing downstream.
        assertEquals(AuthGate.LoggedIn, store.authGate.value)
        assertEquals(AsyncValue.Data<AuthSession?>(guestSession), store.state.value)
        assertEquals(listOf("signInAnonymously"), repo.calls)
        assertEquals(true, store.isGuest.value)
    }

    @Test
    fun guestSignOutReturnsToLoggedOut() = runTest {
        val repo = FakeAuthRepository()
        val store = AuthStore(repo, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        repo.status.value = AuthStatus.SignedOut
        store.continueAsGuest()
        store.signOut()
        assertEquals(AuthGate.LoggedOut, store.authGate.value)
    }

    @Test
    fun savingAGuestAccountKeepsTheSameUserAndClearsGuestFlag() = runTest {
        val repo = FakeAuthRepository()
        val store = AuthStore(repo, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        repo.status.value = AuthStatus.SignedOut
        store.continueAsGuest()

        assertEquals(AccountConversion.Complete, store.saveGuestAccount("a@b.co", "secret1"))
        // D-011: the point of converting in place is that the user id survives, so the `users`
        // row, the subscription and the Stripe customer need no migration.
        val converted = (store.state.value as AsyncValue.Data).value
        assertEquals("guest1", converted?.userId)
        assertEquals("a@b.co", converted?.email)
        assertEquals(false, converted?.isAnonymous)
        assertEquals(false, store.isGuest.value)
        assertEquals(
            listOf("signInAnonymously", "convert:a@b.co:${DeepLinks.EMAIL_CONFIRM_REDIRECT_URL}"),
            repo.calls,
        )
    }

    @Test
    fun savingAGuestAccountNeverLeavesTheLoggedInGate() = runTest {
        val repo = FakeAuthRepository()
        val store = AuthStore(repo, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        repo.status.value = AuthStatus.SignedOut
        store.continueAsGuest()

        // D-002: a session exists before and after the conversion, so the gate must not flicker —
        // a LoggedOut or Unknown emission here would tear down and rebuild the signed-in stack.
        val gates = mutableListOf<AuthGate>()
        val job: Job = launch(UnconfinedTestDispatcher(testScheduler)) { store.authGate.toList(gates) }
        store.saveGuestAccount("a@b.co", "secret1")
        assertEquals(listOf(AuthGate.LoggedIn), gates)
        job.cancel()
    }

    @Test
    fun pendingEmailConfirmationLeavesTheSessionAnonymous() = runTest {
        val repo = FakeAuthRepository()
        val store = AuthStore(repo, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        repo.status.value = AuthStatus.SignedOut
        store.continueAsGuest()
        repo.conversionOutcome = AccountConversion.ConfirmationRequired("a@b.co")

        // Email confirmation on (§2.8): the address is not the user's until the link is followed,
        // so the guest entry point has to stay on screen.
        assertEquals(AccountConversion.ConfirmationRequired("a@b.co"), store.saveGuestAccount("a@b.co", "secret1"))
        assertEquals(true, store.isGuest.value)
        assertEquals(AuthGate.LoggedIn, store.authGate.value)

        // The confirmation link comes back through the ordinary D-003 callback.
        store.handleDeepLink("com.devtodollars.app://login-callback?code=confirm1")
        assertEquals(
            listOf("signInAnonymously", "convert:a@b.co:${DeepLinks.EMAIL_CONFIRM_REDIRECT_URL}", "exchange:confirm1"),
            repo.calls,
        )
        assertEquals(false, store.pendingPasswordReset.value)
    }

    @Test
    fun alreadyRegisteredEmailIsRejectedAndTheGuestSessionSurvives() = runTest {
        val repo = FakeAuthRepository()
        val store = AuthStore(repo, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        repo.status.value = AuthStatus.SignedOut
        store.continueAsGuest()
        repo.conversionFailure = EmailAlreadyRegisteredException("taken@b.co")

        val failure = assertFailsWith<EmailAlreadyRegisteredException> {
            store.saveGuestAccount("taken@b.co", "secret1")
        }
        // Supabase cannot merge a guest into an existing account, so the message must not imply it.
        assertEquals("taken@b.co", failure.email)
        assertEquals(true, failure.message?.contains("can't move this guest session into it"))
        // The guest keeps the session it had; nothing is signed out by a failed conversion.
        assertEquals(AuthGate.LoggedIn, store.authGate.value)
        assertEquals(true, store.isGuest.value)
        assertEquals(AsyncValue.Data<AuthSession?>(guestSession), store.state.value)
    }

    @Test
    fun savingAnAccountIsRefusedWhenTheSessionIsNotAGuest() = runTest {
        val repo = FakeAuthRepository()
        val store = AuthStore(repo, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        repo.status.value = AuthStatus.SignedIn(session("t1"))
        // The same call on a real user would quietly change its email and password.
        assertFailsWith<IllegalStateException> { store.saveGuestAccount("a@b.co", "secret1") }
        assertEquals(emptyList(), repo.calls)
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
        assertFailsWith<AuthLinkException> {
            store.handleDeepLink("com.devtodollars.app://login-callback/reset#error=access_denied")
        }
        assertEquals(false, store.pendingPasswordReset.value)
    }

    @Test
    fun failedLinkIsPublishedForTheScreenUntilDismissed() = runTest {
        val repo = FakeAuthRepository()
        val store = AuthStore(repo, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        assertEquals(null, store.linkError.value)
        assertFailsWith<AuthLinkException> {
            store.handleDeepLink("com.devtodollars.app://login-callback/reset#error=access_denied")
        }
        // The shared sentence, not GoTrue's raw `error` value (D-010).
        assertEquals("That link has expired or was already used. Please request a new one.", store.linkError.value)
        store.dismissLinkError()
        assertEquals(null, store.linkError.value)
        // A link that works raises nothing.
        store.handleDeepLink("com.devtodollars.app://login-callback/reset?code=c1")
        assertEquals(null, store.linkError.value)
    }

    @Test
    fun googleIdTokenSignInDrivesGate() = runTest {
        val repo = FakeAuthRepository()
        val store = AuthStore(repo, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        repo.status.value = AuthStatus.SignedOut
        store.signInWithIdToken(IdTokenProvider.Google, "tok", "n1")
        assertEquals(listOf("idToken:Google:tok:n1"), repo.calls)
        assertEquals(AuthGate.LoggedIn, store.authGate.value)
    }

    @Test
    fun githubStartsBrowserFlowAndCallbackExchangesCodeWithoutResetFlag() = runTest {
        val repo = FakeAuthRepository()
        val store = AuthStore(repo, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        store.signInWithGithub()
        store.handleDeepLink("com.devtodollars.app://login-callback?code=g1")
        assertEquals(listOf("oauth:Github:com.devtodollars.app://login-callback", "exchange:g1"), repo.calls)
        assertEquals(false, store.pendingPasswordReset.value)
    }
}
