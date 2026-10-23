import Foundation
import SharedLogic

/// D-002 gate as a Swift enum; the only auth signal navigation reacts to.
enum Gate: Equatable { case unknown, loggedOut, loggedIn }

/// Outcome of saving a guest account (D-011); the Swift face of Kotlin's `AccountConversion`.
enum AccountSaved: Equatable {
    case complete
    /// The password is live, but the address is only the user's once the emailed link is followed.
    case confirmationRequired(email: String)
}

/// Thin iOS adapter over the app-scoped shared `AuthStore` (D-001).
@MainActor
final class AuthModel: ObservableObject {
    @Published private(set) var gate: Gate = .unknown
    @Published private(set) var pendingPasswordReset = false
    /// True while the session is anonymous (D-009/D-011). Not a gate state: it decides what a screen offers.
    @Published private(set) var isGuest = false
    /// The sentence for the last deep link that failed (D-010), until `dismissLinkError()`.
    @Published private(set) var linkError: String?

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
        watchers.append(observe(store.isGuest) { [weak self] (guest: NSNumber) in
            self?.isGuest = guest.boolValue
        })
        watchers.append(observeOptional(store.linkError) { [weak self] (message: String?) in
            self?.linkError = message
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

    /// "Save your account" (D-011): converts the current guest in place, keeping the same user id.
    /// Throws `EmailAlreadyRegisteredException` when the address belongs to another account.
    func saveGuestAccount(email: String, password: String) async throws -> AccountSaved {
        let outcome = try await store.saveGuestAccount(email: email, password: password)
        if let pending = outcome as? AccountConversionConfirmationRequired {
            return .confirmationRequired(email: pending.email)
        }
        return .complete
    }

    func signOut() async throws { try await store.signOut() }

    func dismissPasswordReset() { store.dismissPasswordReset() }

    func dismissLinkError() { store.dismissLinkError() }

    func handleDeepLink(_ url: String) async throws { try await store.handleDeepLink(url: url) }
}
