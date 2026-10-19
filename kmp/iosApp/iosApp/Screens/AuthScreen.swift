import AuthenticationServices
import SwiftUI

/// Port of Flutter's `AuthScreen` + `EmailForm` (§1.1). Google (native), Apple (native) and GitHub
/// (browser) per D-008 above the email form, which is shown directly rather than behind the Flutter reveal.
/// Navigation on success is the auth gate's job.
struct AuthScreen: View {
    @EnvironmentObject private var env: AppEnvironment
    @ObservedObject var auth: AuthModel

    var body: some View {
        ScrollView {
            VStack(spacing: 24) {
                SocialButtons(auth: auth)
                EmailForm(auth: auth)
                GuestButton(auth: auth)
            }
            .padding(16)
            .frame(maxWidth: 480)
            .frame(maxWidth: .infinity)
        }
        .scrollDismissesKeyboard(.interactively)
        .background(AppColors.background)
        .trackScreen(.login, in: env)
    }
}

private struct SocialButtons: View {
    @ObservedObject var auth: AuthModel
    @State private var errorMessage = ""
    @State private var appleNonce = ""

    var body: some View {
        VStack(spacing: 12) {
            PrimaryButton(title: "Continue with Google") {
                errorMessage = ""
                run { try await auth.signInWithGoogle() }
            }
            PrimaryButton(title: "Continue with GitHub") {
                errorMessage = ""
                run { try await auth.signInWithGithub() }
            }
            // Guideline 4.8: equivalent private login alongside Google/GitHub.
            SignInWithAppleButton(.continue) { request in
                appleNonce = Nonce.random()
                request.requestedScopes = [.fullName, .email]
                request.nonce = Nonce.sha256Hex(appleNonce)
            } onCompletion: { result in
                errorMessage = ""
                handleApple(result)
            }
            .signInWithAppleButtonStyle(.black)
            .frame(height: 48)
            if !errorMessage.isEmpty { ErrorText(errorMessage) }
            Text("or continue with email").font(.subheadline)
        }
    }

    private func handleApple(_ result: Result<ASAuthorization, Error>) {
        switch result {
        case let .failure(error):
            // A dismissed sheet is not worth an error banner.
            if (error as? ASAuthorizationError)?.code != .canceled { errorMessage = error.userMessage }
        case let .success(authorization):
            guard let credential = authorization.credential as? ASAuthorizationAppleIDCredential,
                  let data = credential.identityToken,
                  let token = String(data: data, encoding: .utf8) else {
                errorMessage = "Apple did not return an identity token"
                return
            }
            let nonce = appleNonce
            run { try await auth.signInWithApple(idToken: token, rawNonce: nonce) }
        }
    }

    private func run(_ block: @escaping () async throws -> Void) {
        Task {
            do { try await block() }
            catch is SignInCancelled { /* dismissed picker */ }
            catch { errorMessage = error.userMessage }
        }
    }
}

/// Guest access (D-009). Deliberately the least prominent action on the screen: signing in
/// is the path we want, and an anonymous session cannot be recovered after a sign-out.
private struct GuestButton: View {
    @ObservedObject var auth: AuthModel
    @State private var errorMessage = ""
    @State private var loading = false

    var body: some View {
        VStack(spacing: 8) {
            Divider()
            if !errorMessage.isEmpty { ErrorText(errorMessage) }
            Button("Continue without login") {
                errorMessage = ""
                loading = true
                Task {
                    // Success is the auth gate's job.
                    do { try await auth.continueAsGuest() }
                    catch { errorMessage = error.userMessage }
                    loading = false
                }
            }
            .disabled(loading)
            Text("You can browse as a guest. Your data is tied to this install only.")
                .font(.footnote)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
        }
    }
}

private struct EmailForm: View {
    @ObservedObject var auth: AuthModel

    // Flutter defaults to sign-up.
    @State private var action: AuthAction = .signUp
    @State private var email = ""
    @State private var password = ""
    @State private var confirm = ""
    @State private var errorMessage = ""
    @State private var loading = false
    // Field errors only appear after a submit attempt, like Flutter's Form.validate().
    @State private var submitted = false
    @State private var showCheckEmail = false
    @State private var showRecover = false

    private var isSignUp: Bool { action == .signUp }

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text(isSignUp ? "Create an account" : "Login").font(.title2)
            AppTextField(label: "Email", text: $email,
                         error: submitted ? EmailFormValidation.email(email) : nil,
                         enabled: !loading, keyboard: .emailAddress, contentType: .emailAddress)
            AppTextField(label: "Password", text: $password,
                         error: submitted ? EmailFormValidation.password(password) : nil,
                         obscure: true, enabled: !loading,
                         contentType: isSignUp ? .newPassword : .password)
            if isSignUp {
                AppTextField(label: "Confirm Password", text: $confirm,
                             error: submitted ? EmailFormValidation.confirmPassword(password, confirm) : nil,
                             obscure: true, enabled: !loading, contentType: .newPassword)
            }
            if !errorMessage.isEmpty { ErrorText(errorMessage) }
            HStack {
                Button(isSignUp ? "Have an account?" : "Don't have an account?") {
                    action = isSignUp ? .signIn : .signUp
                    errorMessage = ""
                    submitted = false
                }
                Spacer()
                // Flutter shows this beside the toggle in sign-in mode only.
                if !isSignUp { OutlineTextButton(title: "Forgot Password") { showRecover = true } }
            }
            PrimaryButton(title: isSignUp ? "Create Account" : "Login", enabled: !loading, action: submit)
        }
        .sheet(isPresented: $showRecover) {
            RecoverPasswordSheet(auth: auth, initialEmail: email)
        }
        .alert("Check your Email!", isPresented: $showCheckEmail) {
            Button("Ok", role: .cancel) {}
        } message: {
            Text("We sent an email from hi@devtodollars.com to verify your email")
        }
    }

    private func submit() {
        submitted = true
        let valid = EmailFormValidation.email(email) == nil
            && EmailFormValidation.password(password) == nil
            && (!isSignUp || EmailFormValidation.confirmPassword(password, confirm) == nil)
        guard valid else { return }
        loading = true
        errorMessage = ""
        Task {
            do {
                if isSignUp {
                    try await auth.signUp(email: email, password: password)
                    // Email confirmation is on (§2.8): no session, so flip to login + prompt.
                    action = .signIn
                    submitted = false
                    showCheckEmail = true
                } else {
                    try await auth.signInWithPassword(email: email, password: password)
                }
            } catch {
                errorMessage = error.userMessage
            }
            loading = false
        }
    }
}
