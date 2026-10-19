import Foundation

/// Sign-in vs sign-up toggle of the shared email form (Flutter `AuthAction`).
enum AuthAction { case signIn, signUp }

/// Validation rules ported verbatim from Flutter's `email_form.dart` (PORTING.md §1.1); mirrors Android's
/// `EmailFormValidation`. Pure Swift so it can be checked on the host (`iosApp/Checks`).
enum EmailFormValidation {
    static func email(_ email: String) -> String? {
        if email.isEmpty { return "Email is required" }
        // Same pattern as Flutter / Android: ^\S+@\S+\.\S+$
        if email.range(of: #"^\S+@\S+\.\S+$"#, options: .regularExpression) == nil { return "Invalid email" }
        return nil
    }

    static func password(_ password: String) -> String? {
        if password.isEmpty { return "Password is required" }
        if password.count < 6 { return "Password must be at least 6 characters long" }
        return nil
    }

    static func confirmPassword(_ password: String, _ confirm: String) -> String? {
        if confirm.isEmpty { return "Password confirmation is required" }
        if confirm != password { return "Passwords do not match" }
        return nil
    }
}
