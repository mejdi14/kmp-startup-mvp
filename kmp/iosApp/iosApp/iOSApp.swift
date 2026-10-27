import SwiftUI

@main
struct iOSApp: App {
    // Sign-in, payments and analytics from the boilerplate (`AppEnvironment`, `RootView`) are skipped:
    // the app opens straight into Shadow Studio, and nothing is sent to Supabase or PostHog.
    var body: some Scene {
        WindowGroup {
            ShadowRoot()
        }
    }
}
