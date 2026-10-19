import Foundation

/// What the Home status sentence needs from the metadata state (PORTING.md §1.1).
enum HomeStatus: Equatable {
    case loading
    case failed
    case loaded(subscribed: Bool, planName: String?)
}

/// The four-state status sentence; nil for loading, which renders a spinner. Mirrors Android's `statusText`.
func statusText(_ status: HomeStatus) -> String? {
    switch status {
    case .loading:
        return nil
    case .failed:
        return "Failed to load subscription plan"
    case let .loaded(subscribed, planName):
        if !subscribed { return "You are not currently subscribed to any plan." }
        // Flutter interpolated a missing name as "null"; fall back to a generic sentence.
        if let planName { return "You are currently on the \(planName) plan." }
        return "You are currently subscribed to a plan."
    }
}
