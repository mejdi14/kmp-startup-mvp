import SwiftUI

extension Color {
    init(hex: UInt32) {
        self.init(.sRGB,
                  red: Double((hex >> 16) & 0xFF) / 255,
                  green: Double((hex >> 8) & 0xFF) / 255,
                  blue: Double(hex & 0xFF) / 255)
    }
}

/// Light-only palette mirroring the Android Material3 deepPurple scheme (`ui/theme/Theme.kt`, seed 0xFF673AB7).
/// Flutter defines no dark theme (PORTING.md §1.9), so the app pins the light appearance.
enum AppColors {
    static let primary = Color(hex: 0x675788)
    static let onPrimary = Color(hex: 0xFFFFFF)
    static let error = Color(hex: 0xBA1A1A)
    static let background = Color(hex: 0xFEF7FE)
    static let onBackground = Color(hex: 0x1D1B1F)
    static let outline = Color(hex: 0x7B757F)
    static let inversePrimary = Color(hex: 0xD1BCF9)
}
