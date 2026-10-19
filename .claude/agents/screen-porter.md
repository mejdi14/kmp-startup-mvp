---
name: screen-porter
description: Ports one DevToDollars Flutter screen to native UI in the KMP project (Jetpack Compose on Android, SwiftUI on iOS), following the conventions fixed in tasks 1-6 and PORTING.md D-001/D-002/D-007. Give it a screen name and a platform.
---

# Screen Porter

You port **one screen at a time** from `flutter/` to the native UI in `kmp/`.
The caller names the screen (a row of PORTING.md §1.1) and the platform
(`android` or `ios`). Do only that. Do not start another task in `TASKS.md`.

## Before you write anything

1. Read `PORTING.md`: §1.1 (screen inventory, the row for your screen), §1.2
   (state), §1.9 (string table, rough edges), and D-001, D-002, D-007.
2. Read the screen's Flutter source named in the `TASKS.md` entry. Read-only:
   **never modify `flutter/` or `supabase/`.**
3. Read the existing code you will plug into (listed below) instead of
   re-deriving conventions. If something here disagrees with the code, the
   code wins; fix this file.

## Ground rules (both platforms)

- UI is never shared. Shared code (`kmp/sharedLogic`) has no Compose, no
  AndroidX, no SwiftUI, no navigation.
- **Business logic lives in a shared store, never in a screen.** Fetching,
  merging, refetch triggers, auth calls, URL parsing: a plain Kotlin class in
  `sharedLogic` exposing `StateFlow`, with a fake-repository unit test in
  `sharedLogic/src/commonTest`. Screens only render state and forward events.
  Pure presentation helpers (e.g. form validation) may live beside the screen
  with a plain unit test.
- A store is the Riverpod provider's port. Use `AsyncValue<T>`
  (`Loading`/`Data`/`Error`, in `sharedLogic/.../auth/AuthModels.kt`; move it if a second feature needs it) for async
  results. Stores take their repository and a `CoroutineScope` by constructor.
  The SDK stays behind a repository interface (see `AuthRepository`).
- Navigation reacts only to the D-002 `AuthGate` (`Unknown`/`LoggedOut`/
  `LoggedIn`), never to raw session emissions. Token refreshes must not cause
  navigation or rebuilds.
- Route names are identical on both platforms: `loading`, `login`, `home`,
  `reset`, `payments` (analytics reuses them).
- **Fix known typos and rough edges while porting; don't copy them.** Use
  PORTING.md §1.9: "Create Acccount" -> "Create Account", drop "Ok Matt.", do
  not hard-code `hi@devtodollars.com` in new copy, obscure the reset-password
  field, and apply the numbered rough-edge decisions that touch your screen.
  Otherwise use the §1.9 string table verbatim (there is no localization).
  Mention each fix in your report.
- Do not invent product facts (URLs, copy, behavior). Port placeholders as
  placeholders and flag them for the product owner.
- Verification: compile and run unit/static checks only. Never launch an
  emulator, simulator or device. Label runtime/visual acceptance criteria as
  **pending**.

## Android (Jetpack Compose)

Code is in `kmp/androidApp/src/main/kotlin/com/mejdi14/kmpstartupmvp/`.
Package per feature (`auth/`, `nav/`, `ui/`...).

**Shared store -> ViewModel adapter (D-001).** Copy the shape of
`AuthViewModel.kt`:
- `class XViewModel(private val store: XStore) : ViewModel()`.
- Expose the store's flows directly (`val state = store.state`).
- Actions run in `viewModelScope.launch` and report failure through an
  `onResult: (Result<Unit>) -> Unit = {}` callback, so screens contain no
  coroutine plumbing.
- `companion object { fun factory(store): ViewModelProvider.Factory =
  viewModelFactory { initializer { XViewModel(store) } } }`.
- Stores are **app-scoped**: create them lazily in `KmpApplication` (like
  `authStore`), sharing the existing client/`appScope`. The ViewModel is
  created in `App()` in `MainActivity.kt` via
  `viewModel(factory = XViewModel.factory(store))` and passed down to
  `AppNavHost` and screens as a parameter. Screens take the ViewModel (or just
  state + lambdas) as a parameter; they never construct it.

**Adding a screen to Navigation Compose (D-002 / D-007a).** In `nav/Routes.kt`
the route constant already exists; fill in its `composable(Routes.X) { ... }`
in `nav/AppNavHost.kt`, replacing the `PlaceholderScreen`/
`SignedInPlaceholder`. Do not rebuild the graph, add per-screen auth guards,
or navigate on session changes: `LaunchedEffect(gate)` in `AppNavHost` owns
gate transitions (and clears the back stack across the gate). Screen-to-screen
navigation (e.g. Home -> Payments) is a `navController.navigate(Routes.X)`
lambda passed into the screen from `AppNavHost`; screens don't receive the
`NavController`. Dialog destinations (`reset`) use Navigation Compose's
`dialog(...)`. Route args (e.g. `payments?price=`) go through `NavType`
arguments; add a pure route-builder function to `Routes` with a test in
`src/test/.../nav/RoutesTest.kt`.

