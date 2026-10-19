import SharedLogic
import SwiftUI

/// Port of Flutter's `PaymentsScreen`: an interstitial that fetches the Stripe URL and opens it in an in-app
/// browser (D-005). A nil `price` opens the billing portal. When the browser closes (return deep link or the
/// user backing out) the screen reports it to the store, which polls for the new subscription (D-004), and
/// leaves for Home.
struct PaymentsScreen: View {
    @EnvironmentObject private var env: AppEnvironment
    @ObservedObject var payments: PaymentsModel
    let price: String?
    let onDone: () -> Void

    @State private var error: String?
    @State private var started = false

    var body: some View {
        VStack(spacing: 20) {
            ProgressView()
            Text("You are being redirected to Stripe for payment.\nPlease wait a moment...")
                .font(.system(size: 16))
                .multilineTextAlignment(.center)
            Button("Return to Home", action: onDone).padding(.top, 20)
        }
        .padding(16)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(AppColors.background)
        .navigationBarBackButtonHidden(true)
        .alert("Failed to open Stripe", isPresented: Binding(get: { error != nil }, set: { if !$0 { error = nil } })) {
            Button("Ok", role: .cancel) { onDone() }
        } message: {
            Text(error ?? "")
        }
        .trackScreen(.payments, in: env)
        .task {
            // A re-render must not open a second browser.
            guard !started else { return }
            started = true
            await start()
        }
    }

    private func start() async {
        do {
            let urlString = try await payments.getStripeUrl(price: price)
            guard let url = URL(string: urlString) else {
                throw NSError(domain: "Payments", code: 1, userInfo: [NSLocalizedDescriptionKey: "Error retrieving stripe redirect url"])
            }
            payments.markLaunched(price: price)
            await env.stripeBrowser.open(url, callbackScheme: DeepLinks.shared.SCHEME)
            payments.onReturned()
            onDone()
        } catch {
            self.error = error.userMessage
        }
    }
}
