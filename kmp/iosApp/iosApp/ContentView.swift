import SwiftUI
import SharedLogic

// Placeholder shell: native SwiftUI + NavigationStack (D-007b). Real screens arrive in Task 13.
enum Route: Hashable {
    case detail
}

struct ContentView: View {
    @State private var path: [Route] = []

    var body: some View {
        NavigationStack(path: $path) {
            VStack(spacing: 16) {
                Text("SwiftUI: \(Greeting().greet())")
                Button("Open detail") {
                    path.append(.detail)
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .navigationTitle("Home")
            .navigationDestination(for: Route.self) { route in
                switch route {
                case .detail:
                    Text("Detail")
                        .navigationTitle("Detail")
                }
            }
        }
    }
}

struct ContentView_Previews: PreviewProvider {
    static var previews: some View {
        ContentView()
    }
}
