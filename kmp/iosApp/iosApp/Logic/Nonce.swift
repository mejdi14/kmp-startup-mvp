import CryptoKit
import Foundation
import Security

/// Raw nonce for native sign-in. Supabase gets the raw value; the provider (Apple/Google) gets its
/// SHA-256 hex digest (same scheme as Android's `GoogleSignIn`, D-008).
enum Nonce {
    static func random() -> String {
        var bytes = [UInt8](repeating: 0, count: 32)
        let status = SecRandomCopyBytes(kSecRandomDefault, bytes.count, &bytes)
        precondition(status == errSecSuccess, "Unable to generate nonce")
        return bytes.map { String(format: "%02x", $0) }.joined()
    }

    static func sha256Hex(_ input: String) -> String {
        SHA256.hash(data: Data(input.utf8)).map { String(format: "%02x", $0) }.joined()
    }
}
