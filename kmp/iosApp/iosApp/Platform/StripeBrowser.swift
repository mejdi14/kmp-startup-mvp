import AuthenticationServices
import UIKit

/// In-app browser for Stripe Checkout / Billing Portal (D-005): `ASWebAuthenticationSession`, the iOS
/// counterpart of Android Custom Tabs. Stripe's `return_url` is the D-003 custom scheme, so the session
/// closes itself and hands control back as soon as Stripe redirects.
@MainActor
final class StripeBrowser: NSObject, ASWebAuthenticationPresentationContextProviding {
    private var session: ASWebAuthenticationSession?

    /// Opens [url] and returns when the browser closes, whether Stripe redirected back or the user dismissed it.
    func open(_ url: URL, callbackScheme: String) async {
        await withCheckedContinuation { (continuation: CheckedContinuation<Void, Never>) in
            let session = ASWebAuthenticationSession(url: url, callbackURLScheme: callbackScheme) { [weak self] _, _ in
                // Callback URL or cancellation: both mean "stop waiting, re-query subscriptions" (§2.8).
                Task { @MainActor in self?.session = nil }
                continuation.resume()
            }
            session.presentationContextProvider = self
            // Keep the user's Stripe/Link login across visits.
            session.prefersEphemeralWebBrowserSession = false
            self.session = session
            if !session.start() {
                self.session = nil
                continuation.resume()
            }
        }
    }

    nonisolated func presentationAnchor(for session: ASWebAuthenticationSession) -> ASPresentationAnchor {
        MainActor.assumeIsolated { UIApplication.shared.keyWindowForPresentation ?? ASPresentationAnchor() }
    }
}

extension UIApplication {
    var keyWindowForPresentation: UIWindow? {
        connectedScenes.compactMap { $0 as? UIWindowScene }
            .flatMap(\.windows)
            .first(where: \.isKeyWindow)
    }

    var topViewController: UIViewController? {
        var top = keyWindowForPresentation?.rootViewController
        while let presented = top?.presentedViewController { top = presented }
        return top
    }
}
