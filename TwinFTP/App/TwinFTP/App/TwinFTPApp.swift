import SwiftUI
import TwinCore

@main
struct TwinFTPApp: App {
    var body: some Scene {
        WindowGroup {
            Text("TwinFTP \(ByteFormatter.string(1024))")
        }
    }
}
