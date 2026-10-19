# Porting DevToDollars from Flutter to Kotlin Multiplatform

**Status:** planning complete, no screens ported. Architecture decisions recorded in §4.
**Scope:** Android + iOS only. No web, no desktop.
**UI approach:** fully native UIs. Android = Jetpack Compose + Navigation Compose; iOS = SwiftUI + `NavigationStack`. Shared Kotlin is business/data logic only. No Compose Multiplatform UI, no shared Compose navigation. (Revised 2026-10-19, Task 0.)
**Written:** 2026-10-15.

Read this file before doing any work in `kmp/`. It is written for someone
starting cold with no memory of the planning session. Later sessions should
read this instead of re-exploring `flutter/`.

## Ground rules

- `flutter/` is the **reference implementation**. Read it freely. **Never modify it.**
- `supabase/` is the **backend, reused unchanged** — same schema, same edge
  functions, same Stripe webhooks. Treat its contract as fixed (one exception,
  documented in §2.8: mobile redirect URLs must be added to the allow-list,
  which is dashboard/config, not code).
- `BOILERPLATE-NOTES.md`, `docs/` and the root `.claude/agents/*.md` describe the
  original Flutter/Next.js boilerplate. They are **reference, not instructions**
  for this project.
- The KMP project lives in `kmp/`, with three modules: `sharedLogic` (Kotlin
  Multiplatform, **non-UI**: config, Supabase client, repositories, auth/metadata
  state holders, auth-gate derivation, deep-link URL parsing), `androidApp`
  (native Jetpack Compose UI), and `iosApp` (native SwiftUI UI, consuming the
  `SharedLogic` framework). There is no `sharedUI` module.
