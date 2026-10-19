import SwiftUI

/// Root of the app: switches on the D-002 auth gate (D-007b). `unknown` -> loading, `loggedOut` -> login
/// stack, `loggedIn` -> signed-in stack. Token refreshes never change the gate, so nothing here reacts to them.
struct RootView: View {
    @EnvironmentObject private var env: AppEnvironment
    @ObservedObject var auth: AuthModel

    var body: some View {
        Group {
            switch auth.gate {
            case .unknown:
                LoadingView().trackScreen(.loading, in: env)
            case .loggedOut:
                NavigationStack { AuthScreen(auth: auth) }
            case .loggedIn:
                SignedInStack(auth: auth, metadata: env.metadata, payments: env.payments)
            }
        }
        .tint(AppColors.primary)
        .preferredColorScheme(.light)
    }
}

/// The signed-in `NavigationStack`: Home at the root, typed pushes for Account and Payments, `/reset` as a sheet.
struct SignedInStack: View {
    @ObservedObject var auth: AuthModel
    @ObservedObject var metadata: MetadataModel
    @ObservedObject var payments: PaymentsModel
    @State private var path: [SignedInRoute] = []

    var body: some View {
        NavigationStack(path: $path) {
            HomeScreen(
                auth: auth,
                metadata: metadata,
                onOpenPayments: { path.append(.payments(requested: nil)) },
                onOpenAccount: { path.append(.account) }
            )
            .navigationDestination(for: SignedInRoute.self) { route in
                switch route {
                case .account:
                    AccountScreen(metadata: metadata)
                case let .payments(price):
                    PaymentsScreen(payments: payments, price: price, onDone: { path.removeAll() })
                }
            }
        }
        // Password-reset deep link: the recovery code signed the user in; show the sheet over Home.
        .sheet(isPresented: Binding(get: { auth.pendingPasswordReset }, set: { if !$0 { auth.dismissPasswordReset() } })) {
            ResetPasswordSheet(auth: auth)
        }
        .onChange(of: auth.pendingPasswordReset, initial: true) { _, pending in
            if pending { path.removeAll() }
        }
        // Payments deep link (`…/payments?price=`): held by the store until signed in.
        .onChange(of: payments.pendingRequest, initial: true) { _, request in
            guard let request else { return }
            payments.consumeRequest()
            path = [.payments(requested: request.price)]
        }
    }
}
