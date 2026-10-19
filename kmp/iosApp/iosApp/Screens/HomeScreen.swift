import SwiftUI

// TODO(product-owner): placeholder carried over from Flutter's HomeScreen (a GitHub README, not a
// pricing page). Replace with the real pricing URL.
let pricingURL = URL(string: "https://github.com/devtodollars/mvp-boilerplate/blob/main/flutter/README.md")!

/// Port of Flutter's `HomeScreen` (§1.1): the subscription status sentence plus Payments / Account / Logout.
struct HomeScreen: View {
    @EnvironmentObject private var env: AppEnvironment
    @ObservedObject var auth: AuthModel
    @ObservedObject var metadata: MetadataModel
    let onOpenPayments: () -> Void
    let onOpenAccount: () -> Void

    var body: some View {
        VStack(spacing: 8) {
            if let text = statusText(metadata.state.homeStatus) {
                Text(text).multilineTextAlignment(.center)
            } else {
                ProgressView()
            }
            Link("See Pricing", destination: pricingURL)
        }
        .padding(16)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(AppColors.background)
        .navigationTitle("DevToDollars")
        .navigationBarTitleDisplayMode(.inline)
        .toolbarBackground(AppColors.inversePrimary, for: .navigationBar)
        .toolbarBackground(.visible, for: .navigationBar)
        .toolbar {
            ToolbarItem(placement: .topBarLeading) {
                Button("Logout") { Task { try? await auth.signOut() } }
            }
            ToolbarItemGroup(placement: .topBarTrailing) {
                Button("Payments", action: onOpenPayments)
                Button("Account", action: onOpenAccount)
            }
        }
        .trackScreen(.home, in: env)
    }
}
