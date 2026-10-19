import Foundation

/// A `/payments` deep link waiting for a signed-in user (mirrors the shared `PaymentRequest`).
struct PendingPayment: Equatable {
    let price: String?
}
