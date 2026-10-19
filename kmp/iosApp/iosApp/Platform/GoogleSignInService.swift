import Foundation
import UIKit
#if canImport(GoogleSignIn)
import GoogleSignIn
#endif

struct GoogleIdTokenResult {
    let idToken: String
    let rawNonce: String
}

/// Native Google Sign-In (D-008, iOS half of task 10). Needs `GIDClientID` (iOS client) and
/// `GIDServerClientID` (the web client registered in Supabase) in Info.plist, set through
/// `Config.xcconfig`; an empty value disables the button's flow with a clear error.
enum GoogleSignInService {
    static var isConfigured: Bool {
        #if canImport(GoogleSignIn)
        return !((Bundle.main.object(forInfoDictionaryKey: "GIDClientID") as? String) ?? "").isEmpty
        #else
        return false
        #endif
    }

    @MainActor
    static func idToken() async throws -> GoogleIdTokenResult {
        #if canImport(GoogleSignIn)
        guard isConfigured else {
            throw NSError(domain: "GoogleSignIn", code: 0, userInfo: [NSLocalizedDescriptionKey:
                "Google sign-in is not configured: set GOOGLE_IOS_CLIENT_ID and GOOGLE_WEB_CLIENT_ID in iosApp/Configuration/Config.xcconfig"])
        }
        guard let presenter = UIApplication.shared.topViewController else {
            throw NSError(domain: "GoogleSignIn", code: 1, userInfo: [NSLocalizedDescriptionKey: "No window to present Google sign-in"])
        }
        let rawNonce = Nonce.random()
        do {
            let result = try await GIDSignIn.sharedInstance.signIn(
                withPresenting: presenter, hint: nil, additionalScopes: nil, nonce: Nonce.sha256Hex(rawNonce))
            guard let token = result.user.idToken?.tokenString else {
                throw NSError(domain: "GoogleSignIn", code: 2, userInfo: [NSLocalizedDescriptionKey: "Google did not return an ID token"])
            }
            return GoogleIdTokenResult(idToken: token, rawNonce: rawNonce)
        } catch let error as NSError where error.domain == kGIDSignInErrorDomain && error.code == GIDSignInError.canceled.rawValue {
            throw SignInCancelled()
        }
        #else
        throw NSError(domain: "GoogleSignIn", code: 3, userInfo: [NSLocalizedDescriptionKey: "Google Sign-In SDK is not linked"])
        #endif
    }

    /// Hands an incoming URL to the SDK; returns whether it was a Google sign-in redirect.
    @discardableResult
    static func handle(_ url: URL) -> Bool {
        #if canImport(GoogleSignIn)
        return GIDSignIn.sharedInstance.handle(url)
        #else
        return false
        #endif
    }
}
