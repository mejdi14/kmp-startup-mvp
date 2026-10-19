import SwiftUI

/// Filled primary button (the Flutter submit button: `primary` on `onPrimary`).
struct PrimaryButton: View {
    let title: String
    var enabled = true
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(.body.weight(.medium))
                .frame(maxWidth: .infinity, minHeight: 28)
        }
        .buttonStyle(.borderedProminent)
        .tint(AppColors.primary)
        .controlSize(.large)
        .disabled(!enabled)
    }
}

/// Low-emphasis text button in `outline` ("Forgot Password" style).
struct OutlineTextButton: View {
    let title: String
    let action: () -> Void

    var body: some View {
        Button(title, action: action).foregroundStyle(AppColors.outline)
    }
}

/// Inline form-level error in `error`.
struct ErrorText: View {
    let message: String

    init(_ message: String) { self.message = message }

    var body: some View {
        Text(message).font(.subheadline).foregroundStyle(AppColors.error)
    }
}

/// Labelled text field with an optional inline error and password obscuring.
struct AppTextField: View {
    let label: String
    @Binding var text: String
    var error: String?
    var obscure = false
    var enabled = true
    var keyboard: UIKeyboardType = .default
    var contentType: UITextContentType?

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Group {
                if obscure {
                    SecureField(label, text: $text)
                } else {
                    TextField(label, text: $text)
                        .keyboardType(keyboard)
                }
            }
            .textContentType(contentType)
            .textInputAutocapitalization(.never)
            .autocorrectionDisabled()
            .disabled(!enabled)
            .padding(12)
            .overlay(RoundedRectangle(cornerRadius: 4).stroke(error == nil ? AppColors.outline : AppColors.error))
            if let error { ErrorText(error) }
        }
    }
}

/// Centered spinner; the `/loading` splash and any async placeholder.
struct LoadingView: View {
    var body: some View {
        ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

/// Generic error state: message plus optional retry.
struct ErrorState: View {
    let message: String
    var onRetry: (() -> Void)?

    var body: some View {
        VStack(spacing: 16) {
            ErrorText(message)
            if let onRetry { Button("Retry", action: onRetry) }
        }
        .padding(16)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

extension View {
    /// One screen view per appearance, like Flutter's `PosthogObserver` (PORTING.md §1.5).
    func trackScreen(_ route: AppRoute, in env: AppEnvironment) -> some View {
        onAppear { env.screen(route) }
    }
}
