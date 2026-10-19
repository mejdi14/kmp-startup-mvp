import Foundation
import SharedLogic
#if canImport(PostHog)
import PostHog
#endif

/// PostHog iOS SDK behind the shared `AnalyticsTracker` (task 12's iOS half). Config comes from the
/// `com.posthog.posthog.*` Info.plist keys, the same native keys the Flutter app used (§1.5); a missing key
/// disables analytics. Screen views come from the SwiftUI screens, not UIKit autocapture.
final class PostHogTracker: NSObject, AnalyticsTracker {
    private let enabled: Bool

    override init() {
        #if canImport(PostHog)
        let info = Bundle.main.infoDictionary ?? [:]
        let apiKey = (info["com.posthog.posthog.API_KEY"] as? String) ?? ""
        if apiKey.isEmpty {
            enabled = false
            NSLog("PostHog API key missing; analytics disabled")
        } else {
            let host = (info["com.posthog.posthog.POSTHOG_HOST"] as? String) ?? "https://app.posthog.com"
            let config = PostHogConfig(projectToken: apiKey, host: host)
            config.captureApplicationLifecycleEvents =
                (info["com.posthog.posthog.CAPTURE_APPLICATION_LIFECYCLE_EVENTS"] as? Bool) ?? true
            config.captureScreenViews = false
            #if DEBUG
            config.debug = true
            #endif
            PostHogSDK.shared.setup(config)
            enabled = true
        }
        #else
        enabled = false
        #endif
        super.init()
    }

    func identify(userId: String, properties: [String: Any]) {
        #if canImport(PostHog)
        if enabled { PostHogSDK.shared.identify(userId, userProperties: properties) }
        #endif
    }

    func reset() {
        #if canImport(PostHog)
        if enabled { PostHogSDK.shared.reset() }
        #endif
    }

    func screen(name: String) {
        #if canImport(PostHog)
        if enabled { PostHogSDK.shared.screen(name) }
        #endif
    }
}