- **UI is never shared.** Screens, navigation, themes, and presentation
  adapters (the glue between shared state and a platform's UI framework) are
  written separately per platform. Shared code must not depend on Compose,
  Compose Multiplatform, AndroidX navigation, or any UI toolkit.

---

# 1. Inventory of the Flutter app

The entire app is **16 Dart files, ~1,100 LOC**: 3 screens, 1 inline route,
2 dialogs, 3 Riverpod providers, 4 hand-written models. This is a small port.
The hard parts are not volume — they are OAuth deep links, Stripe return
handling, and iOS payment-policy risk.

App package: `devtodollars`, title "DevToDollars". Flutter targets iOS, Android,
Web, macOS, Linux, Windows today; **web is the primary target in practice**, and
several flows (OAuth, email confirmation, password reset, Stripe return) only
actually work on web. See §1.9.

## 1.1 Screens

| Screen | File | Route | State it holds / watches |
|---|---|---|---|
| `AuthScreen` | `flutter/lib/screens/auth_screen.dart` | `login` → `/login` | plain `StatefulWidget`; local `bool showEmailForm` only. Watches **no** providers — the router redirects it away on login. |
| `HomeScreen` | `flutter/lib/screens/home_screen.dart` | `home` → `/` | `ConsumerStatefulWidget`; watches `authProvider.notifier` (for `signOut`) and `metadataProvider` (`AsyncValue<UserMetadata?>`, consumed via `.when`). Required param `title`. |
| `PaymentsScreen` | `flutter/lib/screens/payments_screen.dart` | `payments` → `/payments?price=…` | `ConsumerStatefulWidget`; `ref.read(authProvider.notifier)` one-shot in `initState`. Param `String? price`. Nothing is re-read after returning from Stripe. |
| *(loading)* | `flutter/lib/services/router_notifier.dart:53-59` | `loading` → `/loading` | anonymous builder, `Center(CircularProgressIndicator())`. No screen class, no `Scaffold`. This is the de-facto splash. |
| `ResetPasswordDialog` | `flutter/lib/components/reset_password_dialog.dart` | `reset` → `/reset` (child of `/`, rendered as a dialog **page**) | local `errMessage`, controller, form key; reads `authProvider.notifier`. Deep-link target of Supabase recovery email. |
| `RecoverPasswordDialog` | `flutter/lib/components/recover_password_dialog.dart` | not a route — imperative `showDialog` from `email_form.dart` | local `errMessage`, controller, form key; reads `authProvider.notifier`. Required param `email`. |

Screen details worth keeping:

- **AuthScreen** renders `SupaSocialsAuth` (from `supabase_auth_ui`) with
  GitHub + Google, plus a "or continue with email" `TextButton` that reveals
  `EmailForm`. It has dead code: `handleSignUpComplete()` is never called (the
  same "Check your Email!" dialog is duplicated inline in `email_form.dart`).
- **HomeScreen** shows one sentence driven by `metadataProvider`:
  `"You are currently on the ${subscription.prices?.products?.name} plan."` /
  `"You are not currently subscribed to any plan."` / spinner / `"Failed to load
  subscription plan"`. AppBar has **Payments** (`replaceNamed("payments")` with
  **no price param**, so it always opens the Stripe *billing portal*) and
  **Logout**. "See Pricing" launches an external GitHub README URL — a
  placeholder, not a real pricing page.
- **PaymentsScreen** is a pure interstitial. `initState` → post-frame →
  `getUserStripeLink(price:)` → `launchUrl(url, webOnlyWindowName: "_self")`.
  On `FunctionException` it shows an AlertDialog "Failed to open Stripe" with
  `e.details?["message"]`.

Supporting widgets:

- `flutter/lib/components/email_form.dart` — `EmailForm` + `enum AuthAction
  { signIn, signUp }`. Defaults to **signUp**. Three controllers (email,
  password, confirm), `errorMessage`, `loading`, form key. Validation: email
  non-empty + `RegExp(r"^\S+@\S+\.\S+$")`; password non-empty + ≥6 chars;
  confirm must match. Uses `AutofillGroup` with email/password hints.
  Sign-up succeeds → flips to signIn and shows the "Check your Email!" dialog.
  UI typo in the original: `"Create Acccount"`.
- `flutter/lib/components/dialog_page.dart` — `DialogPage<T> extends Page<T>`,
  pure go_router plumbing so a dialog can be a navigator page. KMP equivalent:
  a dialog destination in the nav graph.

## 1.2 State management

Riverpod only (`flutter_riverpod` 2.5.1, `riverpod_annotation` 2.3.5, codegen by
`riverpod_generator` 2.4.0). `ProviderScope` in `main.dart:13`. **Exactly three
providers**, all in `flutter/lib/services/`. No freezed, no json_serializable —
model JSON is hand-written.

| Provider | File | Type | State | Consumers |
|---|---|---|---|---|
| `authProvider` | `auth_notifier.dart:13-14` (`@Riverpod(keepAlive: true) class Auth extends _$Auth`) | `StreamNotifierProvider<Auth, Session?>`, **keepAlive** | `AsyncValue<Session?>` | router (watch), HomeScreen (watch `.notifier`), PaymentsScreen, EmailForm, both dialogs (read `.notifier`), `metadataProvider` (watch) |
| `metadataProvider` | `metadata_notifier.dart:9-10` (`@riverpod class Metadata extends _$Metadata`) | `AutoDisposeAsyncNotifierProvider<Metadata, UserMetadata?>` | `AsyncValue<UserMetadata?>` | HomeScreen only |
| `routerProvider` | `router_notifier.dart:19-20` (`@riverpod GoRouter router(RouterRef ref)`) | `AutoDisposeProvider<GoRouter>` | `GoRouter` | `main.dart:22` (watch) |

`Auth` is the central state holder and the app's whole write surface:

- `build()` returns a `Stream<Session?>` fed by a **manual
  `StreamController<Session?>.broadcast()`** that re-emits
  `client.auth.onAuthStateChange` sessions. `ref.onDispose` cancels and closes.
- Methods: `signInWithPassword`, `signInWithOAuth` (**dead code — never called
  from any screen**), `signUp`, `recoverPassword`, `updatePassword`,
  `getUserStripeLink({String? price})`, `signOut`. Getters `client`,
  `currentSession` (**also dead**).
- Side effect inside the stream listener: PostHog `identify` / `reset`.
- **Porting-critical:** `build()` only forwards *future* `onAuthStateChange`
  events; the first value arrives from the SDK's `AuthChangeEvent.initialSession`
  emission at startup. That is what makes the `/loading` gate work. The KMP port
  must reproduce "emit null-or-restored-session exactly once at startup".

`Metadata`:

- `build()` does `ref.watch(authProvider).value`; returns `null` when the session
  is null, so it refetches automatically on login/logout/**token refresh**.
- Fires both queries concurrently:
  `final (metadata, subscription) = await (getUserDetails(), getSubscription()).wait;`
  then mutates `metadata.subscription = subscription`. `UserMetadata` is a
  **mutable** class (non-final `subscription`).

Models (hand-rolled `fromJson`/`toJson`, snake_case keys):

- `flutter/lib/models/user_metadata.dart` — `UserMetadata { avatarUrl,
  billingAddress (Map), fullName, id, paymentMethod (Map), subscription }`.
- `flutter/lib/models/stripe.dart` — `SubscriptionWithPrice` (**all timestamps
  typed `String`**, not `DateTime`), `PriceWithProduct`, `Product`.

Generated files — do **not** hand-port: `auth_notifier.g.dart`,
`metadata_notifier.g.dart`, `router_notifier.g.dart`.

Other state: plain `setState` in the four stateful widgets. Global mutables:
`navigatorKey` and `Uri? initUrl` in `router_notifier.dart:16-17`, plus a second
file-level `final Uri initUrl = Uri.base` in `auth_screen.dart:7`.

## 1.3 Supabase call sites

Client: `supabase_flutter` 2.5.7, singleton `Supabase.instance.client`.
**13 touch points total.**

**Init** — `main.dart:9-12`:
`Supabase.initialize(url: String.fromEnvironment('SUPABASE_URL'), anonKey: String.fromEnvironment('SUPABASE_ANON_KEY'))`.
No `authOptions`, so all defaults apply: PKCE flow, `detectSessionInUri: true`,
`autoRefreshToken: true`, SharedPreferences storage.

**Auth** — all in `auth_notifier.dart`:

| Line | Call |
|---|---|
| 23 | `client.auth.onAuthStateChange.listen(...)` — the app's single auth event source |
| 46 | `client.auth.currentSession` (getter; **unused by any screen**) |
| 49 | `client.auth.signInWithPassword(email:, password:)` |
| 54-61 | `client.auth.signInWithOAuth(provider, redirectTo: kIsWeb ? Uri.base.origin : null, queryParams: {'access_type':'offline','prompt':'consent'})` — **never called**; the social buttons go through `supabase_auth_ui` instead |
| 65 | `client.auth.signUp(email:, password:)` |
| 70 | `client.auth.resetPasswordForEmail(email, redirectTo: p.join(Uri.base.origin, "reset"))` |
| 74-78 | `client.auth.updateUser(UserAttributes(password: password))` |
| 96 | `client.auth.signOut()` |

Plus, inside `supabase_auth_ui` 0.4.4 (`SupaSocialsAuth`, the path actually
taken): its own `onAuthStateChange.listen(...)` and
`signInWithOAuth(provider, redirectTo: widget.redirectUrl)`. Because
`googleAuthConfig`/`nativeAppleAuthEnabled` are not passed, the native
`signInWithIdToken` branches are never reached.

**Database** — all in `metadata_notifier.dart`:

| Line | Call |
|---|---|
| 25 | `client.from('users').select('*').single()` — relies on RLS `auth.uid() = id`; **throws if the row is missing** (no `maybeSingle`) |
| 30-36 | `client.from('subscriptions').select('*, prices(*, products(*))').inFilter('status', ['trialing','active']).order('created', ascending: false).limit(1).maybeSingle()` |

No inserts, updates, upserts, deletes or RPC anywhere.

**Edge functions** — one call site, `auth_notifier.dart:84-87`:
`client.functions.invoke("get_stripe_url", body: {"return_url": kIsWeb ? Uri.base.origin : null, "price": price})`,
then `res.data["redirect_url"]`.

**Storage:** none. **Realtime:** none in the app (and the publication only
covers `products`/`prices` anyway — see §2.6).

## 1.4 Stripe / payments

**No Stripe SDK at all.** No `flutter_stripe`, no WebView, no `in_app_purchase`.
The approach is: edge function → hosted Stripe URL → `url_launcher`.

1. User reaches `/payments`, either from the AppBar "Payments" button (no price →
   billing portal) or a URL `…/payments?price=price_XXX` (the README advertises
   exactly this).
2. `router_notifier.dart:87-88` extracts `state.uri.queryParameters["price"]`.
3. `initState` → post-frame → `Auth.getUserStripeLink(price:)`.
4. `functions.invoke("get_stripe_url", body: {return_url, price})`.
   `return_url` = `Uri.base.origin` on web, **`null` on mobile**.
5. The function returns `{"redirect_url": "https://checkout.stripe.com/..."}`.
6. `launchUrl(url, webOnlyWindowName: "_self")` — same-tab on web, external
   browser on mobile.
7. **How the app learns a purchase succeeded: indirectly, and only on web.**
   Stripe redirects back to `return_url` → the web app cold-starts →
   `authProvider` restores → `metadataProvider` rebuilds → re-queries
   `subscriptions` → the HomeScreen sentence updates. There is **no polling, no
   realtime listener, no `ref.invalidate(metadataProvider)`, and no success
   callback** anywhere in the Dart code. On mobile, `return_url == null`, so
   nothing brings the user back and nothing refreshes. **This is a real gap the
   KMP port must fix.**

Subscription status is read only from the `subscriptions` query above and shown
as `subscription.prices?.products?.name`. `subscription == null` is the only
"not subscribed" signal. `checkout_sessions` is never read. No price/product
catalogue is ever fetched — pricing lives on the marketing site.
Cancellation/plan change is entirely delegated to the Stripe Billing Portal via
the price-less `/payments` path.

## 1.5 PostHog / analytics

Package `posthog_flutter` 4.5.0. **Keys are configured natively, not via
dart-define.**

- Android: `flutter/android/app/src/main/AndroidManifest.xml` —
  `com.posthog.posthog.API_KEY`, `POSTHOG_HOST = https://app.posthog.com`,
  `TRACK_APPLICATION_LIFECYCLE_EVENTS = true`, `DEBUG = true`.
- iOS: `flutter/ios/Runner/Info.plist` — same keys,
  `CAPTURE_APPLICATION_LIFECYCLE_EVENTS = true`.
- Web: inline `posthog.init(...)` in `flutter/web/index.html`.
- macOS/Linux/Windows: no config — analytics silently no-op.
- There is **no `Posthog()` init/debug/enable call in Dart at all.**

Exactly three Dart call sites:

| File:line | Call |
|---|---|
| `auth_notifier.dart:29-32` | `Posthog().identify(userId: session.user.id, userProperties: {"email": …})` — on **every** non-null session emission, i.e. login *and* every hourly token refresh |
| `auth_notifier.dart:34` | `Posthog().reset()` — on null session (logout) |
| `router_notifier.dart:25` | `observers: [PosthogObserver()]` on the GoRouter |

`PosthogObserver` captures screen views named by **route path**: `root ('/')`,
`/login`, `/payments`, `/loading`, `/reset`. Dialogs shown via `showDialog`
(RecoverPasswordDialog) are not captured.

**No custom `capture()` calls client-side.** All business events come from the
server: `get_stripe_url` emits `"user starts checkout"` / `"user opens billing
portal"`; `on_user_modify` emits `"user signs up"` / `"user signs in"` /
`"user deletes account"`. **Feature flags are not used.**

## 1.6 Routing

`go_router` 13.2.5, declared in exactly one place:
`flutter/lib/services/router_notifier.dart:19-93`, inside
`@riverpod GoRouter router(RouterRef ref)`. Wired in `main.dart:22-24` via
`MaterialApp.router(routerConfig: ref.watch(routerProvider))`.

Config: `initialLocation: initUrl?.path` (commented `// DO NOT REMOVE`),
`navigatorKey: navigatorKey` (a file-level `GlobalKey<NavigatorState>` that keeps
the same navigator across router rebuilds), `observers: [PosthogObserver()]`.

| name | path | full path | builder |
|---|---|---|---|
| `loading` | `/loading` | `/loading` | inline spinner |
| `login` | `/login` | `/login` | `const AuthScreen()` |
| `home` | `/` | `/` | `const HomeScreen(title: "DevToDollars")` |
| `reset` | `reset` (child of `/`) | `/reset` | `DialogPage(child: ResetPasswordDialog())` |
| `payments` | `/payments` | `/payments` | `PaymentsScreen(price: state.uri.queryParameters["price"])` |

No `ShellRoute`, no `StatefulShellRoute`, no `errorBuilder`, no custom
transitions.

**Auth guard** (`router_notifier.dart:26-51`) switches on
`ref.watch(authProvider)`:

- `loading:` → `/loading`
- `error:` → `/loading`
- `data(user):`
  1. rebuild the intended initial path from `initUrl?.path` (+ query if present);
  2. `if (user == null && initUrl?.path != '/login') return '/login';`
  3. `if (user != null && (initUrl?.path == '/login' || initUrl?.path == '/loading')) return '/';`
  4. null out the global `initUrl` and return `path`.

**Port-critical quirks:** the guard keys off the mutable global `initUrl`
(captured once as `Uri.base` at library load), **not** off `state.uri`. So (a)
the deep-link target survives the login round-trip only via that global; (b)
once `initUrl` is nulled, a logged-out user always matches rule 2 — it works by
accident of null handling, not by design; (c) `routerProvider` is auto-dispose
and watches `authProvider`, so the whole `GoRouter` is rebuilt on **every** auth
event including silent token refreshes, and the shared `navigatorKey` is the only
thing preventing the widget tree from being discarded. **Do not reproduce this
design.** See decision D-002.

**Deep links:** web uses `usePathUrlStrategy()` plus a Netlify SPA rewrite;
`Uri.base` is the deep-link source. **Mobile has no custom scheme and no App
Link registered** — `AndroidManifest.xml` has only `MAIN`/`LAUNCHER`,
`Info.plist` has no `CFBundleURLTypes`, no associated-domains entitlement.
`supabase_flutter` pulls in `app_links` and starts a deep-link observer, so
callbacks *would* be handled, but with no scheme registered **OAuth return,
email confirmation and the `/reset` link only work on web today.**

## 1.7 Session persistence

**Zero bespoke persistence code in `lib/`** — it is all `supabase_flutter` 2.5.7
default behaviour triggered by the bare `Supabase.initialize(url, anonKey)`:

- **Storage:** `SharedPreferencesLocalStorage` → Android `SharedPreferences`,
  iOS `NSUserDefaults`, web `localStorage`. Key:
  `"sb-${host.split('.').first}-auth-token"` → for this project
  `sb-ibyxomczmyaptsjgzvzi-auth-token` (locally `sb-localhost-auth-token`).
  Value is `jsonEncode(session.toJson())` — access token, refresh token, expiry,
  user object. **Not encrypted, not Keychain, not EncryptedSharedPreferences.**
  PKCE code verifiers live in a second store.
- **Write path:** persisted on every session emission; removed on
  `AuthChangeEvent.signedOut`.
- **Startup check:** inside `await Supabase.initialize(...)` →
  `SupabaseAuth.initialize()`: subscribe to `onAuthStateChange`, init storage,
  `hasAccessToken()` → `setInitialSession(persisted)`, register a lifecycle
  observer, start the deep-link observer, then **always emit
  `AuthChangeEvent.initialSession`** (null session if nothing stored). A real
  network `recoverSession()` happens lazily afterwards. **There is no
  `main()`-level session check and no bootstrap/splash widget in app code** —
  the "splash" is purely `authProvider` sitting in `AsyncLoading`.
- **Token refresh:** `autoRefreshToken: true`; gotrue runs a timer, and the
  lifecycle observer calls `startAutoRefresh()` on resume /
  `stopAutoRefresh()` on pause. Backend: `jwt_expiry = 3600`, rotation on,
  `refresh_token_reuse_interval = 10`. Every refresh emits `tokenRefreshed` →
  rebuilds the GoRouter **and** re-fetches `metadataProvider`. Duplicate work,
  hourly. Fix in the port.

## 1.8 Dependencies (pub.dev)

Environment: `sdk: '>=3.2.3 <4.0.0'`; lock resolved against `dart >=3.4.0`,
`flutter >=3.22.0`. Flutter SDK pinned in `.metadata` to `78666c8dc57e…`.

**Direct runtime (8 + 2 SDK):**

| Package | Constraint → resolved | Tags | Used for |
|---|---|---|---|
| `flutter` | sdk | [UI] | the entire view layer |
| `flutter_web_plugins` | sdk | [PLATFORM][UI] | only `usePathUrlStrategy()` — **web-only, dropped in the port** |
| `cupertino_icons` | `^1.0.2` → 1.0.8 | [UI] | **not referenced in any Dart file** — template leftover |
| `go_router` | `^13.2.0` → 13.2.5 | [UI][NAV] | the only router; routes + auth redirect |
| `supabase_flutter` | `^2.3.4` → 2.5.7 | [NETWORK][PLATFORM] | auth, Postgres, functions, session persistence, deep-link/OAuth callbacks. Pulls `gotrue` 2.8.2, `postgrest` 2.1.2, `functions_client` 2.2.0, `realtime_client` 2.1.0, `storage_client` 2.0.2, `shared_preferences` 2.2.3, `app_links` 6.1.4, `rxdart`, `jwt_decode` |
| `flutter_riverpod` | `^2.4.10` → 2.5.1 | [STATE] | ProviderScope, Consumer widgets, ref.watch/read |
| `riverpod_annotation` | `^2.3.4` → 2.3.5 | [STATE][CODEGEN] | `@riverpod` on the 3 providers |
| `url_launcher` | `^6.2.5` → 6.3.0 | [PLATFORM] | opens the Stripe URL and the external pricing link |
| `posthog_flutter` | `^4.1.0` → 4.5.0 | [ANALYTICS][PLATFORM] | identify/reset + `PosthogObserver` |
| `supabase_auth_ui` | `^0.4.1` → 0.4.4 | [UI][AUTH] | **only** `SupaSocialsAuth` (GitHub + Google buttons). Heavy transitive surface to replace by hand: `google_sign_in` 6.2.1, `sign_in_with_apple` 5.0.0, `font_awesome_flutter`, `email_validator`. Its other widgets are unused — the app hand-rolls `EmailForm` |

**Direct dev (5 + 1 SDK):** `flutter_test` (only an empty
`testWidgets('TODO: add tests')` — **there are no tests**), `flutter_lints`
3.0.2, `riverpod_generator` 2.4.0, `build_runner` 2.4.11, `custom_lint` 0.6.4,
`riverpod_lint` 2.3.10.

**Explicitly NOT present** (saves porting work): no `flutter_stripe`, no
`in_app_purchase`, no `webview_flutter`, no own HTTP client, no `freezed`, no
`json_serializable`, no `intl`/localizations, no secure storage, no Firebase, no
push notifications, no DI container, no local database.

One irregular import: `auth_notifier.dart:8` imports `package:path/path.dart`
with `// ignore: depend_on_referenced_packages` — it is transitive, not declared.

## 1.9 Other notable things

**Theming.** There is barely any: `ThemeData(colorScheme:
ColorScheme.fromSeed(seedColor: Colors.deepPurple), useMaterial3: true)`. No
dark theme, no custom text theme, no custom fonts, **no assets at all**.
`debugShowCheckedModeBanner: false`. A no-op
`builder: (context, child) => Container(child: child)` placeholder in
`main.dart:31`. Colors used in screens: `colorScheme.inversePrimary` (AppBar),
`.primary`/`.onPrimary` (submit button), `.error` (inline errors), `.outline`
("Forgot Password"). **KMP: Material 3 seeded from deepPurple `0xFF673AB7` is a
faithful match.**

**Localization.** None. Every string is a hard-coded English literal. Full
user-facing string list (use as the resource table): "DevToDollars",
"Payments", "Logout", "See Pricing", "You are currently on the {plan} plan.",
"You are not currently subscribed to any plan.", "Failed to load subscription
plan", "or continue with email", "Login", "Create an account", "Create
Acccount" (sic), "Email", "Password", "Confirm Password", "Email is required",
"Invalid email", "Password is required", "Password must be at least 6 characters
long", "Password confirmation is required", "Passwords do not match", "Don't
have an account?", "Have an account?", "Forgot Password", "Check your Email!",
"We sent an email from hi@devtodollars.com to verify your email", "Ok Matt.",
"Recover Password", "Get instructions sent to this email that explain how to
reset your password", "Cancel", "Send", "Reset Password", "Set New Password",
"Submit", "Ok", "Failed to open Stripe", "You are being redirected to Stripe for
payment.\nPlease wait a moment...", "Return to Home".

**Env / config.** Compile-time `--dart-define-from-file`, read via
`const String.fromEnvironment(...)`. `flutter/env.json` → prod
`SUPABASE_URL=https://ibyxomczmyaptsjgzvzi.supabase.co` + anon JWT;
`flutter/env.local.json` → `http://localhost:54321` + demo anon key. Only those
two keys exist; both files are committed (anon keys, by design). PostHog keys are
**not** in env.json — they are in the native manifest/plist/index.html, so each
platform build carries the key separately.

**Platform channels / native code.** None custom. `MainActivity.kt` is an empty
`FlutterActivity`; `AppDelegate.swift` is the stock registrant. All six platform
folders are stock templates.

**Notifications.** None. No FCM/APNs, no local notifications, no permission
requests. The only user messaging is transactional email, server-side
(`_shared/loops.ts`, currently commented out).

**Build identifiers — all still template defaults, fix during the port:**
Android `applicationId` **`com.example.test`**, `minSdkVersion 21`, Java/Kotlin
target 1.8, AGP 7.3.0, Kotlin 1.7.10, **release signed with the debug key**,
only `INTERNET` permission, `android:label="DevToDollars"`. iOS
`platform :ios, '13.0'`, `use_frameworks!`, display name DevToDollars, all
orientations. Version `0.0.0+0`, bumped by `bumpversion.sh`.

**Rough edges in the Flutter app — decide about each while porting:**

1. No subscription refresh after returning from Stripe; `return_url` is `null`
   entirely on non-web. → **D-004, D-005.**
2. No deep-link scheme on iOS/Android → OAuth, email confirmation and `/reset`
   are web-only today. → **D-003, §2.8.**
3. `from('users').select('*').single()` throws if the row is absent, which makes
   the whole HomeScreen card show "Failed to load subscription plan". → use the
   `maybeSingle` equivalent in KMP.
4. The reset-password field is **not obscured** (`reset_password_dialog.dart:33-46`).
   → fix in the port.
5. Router + metadata both rebuild on every token refresh — an hourly needless
   re-fetch. → **D-002.**
6. Dead code: `Auth.signInWithOAuth`, `Auth.currentSession`,
   `handleSignUpComplete`, `cupertino_icons`; `test/widget_test.dart` is an
   empty stub.
7. Hard-coded strings and typos: "Create Acccount", "Ok Matt.",
   "hi@devtodollars.com".
8. `applicationId com.example.test` and debug signing on release.

## 1.10 File tree of `flutter/lib/`

```
flutter/lib/
├── main.dart                        # usePathUrlStrategy, Supabase.initialize, ProviderScope, MaterialApp.router
├── components/
│   ├── dialog_page.dart             # DialogPage<T> extends Page<T>  (go_router dialog plumbing)
│   ├── email_form.dart              # enum AuthAction; EmailForm sign-in/sign-up form
│   ├── recover_password_dialog.dart # -> Auth.recoverPassword
│   └── reset_password_dialog.dart   # -> Auth.updatePassword            [route /reset]
├── models/
│   ├── stripe.dart                  # SubscriptionWithPrice, PriceWithProduct, Product
│   └── user_metadata.dart           # UserMetadata (mutable .subscription)
├── screens/
│   ├── auth_screen.dart             # AuthScreen + file-level `final Uri initUrl`   [/login]
│   ├── home_screen.dart             # HomeScreen                                   [/]
│   └── payments_screen.dart         # PaymentsScreen                               [/payments]
└── services/
    ├── auth_notifier.dart           # @Riverpod(keepAlive:true) Auth -> StreamNotifier<Session?>
    ├── auth_notifier.g.dart         # GENERATED
    ├── metadata_notifier.dart       # @riverpod Metadata -> AsyncNotifier<UserMetadata?>
    ├── metadata_notifier.g.dart     # GENERATED
    ├── router_notifier.dart         # navigatorKey, mutable `Uri? initUrl`, routes + auth redirect
    └── router_notifier.g.dart       # GENERATED
```

---

# 2. Backend contract (`supabase/`, reused unchanged)

The client needs only `SUPABASE_URL` and `SUPABASE_ANON_KEY`. No Stripe key and
no service-role key ever ships in the app.

Schema source of truth: **`supabase/migrations/20240717231009_init.sql`** (the
only migration). ⚠️ **`supabase/functions/types_db.ts` is drifted** — it declares
`xmr_products`, `xmr_prices`, `xmr_invoices` and a `subscriptions.xmr_price_id`
column that **no migration creates** (leftovers of an uninstalled Monero
add-on). **Model Kotlin DTOs from the migration, not from `types_db.ts`.**

## 2.1 Tables and client access

`public` schema; PostgREST `max_rows = 1000`. **No RPC functions are exposed to
the client.**

| table | client SELECT | client write | notes |
|---|---|---|---|
| `products` | ✅ anon + authed (`USING (true)`) | ❌ service-role only | `id` is the Stripe `prod_…`. Filter `active = true`. |
| `prices` | ✅ anon + authed (`USING (true)`) | ❌ service-role only | `id` is `price_…`; `unit_amount` is **minor units** (cents); **`currency` is UPPERCASE** — the webhook writes `.toUpperCase()` despite the SQL comment saying lowercase. Enums `pricing_type` (`one_time`/`recurring`), `pricing_plan_interval` (`day`/`week`/`month`/`year`). |
| `users` | ✅ own row (`auth.uid() = id`) | ✅ **UPDATE own row only** | No INSERT and no DELETE policy. Columns: `id`, `full_name`, `avatar_url`, `billing_address` (jsonb), `payment_method` (jsonb). |
| `subscriptions` | ✅ own rows (`auth.uid() = user_id`) | ❌ service-role only | enum `subscription_status` = `trialing`/`active`/`canceled`/`incomplete`/`incomplete_expired`/`past_due`/`unpaid`/`paused`. The five `*_at`/`trial_*` columns carry a `now()` default but the webhook explicitly writes `null` — **model them nullable in Kotlin.** |
| `checkout_sessions` | ✅ own rows | ❌ service-role only | enums `checkout_mode` (`payment`/`setup`/`subscription`), `checkout_status` (`complete`/`expired`/`open`), `checkout_payment_status` (`paid`/`unpaid`/`no_payment_required`). Only `checkout.session.completed` is handled, so rows only ever appear as `status='complete'`. |
| `customers` | ❌ **no RLS policy at all** | ❌ | private `id` → `stripe_customer_id` map. **Never query it.** |

## 2.2 Auth configuration

From `supabase/config.toml` (these govern **local dev**; the hosted project is
configured in the dashboard — verify production separately):

- `site_url = "http://127.0.0.1:3000"`,
  `additional_redirect_urls = ["https://127.0.0.1:3000", "http://localhost:*", "http://localhost:*/**"]`
- `jwt_expiry = 3600`, `enable_refresh_token_rotation = true`,
  `refresh_token_reuse_interval = 10`, `enable_signup = true`,
  `enable_manual_linking = false` (→ you **cannot** merge a Google and an email
  account via identity linking).
- **Email/password:** `enable_signup = true`, **`enable_confirmations = true`**
  (confirmation required before sign-in), `double_confirm_changes = true`.
- **Google OAuth:** enabled, `redirect_uri = http://localhost:54321/auth/v1/callback`.
- **GitHub OAuth:** enabled, same redirect.
- **Phone/SMS:** signup flag on but the only provider block
  (`[auth.sms.twilio]`) is `enabled = false` → **phone auth is unusable.**
- **Apple: not configured.** There is no `[auth.external.apple]` block.
  (App Store Review Guideline 4.8 generally requires an equivalent private-login
  option when you offer third-party social login — relevant for iOS.)
- Anonymous sign-ins: default **off**.
- **No custom claims.** `[auth.hook.custom_access_token]` is fully commented out.
  **Entitlement is not in the JWT** — it must be read from `subscriptions`.

**Triggers on `auth.users`:**

1. **Profile creation** (in the migration):
   `handle_new_user()` is `security definer` and inserts into `public.users
   (id, full_name, avatar_url)` from `new.raw_user_meta_data->>'full_name'` and
   `->>'avatar_url'`. → **The client must not insert into `users` (it can't).
   To populate those fields at signup, pass `full_name`/`avatar_url` in the
   signup user metadata** (supabase-kt: the `data` JsonObject on
   `signUpWith(Email)`). Otherwise they land NULL and need a later UPDATE.
2. **Analytics webhook** (in `seed.sql`, so **local only** unless created by hand
   in the hosted project): `handle_user_modify` AFTER INSERT/UPDATE/DELETE calls
   `on_user_modify`.

## 2.3 Edge function: `get_stripe_url` — the only client-callable function

`POST {SUPABASE_URL}/functions/v1/get_stripe_url`. `verify_jwt = false` at the
gateway, **but the function enforces auth itself**: it requires an
`Authorization` header, strips `"Bearer "`, and calls `auth.getUser(token)` with
the service-role client. **A user JWT is required.**

Request body — field names exactly as in `get_stripe_url/index.ts:7`:

```json
{ "price": "price_1234", "return_url": "https://your.app/after-stripe" }
```

- `price` — a Stripe price ID. The field is **`price`**, not `price_id`.
- `return_url` — used as **both** `success_url` and `cancel_url` for Checkout,
  and as `return_url` for the billing portal.

**The endpoint is overloaded.** It looks the price up in the `prices` table:
if found → **Checkout session**; if absent/null/unknown → **billing portal**.
A typo'd price ID silently yields the portal instead of an error.

Success — HTTP 200: `{"redirect_url": "https://checkout.stripe.com/c/pay/cs_…"}`.
The field is exactly **`redirect_url`**. Model it **nullable**: if `session.url`
were missing, `JSON.stringify` yields `{}`.

Failure — HTTP **400** for *every* failure mode including auth:
`{"message": "<error text>"}`. Observed messages: `"Missing Authorization
header"`, `"Authentication Error"`, `"Unable to access customer record."`,
`"Unable to create checkout session."`, `"Could not create billing portal"`,
`"Could not get customer."`, plus raw GoTrue errors.

⚠️ **KMP pitfall:** supabase-kt's `functions.invoke` sends the anon key as the
bearer when there is no session. `getUser(<anon key>)` fails → you get a **400,
not a 401**. Guard the call on an authenticated session, and surface `message`
from the 400 body.

Side effects: `createOrRetrieveCustomer` (upserts `customers`, sets Stripe
`metadata.supabaseUUID`), and a PostHog event `"user starts checkout"` or
`"user opens billing portal"`.

## 2.4 Edge functions not in the client surface

- **`stripe_webhook`** — public, Stripe-signature-authenticated
  (`Stripe-Signature` header), no CORS. A mobile client never calls it. It is
  the **only writer** of `subscriptions` and `checkout_sessions`.
- **`on_user_modify`** — `verify_jwt` defaults to true and the `seed.sql`
  trigger calls it with a service-role bearer. Emits the PostHog signup/signin/
  delete events. Not client-callable in practice. The Loops welcome email is
  commented out, so `_shared/loops.ts` is dead code today.
- `_scripts/sync-stripe.ts` is operator tooling, not a function.

## 2.5 Stripe: how it actually works

**Stripe-hosted Checkout page**, not Payment Intents, not PaymentSheet, not the
native mobile SDK. `stripe@20.1.2`, `apiVersion: "2024-06-20"`. Session params:
`allow_promotion_codes: true`, `billing_address_collection: "required"`,
`customer_update: {address: "auto"}`, one line item qty 1,
`cancel_url` **and** `success_url` both = `returnUrl`. By `price.type`:
`recurring` → `mode: "subscription"` (+ `trial_end`, but the webhook defaults
`trial_period_days` to `0` so there is normally no trial); `one_time` →
`mode: "payment"`; neither → **no `mode` set and Stripe rejects the session**
(surfaces as 400 `"Unable to create checkout session."`).

Billing portal: `stripe.billingPortal.sessions.create({customer, return_url})`.

**Webhook events handled — exactly ten:** `product.created/updated/deleted`,
`price.created/updated/deleted`, `checkout.session.completed`,
`customer.subscription.created/updated/deleted`. Anything else → 400
`"Unsupported event type: …"`. (`docs/stripe/setup.md` tells you to select *all*
events in Stripe, so in a by-the-book setup most deliveries 400 and Stripe
retries — noisy but harmless to the client.) Notably absent:
`invoice.payment_succeeded/failed`, `payment_intent.*`,
`customer.subscription.trial_will_end`. Renewal failures reach the DB only as a
`customer.subscription.updated` status change to `past_due`/`unpaid`.

`manageSubscriptionStatusChange` upserts `subscriptions`; on `created` it also
copies billing details into `users.billing_address`/`users.payment_method` (only
when the payment method has name **and** phone **and** address).
`insertCheckoutSession` uses `.insert()`, not `.upsert()` — a duplicate delivery
causes a PK conflict → 400 → more Stripe retries.

## 2.6 How a client discovers that a subscription became active

**There is no push mechanism. The client must poll.**

1. `subscriptions` is **not in the `supabase_realtime` publication** — the
   migration publishes only `products` and `prices`. So
   `postgresChangeFlow("subscriptions")` would never fire. Same for
   `checkout_sessions` and `users`.
2. No custom JWT claim → refreshing the session reveals nothing.
3. Therefore: re-query `subscriptions` after returning from Stripe. The
   canonical query, in supabase-kt terms:

   ```kotlin
   postgrest.from("subscriptions")
       .select(Columns.raw("*, prices(*, products(*))")) {
           filter {
               isIn("status", listOf("trialing", "active"))
           }
           order("created", Order.DESCENDING)
           limit(1)
       }
   ```

   PostgREST embedding nests the response as
   `{..., "prices": {..., "products": {...}}}` — note the **plural** keys
   `prices` and `products` even though each is a single object.
   **"Entitled" = `status ∈ {trialing, active}`.**
4. The webhook is asynchronous, so the row is **not** guaranteed present the
   moment the user returns from Stripe. **Poll with backoff** (e.g. every ~2 s
   for ~30 s) before declaring failure.
5. For one-time purchases (`mode: "payment"`) **nothing lands in
   `subscriptions`** — the only record is `checkout_sessions` with
   `mode='payment'`, `status='complete'`, `payment_status='paid'`.

**Storage:** no buckets exist. **Realtime:** only `products`/`prices` are
replicated; no broadcast, no presence, no application channels.

## 2.7 Env vars the backend expects (names only)

Function runtime: `SUPABASE_URL`, `SUPABASE_SERVICE_ROLE_KEY` (both
auto-injected), `STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SIGNING_SECRET`,
`POSTHOG_CLIENT_KEY`, `LOOPS_API_KEY`, `LOOPS_WELCOME_TEMPLATE_ID`.
`config.toml` substitutions: `GOOGLE_CLIENT_ID`, `GOOGLE_SECRET`,
`GITHUB_CLIENT_ID`, `GITHUB_SECRET`, `SUPABASE_AUTH_SMS_TWILIO_AUTH_TOKEN`,
`S3_*`. **Client side: `SUPABASE_URL` + `SUPABASE_ANON_KEY` only.**

## 2.8 The one thing that cannot stay unchanged

**Mobile redirect URLs are not allow-listed.** `site_url` and
`additional_redirect_urls` are all `localhost`/`127.0.0.1:3000` web origins. A
custom scheme (e.g. `com.devtodollars.app://login-callback`) or an App/Universal
Link is not in the allow-list, so GoTrue will **reject `redirect_to`** for OAuth
and for email links. This must be added in two places:

1. `supabase/config.toml` → `additional_redirect_urls` (for local dev).
2. Supabase dashboard → Authentication → URL Configuration (for the hosted
   project).

This is configuration, not code: schema, functions and webhooks stay untouched.
**It is a prerequisite for session 4 (OAuth) and session 6 (password reset), not
for session 1.**

Also: **email confirmation is ON**, so `signUp` returns a user with **no
session** — the client must show a "check your email" state and must not expect
to be logged in. The confirmation link resolves against `site_url`
(`http://127.0.0.1:3000`), which on a phone goes nowhere, so either point
`site_url` at a real https host that deep-links into the app, or pass a mobile
`redirectTo` on signup (and allow-list it).

**Stripe `return_url` needs no backend change**, but the client must always send
a real one: Stripe accepts arbitrary custom schemes in
`success_url`/`cancel_url`/portal `return_url`. Note that `createStripePortal`
passes `return_url: returnUrl` with **no `|| undefined` guard** (unlike
`checkoutWithStripe`), so sending `null` — which the Flutter app does on mobile —
plausibly 400s with `"Could not create billing portal"`. Always send a non-null
deep link. And because success and cancel are **the same URL**, the return tells
you nothing: treat it purely as "stop waiting, go re-query `subscriptions`".

---

# 3. Technology research for the KMP port (web search, 2026-10-15)

This is current-state research, not training-data recall — version numbers in
particular should be re-checked against Maven Central / GitHub Releases at
implementation time, since the ecosystem was mid-upgrade at the time of
writing. Everything below is scoped to **Android + iOS only**, per the ground
rules; Linux/Windows/JS/Wasm findings are noted only where they bled into the
same release.

## 3.1 supabase-kt — auth + session persistence on Android and iOS

**Maintained, active, mid-major-version.** Group id
`io.github.jan-tennert.supabase` (ignore `io.github.ferhatwi` hits in search —
that's an unrelated fork/squat, not the project used by `supabase-kt` docs).

- **Version line:** 3.x is current. **3.0.0** was the big migration: moved to
  **Ktor 3** (so the app's Ktor version must be ≥ 3.0.0-rc-1, and Ktor 2 is
  incompatible), renamed the `gotrue-kt` module to **`auth-kt`**, and added
  `wasmJs` targets. **3.2.1** split plugins (e.g. Compose Auth) out into a
  separate repo — don't expect them in the main `supabase-kt` repo anymore —
  and bumped Ktor to 3.2.1 and `kotlinx-datetime` to 0.7.1. Search snapshots
  place the newest stable tag somewhere in the **3.7.x–3.8.x** range as of this
  research; **confirm the exact current version against
  `github.com/supabase-community/supabase-kt/releases` or Maven Central before
  pinning it** — don't hand-copy a version number from this file.
- **Dependency shape:** pull in via the BOM, then
  `implementation("io.github.jan-tennert.supabase:auth-kt")` (+ `postgrest-kt`,
  `functions-kt` for this app's needs). Needs a Ktor HTTP engine per platform
  (e.g. OkHttp on Android, Darwin on iOS) added by the app, same as any
  Ktor-based client.
- **Platform support (Android + iOS):** both are in the **fully-supported**
  tier, including persistent session storage and the built-in OAuth deep-link
  helpers. (Linux has no persistent session storage and is a separate concern
  — not relevant here.)
- **Session persistence model — direct analog to §1.7:** there's a
  `SessionManager` interface; the default implementation is
  `SettingsSessionManager`, backed by **multiplatform-settings**
  (russhwolf/multiplatform-settings), which on Android resolves to
  `SharedPreferences` and on iOS to `NSUserDefaults`. **This is the same
  security posture as the Flutter app's `SharedPreferencesLocalStorage`** —
  plaintext, not Keychain/EncryptedSharedPreferences by default. Parity with
  Flutter needs no extra work; if the port wants to *improve* on Flutter here,
  swap in a custom `SessionManager`/`Settings` impl backed by Android
  `EncryptedSharedPreferences` or DataStore+Tink and iOS Keychain — optional,
  not required for parity. (2.4.0 also changed the default storage key scheme
  so multiple `SessionManager` instances on one device don't collide, and added
  a constructor `key` param — only relevant if this app ever needs two Supabase
  clients side by side, which it doesn't.)
- **Startup behavior:** functionally the same shape as `supabase_flutter` —
  session is loaded from storage and a local "has a session" check happens
  before any network call; a real `refreshSession()` happens lazily after. The
  stream-of-auth-events pattern in §1.2/§1.7 (one initial emission at startup,
  then `onAuthStateChange`) maps directly to `auth.sessionStatus` /
  `auth.events` (naming has moved around between 2.x and 3.x — one 3.2.0 note
  mentions "a new event system for auth" exposed as a flow — check the
  `auth-kt` README for the exact current flow name before coding).
- **OAuth deep links — directly relevant to the §1.6/§2.8 gap:** supabase-kt
  exposes `auth.parseSessionFromUrl(url)` / `parseSessionFromFragment(fragment)`
  plus `auth.importSession(session)`, i.e. the SDK expects *the app* to own the
  platform deep-link registration (Android App Link / intent-filter, iOS
  Associated Domains or custom `CFBundleURLTypes` scheme) and hand the
  resulting URL to the SDK — there's no magic auto-registration the way
  `supabase_flutter`'s bundled `app_links` package tries to do. This confirms
  decision work still needed: register a real scheme/App Link on both
  platforms, add it to Supabase's redirect allow-list (§2.8), and wire it to
  `importSession`.

## 3.2 State management, replacing Riverpod

**There is no drop-in Riverpod equivalent** — Riverpod is Dart/Flutter-only.
The ecosystem question is really "what plays the role of
`StreamNotifierProvider`/`AsyncNotifierProvider` in Kotlin/Compose,” and there
are three live answers, all actively maintained as of this research:

1. **AndroidX Lifecycle `ViewModel` (multiplatform), group
   `org.jetbrains.androidx.lifecycle` for the Compose Multiplatform build** —
   the "official, Google-aligned" option, and the closest conceptual match to
   this app's three Riverpod providers: one `ViewModel` exposing `StateFlow`,
   collected in Compose via `collectAsStateWithLifecycle`.
   - Status has been moving fast: JetBrains' own docs (last touched mid-2025)
     still flagged the common `ViewModel` as **Experimental** and noted
     non-JVM targets can't use the no-arg `viewModel()` helper (needs an
     explicit initializer). Upstream AndroidX Lifecycle **2.11.0** (released
     **2026-06-17**) is the first release stating ViewModel-Compose and
     ViewModel-Navigation3 **support all Kotlin Multiplatform targets** —
     i.e. the iOS story firmed up only recently. A 2.12.0-alpha line was
     already visible in the same search pass, so this is still moving;
     **re-check the current stability annotation before committing to it**,
     though for Android+iOS specifically (vs. wasm/JS) this has been
     workable for longer than the "all targets" headline suggests.
   - For this app's size (3 screens, 3 providers), this is the
     lowest-ceremony choice: one `ViewModel` per Riverpod provider
     (`AuthViewModel`, `MetadataViewModel`), `StateFlow<T>` instead of
     `AsyncValue<T>`, manual `sealed class UiState { Loading; Data; Error }`
     instead of `AsyncValue.when`.
2. **Decompose** (Arkadii Ivanov's fork — the original `badoo/Decompose` is
   **archived/unmaintained**; use `com.arkivanov.decompose`, not the badoo
   coordinates). Actively released (3.5.0 seen dated 2026-03-15 in this
   search). Goes further than a ViewModel replacement: it bundles
   `InstanceKeeper` (ViewModel-equivalent retained state),
   `StateKeeper` (process-death state saving), and a component-based
   navigation model — i.e. it would also replace the go_router role from §1.6,
   not just Riverpod. Worth considering only if the port wants unified
   nav+state; likely overkill for a 3-screen app.
3. **Molecule** (Cash App). Reached a stable multiplatform 1.0, now on a 2.x
   runtime (`molecule-runtime`), JVM/JS/Native. Lets you write a "presenter"
   as a `@Composable` function that reads events and emits a state object,
   compiled by the Compose compiler plugin into a `StateFlow`/`Flow` — no
   custom Gradle plugin needed since 2.0.0 (uses JetBrains' Compose compiler
   plugin directly). It's narrower than Decompose: no navigation, no
   lifecycle scoping — just the state-stream mechanism. Pairs well *with*
   plain `ViewModel`s rather than replacing them outright.

> **Superseded in part (2026-10-19):** this section was written assuming
> Compose Multiplatform UI. The UI is now native per platform (see D-001), so
> the shared layer exposes plain Kotlin `StateFlow` state holders rather than
> AndroidX `ViewModel`s; Android wraps them in a `ViewModel`, iOS in an
> `ObservableObject`. The research below is kept as the survey of options.

**For this specific port:** plain multiplatform `ViewModel` + `StateFlow`
(option 1) is the closest like-for-like replacement for the three Riverpod
providers and adds the least new surface area. Decompose only earns its
weight if the routing layer (go_router replacement) is designed alongside it;
Molecule is a nice-to-have for the async-state-merging pattern in
`metadata_notifier.dart` (concurrent `getUserDetails()` + `getSubscription()`)
but not a requirement. Re-verify the `ViewModel` multiplatform stability
annotation at the Kotlin/Compose version actually pinned in `kmp/`, since it
was still marked experimental in some docs seen during this research even as
the underlying library matured.

Sources consulted (via web search, not re-fetched/archived here): GitHub
`supabase-community/supabase-kt` releases and compare pages, Maven Central /
`klibs.io` listings for `auth-kt` and `supabase-kt`, the Supabase Kotlin
reference docs, AndroidX Lifecycle release notes (2.11.0), JetBrains'
Kotlin Multiplatform "Compose ViewModel" doc, `arkivanov/Decompose` releases,
and `cashapp/molecule` release notes / blog posts.

---

# 4. Architecture decisions

Each record: what was chosen, what was rejected and why, and the condition
that would flip the decision later. These resolve the `D-00x` forward
references scattered through §1–§3. No code here — decisions only.

## D-001 — State management: shared `StateFlow` state holders + native presentation adapters

**Chosen:** the shared module (`sharedLogic`) contains, per Riverpod provider,
a plain Kotlin state holder (`AuthStore` for `authProvider`, `MetadataStore`
for `metadataProvider`) that owns business/data logic and exposes
`StateFlow`. It has **no dependency on any UI or lifecycle library** — no
Compose, no AndroidX `ViewModel`. A hand-written sealed result type plays the
role of `AsyncValue<T>`. Each platform adapts the stores to its own UI layer:
- **Android:** a thin AndroidX `ViewModel` per store (scoped via
  `viewModelScope`), collected in Compose with `collectAsStateWithLifecycle`.
- **iOS:** a thin `ObservableObject` per store (`@MainActor`, `@Published`),
  observed from SwiftUI. How the Kotlin `StateFlow` is bridged to Swift
  (hand-written collector wrapper vs. a library such as SKIE) is decided in
  Task 13 when iOS is first built; it is purely an iOS-side choice and does not
  change the shared API.

These adapters are presentation code and live in `androidApp` / `iosApp`, not
in `sharedLogic`.

**Rejected:**
- **Shared Compose Multiplatform UI + multiplatform `ViewModel`** (the
  original D-001) — superseded by the decision to build native UIs. It would
  force a Compose dependency into shared code and a non-native iOS UI.
- **Decompose** — bundles navigation and a component tree; that conflicts with
  using each platform's native navigation (D-007).
- **Molecule** — its presenters are `@Composable`, i.e. a Compose dependency
  in shared code.
- **Hand-rolled state with no store abstraction** — would duplicate the
  concurrent-fetch merge and auth-gate logic in both UIs.

**Why:** keeps all business behavior (auth-gate derivation per D-002, the
concurrent `getUserDetails()` + `getSubscription()` fetch, session restore)
written and tested once, while every pixel and every navigation decision stays
native. `StateFlow` is the idiomatic shared-Kotlin state primitive and is
consumable from both Compose and Swift.

**Reverses if:** the Kotlin→Swift `StateFlow` bridge proves too costly in
practice (Task 13) — fallback is callback/listener APIs on the stores. Does
not reverse because of anything on the Android side.

## D-002 — Auth-driven navigation: single derived auth-gate state, not raw `Session?`

**Chosen:** collapse the `Session?` stream from `AuthStore` into a small
derived state — `Unknown` (startup, still restoring) / `LoggedOut` /
`LoggedIn` — with `distinctUntilChanged()` semantics, and only navigate
imperatively on a transition between those three values. The derivation lives in shared code (`AuthStore`); each platform's navigation (D-007) consumes it. Silent token
refreshes (hourly, §1.7) update the session object inside `LoggedIn` without
emitting a new auth-gate value and without touching navigation.

**Rejected:** porting the Flutter design as-is — a `GoRouter` instance that
is fully rebuilt on *every* `authProvider` emission, including token
refreshes, propped up only by a shared `navigatorKey` global to stop the
widget tree from being discarded (§1.6). It works in Flutter by accident of
how `navigatorKey` papers over the rebuild; there's no equivalent accidental
safety net in either platform's native navigation stack, and deliberately rebuilding
the graph hourly has no upside here.

**Why:** the hourly `tokenRefreshed` event doesn't change *what screen the
user should be on* — it only changes the session payload. Separating "is the
user logged in" from "what is the current session object" removes the
rebuild-on-refresh bug class at the root instead of working around it per
screen. This also makes `MetadataStore`'s refetch trigger (§1.2: refetch
on login/logout/refresh) independently tunable from navigation, instead of
both being driven by the same raw stream emission.

**Reverses if:** a future requirement needs navigation to react to
*something inside* the session object changing while logged in (e.g.
force-navigating on a specific claim change) — then the auth-gate enum needs
a fourth case or a parallel signal, rather than silently folding it back into
"any session emission triggers navigation."

## D-003 — Mobile deep links: custom URL scheme, not App Links / Universal Links

**Chosen:** register a single custom URL scheme (e.g.
`com.devtodollars.app://login-callback`) on both Android (intent-filter) and
iOS (`CFBundleURLTypes`), and add it to the Supabase redirect allow-list
(§2.8) for OAuth, email confirmation, and the `/reset` deep link.

**Rejected:** Android App Links / iOS Universal Links (HTTPS-based deep
links verified via `assetlinks.json` / `apple-app-site-association` hosted
on a real domain). These are the more "correct" mobile answer and avoid the
classic custom-scheme risk — another installed app registering the same
scheme and intercepting the redirect — but that risk is specifically what
PKCE exists to close, and PKCE is already the Supabase client default
(§1.7), not something this port has to add. Given that, paying the setup
cost (hosting and maintaining the verification file on the marketing
domain, keeping it in sync with app signing) buys little beyond what PKCE
already buys.

**Why:** lowest implementation cost that still closes the actual attack this
port cares about, and it's the pattern Supabase's own docs assume
(`parseSessionFromUrl`/`importSession`, §3.1) without extra plumbing.

**Reverses if:** the team stands up the marketing domain changes anyway for
other reasons (e.g. SEO, universal-link-based sharing), or a security review
flags custom-scheme interception as unacceptable even under PKCE — then
switch to App Links/Universal Links, which only changes how the OS routes
the redirect to the app, not anything downstream of `importSession`.

## D-004 — Detecting a completed subscription after returning from Stripe

**Chosen:** on returning from Stripe (via the D-003 deep link, or the app
coming back to the foreground during a pending checkout), poll
`subscriptions` with backoff (e.g. every ~2s for ~30s) rather than trusting
a single re-query, since the webhook write is asynchronous (§2.6).

**Rejected:**
- **Realtime subscription** on the `subscriptions` table — not available
  without a backend change; the table isn't in the `supabase_realtime`
  publication (§2.6), and the ground rules treat the backend as fixed except
  for the §2.8 redirect allow-list.
- **Relying on cold start**, i.e. the Flutter app's actual (accidental)
  mechanism: the webhook typically finishes before a *web* page reload, but
  on mobile the app is rarely killed between leaving for Stripe and
  returning, so there is no cold start to trigger a re-query at all. This is
  the gap called out in §1.4 — it must be fixed, not ported.

**Why:** polling is the only option left once realtime and cold-start-reload
are both off the table, and a short bounded backoff window matches how fast
the `stripe_webhook` function actually resolves in practice rather than
either a single too-early check or unbounded polling.

**Reverses if:** the backend ground rules ever relax enough to add
`subscriptions` to the realtime publication (a one-line migration change,
but still a backend change today's ground rules exclude) — then the poll
loop can be replaced with a realtime listener plus a short initial grace
period for the same async-webhook reason.

## D-005 — How Stripe Checkout / Billing Portal is presented on mobile

**Chosen:** open the Stripe URL in an in-app browser tab (iOS
`ASWebAuthenticationSession` / Android Custom Tabs, or a cross-platform
library wrapping both), passing the D-003 custom scheme as `return_url`, so
the browser session closes itself and hands control back to the app the
moment Stripe redirects.

**Rejected:** launching the system browser and sending no `return_url` on
mobile, which is what the Flutter app actually does today (§1.4: `return_url
== null` on non-web). That leaves the user stranded in Safari/Chrome with no
way back into the app and nothing to trigger D-004's poll — it isn't a
design, it's the absence of one, and porting it unchanged would keep the
exact gap §1.4 flags as a must-fix.

**Why:** an in-app browser tab keeps the user inside the app's navigation
context and guarantees the `return_url` redirect actually reaches the app
(via D-003) so D-004's poll has something to trigger it, while still using
Stripe's hosted Checkout/portal pages unchanged (§2.5) — no Stripe mobile
SDK, no backend change.

**Reverses if:** Stripe ships a dedicated mobile SDK integration this
project later adopts (ground rules currently keep Stripe backend/flow fixed,
so this would be a scope change, not just an implementation detail) — then
the in-app browser tab and D-003's deep link return would both be replaced
by native SDK callbacks.

## D-006 — Session storage security posture: default `SettingsSessionManager`

**Chosen:** use supabase-kt's default `SettingsSessionManager`
(multiplatform-settings → Android `SharedPreferences`, iOS
`NSUserDefaults`), unmodified, for v1.

**Rejected:** a custom `SessionManager` backed by Android
`EncryptedSharedPreferences`/DataStore+Tink and iOS Keychain. Strictly
better security, but it's net-new work with no parity requirement behind
it — the Flutter app's actual current behavior (§1.7) is the same plaintext
`SharedPreferencesLocalStorage`/`NSUserDefaults` storage, so shipping the
default is not a regression against what's live today.

**Why:** matches the existing app's real security posture exactly (not an
upgrade, not a downgrade), and defers genuinely new security work to a
moment when it's driven by an actual requirement rather than done
speculatively during a straight port.

**Reverses if:** a security review or compliance requirement (e.g. before a
larger launch, or a specific customer ask) calls for encrypted-at-rest
session tokens — then swap in a custom `SessionManager`/`Settings`
implementation; supabase-kt's `SessionManager` interface (§3.1) is built to
be replaced without touching call sites elsewhere in the app.

## D-007 — Navigation/routing: native per platform (split by platform)

The route table from §1.6 (`/login`, `/`, `/reset`, `/payments`, plus the
`/loading` splash state) is ported **once per platform, natively**. Both
implementations are driven by the same shared D-002 auth-gate state
(`Unknown`/`LoggedOut`/`LoggedIn`); neither re-evaluates a guard on every
recomposition/redraw. Route names are kept identical on both platforms so
analytics (task 12) emits the same screen names.

### D-007a — Android: Navigation Compose

**Chosen:** `androidx.navigation:navigation-compose` (`NavHost` +
`NavController`) in `androidApp`, navigating only on auth-gate transitions.
Dialog-style destinations (`/reset`) use Navigation Compose's `dialog`
destinations. Deep links are handled via manifest intent-filters mapped to
nav deep links (D-003).

**Rejected:** Decompose navigation (conflicts with D-001's native-adapter
model), and any shared/multiplatform navigation library.

### D-007b — iOS: SwiftUI `NavigationStack`

**Chosen:** `NavigationStack` with a typed path / `navigationDestination(for:)`
in `iosApp`, with the root view switching on the auth-gate state (a
loading view for `Unknown`, the login stack for `LoggedOut`, the signed-in
stack for `LoggedIn`). `/reset` and similar are presented as sheets. Deep
links arrive through `onOpenURL` (D-003) and are parsed by shared URL-parsing
code.

**Rejected:** UIKit navigation controllers and third-party SwiftUI routers —
no need beyond what `NavigationStack` provides at this app's size.

**Why (both):** platform-native navigation gives native back gestures,
transitions, and state restoration for free, and removes any need for shared
UI code. Keeping navigation per platform leaves D-001's shared stores
unaware of navigation.

**Reverses if:** the project ever decides to share UI after all (a new
decision superseding the native-UI approach) — only then would a shared
navigation library be reconsidered.

## D-008 — OAuth sign-in mechanism: native SDK for Google/Apple, browser redirect for GitHub

**Chosen:** use native sign-in SDKs where one exists —
Google Sign-In (`signInWithIdToken`) on both platforms, and natively-presented
Sign in with Apple on iOS (new: the Flutter app never configured Apple,
§2.2) — and fall back to the browser-redirect `signInWithOAuth` flow (D-003
deep link return) only for GitHub, which has no native mobile SDK.

**Rejected:** porting the Flutter app's actual behavior as-is — browser-
redirect for every provider via `supabase_auth_ui`'s `SupaSocialsAuth`,
with the native `signInWithIdToken` branch never reached because
`googleAuthConfig`/`nativeAppleAuthEnabled` are never passed (§1.3). That's
a straight port, but it carries over worse mobile UX for Google (a full
browser hand-off instead of the native account picker) and leaves the iOS
App Store Guideline 4.8 exposure open — the app offers third-party social
login (Google, GitHub) with no Apple-equivalent, which Apple review can
reject (noted as a risk in §2.2, never resolved in the Flutter app).

**Why:** native SDKs are strictly better UX where available, and adding
Sign in with Apple is the direct fix for the Guideline 4.8 exposure rather
than something to discover during App Store review. GitHub has no native
mobile SDK, so it has no native path to choose — it keeps using the same
browser-redirect machinery D-003/D-005 already built for Stripe, rather than
inventing a second redirect mechanism.

**Reverses if:** native SDK integration (Google Sign-In client config,
Apple's entitlements/capability setup) turns out too costly for a v1
timeline — then fall back to browser-redirect for all three providers (the
Flutter-parity option above) and accept both the UX gap and the open
Guideline 4.8 risk as a known, explicitly deferred tradeoff rather than an
oversight.

## D-009 — "Continue without login": Supabase anonymous session, not a session-less guest mode

**Chosen:** back the "Continue without login" button with
`auth.signInAnonymously()`. The guest gets a real Supabase user with a real
JWT that simply has no email and no credentials. Everything downstream is
untouched: `AuthGate.from` sees a non-null session and reports `LoggedIn`, the
`on_auth_user_created` trigger inserts the guest's `public.users` row, and
every `auth.uid() = id` RLS policy (§2.1) applies to the guest exactly as it
does to a signed-in user. One repository method, one store method, one button
per platform.

**Rejected:** a client-side guest mode that bypasses the gate with no session
at all. That requires a third `AuthGate` state, which contradicts D-002 — the
gate exists precisely so navigation reacts to one derived signal — and it
forces a guest branch into `Routes.forGate`, `AppNavHost`, `RootView`,
`MetadataStore` and every screen that reads it. Worse, the degraded states are
not cosmetic: with no JWT there is no `users` row, so Home cannot render
subscription status, Account cannot load or save a profile, and
`get_stripe_url` (§2.3) rejects the call outright, since it authenticates the
caller by its bearer token. Guest mode would mean building empty states for
three screens that already work.

**Why:** the anonymous session is the option the existing architecture already
accommodates, and the trigger plus `auth.uid()` RLS mean it needs no schema
change whatsoever. It is also the only one of the two that can grow: Supabase
can convert an anonymous user into a permanent one by setting an email and
password on it, so "browse first, sign up later" stays open as a follow-up,
whereas a session-less guest has nothing to convert.

**Costs accepted:** `enable_anonymous_sign_ins` must be on — in
`supabase/config.toml` for local (done) and in the hosted dashboard, or the
button fails with "Anonymous sign-ins are disabled". Each guest is a row in
`auth.users`, so abandoned guest accounts accumulate and are worth a periodic
cleanup. And a guest who signs out is unrecoverable: there are no credentials
to sign back in with, which is why the button is the least prominent action on
the auth screen and carries a line of explanatory copy.

**Reverses if:** guest rows prove unacceptable (abuse, or billing on
`auth.users` volume) — then drop guest access rather than reimplement it
session-lessly, because the degraded-screen work above is the real cost and it
does not get cheaper later.

## D-010 — Guest conversion: `updateUser` on the anonymous user, no merge path

**Chosen:** convert a guest in place. `AuthStore.saveGuestAccount` calls
`auth.updateUser { email; password }` on the *current* anonymous user, which
keeps the same user id — so its `public.users` row, its subscription and its
Stripe customer need no migration, and nothing downstream can tell that the
account used to be a guest. `AuthSession` carries the SDK's `isAnonymous` flag
and `AuthStore.isGuest` derives from it, so both platforms decide what to offer
off the session stream they already collect. The conversion returns
`AccountConversion.Complete` or `ConfirmationRequired(email)`: with email
confirmation on (§2.8) GoTrue holds the address as `new_email` and the session
stays anonymous until the link is followed, which arrives through the existing
D-003 callback (`com.devtodollars.app://login-callback?code=…`, already
classified as `DeepLink.OAuthCallback`) and needs no new deep-link route.

A session exists before and after, so `AuthGate` never leaves `LoggedIn` and
nothing navigates — D-002 holds, and a commonTest asserts the gate emits
`LoggedIn` once across a conversion rather than flickering.

**Rejected:** treating an already-registered email as something to recover
from, by signing the guest into that account or merging the two. Supabase
rejects the `updateUser` with `email_exists` and offers no merge; doing it
ourselves would mean a server-side job that re-points the guest's rows at
another user id and reconciles two Stripe customers — an edge function and a
reconciliation policy for a case that only ever arises when someone already
has an account. So `EmailAlreadyRegisteredException` says the honest thing
instead: that address has an account, this session cannot be moved into it,
sign in to that one and this stays behind — or save this session under a
different email.

**Rejected:** a "guest" state on `AuthGate`, or a guest branch through
navigation. Already rejected in D-009 and nothing here changes it: `isGuest`
is screen content, not a navigation signal, and it is deliberately a separate
`StateFlow`.

**Why:** in-place conversion is the one option where "browse first, sign up
later" costs nothing downstream — no data migration, no second user id, no
Stripe reconciliation — which is the opening D-009 left and the reason the
anonymous session was chosen over a session-less guest mode. The sharp edge
D-009 accepted was that a guest who signs out is unrecoverable; that is now
guarded at both ends: Account offers "Save your account" while the session is
anonymous, and Logout asks first, naming what is lost and offering the
conversion instead of the sign-out.

**Costs accepted:** the confirmation round-trip means a guest can be left
mid-conversion — password set, address pending — and still unrecoverable, so
the guest section stays on screen (and keeps warning) until `isAnonymous`
actually clears. `email_exists` is the one rejection with a bespoke message;
every other GoTrue error surfaces as its own text. And the hosted project's
`redirect_to` allow-list (§2.8) must contain the bare callback URL, or the
confirmation link cannot return.

**Reverses if:** merging a guest into an existing account becomes a real
requirement (support asking for it repeatedly) — then it needs a server-side
merge with Stripe reconciliation, not a client-side retry, and this decision
becomes its client half rather than being replaced.