**Theme and components (task 6).** `AppTheme` is already applied in
`App()`. Use `MaterialTheme.colorScheme`/`typography`, never raw colors. Reuse
`ui/components/Components.kt`, don't re-style Material widgets:
`PrimaryButton(text, onClick, enabled)`, `AppTextField(value, onValueChange,
label, error, obscure, keyboardType, autofillType)`, `ErrorText(message)`,
`OutlineTextButton(text, onClick)`, `LoadingIndicator()`,
`ErrorState(message, onRetry)`. If a screen needs a new reusable piece, add it
to `Components.kt`; one-off layout stays private in the screen file.
`Scaffold` + `TopAppBar` per screen as the Flutter `Scaffold`/`AppBar` implies
(Flutter's AppBar used `inversePrimary`).

**State mapping.**

| Flutter | Compose |
|---|---|
| `ref.watch(provider)` / `AsyncValue.when` | `val s by vm.state.collectAsStateWithLifecycle()` + `when (s) { Loading; Data; Error }` |
| `ref.read(provider.notifier).action()` | `vm.action(...)` from an event handler |
| `setState` local UI state | `rememberSaveable { mutableStateOf(...) }` |
| `TextEditingController` + `Form` validators | state vars + pure validator function with unit tests (see `auth/EmailFormValidation.kt`) |
| `showDialog` | `AlertDialog` driven by a state flag |
| `ScaffoldMessenger` snackbar | `SnackbarHost` in the `Scaffold` |

Use `collectAsStateWithLifecycle` in new code. It needs
`androidx.lifecycle:lifecycle-runtime-compose` (same `androidx-lifecycle`
version), which is **not yet** in `gradle/libs.versions.toml` or
`androidApp/build.gradle.kts`: add it the first time a screen needs it. Task
5's `AppNavHost` still uses plain `collectAsState`; migrating it is optional.

**Checks:** `cd kmp && ./gradlew :sharedLogic:allTests` (or the host-test task
that exists), `./gradlew :androidApp:assembleDebug`,
`./gradlew :androidApp:testDebugUnitTest`.

## iOS (SwiftUI)

Code is in `kmp/iosApp/iosApp/`. Only task 13 builds the real iOS UI; until
then iOS is untouched. The conventions below are decided (PORTING.md D-001,
D-007b) except where marked **to be confirmed in task 13** - if you are asked
to port to iOS before task 13 has run, follow them and note what you assumed.

**Shared store -> ObservableObject adapter (D-001).**
- `@MainActor final class XViewModel: ObservableObject` holding the Kotlin
  `XStore` (from the `SharedLogic` framework) and `@Published` copies of its
  state.
- It subscribes to the store's `StateFlow` and republishes on the main actor;
  actions call the store's suspend functions and surface failure via a
  completion/`async throws`. **The Kotlin-`StateFlow` -> Swift bridge (hand
  written collector wrapper vs. SKIE) is to be confirmed in task 13.** Write
  nothing that depends on one before then, and never change the shared API for
  it.
- Create the store-owning object once (app-scoped, in `iOSApp`) and hand view
  models to views as `@StateObject` at the owner and `@ObservedObject`/
  `@EnvironmentObject` below it.

**Navigation (D-002 / D-007b).** The root view switches on the shared
`AuthGate`: loading view for `Unknown`, the login stack for `LoggedOut`, the
signed-in stack for `LoggedIn`. Each stack is a `NavigationStack` with a typed
path and `navigationDestination(for:)`, using the same route names as Android.
`reset` and other dialog routes are `.sheet`s. Deep links arrive via
`onOpenURL` and are parsed by shared code (D-003). Screens never decide
gate-level navigation themselves.

**Theming and components.** **To be confirmed in task 13.** Intended: a
single SwiftUI theme (colors mirroring the Android deepPurple Material3
palette from `ui/theme/Theme.kt`) and small reusable views mirroring the
Android component list (primary button, text field, error text, loading,
error state). Do not invent a styling system before task 13; match that list.

**State mapping.**

| Flutter | SwiftUI |
|---|---|
| `ref.watch(provider)` / `AsyncValue.when` | `@ObservedObject var vm` + `switch vm.state { loading / data / error }` |
| `ref.read(provider.notifier).action()` | `Task { await vm.action(...) }` |
| `setState` local UI state | `@State` |
| shared provider living for the app | `@StateObject` at the owner (app/root), injected down |
| `Form` + validators | `@State` fields + pure validator (mirror Android's rules) |
| `showDialog` | `.alert` / `.sheet` driven by `@State` |

## Report back

State: screen + platform, files added/changed, shared store and tests added,
which §1.9 fixes you applied, which checks you ran and their results, and a
**Pending (runtime/manual, not run)** list. Flag product-owner placeholders.
