import SharedLogic
import SwiftUI

/// Why a guest should convert (D-010); the sign-out warning in `HomeScreen` says the other half.
let guestAccountMessage =
    "You're signed in as a guest, so this account only exists on this device and can't be "
        + "recovered. Add an email and password to keep it — nothing you've done is lost."

/// Shown for delete-account: the fixed backend has no self-service deletion path (PORTING.md §2.1/§2.3/§2.4).
let deleteBlockedMessage =
    "Account deletion isn't available in the app yet. Please contact support to delete your account."

/// Profile editing (`full_name`/`avatar_url`) plus the delete-account entry point (task 9), and for a
/// guest session the "Save your account" conversion (D-010). The guest section sits above the profile
/// state, not inside it: converting is what a guest came here for and must not depend on the
/// `users` row having loaded.
struct AccountScreen: View {
    @EnvironmentObject private var env: AppEnvironment
    @ObservedObject var auth: AuthModel
    @ObservedObject var metadata: MetadataModel

    var body: some View {
        VStack(spacing: 0) {
            if auth.isGuest { SaveAccountSection(auth: auth) }
            switch metadata.state {
            case .loading:
                LoadingView()
            case .failed, .loaded(nil):
                ErrorState(message: "Failed to load profile", onRetry: metadata.refresh)
            case let .loaded(profile?):
                ProfileForm(metadata: metadata, initialName: profile.fullName, initialAvatar: profile.avatarUrl)
            }
        }
        .background(AppColors.background)
        .navigationTitle("Account")
        .navigationBarTitleDisplayMode(.inline)
        .trackScreen(.account, in: env)
    }
}

/// Guest-only conversion entry point (D-010). It stays on screen while a confirmation is pending,
/// because until the link is followed the address is not yet the user's and the account is still
/// unrecoverable.
private struct SaveAccountSection: View {
    @ObservedObject var auth: AuthModel
    @State private var showForm = false
    @State private var savedOutcome: AccountSaved?
    @State private var pendingEmail: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Save your account").font(.headline)
            Text(guestAccountMessage)
            PrimaryButton(title: "Save your account") { showForm = true }
            Divider().padding(.top, 8)
        }
        .padding(16)
        // The alert waits for `onDismiss`: SwiftUI drops an alert raised while a sheet is still closing.
        .sheet(isPresented: $showForm, onDismiss: {
            // .complete needs nothing: `isGuest` has gone false and this whole section disappears.
            if case let .confirmationRequired(email) = savedOutcome { pendingEmail = email }
            savedOutcome = nil
        }) {
            SaveAccountSheet(auth: auth) { savedOutcome = $0 }
        }
        .alert("Confirm your email", isPresented: Binding(get: { pendingEmail != nil },
                                                          set: { if !$0 { pendingEmail = nil } })) {
            Button("Ok", role: .cancel) {}
        } message: {
            Text("Your password is set. Open the link we sent to \(pendingEmail ?? "") to finish — "
                + "until then this is still a guest session, so don't sign out.")
        }
    }
}

private struct ProfileForm: View {
    @ObservedObject var metadata: MetadataModel
    let initialName: String
    let initialAvatar: String

    @State private var name = ""
    @State private var avatar = ""
    @State private var submitted = false
    @State private var saving = false
    @State private var message: String?
    @State private var failed = false
    @State private var showDeleteInfo = false

    private var nameError: String? { submitted ? ProfileValidation.shared.fullName(name: name) : nil }
    private var avatarError: String? { submitted ? ProfileValidation.shared.avatarUrl(url: avatar) : nil }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Text("Profile").font(.title2)
                AppTextField(label: "Full name", text: $name, error: nameError, enabled: !saving, contentType: .name)
                AppTextField(label: "Avatar URL", text: $avatar, error: avatarError, enabled: !saving,
                             keyboard: .URL, contentType: .URL)
                if let message {
                    if failed { ErrorText(message) } else { Text(message) }
                }
                PrimaryButton(title: "Save", enabled: !saving, action: save)
                Divider().padding(.top, 8)
                Text("Delete account").font(.headline)
                Button("Delete my account", role: .destructive) { showDeleteInfo = true }
            }
            .padding(16)
        }
        .onAppear { name = initialName; avatar = initialAvatar }
        // Saving refetches, which re-enters with the stored values; keep edits meanwhile.
        .onChange(of: initialName) { _, new in if !saving { name = new } }
        .onChange(of: initialAvatar) { _, new in if !saving { avatar = new } }
        .onChange(of: name) { _, _ in message = nil }
        .onChange(of: avatar) { _, _ in message = nil }
        .alert("Delete account", isPresented: $showDeleteInfo) {
            Button("Ok", role: .cancel) {}
        } message: {
            Text(deleteBlockedMessage)
        }
    }

    private func save() {
        submitted = true
        if ProfileValidation.shared.fullName(name: name) != nil
            || ProfileValidation.shared.avatarUrl(url: avatar) != nil { return }
        saving = true
        message = nil
        Task {
            do {
                try await metadata.updateProfile(fullName: name, avatarUrl: avatar)
                failed = false
                message = "Profile saved"
            } catch {
                failed = true
                message = error.userMessage
            }
            saving = false
        }
    }
}
