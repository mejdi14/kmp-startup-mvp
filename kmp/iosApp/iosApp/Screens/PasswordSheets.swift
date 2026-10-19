import SwiftUI

/// Port of Flutter's `RecoverPasswordDialog` (§1.1): request a reset email for `initialEmail`.
/// Presented as a sheet (D-007b).
struct RecoverPasswordSheet: View {
    @Environment(\.dismiss) private var dismiss
    @ObservedObject var auth: AuthModel
    @State private var email: String
    @State private var error: String?
    @State private var loading = false

    init(auth: AuthModel, initialEmail: String) {
        self.auth = auth
        _email = State(initialValue: initialEmail)
    }

    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 24) {
                Text("Get instructions sent to this email that explain how to reset your password")
                AppTextField(label: "Email", text: $email, error: error, enabled: !loading,
                             keyboard: .emailAddress, contentType: .emailAddress)
                Spacer()
            }
            .padding(16)
            .navigationTitle("Recover Password")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() }.disabled(loading) }
                ToolbarItem(placement: .confirmationAction) { Button("Send", action: send).disabled(loading) }
            }
        }
        .onChange(of: email) { _, _ in error = nil }
        .interactiveDismissDisabled(loading)
        .presentationDetents([.medium])
    }

    private func send() {
        let trimmed = email.trimmingCharacters(in: .whitespacesAndNewlines)
        if let invalid = EmailFormValidation.email(trimmed) {
            error = invalid
            return
        }
        loading = true
        Task {
            do {
                try await auth.recoverPassword(email: trimmed)
                dismiss()
            } catch {
                self.error = error.userMessage
            }
            loading = false
        }
    }
}

/// Port of Flutter's `ResetPasswordDialog` (§1.1), shown after the `/reset` deep link signs the user in.
/// Rough edge #4 fixed: the new-password field is obscured (Flutter's was plain text).
struct ResetPasswordSheet: View {
    @EnvironmentObject private var env: AppEnvironment
    @ObservedObject var auth: AuthModel
    @State private var password = ""
    @State private var error: String?
    @State private var loading = false

    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 24) {
                Text("Set New Password")
                AppTextField(label: "Password", text: $password, error: error, obscure: true,
                             enabled: !loading, contentType: .newPassword)
                Spacer()
            }
            .padding(16)
            .navigationTitle("Reset Password")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { auth.dismissPasswordReset() }.disabled(loading)
                }
                ToolbarItem(placement: .confirmationAction) { Button("Submit", action: submit).disabled(loading) }
            }
        }
        .onChange(of: password) { _, _ in error = nil }
        .interactiveDismissDisabled(loading)
        .presentationDetents([.medium])
        .trackScreen(.reset, in: env)
    }

    private func submit() {
        if let invalid = EmailFormValidation.password(password) {
            error = invalid
            return
        }
        loading = true
        Task {
            do {
                try await auth.updatePassword(password)
                auth.dismissPasswordReset()
            } catch {
                self.error = error.userMessage
            }
            loading = false
        }
    }
}
