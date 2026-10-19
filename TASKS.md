# Build order: Flutter → Kotlin Multiplatform port

**Read `PORTING.md` first.** It is the reference — the Flutter inventory
(§1), the Supabase backend contract (§2), the library research (§3), and the
architecture decisions D-001–D-008 (§4). This file is only the build order.
Each task below is written so a session that has read only `PORTING.md` and
this file can pick it up with no other context.

**Ground rules carried over from `PORTING.md`:** `flutter/` is reference
only, never modified. `supabase/` is fixed except the §2.8 redirect
allow-list change. The KMP project lives in `kmp/`.

**Native UIs (revised in Task 0):** the shared module `sharedLogic` is
business/data only — no UI, no Compose, no navigation. Android UI is Jetpack
Compose with Navigation Compose (`androidApp`); iOS UI is SwiftUI with
`NavigationStack` (`iosApp`). Screens, navigation, themes, and presentation
adapters (shared `StateFlow` store → `ViewModel` on Android, `ObservableObject`
on iOS; see D-001) are per-platform. There is no `sharedUI` module and no
Compose Multiplatform UI. Wherever a task below says "ViewModel," it means the
shared store (business logic) plus the thin Android adapter; the iOS adapter is
built in task 13.

**Task 0 — done:** project scaffold in `kmp/` (`androidApp` native Compose +
Navigation Compose placeholder, `iosApp` native SwiftUI, `sharedLogic` non-UI
KMP library; `sharedUI` removed), root `.gitignore`, and this documentation
rewrite for native UIs.

