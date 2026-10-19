import Foundation

/// Route names are identical on both platforms (PORTING.md D-007) so analytics emits the same screens.
enum AppRoute: String {
    case loading, login, home, reset, account, payments

    /// Screen-view name, matching Flutter's `PosthogObserver` route paths (§1.5).
    var screenName: String {
        switch self {
        case .home: return "/"
        case .login: return "/login"
        case .payments: return "/payments"
        case .loading: return "/loading"
        case .reset: return "/reset"
        case .account: return "/account"
        }
    }
}

/// Pushed destinations of the signed-in `NavigationStack` (D-007b).
enum SignedInRoute: Hashable {
    case account
    /// `price == nil` opens the billing portal, otherwise Checkout for that price.
    case payments(price: String?)

    static func payments(requested price: String?) -> SignedInRoute {
        let trimmed = price?.trimmingCharacters(in: .whitespacesAndNewlines)
        return .payments(price: (trimmed?.isEmpty ?? true) ? nil : trimmed)
    }

    var appRoute: AppRoute {
        switch self {
        case .account: return .account
        case .payments: return .payments
        }
    }
}
