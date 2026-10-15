# Porting DevToDollars from Flutter to Kotlin Multiplatform

**Status:** planning complete, no screens ported.
**Scope:** Android + iOS only. No web, no desktop.
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
- The KMP project lives in `kmp/`.

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
