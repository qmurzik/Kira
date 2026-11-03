// swift-tools-version:5.9
import PackageDescription

let package = Package(
    name: "TwinCore",
    platforms: [.iOS(.v17), .macOS(.v14)],
    products: [
        .library(name: "TwinCore", targets: ["TwinCore"])
    ],
    targets: [
        .systemLibrary(name: "CZlib", path: "Sources/CZlib"),
        .target(
            name: "TwinCore",
            dependencies: ["CZlib"],
            path: "Sources/TwinCore"
        ),
        .testTarget(
            name: "TwinCoreTests",
            dependencies: ["TwinCore"],
            path: "Tests/TwinCoreTests",
            resources: [.copy("Fixtures")]
        )
    ]
)
