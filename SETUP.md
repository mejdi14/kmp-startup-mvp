# Setup

Everything you need to go from clone to a running build.

## 1. Prerequisites

| Tool | Notes |
|---|---|
| JDK 21 | Gradle toolchain; CI uses Temurin 21 |
| Android SDK | Create `kmp/local.properties` with `sdk.dir=/path/to/Android/sdk` (git-ignored; Android Studio does this for you) |
| Xcode (macOS) | iOS app and Kotlin/Native framework |
| Docker + [Supabase CLI](https://supabase.com/docs/guides/cli) | Only for local Supabase |

## 2. Choose a backend: local or hosted

The build type picks the backend, exactly like Flutter's `env.local.json` /
`env.json` split:

| Build | Backend | Selected by |
|---|---|---|
| Android debug / Xcode Debug | **Local** `http://localhost:54321` | `BuildConfig.SUPABASE_ENV = "LOCAL"` / `#if DEBUG` |
| Android release / Xcode Release | **Hosted** | `BuildConfig.SUPABASE_ENV = "HOSTED"` / `#else` |

The two configs are constants in
`kmp/sharedLogic/src/commonMain/kotlin/com/mejdi14/kmpstartupmvp/config/SupabaseConfig.kt`
(`LOCAL` and `HOSTED`). They contain only the Supabase URL and the **anon**
key, which are public by design and committed, as Flutter committed its env
files. `.env.example` documents these two values for each environment; **the
build does not read it**. To use your own project, copy the values into
`SupabaseConfig.kt`.

> The committed `HOSTED` values point at the original template's project. Replace
> them with your own before shipping.

### Local

```sh
supabase start                       # prints API URL and anon key
supabase functions serve --env-file supabase/.env.local --import-map supabase/functions/deno.json
```

- Local keys are the stock Supabase demo values; they usually need no change.
- **Android emulator or physical device:** `localhost` is the device itself.
  Run `adb reverse tcp:54321 tcp:54321` after each device/emulator start.
- **iOS simulator:** `localhost` reaches the Mac directly. A physical iPhone
  cannot reach it; use a hosted project or a Release build for device testing.
- Create `supabase/.env.local` (git-ignored) with the function secrets listed in
  section 5.

### Hosted

1. Create a Supabase project and apply the schema: `supabase link --project-ref <ref>`
   then `supabase db push`.
2. Deploy functions: `supabase functions deploy --import-map supabase/functions/deno.json`.
3. Set secrets: `supabase secrets set --env-file supabase/.env`.
4. Put the project URL and anon key into `SupabaseConfig.HOSTED`.

## 3. Allow the mobile redirect URL (required for auth)

The app uses the custom scheme `com.devtodollars.app://login-callback` for OAuth,
email confirmation, password reset and Stripe returns (PORTING.md D-003).
Supabase rejects redirects that are not allow-listed, so add it in **both**:

- Local: `supabase/config.toml` -> `additional_redirect_urls`
  (add `"com.devtodollars.app://login-callback"` and, for password reset,
  `"com.devtodollars.app://login-callback/**"`), then `supabase stop && supabase start`.
- Hosted: Dashboard -> Authentication -> URL Configuration -> Redirect URLs.

Email confirmation is on, so sign-up shows "check your email" instead of
logging in. Locally, read the mail in Inbucket at http://localhost:54324.

## 4. Social sign-in and guest access (optional)

Without these, the buttons show a clear "not configured" error.

- **Google (native).** Create an OAuth *web* client and register it in Supabase
  (Authentication -> Providers -> Google; locally `GOOGLE_CLIENT_ID` /
  `GOOGLE_SECRET` for `config.toml`). Also create Android and iOS clients.
  - Android: `GOOGLE_WEB_CLIENT_ID=<web client id>` in `~/.gradle/gradle.properties`
    (or `-PGOOGLE_WEB_CLIENT_ID=...`).
  - iOS: fill `GOOGLE_IOS_CLIENT_ID`, `GOOGLE_WEB_CLIENT_ID` and
    `GOOGLE_REVERSED_CLIENT_ID` (`com.googleusercontent.apps.<id>`) in
    `kmp/iosApp/Configuration/Config.xcconfig`.
- **GitHub.** Browser redirect; enable the provider in Supabase with your
  GitHub OAuth app (`GITHUB_CLIENT_ID` / `GITHUB_SECRET` locally).
- **Apple (iOS).** Enable the provider in Supabase and the *Sign in with Apple*
  capability for your App ID.

### Guest access — "Continue without login"

That button signs the user in **anonymously** (PORTING.md D-009): a real
Supabase user with no email and no credentials, so the auth gate, the
`public.users` trigger and every RLS policy work unchanged. Anonymous sign-in
has to be switched on, or the button fails with *"Anonymous sign-ins are
disabled"*:

- Local: already enabled — `enable_anonymous_sign_ins = true` in
  `supabase/config.toml`.
- Hosted: Dashboard -> Authentication -> Sign In / Providers -> **Anonymous
  sign-ins**.

Two things to know before you keep it: every guest is a row in `auth.users`,
so abandoned guest accounts accumulate and deserve a periodic cleanup; and a
guest who signs out can never sign back in, since there are no credentials to
do it with. To remove guest access entirely, delete the button from
`AuthScreen.kt` and `AuthScreen.swift` — the shared `continueAsGuest()` has no
other caller.

### Turning a guest into a real account

A guest can keep the account they built (PORTING.md D-010). The Account screen
shows **Save your account** while the session is anonymous; it asks for an
email and password and calls `AuthStore.saveGuestAccount`, which sets both on
the *existing* user. The user id does not change, so the `public.users` row,
the subscription and the Stripe customer carry over untouched — there is no
migration step and nothing else in the app has to know it happened.

Two outcomes, depending on your project's email-confirmation setting:

- **Confirmation off** — the credentials work immediately, the session stops
  being anonymous, and the guest section disappears on its own.
- **Confirmation on** (the default, and what §2.8 assumes) — the password is
  set right away, but the address stays pending until the emailed link is
  opened. The session is *still anonymous* until then, so the warning and the
  entry point stay on screen. The link returns through the existing D-003 deep
  link, `com.devtodollars.app://login-callback`, so add that bare URL to
  Dashboard -> Authentication -> URL Configuration -> **Redirect URLs** (local
  already allows it via `supabase/config.toml`), or the link cannot come back.

If the email already belongs to another account, Supabase refuses it
(`email_exists`) and the app says so plainly: a guest session cannot be folded
into an existing account — there is no merge, in Supabase or here — so the
choice is to sign in to that account and leave this session behind, or save it
under a different email.

Because a guest sign-out is unrecoverable, **Logout** asks a guest to confirm
first and offers the conversion instead. The warning copy lives in
`HomeScreen.kt` / `HomeScreen.swift` (`GUEST_SIGN_OUT_WARNING` /
`guestSignOutWarning`) and the Account section's in `AccountScreen.kt` /
`AccountScreen.swift`.

## 5. Stripe, PostHog, Loops (backend)

Function secrets (`supabase/.env.local` for local, `supabase/.env` for hosted;
both git-ignored, never commit them):

```
STRIPE_SECRET_KEY=
STRIPE_WEBHOOK_SIGNING_SECRET=
POSTHOG_CLIENT_KEY=
LOOPS_API_KEY=
LOOPS_WELCOME_TEMPLATE_ID=
```

`SUPABASE_URL` and `SUPABASE_SERVICE_ROLE_KEY` are injected automatically.
Sync Stripe products/prices into Supabase:

```sh
deno run -A supabase/functions/_scripts/sync-stripe.ts
```

Point your Stripe webhook at the deployed `stripe_webhook` function.

**PostHog (client).** The public project key and host are set natively:
`androidApp/src/main/AndroidManifest.xml` (`com.posthog.posthog.API_KEY`) and
`iosApp/iosApp/Info.plist`. Replace the committed key with your own.

## 6. Build and run

```sh
cd kmp
./gradlew :androidApp:assembleDebug        # Android debug (local Supabase)
./gradlew :androidApp:assembleRelease      # unsigned; see below
```

- Android: open `kmp/` in Android Studio and run `androidApp`.
- iOS: open `kmp/iosApp/iosApp.xcodeproj`, set your team via `TEAM_ID` in
  `iosApp/Configuration/Config.xcconfig`, and run. The "Compile Kotlin
  Framework" build phase calls Gradle for you.

Change the application ID / bundle ID (`com.mejdi14.kmpstartupmvp`) and the URL
scheme (`com.devtodollars.app`, in `AndroidManifest.xml`, `Info.plist` and
`DeepLink.kt`) for your own app.

**Release signing:** the Android release build is intentionally *unsigned*. Add
a `signingConfig` fed by your keystore properties in `androidApp/build.gradle.kts`
before shipping. It must not fall back to the debug key.

## 7. Checks (no device needed)

```sh
cd kmp
./gradlew :sharedLogic:allTests :androidApp:testDebugUnitTest   # unit tests
./gradlew :androidApp:lintDebug                                  # lint
./gradlew :sharedLogic:iosSimulatorArm64Test                     # Kotlin/Native tests (macOS)
iosApp/Checks/run.sh                                             # pure-Swift checks (macOS)
```

CI (`.github/workflows/ci.yml`) runs the same on every push and pull request.

## Troubleshooting

- **Network error on Android debug:** run `adb reverse tcp:54321 tcp:54321`.
- **Login redirect rejected / opens nothing:** the redirect URL is not
  allow-listed (section 3).
- **Google button says not configured:** the client IDs in section 4 are empty.
- **Payments return doesn't update:** the app re-queries `subscriptions` after
  returning; check that the `stripe_webhook` function is deployed and receiving events.
