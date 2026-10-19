# kmp — native-UI Kotlin Multiplatform app

- `sharedLogic/` — Kotlin Multiplatform, **business/data only** (no UI).
  Built as a library for Android and as the `SharedLogic` framework for iOS.
- `androidApp/` — native Jetpack Compose UI with Navigation Compose.
- `iosApp/` — native SwiftUI UI with `NavigationStack`; open `iosApp/iosApp.xcodeproj` in Xcode.

See `../PORTING.md` (decisions) and `../TASKS.md` (build order).

- Build + checks (no device needed): `./gradlew build`
- iOS framework compile: `./gradlew :sharedLogic:linkDebugFrameworkIosSimulatorArm64`
- Shared tests: `./gradlew :sharedLogic:testAndroidHostTest :sharedLogic:iosSimulatorArm64Test`
