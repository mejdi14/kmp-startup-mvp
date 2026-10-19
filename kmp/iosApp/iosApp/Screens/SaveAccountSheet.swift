import SwiftUI

/// "Save your account" (D-010): collects the credentials that turn the current guest session into a
/// permanent account. Same rules as the sign-up form (`EmailFormValidation`) and the same sheet shape
/// as `RecoverPasswordSheet` (D-007b). The outcome goes to `onSaved`, because a pending email
/// confirmation needs a different sentence than a finished conversion.
struct SaveAccountSheet: View {
    @Environment(\.dismiss) private var dismiss
    @ObservedObject var auth: AuthModel
    let onSaved: (AccountSaved) -> Void

    @State private var email = ""
    @State private var password = ""
    @State private var confirm = ""
    @State private var submitted = false
    @State private var loading = false
    @State private var errorMessage = ""

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    Text("Pick an email and password. You keep everything from this session — same account, just recoverable.")
                    AppTextField(label: "Email", text: $email,
                                 error: submitted ? EmailFormValidation.email(email.trimmed) : nil,
                                 enabled: !loading, keyboard: .emailAddress, contentType: .emailAddress)
                    AppTextField(label: "Password", text: $password,
                                 error: submitted ? EmailFormValidation.password(password) : nil,
                                 obscure: true, enabled: !loading, contentType: .newPassword)
                    AppTextField(label: "Confirm Password", text: $confirm,
                                 error: submitted ? EmailFormValidation.confirmPassword(password, confirm) : nil,
                                 obscure: true, enabled: !loading, contentType: .newPassword)
                    if !errorMessage.isEmpty { ErrorText(errorMessage) }
                }
                .padding(16)
            }
            .background(AppColors.background)
            .navigationTitle("Save your account")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() }.disabled(loading) }
                ToolbarItem(placement: .confirmationAction) { Button("Save", action: save).disabled(loading) }
            }
        }
        .onChange(of: email) { _, _ in errorMessage = "" }
        .onChange(of: password) { _, _ in errorMessage = "" }
        .onChange(of: confirm) { _, _ in errorMessage = "" }
        .interactiveDismissDisabled(loading)
    }

    private func save() {
        submitted = true
        let trimmed = email.trimmed
        let valid = EmailFormValidation.email(trimmed) == nil
            && EmailFormValidation.password(password) == nil
            && EmailFormValidation.confirmPassword(password, confirm) == nil
        guard valid else { return }
        loading = true
        errorMessage = ""
        Task {
            do {
                let outcome = try await auth.saveGuestAccount(email: trimmed, password: password)
                onSaved(outcome)
                dismiss()
            } catch {
                // A taken address carries its own explanation (it cannot be merged into).
                errorMessage = error.userMessage
            }
            loading = false
        }
    }
}

private extension String {
    var trimmed: String { trimmingCharacters(in: .whitespacesAndNewlines) }
}
