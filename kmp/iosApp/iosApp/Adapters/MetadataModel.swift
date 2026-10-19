import Foundation
import SharedLogic

/// The signed-in user's profile plus current plan, copied out of the Kotlin `UserMetadata`.
struct UserProfile: Equatable {
    var fullName: String
    var avatarUrl: String
    var subscribed: Bool
    var planName: String?
}

enum MetadataState: Equatable {
    case loading
    case failed
    /// `nil` when signed out or when the stored value is absent.
    case loaded(UserProfile?)

    var homeStatus: HomeStatus {
        switch self {
        case .loading: return .loading
        case .failed: return .failed
        case let .loaded(profile): return .loaded(subscribed: profile?.subscribed ?? false, planName: profile?.planName)
        }
    }
}

/// Thin iOS adapter over the app-scoped shared `MetadataStore` (D-001).
@MainActor
final class MetadataModel: ObservableObject {
    @Published private(set) var state: MetadataState = .loading

    private let store: MetadataStore
    private var watcher: Watcher?

    init(store: MetadataStore) {
        self.store = store
        watcher = FlowWatchKt.watch(store.state) { [weak self] value in
            MainActor.assumeIsolated { self?.state = Self.map(value) }
        }
    }

    deinit { watcher?.cancel() }

    func refresh() { store.refresh() }

    func updateProfile(fullName: String, avatarUrl: String) async throws {
        try await store.updateProfile(fullName: fullName, avatarUrl: avatarUrl)
    }

    private static func map(_ value: Any?) -> MetadataState {
        switch value {
        case let data as AsyncValueData<AnyObject>:
            guard let user = data.value as? UserMetadata else { return .loaded(nil) }
            let subscription = user.subscription
            return .loaded(UserProfile(
                fullName: user.fullName ?? "",
                avatarUrl: user.avatarUrl ?? "",
                subscribed: subscription != nil,
                planName: subscription?.prices?.products?.name
            ))
        case is AsyncValueError:
            return .failed
        default:
            return .loading
        }
    }
}
