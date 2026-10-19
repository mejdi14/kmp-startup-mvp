import Foundation
import SharedLogic

/// D-002 gate as a Swift enum; the only auth signal navigation reacts to.
enum Gate: Equatable { case unknown, loggedOut, loggedIn }

/// Thin iOS adapter over the app-scoped shared `AuthStore` (D-001).
@MainActor
final class AuthModel: ObservableObject {
    @Published private(set) var gate: Gate = .unknown
    @Published private(set) var pendingPasswordReset = false

    private let store: AuthStore
    private var watchers: [Watcher] = []

    init(store: AuthStore) {
        self.store = store
        watchers.append(observe(store.authGate) { [weak self] (gate: AuthGate) in
            if gate == AuthGate.loggedin { self?.gate = .loggedIn }
            else if gate == AuthGate.loggedout { self?.gate = .loggedOut }
            else { self?.gate = .unknown }
        })
        watchers.append(observe(store.pendingPasswordReset) { [weak self] (pending: NSNumber) in
            self?.pendingPasswordReset = pending.boolValue
        })
    }

    deinit { watchers.forEach { $0.cancel() } }

    func signInWithPassword(email: String, password: String) async throws {
        try await store.signInWithPassword(email: email, password: password)
    }

    func signUp(email: String, password: String) async throws {
        try await store.signUp(email: email, password: password)
    }

    func recoverPassword(email: String) async throws {
        try await store.recoverPassword(email: email, redirectUrl: DeepLinks.shared.RESET_REDIRECT_URL)
    }

    func updatePassword(_ password: String) async throws {
        try await store.updatePassword(password: password)
    }

    /// Native Google sign-in (D-008).
    func signInWithGoogle() async throws {
        let google = try await GoogleSignInService.idToken()
        try await store.signInWithIdToken(provider: .google, idToken: google.idToken, nonce: google.rawNonce)
    }

    /// Native Sign in with Apple (D-008, finishes task 10's iOS half).
    func signInWithApple(idToken: String, rawNonce: String) async throws {
        try await store.signInWithIdToken(provider: .apple, idToken: idToken, nonce: rawNonce)
    }

    /// GitHub browser flow; success returns through the deep link, so this only covers launching.
    func signInWithGithub() async throws {
        try await store.signInWithGithub()
    }

    /// "Continue without login" (D-009): anonymous session, so the auth gate navigates as usual.
    func continueAsGuest() async throws {
        try await store.continueAsGuest()
    }

    func signOut() async throws { try await store.signOut() }

    func dismissPasswordReset() { store.dismissPasswordReset() }

    func handleDeepLink(_ url: String) async throws { try await store.handleDeepLink(url: url) }
}
