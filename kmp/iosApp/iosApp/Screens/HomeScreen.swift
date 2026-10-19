import SwiftUI

// TODO(product-owner): placeholder carried over from Flutter's HomeScreen (a GitHub README, not a
// pricing page). Replace with the real pricing URL.
let pricingURL = URL(string: "https://github.com/devtodollars/mvp-boilerplate/blob/main/flutter/README.md")!

/// Sign-out warning for a guest (D-011). A guest has no credentials, so signing out destroys the
/// account rather than parking it; saying so is the point, and "Save your account" is the way out.
let guestSignOutWarning =
    "You're signed in as a guest. Signing out deletes this session for good — the account can't be "
        + "recovered and everything in it goes with it. Save your account first to keep it."

/// Port of Flutter's `HomeScreen` (§1.1): the subscription status sentence plus Payments / Account / Logout.
struct HomeScreen: View {
    @EnvironmentObject private var env: AppEnvironment
    @ObservedObject var auth: AuthModel
    @ObservedObject var metadata: MetadataModel
    let onOpenPayments: () -> Void
    let onOpenAccount: () -> Void
    @State private var confirmGuestSignOut = false

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
                // A guest sign-out is irreversible, so it asks first (D-011).
                Button("Logout") {
                    if auth.isGuest { confirmGuestSignOut = true } else { Task { try? await auth.signOut() } }
                }
            }
            ToolbarItemGroup(placement: .topBarTrailing) {
                Button("Payments", action: onOpenPayments)
                Button("Account", action: onOpenAccount)
            }
        }
        .confirmationDialog("Lose this account?", isPresented: $confirmGuestSignOut, titleVisibility: .visible) {
            Button("Save your account") { onOpenAccount() }
            Button("Sign out anyway", role: .destructive) { Task { try? await auth.signOut() } }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text(guestSignOutWarning)
        }
        .trackScreen(.home, in: env)
    }
}
