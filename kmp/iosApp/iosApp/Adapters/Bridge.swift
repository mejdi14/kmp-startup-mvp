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
    /// The one sentence to show for a failed call (D-010). Kotlin failures arrive as an `NSError`
    /// wrapping the throwable; the copy comes from `sharedLogic`, so it matches Android exactly.
    /// The raw SDK/NSURL detail is logged rather than shown — it used to be the whole message.
    var userMessage: String {
        let ns = self as NSError
        guard let throwable = ns.userInfo["KotlinException"] as? KotlinThrowable else {
            return localizedDescription
        }
        NSLog("[AppError] %@", throwable.technicalMessage())
        return throwable.userMessage()
    }
}

/// A native sign-in the user dismissed; callers show no error for it.
struct SignInCancelled: Error {}
