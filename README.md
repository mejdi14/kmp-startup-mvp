# KMP Startup MVP

A production template for a subscription mobile app: native UI on both
platforms (Jetpack Compose on Android, SwiftUI on iOS) over a shared Kotlin
Multiplatform core, backed by Supabase, Stripe and PostHog.

It is a port of the Flutter template kept under `flutter/`. That directory,
`BOILERPLATE-NOTES.md` and `docs/` describe the *original* Flutter/Next.js
boilerplate and are reference only.

## What you get

- Email/password auth, password reset, Google (native), GitHub and Apple sign-in
- Auth-gated navigation, session persistence (Supabase PKCE sessions)
- Subscription status on Home, Stripe Checkout / Billing Portal via the
  `get_stripe_url` edge function, with polling after returning to the app
- PostHog identify/reset and screen-view analytics
- Deep links on the `com.devtodollars.app://` scheme
- GitHub Actions CI (Android tests, lint, assemble; iOS simulator build)

## Layout

| Path | Contents |
|---|---|
| `kmp/sharedLogic` | Shared business/data logic only (no UI): Supabase, stores, repositories |
| `kmp/androidApp` | Jetpack Compose UI + Navigation Compose |
| `kmp/iosApp` | SwiftUI UI + `NavigationStack`; open `iosApp.xcodeproj` |
| `supabase/` | Database migrations, edge functions, local config |
| `.env.example` | Reference for the client keys (see below) |
| `SETUP.md` | Full setup guide |
| `PORTING.md`, `TASKS.md` | Design decisions and build order of the port |

## Quick start

Prerequisites: JDK 21, Android SDK (set `sdk.dir` in `kmp/local.properties`),
and for iOS macOS with Xcode. For local backend: Docker + the Supabase CLI.

```sh
# Local backend (debug builds point here)
supabase start

# Checks that need no device
cd kmp
./gradlew :sharedLogic:allTests :androidApp:testDebugUnitTest :androidApp:lintDebug
./gradlew :androidApp:assembleDebug
```

Then follow **[SETUP.md](SETUP.md)** to choose local vs. hosted Supabase, set
the optional Google client IDs, run on a device, and configure Stripe.

## Local vs. hosted at a glance

- **Debug builds** use local Supabase (`http://localhost:54321`).
- **Release builds** use hosted Supabase.

This mirrors `flutter/env.local.json` / `flutter/env.json`. Both configs live in
`SupabaseConfig.kt`; see `SETUP.md`.
