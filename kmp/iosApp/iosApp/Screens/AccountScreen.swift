import SharedLogic
import SwiftUI

/// Shown for delete-account: the fixed backend has no self-service deletion path (PORTING.md §2.1/§2.3/§2.4).
let deleteBlockedMessage =
    "Account deletion isn't available in the app yet. Please contact support to delete your account."

/// Profile editing (`full_name`/`avatar_url`) plus the delete-account entry point (task 9).
struct AccountScreen: View {
    @EnvironmentObject private var env: AppEnvironment
    @ObservedObject var metadata: MetadataModel

    var body: some View {
        Group {
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