**Platform sequencing:** tasks 1–12 are built and verified on **Android
only** (shared logic is written to compile for iOS, but no SwiftUI is written
before task 13). Task 13 is where the complete SwiftUI UI and navigation are
built and everything built so far gets verified on iOS too. Where a task below says "a real device," read that as
"a real Android device" for every task before 13. Two tasks below break that
clean sequencing and are flagged explicitly where they occur: part of task 9
(iOS deep-link registration is config-only until 13) and part of task 10
(Apple sign-in can't be verified until 13 exists).

**Why task 8 is a single screen:** the screen inventory in PORTING.md §1.1
has six rows: `AuthScreen` (task 4), `HomeScreen`, `PaymentsScreen` (task 11),
the inline `/loading` route (task 5, not a real screen), and the two password
dialogs (task 9). Once those are carved into their own tasks, exactly one
screen is left over for "remaining screens" — `HomeScreen`. Task 8 is that
one task; there was nothing to split further.

Each task's record has four fields: **Covers**, **Flutter files**, **Done
when**, **Depends on**.

---

## Task 1 — Config and secrets

**Covers:** Supabase URL and anon key, PostHog project key, and debug/release
build variants, matching how `flutter/` handles each. Flutter reads
`SUPABASE_URL`/`SUPABASE_ANON_KEY` at compile time via
`--dart-define-from-file` (`String.fromEnvironment`); PostHog's key is
configured natively per platform, not through that mechanism at all (§1.5) —
keep that split in the KMP port rather than unifying them, since task 12
(Analytics) is where the PostHog native config actually gets wired, not here.
Debug build points at local Supabase (`http://localhost:54321` + the demo
anon key); release points at the hosted project
(`https://ibyxomczmyaptsjgzvzi.supabase.co` + its anon key). Both are
non-secret by design — Flutter commits both `env.json` and `env.local.json`
(§1.9) — so the KMP equivalents (Gradle build-config fields or a committed
config object) can be committed too; don't treat the anon key as something to
hide. While touching build variants, also fix two template leftovers flagged
in §1.9 rough edge #8: Android `applicationId` is still `com.example.test`,
and the release build is still signed with the debug key — fix the
`applicationId`; fix the signing if a release keystore is available, otherwise
leave a visible TODO rather than silently keeping the debug key.

**Flutter files:** `flutter/env.json`, `flutter/env.local.json`,
`flutter/lib/main.dart:9-12` (the bare `Supabase.initialize(url, anonKey)`
call whose defaults are documented in §1.3/§1.7), `flutter/android/app/src/main/AndroidManifest.xml`
(`applicationId`, PostHog keys — PostHog keys themselves are task 12's job,
but note their existence here since you'll be in this file anyway),
`flutter/ios/Runner/Info.plist`.

**Done when:** a debug build resolves the local Supabase URL/anon key at
build time with no network call required to prove it; a release build
resolves the hosted project's values; `applicationId` is no longer
`com.example.test`; release signing is either fixed or has a tracked,
visible TODO — not silently left on the debug key.

**Depends on:** nothing — this is the first task.

---

## Task 2 — Supabase client

**Covers:** wiring supabase-kt (`auth-kt` + `postgrest-kt` + `functions-kt`
per PORTING.md §3.1, via the BOM) with a Ktor engine per platform (OkHttp on
Android, Darwin on iOS — the iOS half can only be *written* here, not run
until task 13), producing a single client instance analogous to
`Supabase.instance.client` (§1.3). Session storage is wired via
`expect`/`actual`: the interface and call sites are common code, but
constructing the underlying `Settings` differs per platform (Android's
`SharedPreferences`-backed `Settings` needs a `Context`; iOS's
`NSUserDefaults`-backed one doesn't) — this is decision D-006: use
supabase-kt's default `SettingsSessionManager` unmodified, no custom
encrypted storage. Wrap the client in a thin repository (e.g.
`SupabaseRepository`/`AuthRepository`) so no ViewModel in later tasks talks
to the SDK directly. **No UI in this task.**

**Flutter files:** `flutter/lib/main.dart:9-12` (the init call and its
implicit defaults: PKCE flow, `detectSessionInUri: true`, `autoRefreshToken:
true`, SharedPreferences storage — §1.3, §1.7), `flutter/pubspec.yaml`'s
`supabase_flutter` entry as the dependency this replaces (§1.8).

**Done when:** the client constructs without crashing on both the Android
target and the iOS target build (iOS run-verification is task 13's job, but
it must at least compile here); a trivial unauthenticated call (e.g.
`SELECT` on `products`, public per §2.1) succeeds against local Supabase from
an Android test or debug build, proving the repository wrapper and session
storage wiring both work end-to-end with no UI involved.

**Depends on:** Task 1 (needs the config values to point at).

---

## Task 3 — Auth logic

**Covers:** a shared `AuthStore` (per D-001, in `sharedLogic`, no UI
dependencies) plus the thin Android `AuthViewModel` adapter, replacing the `Auth`
`StreamNotifier` (§1.2). The store provides: `signInWithPassword`, `signUp`, `recoverPassword`,
`updatePassword`, `signOut`, and session restore on launch reproducing the
"emit null-or-restored-session exactly once at startup" behavior the
porting-critical note in §1.2 calls out. Also exposes the small derived
auth-gate state D-002 needs (`Unknown`/`LoggedOut`/`LoggedIn`) — built here
since it's a pure function of the session stream, even though navigation
itself (its only consumer) is task 5. **Deliberately not ported:** the dead
`Auth.signInWithOAuth` method and the dead `currentSession` getter (§1.2,
§1.9 rough edge #6) — a *working* OAuth sign-in is built properly in task 10
per D-008, not carried over as dead code. **Deliberately deferred to task
12:** the PostHog `identify()`/`reset()` side effects that live alongside
`Auth` in Flutter (§1.5) — kept out of this task so it stays pure
auth-state-and-side-effect-free logic. **Deliberately deferred to task 8:**
the `Metadata` notifier (subscription/profile fetch) — its only consumer in
Flutter is `HomeScreen` (§1.2), so it's built alongside that screen, not
here. **Stores and state only — no screens.**

**Flutter files:** `flutter/lib/services/auth_notifier.dart` (not
`auth_notifier.g.dart` — generated, don't hand-port, §1.2).

**Done when:** the `AuthStore`'s state is verifiable from a test or a
debug harness with no screen attached — session restore, sign-in, sign-up,
sign-out, and the three-state auth-gate derivation all work against a real
or fake Supabase client.

**Depends on:** Task 2.

---

## Task 4 — Auth UI (Android)

**Android only** (Jetpack Compose in `androidApp`; the SwiftUI equivalent is built in task 13).

**Covers:** Jetpack Compose screens for login and sign-up — Flutter has one screen
(`AuthScreen`) whose embedded `EmailForm` toggles between the two via an
`AuthAction` enum (§1.1); port that shape rather than splitting into two
separate screens if it's not needed. Validation rules carried over exactly:
email non-empty + `^\S+@\S+\.\S+$`; password non-empty + ≥6 chars;
sign-up confirm-password must match (§1.1). Post-signup "Check your Email!"
messaging, since email confirmation is on and `signUp` returns no session
(§2.8). Fix the "Create Acccount" typo while porting that string (§1.9 rough
edge #7). Drop the dead `handleSignUpComplete()` (§1.1 — never called in
Flutter). **Not in this task:** SSO buttons (task 10) and the forgot-password
dialog (task 9) — email/password only. Uses default Material3 styling for
now; task 6 retrofits shared components onto these screens afterward, so
don't block on task 6 to start this one.

**Flutter files:** `flutter/lib/screens/auth_screen.dart`,
`flutter/lib/components/email_form.dart`.

**Done when:** against local Supabase, a new user can sign up with
email/password and sees the check-your-email state; an existing confirmed
user can sign in; validation errors match the Flutter rules above. Screens
render and are directly launchable for manual verification even though
they're not yet reachable through real app navigation — task 5 wires that.

**Status:** code written and compiled (`./gradlew :androidApp:assembleDebug`);
validation rules unit-tested (`:androidApp:testDebugUnitTest`). **Pending
(runtime/manual, not run):** sign-up → check-your-email state, sign-in with a
confirmed user, and visual check of validation errors against local Supabase.
`AuthScreen` is currently the sole nav destination in `MainActivity`; task 5
replaces that with the real graph. Sign-up success flips the form to login and
shows the dialog (as in Flutter); "Ok Matt." became "Ok".

**Depends on:** Task 3.

---

## Task 5 — Navigation and auth gate (Android)

**Android only:** Navigation Compose in `androidApp`; the SwiftUI `NavigationStack` equivalent is built in task 13.

**This is the vertical-slice gate.** Per the build order: it must build and
run on a real Android device with a working login before any task after it
starts.

**Covers:** the nav graph (Navigation Compose, decision
D-007a) covering the Flutter route table in §1.6 — `/login`, `/`, `/reset`,
`/payments` — plus the `/loading` splash state (§1.1: not a real screen
class in Flutter, just a spinner; port it the same way). Implements the
signed-in/signed-out split as decision D-002: navigate only on a transition
between task 3's `Unknown`/`LoggedOut`/`LoggedIn` states, not on every raw
session emission — this is the fix for the bug documented in §1.6 (Flutter's
`GoRouter` rebuilds entirely on every token refresh, papered over only by a
shared `navigatorKey` global). **Not in this task:** deep-link parsing
(OAuth return, password-reset link, Stripe return) — those are added on top
of this graph starting in task 9. The signed-in destination for this task's
verification can be a placeholder screen (e.g. "Signed in as {email}" + a
sign-out button) — the real `HomeScreen` port is task 8; this task is about
proving the auth gate and navigation, not final UI.

**Flutter files:** `flutter/lib/services/router_notifier.dart` (not the
generated `.g.dart`), `flutter/lib/main.dart:22-24` (the `MaterialApp.router`
wiring it replaces), `flutter/lib/components/dialog_page.dart` (go_router
dialog plumbing — note its KMP equivalent for later; task 9 is the first task
that actually needs a dialog-style destination, for `/reset`).

**Done when:** the app builds and installs on a real Android device. Cold
launch with no stored session routes to the login screen (task 4). Signing
in transitions to a signed-in destination (placeholder acceptable) with no
visible nav-graph remount. Leaving the app logged in across a token refresh
produces no remount/flicker — verify this specifically, since it's the exact
failure mode §1.6 flags as "do not reproduce." Signing out returns to login.

**Depends on:** Tasks 3, 4 — hard dependency, since running on a device with
working login is the literal done criterion.

**Status:** code written and compiled (`./gradlew :androidApp:assembleDebug`); gate→route mapping unit-tested (`:androidApp:testDebugUnitTest`). `reset`/`payments` are placeholder routes (tasks 9/11). **Pending (runtime/manual, not run):** install on a real device, cold launch → login, sign-in → home with no remount, token-refresh no-flicker check, sign-out → login. Task 5 is the vertical-slice gate; do not start task 6 until these pass.

---

## Task 6 — Theme and shared components (Android)

**Android only.** "Shared" here means shared across Android screens, not across platforms; iOS theming is part of task 13.

**Covers:** a Jetpack Compose Material3 theme seeded from Flutter's
`ColorScheme.fromSeed(seedColor: Colors.deepPurple)` (`0xFF673AB7` — PORTING.md
§1.9 already confirms this is a faithful Material3 match) and a small shared
component set: primary button (`.primary`/`.onPrimary`), text field (with
Compose's autofill support, replacing
Flutter's `AutofillGroup`), inline error text (`.error`), "forgot password"
style outline text (`.outline`), a loading indicator, and a generic error-state
composable. There is very little to port here — Flutter barely themes
anything: no dark theme, no custom text theme, no custom fonts, no assets
(§1.9) — so most of this task is establishing the component *shapes*, not
porting existing visual design. Retrofit task 4's auth screens onto these
components as part of finishing this task.

**Flutter files:** `flutter/lib/main.dart:27-31` (the `ThemeData`/
`useMaterial3: true` setup, and the dead no-op `builder` placeholder — drop
it, don't port it), plus the scattered `colorScheme.*` usages in
`auth_screen.dart`, `home_screen.dart`, `email_form.dart`,
`reset_password_dialog.dart`.

**Done when:** a shared theme object and the component list above exist,
and task 4's screens visibly use them instead of raw Material3 defaults.

**Depends on:** Task 4 in practice, since "done" includes retrofitting its
screens — the tokens themselves don't strictly require it, but there's
nothing to retrofit before task 4 exists.

**Status:** code written and compiled (`./gradlew :androidApp:assembleDebug`); `:androidApp:testDebugUnitTest` passes. Theme (`ui/theme/Theme.kt`, light only) and components (`ui/components/Components.kt`: PrimaryButton, AppTextField w/ autofill, ErrorText, OutlineTextButton, LoadingIndicator, ErrorState) added; `AuthScreen`, the loading route and the placeholder home now use them. The palette is a hand-written approximation of Flutter's `fromSeed(deepPurple)` scheme, not bit-exact. **Pending (runtime/manual, not run):** visual check on a device that the screens render with the theme.

---

## Task 7 — `screen-porter` agent

**Covers:** writing `.claude/agents/screen-porter.md`, codifying the
conventions fixed by tasks 1–6 (Android) and the iOS conventions decided in
PORTING.md (D-001, D-007b) so per-screen work can run as independent,
low-context sessions. It must document **both platform conventions**:
- **Android (Jetpack Compose):** how a screen's `ViewModel` adapter wraps its shared
  store (D-001), how a screen is added to the Navigation Compose graph
  (D-002/D-007a), which task 6 components to reuse, and the
  `setState`/Riverpod-`.watch` → `collectAsStateWithLifecycle` mapping.
- **iOS (SwiftUI):** how a screen's `ObservableObject` adapter wraps its shared
  store (D-001), how a screen is added to the `NavigationStack` (D-002/D-007b),
  the SwiftUI theming/component conventions that task 13 will establish
  (marked "to be confirmed in task 13" until then), and the
  `setState`/`.watch` → `@Published`/`@StateObject` mapping.
- Policy for both: business logic belongs in shared stores, never in a
  screen; fix known typos/strings while porting (§1.9's string table) rather
  than copying them verbatim. The agent reads `PORTING.md` §1.1 (screen
  inventory) for whichever screen it is asked to port.

**Flutter files:** none directly — source material is `PORTING.md` §1.1–§1.9
plus tasks 1–6's actual output.

**Done when:** the agent file exists and is concrete enough that invoking it
on a new screen, for either platform, doesn't require re-deriving conventions
or re-reading tasks 1–6.

**Depends on:** Tasks 1–6 (it documents conventions that must already exist).

**Status:** `.claude/agents/screen-porter.md` written (frontmatter + Android and iOS conventions, grounded in the actual task 1–6 code). Doc-only task; nothing to compile or run. **Pending:** a real invocation on a screen (task 8) to confirm it is concrete enough; iOS theming/components and the `StateFlow`→Swift bridge are marked "to be confirmed in task 13."

---

## Task 8 — Home screen (Android)

**Android only** (Jetpack Compose; the SwiftUI Home screen is built in task 13).

**Covers:** the one remaining screen (see the note at the top of this file)
— `HomeScreen` (§1.1), using the `screen-porter` agent from task 7. Build a
shared `MetadataStore` (plus its thin Android `ViewModel` adapter) as the port of the `Metadata` notifier (§1.2): the
concurrent `getUserDetails()` + `getSubscription()` fetch, refetching on
login/logout/token-refresh. Fix rough edge #3 while porting it: the Flutter
query is `.from('users').select('*').single()`, which throws if the profile
row is missing; use the `maybeSingle` equivalent so a missing row renders
the "failed to load" state instead of crashing. Port the four-state status
sentence (has-plan / no-plan / loading / failed-to-load, §1.1) and the AppBar
actions: "Payments" (price-less → billing portal; wire the navigation now,
full behavior lands in task 11) and "Logout" (→ task 3's `signOut`). "See
Pricing" is a placeholder external link in Flutter today (a GitHub README
URL, not a real pricing page, §1.1) — port the placeholder as-is rather than
inventing a real URL; flag it for the product owner to replace.

**Flutter files:** `flutter/lib/screens/home_screen.dart`,
`flutter/lib/services/metadata_notifier.dart` (not the generated `.g.dart`),
`flutter/lib/models/user_metadata.dart`, `flutter/lib/models/stripe.dart`.

**Done when:** this screen replaces task 5's placeholder signed-in
destination. All four status-sentence states render correctly against local
Supabase, including forcing the missing-profile-row case to confirm the
`maybeSingle` fix doesn't crash. Logout returns to login via task 5's gate.
The Payments button navigates to the payments route without crashing (full
payments correctness is task 11's job).

**Depends on:** Tasks 5, 6, 7.

**Status:** implemented and compiled. Shared `MetadataStore` + `MetadataRepository` + models (`sharedLogic/.../metadata/`), `maybeSingle` via `decodeSingleOrNull` (missing profile row -> `MissingProfileException` -> failed-to-load state), `MetadataViewModel`, `HomeScreen` (replaces the placeholder), Payments button navigates to `payments`, Logout -> `signOut`. Refetches on login/logout/`refresh()` but **not** on token refresh (rough edge #5, D-002). Added kotlinx-serialization plugin and `lifecycle-runtime-compose`. Checks passed: `:sharedLogic:testAndroidHostTest` (8 new MetadataStore tests), `:androidApp:testDebugUnitTest` (5 new HomeScreen tests), `:androidApp:assembleDebug`. Fixes: unnamed plan no longer renders "null". **Product-owner flag:** "See Pricing" still opens the placeholder GitHub README URL. **Pending (runtime/manual, not run):** all four states against local Supabase, forcing the missing-row case, logout returning to login, Payments navigation on a device; real-device confirmation of `screen-porter` (task 7).

---

## Task 9 — Account management

**Covers:** password-reset request (`RecoverPasswordDialog` →
`Auth.recoverPassword`, §1.1) and password-reset completion
(`ResetPasswordDialog` on the `/reset` deep link → `Auth.updatePassword`,
§1.1) — fixing rough edge #4 while porting it: Flutter's reset-password
field is **not obscured**; obscure it this time. Also profile editing
(`full_name`/`avatar_url` via the `users` table's own-row UPDATE policy,
§2.1) and delete-account.

**This task does the platform deep-link registration for the first time** —
the Android intent-filter and iOS `CFBundleURLTypes` entry for decision
D-003's custom scheme (e.g. `com.devtodollars.app://login-callback`) — since
password reset is the first flow in the build order that needs it. Tasks 10
and 11 add more paths under this same registered scheme; they don't
re-register it. The iOS half of this registration is config-only until task
13 can actually run the app on iOS — verify the whole flow end-to-end on
Android here, and revisit it as part of task 13. This also requires the
§2.8 redirect allow-list change (`supabase/config.toml` locally, and the
hosted project's dashboard for production — dashboard access may need
someone outside this task).

**Open gap to flag, not silently skip:** per §2.1/§2.3/§2.4, **no existing
edge function or RLS policy exposes self-service account deletion** to an
anon-key client — `users` has no DELETE policy, and deleting the
`auth.users` row itself needs the admin API (service-role), which this
ground-rules-fixed backend doesn't expose. If no existing mechanism turns up
during this task, "done" for delete-account is a UI entry point that
visibly surfaces this as blocked (e.g. "contact support to delete your
account") — not a missing button, and not a new edge function added without
explicit sign-off, since that would break the "backend is fixed" ground
rule beyond the one documented §2.8 exception.

**Flutter files:** `flutter/lib/components/recover_password_dialog.dart`,
`flutter/lib/components/reset_password_dialog.dart`,
`flutter/lib/components/dialog_page.dart`.

**Done when:** a forgot-password email can be requested and received
(verify against local Supabase's mail trap or the real provider); opening
the `/reset` link on Android deep-links into the app and sets a new password
with the field obscured; profile edits to `full_name`/`avatar_url` persist
and are visible after a refetch; delete-account either calls a real
mechanism discovered during this task, or ends in the explicit blocked-state
UI described above.

**Depends on:** Task 5 (nav graph to attach the `/reset` destination to).
Task 8 landing first makes account management reachable from Home but isn't
a hard blocker.

---

## Task 10 — SSO

**Covers:** decision D-008 — native Google Sign-In (`signInWithIdToken`) on
both platforms, native Sign in with Apple on iOS (new: Flutter never
configured Apple at all, §2.2), and GitHub via the browser-redirect
`signInWithOAuth` path reusing task 9's deep-link registration. Adds the SSO
buttons to task 4's login screen and the corresponding sign-in methods to
task 3's `AuthStore`. Includes the actual per-provider config: Google
OAuth client registration, Apple's capability/entitlement setup — Supabase's
local `config.toml` already has Google and GitHub OAuth enabled for local
dev (§2.2); the hosted project's client IDs need verifying separately.

**Apple sign-in cannot be verified until task 13 exists** — iOS isn't built
until then. Do the Google + GitHub half of this task now; treat the Apple
half as carried forward and finished as part of task 13, not as blocking
this task's Google/GitHub completion.

**Flutter files:** `flutter/lib/screens/auth_screen.dart` (the
`SupaSocialsAuth` widget this replaces, §1.1), the dead
`Auth.signInWithOAuth` in `auth_notifier.dart` (§1.2 — not hand-ported in
task 3; this is where its working equivalent actually gets built).

**Done when:** Google sign-in via the native SDK succeeds and reaches Home
(task 8) through task 5's gate; GitHub sign-in works via the browser-redirect
path and returns through task 9's deep link. Apple sign-in is explicitly
tracked as finished by task 13, not by this task, and that's an expected
property of this build order, not a miss.

**Depends on:** Tasks 3, 4, 9 (deep-link registration). Apple sign-in
specifically also depends on Task 13, out of numeric order — see above.

---

## Task 11 — Payments

**Covers:** `PaymentsScreen` (§1.1, §1.4) built on decisions D-004
(poll-with-backoff to detect a completed subscription, since the webhook
write is async and `subscriptions` isn't in the realtime publication, §2.6)
and D-005 (open Stripe in an in-app browser tab — Android Custom Tabs here;
the iOS `ASWebAuthenticationSession` half is task 13's job — using task 9's
registered deep-link scheme as `return_url`). Calls `get_stripe_url` with
the exact body shape from §2.3 (`{"price": ..., "return_url": ...}`), and
guards the call on an authenticated session per the §2.3 KMP pitfall note
(an anon-key call gets a 400, not a 401, which is easy to misdiagnose).
Implements both sides of the endpoint's overload: a known price → Checkout,
no/unknown price → billing portal. Makes task 8's "Payments" AppBar button
fully functional.

**Flutter files:** `flutter/lib/screens/payments_screen.dart`.

**Done when:** tapping Payments with no price opens the billing portal in an
in-app browser tab and returns cleanly with no subscription change. A
`price_XXX` deep link opens Checkout; completing a test-mode Stripe payment
redirects back into the app via task 9's deep link; the Home screen's status
sentence (task 8) updates within the poll window with **no app restart
required** — this is the exact gap §1.4 flags as a must-fix, and this task is
where it gets proven fixed, on a real Android device.

**Depends on:** Tasks 8 (displays the result), 9 (deep-link return
mechanism).

---

## Task 12 — Analytics

**Covers:** PostHog native config (Android manifest / iOS Info.plist keys,
§1.5 — PostHog was never wired through the generic env mechanism even in
Flutter, so it's its own config step here, separate from task 1's Supabase
config). `identify()` tied to task 3's session stream (fires on every
non-null session emission, including hourly token refresh — Flutter does
this too and §1.5 never flags it as a problem, so port as-is unless it
proves noisy in practice) and `reset()` on logout. Screen-view tracking
analogous to `PosthogObserver` (§1.5), tied to task 5's nav graph, using the
same route-path names Flutter uses (`/`, `/login`, `/payments`, `/loading`,
`/reset`).

**The six events named in this task's title are already emitted
server-side**, not from any client `capture()` call (§1.5): `on_user_modify`
emits "user signs in" / "user signs up" / "user deletes account";
`get_stripe_url` emits "user starts checkout" / "user opens billing portal";
`stripe_webhook`'s `checkout.session.completed` handling is the source for
checkout completing. This task's job for those six is **verification, not
implementation** — confirm each one still appears in PostHog once the
client flow that triggers it (sign-in/up: tasks 3–4/10; delete-account: task
9; checkout start/portal-open/complete: task 11) is actually wired up.

**Flutter files:** `flutter/lib/services/auth_notifier.dart:29-34`
(`identify`/`reset`), `flutter/lib/services/router_notifier.dart:25`
(`PosthogObserver`), the native PostHog config in `AndroidManifest.xml` /
`Info.plist` (the `web/index.html` config is out of scope — no web target).

**Done when:** `identify`/`reset` calls match session lifecycle in the
PostHog project; screen views appear per named route; each of the six
server-emitted events has been independently confirmed to fire by exercising
its triggering flow once, end to end.

**Depends on:** Tasks 3, 5, 9, 11 — this task is largely verification and
can't be fully closed out until the flows it's checking exist, which the
given order already guarantees by the time task 12 starts.

**Status:** implemented and compiled. Shared `AnalyticsTracker` interface + `AnalyticsSessionObserver` (`sharedLogic/.../analytics/`): `identify` on every non-null session emission (token refreshes included, as in Flutter), `reset` on null session. Android: `PostHogTracker` (posthog-android 3.72.4, config from manifest `com.posthog.posthog.*` meta-data, same key/host as Flutter), started in `KmpApplication`; screen views from `currentBackStackEntryFlow` via `ScreenNames` (`/`, `/login`, `/payments`, `/loading`, `/reset`, plus `/account`). Checks passed: `:sharedLogic:testAndroidHostTest` (3 new), `:androidApp:testDebugUnitTest` (3 new), `:androidApp:assembleDebug`. iOS PostHog init is task 13. **Pending (runtime/manual, not run):** confirming identify/reset/screen events in the PostHog project and the six server-emitted events by exercising each flow on a device.

---

## Task 13 — iOS target (complete SwiftUI UI and navigation)

**Everything before this task is Android only.** This task builds the
**complete native SwiftUI UI** — every screen the Android tasks 4–11 produced
(auth, home, payments, password dialogs/sheets, SSO buttons), the SwiftUI
theme/components, and `NavigationStack` navigation driven by the shared
auth-gate (D-002/D-007b) — plus the iOS presentation adapters
(`ObservableObject` wrappers over the shared stores, including the
`StateFlow`→Swift bridge decision from D-001). It then verifies tasks 1–12's
shared logic there, and finishes the two things explicitly deferred to it
above (task 9's iOS deep-link registration, task 10's Apple sign-in). SwiftUI
screens are written natively, not translated from the Compose code.

**Expect/actual gaps to anticipate** (a starting list, not exhaustive — budget
real discovery time, not just these):
- **Session storage** (task 2): the `Settings` backing differs
  (`NSUserDefaults` vs Android's `Context`-dependent `SharedPreferences`)
  even though the `SessionManager` interface doesn't.
- **In-app browser presentation** (task 11/D-005): `ASWebAuthenticationSession`
  vs Android Custom Tabs are different APIs, not just different backends for
  one shared interface — this is a bigger actual than most.
- **Deep link entry point** (tasks 9–11/D-003): iOS's
  `application(_:open:options:)` vs Android's intent handling, both feeding
  the same common-code URL parser.
- **Native Google Sign-In initialization** (task 10): different per-platform
  setup call even where the Kotlin-facing surface looks similar.
- **PostHog SDK initialization** (task 12): native per-platform call, same
  as it already is in the Flutter app (§1.5) — not a regression, just not
  shareable.
- Kotlin/Native interop quirks in the `SharedLogic` framework (Swift-facing
  names, `StateFlow`/suspend bridging, sealed-class exposure) that typically
  only surface once Swift code consumes it.

**Flutter files:** none directly — per §1.6, OAuth return, email
confirmation, and `/reset` never actually worked on iOS (or Android) in the
Flutter app; only web worked. There is no working Flutter iOS behavior to
port for those flows, so this is net-new correctness work, not a port.

**Done when:** the SwiftUI screens and navigation cover the same flows as Android; a full sign-up → confirm → sign-in → subscribe → see updated
plan status flow works on a real iOS device (not just the simulator — the
Apple sign-in note in task 10 is specifically about device-vs-simulator
provisioning differences), and task 9's deep link plus task 10's Apple
sign-in are both verified for the first time here.

**Depends on:** Tasks 1–12.

**Status:** implemented and compiled; runtime acceptance pending. Swift UI in `kmp/iosApp/iosApp/`: `Screens/` (Auth + email form, Google/GitHub/Apple buttons, Recover/Reset password sheets, Home, Account, Payments), `Navigation/RootView.swift` (root switches on the D-002 `AuthGate`; `NavigationStack` with typed `SignedInRoute` path; `/reset` as a sheet; `onOpenURL` -> `AppEnvironment.handle`), `Theme/` (palette mirroring Android's deepPurple scheme, light only; components), `Adapters/` (`AuthModel`/`MetadataModel`/`PaymentsModel` `ObservableObject`s over the shared stores), `Platform/` (`StripeBrowser` on `ASWebAuthenticationSession`, `GoogleSignInService`, `PostHogTracker`), `Logic/` (pure Swift: validation, routes/screen names, status text, nonce). **D-001 bridge decision:** hand-written collector wrapper, no SKIE: `sharedLogic/src/iosMain/.../ios/FlowWatch.kt` (`StateFlow.watch`, main-thread callbacks) plus `AppGraph.kt` (iOS counterpart of `KmpApplication`; Swift can't build a `CoroutineScope`). Project: SharedLogic framework linked, SPM `GoogleSignIn-iOS` 9.2+ and `posthog-ios` 3.92+, Sign in with Apple entitlement, Info.plist keys (Google client IDs from `Config.xcconfig`, PostHog, URL schemes). Fixes carried over from Android: "Create Account" typo, obscured reset field, no "Ok Matt." Checks passed: `xcodebuild build` for `iphonesimulator` and `iphoneos` (`CODE_SIGNING_ALLOWED=NO`, compile only), `iosApp/Checks/run.sh` (27 host-side checks of the pure Swift logic), `:sharedLogic:testAndroidHostTest`, `:sharedLogic:compileTestKotlinIosSimulatorArm64`, `:androidApp:testDebugUnitTest`. **Pending (runtime/manual, not run):** every "Done when" item: sign-up -> confirm -> sign-in -> subscribe -> plan status on a real iOS device; `/reset` deep link; Apple sign-in (needs the capability on the App ID and a Supabase Apple provider); Google sign-in (needs `GOOGLE_IOS_CLIENT_ID`/`GOOGLE_WEB_CLIENT_ID`/`GOOGLE_REVERSED_CLIENT_ID` in `Config.xcconfig`); GitHub return via `onOpenURL` (relies on supabase-kt's iOS default external-auth action, untested); Stripe browser return; PostHog events; visual/layout review. Product-owner placeholders unchanged: pricing URL, "hi@devtodollars.com" copy.

---

## Task 14 — CI

**Covers:** a GitHub Actions workflow that builds and tests on every PR —
Android build always; iOS build too if a macOS runner is in budget. Runs the
unit tests from tasks 2–3 (repository/store tests need no device) and
whatever's added later. Lint/static analysis as the rough analog to
Flutter's `flutter_lints` (§1.8). **There is nothing to port here**: the
Flutter app's only test is an empty `testWidgets('TODO: add tests')` stub
(§1.8/§1.9 rough edge #6), and no CI config is referenced anywhere in
`PORTING.md` — this is new infrastructure, not a gap in the port.

**Flutter files:** none (confirmed absence, not an oversight).

**Done when:** a PR triggers a run that builds the Android target (and iOS,
if feasible) and runs the existing unit tests, failing the check on any
build or test failure.

**Depends on:** enough of tasks 1–3 to have something to build and test;
practically this can start as soon as task 5's vertical slice exists and
just grows as later tasks add modules/tests.

---

## Task 15 — Template polish

**Covers:** a README for the KMP project, setup docs (mirroring the local
vs. hosted Supabase split in `flutter/env.local.json`/`env.json`, §1.9), and
a `.env.example` (or Gradle-properties-example) for the two keys from task 1
— non-secret by the same logic Flutter already commits both env files
under. `BOILERPLATE-NOTES.md` and `docs/` describe the *original*
Flutter/Next.js boilerplate and are reference-only per the ground rules
(read for tone/structure if useful; don't carry over Flutter/Next.js-specific
instructions).

**Flutter files:** `flutter/env.json`, `flutter/env.local.json` (the pattern
to follow), `BOILERPLATE-NOTES.md`, `docs/` (reference only).

**Done when:** a new developer can clone the repo, follow the README and
setup docs, copy `.env.example`, point at local or hosted Supabase, and get
a running build without reading `PORTING.md` or asking a question this file
doesn't already answer.

**Depends on:** effectively everything before it — it documents the
finished state, which is why it's last.

---

## Dependency summary

| Task | Depends on | Notable cross-order dependency |
|---|---|---|
| 1 | — | |
| 2 | 1 | |
| 3 | 2 | |
| 4 | 3 | |
| 5 | 3, 4 | **Hard gate** — real-device build + working login required |
| 6 | 4 | retrofit, not a blocking build dependency |
| 7 | 1–6 | |
| 8 | 5, 6, 7 | |
| 9 | 5 | iOS half of deep-link registration config-only until 13 |
| 10 | 3, 4, 9 | Apple sign-in half depends on **13**, out of numeric order |
| 11 | 8, 9 | iOS half of D-005 (browser tab) depends on **13** |
| 12 | 3, 5, 9, 11 | verification-only task |
| 13 | 1–12 | also finishes 9's iOS half and 10's Apple half |
| 14 | 1–3 (minimum) | can start early, grows over time |
| 15 | all | |
