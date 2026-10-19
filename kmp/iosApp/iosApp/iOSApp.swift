import SwiftUI

@main
struct iOSApp: App {
    @StateObject private var env = AppEnvironment()

    var body: some Scene {
        WindowGroup {
            RootView(auth: env.auth)
                .environmentObject(env)
                // D-003 custom-scheme deep links (reset, OAuth return, Stripe return, payments).
                .onOpenURL { env.handle(url: $0) }
        }
    }
}
