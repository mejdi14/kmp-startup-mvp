import Foundation
import SharedLogic

/// Thin iOS adapter over the app-scoped shared `PaymentsStore` (D-001).
@MainActor
final class PaymentsModel: ObservableObject {
    @Published private(set) var pendingRequest: PendingPayment?
    @Published private(set) var syncing = false

    private let store: PaymentsStore
    private var watchers: [Watcher] = []

    init(store: PaymentsStore) {
        self.store = store
        watchers.append(observeOptional(store.pendingRequest) { [weak self] (request: PaymentRequest?) in
            self?.pendingRequest = request.map { PendingPayment(price: $0.price) }
        })
        watchers.append(observe(store.syncing) { [weak self] (syncing: NSNumber) in
            self?.syncing = syncing.boolValue
        })
    }

    deinit { watchers.forEach { $0.cancel() } }

    func getStripeUrl(price: String?) async throws -> String {
        try await store.getStripeUrl(price: price)
    }

    func markLaunched(price: String?) { store.markLaunched(price: price) }
    func onReturned() { store.onReturned() }
    func requestPayments(price: String?) { store.requestPayments(price: price) }
    func consumeRequest() { store.consumeRequest() }
}
