import Foundation
import SharedLogic

/// iOS counterpart of Android's `KmpApplication`: owns the shared graph and the adapters, created once.
@MainActor
final class AppEnvironment: ObservableObject {
    let analytics: PostHogTracker
    let auth: AuthModel
    let metadata: MetadataModel
    let payments: PaymentsModel
    let stripeBrowser = StripeBrowser()

    init() {
        #if DEBUG
        let environment = "LOCAL"
        #else
        let environment = "HOSTED"
        #endif
        let analytics = PostHogTracker()
        let graph = AppGraph(environment: environment, analytics: analytics)
        self.analytics = analytics
        auth = AuthModel(store: graph.authStore)
        metadata = MetadataModel(store: graph.metadataStore)
        payments = PaymentsModel(store: graph.paymentsStore)
    }

    func screen(_ route: AppRoute) { analytics.screen(name: route.screenName) }

    /// Single entry point for D-003 deep links (`onOpenURL`), mirroring Android's `MainActivity.handleDeepLink`.
    func handle(url: URL) {
        if GoogleSignInService.handle(url) { return }
        let string = url.absoluteString
        switch DeepLinks.shared.parse(url: string) {
        case is DeepLinkStripeReturn:
            payments.onReturned()
            return
        case let link as DeepLinkOpenPayments:
            payments.requestPayments(price: link.price)
            return
        default:
            break
        }
        Task {
            do { try await auth.handleDeepLink(string) }
            catch { NSLog("Deep link rejected: \(error.userMessage)") }
        }
    }
}
