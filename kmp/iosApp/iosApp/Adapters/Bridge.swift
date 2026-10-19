import Foundation
import SharedLogic

/// Kotlin `StateFlow` -> Swift bridge (D-001, decided in task 13): a hand-written collector wrapper
/// (`FlowWatchKt.watch`, in `sharedLogic/iosMain`), not SKIE. The callback runs on the main thread.
@MainActor
func observe<T>(_ flow: Kotlinx_coroutines_coreStateFlow, as type: T.Type = T.self,
                _ onChange: @escaping @MainActor (T) -> Void) -> Watcher {
    FlowWatchKt.watch(flow) { value in
        MainActor.assumeIsolated {
            if let typed = value as? T { onChange(typed) }
        }
    }
}

/// Like `observe`, for flows of nullable values (nil is delivered as `Optional<T>.none`).
@MainActor
func observeOptional<T>(_ flow: Kotlinx_coroutines_coreStateFlow, as type: T.Type = T.self,
                        _ onChange: @escaping @MainActor (T?) -> Void) -> Watcher {
    FlowWatchKt.watch(flow) { value in
        MainActor.assumeIsolated { onChange(value as? T) }
    }
}

extension Error {
    /// Message of a failed Kotlin suspend call (surfaced as an `NSError` wrapping the throwable).
    var userMessage: String {
        let ns = self as NSError
        if let throwable = ns.userInfo["KotlinException"] as? KotlinThrowable {
            return throwable.userMessage()
        }
        return localizedDescription
    }
}

/// A native sign-in the user dismissed; callers show no error for it.
struct SignInCancelled: Error {}
