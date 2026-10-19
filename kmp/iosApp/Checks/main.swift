import Foundation

// Host-side (macOS) unit checks for the pure Swift logic in iosApp/Logic. No simulator or device.
// Run: iosApp/Checks/run.sh
var failures = 0
func check(_ cond: Bool, _ name: String) {
    if cond { print("ok   \(name)") } else { print("FAIL \(name)"); failures += 1 }
}

// EmailFormValidation (rules from Flutter's email_form.dart)
check(EmailFormValidation.email("") == "Email is required", "email empty")
check(EmailFormValidation.email("a@b") == "Invalid email", "email no tld")
check(EmailFormValidation.email("a b@c.de") == "Invalid email", "email whitespace")
check(EmailFormValidation.email("a@b.co") == nil, "email valid")
check(EmailFormValidation.password("") == "Password is required", "password empty")
check(EmailFormValidation.password("12345") == "Password must be at least 6 characters long", "password short")
check(EmailFormValidation.password("123456") == nil, "password ok")
check(EmailFormValidation.confirmPassword("abcdef", "") == "Password confirmation is required", "confirm empty")
check(EmailFormValidation.confirmPassword("abcdef", "abcdeg") == "Passwords do not match", "confirm mismatch")
check(EmailFormValidation.confirmPassword("abcdef", "abcdef") == nil, "confirm match")

// Screen names (match Flutter PosthogObserver paths / Android ScreenNames)
check(AppRoute.home.screenName == "/", "screen home")
check(AppRoute.login.screenName == "/login", "screen login")
check(AppRoute.payments.screenName == "/payments", "screen payments")
check(AppRoute.loading.screenName == "/loading", "screen loading")
check(AppRoute.reset.screenName == "/reset", "screen reset")
check(AppRoute.account.screenName == "/account", "screen account")
check(SignedInRoute.account.appRoute == .account && SignedInRoute.payments(price: nil).appRoute == .payments, "route mapping")

// Payments route price normalization (Android Routes.payments)
check(SignedInRoute.payments(requested: nil) == .payments(price: nil), "payments nil")
check(SignedInRoute.payments(requested: "  ") == .payments(price: nil), "payments blank")
check(SignedInRoute.payments(requested: " price_1 ") == .payments(price: "price_1"), "payments trimmed")

// Home status sentence
check(statusText(.loading) == nil, "status loading")
check(statusText(.failed) == "Failed to load subscription plan", "status failed")
check(statusText(.loaded(subscribed: false, planName: nil)) == "You are not currently subscribed to any plan.", "status none")
check(statusText(.loaded(subscribed: true, planName: "Pro")) == "You are currently on the Pro plan.", "status plan")
check(statusText(.loaded(subscribed: true, planName: nil)) == "You are currently subscribed to a plan.", "status nameless")

// Nonce: known SHA-256 vector and raw nonce shape
check(Nonce.sha256Hex("abc") == "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", "sha256 vector")
check(Nonce.random().count == 64 && Nonce.random() != Nonce.random(), "nonce random")

print(failures == 0 ? "ALL PASSED" : "\(failures) FAILED")
exit(failures == 0 ? 0 : 1)
