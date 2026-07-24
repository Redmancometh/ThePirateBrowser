import Foundation

struct TorrentResult: Identifiable, Hashable, Sendable {
    let name: String
    let source: TorrentSource
    let magnet: String
    let size: Int64
    let seeders: Int
    let leechers: Int

    var id: String { magnet }

    var metadata: String {
        "\(ByteCountFormatter.string(fromByteCount: size, countStyle: .file)) · "
            + "\(seeders) seeders · \(leechers) leechers"
    }
}

struct SearchOutcome: Sendable {
    let results: [TorrentResult]
    let failures: [String]
}

enum TorrentSource: String, CaseIterable, Codable, Identifiable, Sendable {
    case pirateBay = "The Pirate Bay"
    case knaben = "Knaben"
    case magnetz = "Magnetz"
    case torrentsCsv = "Torrents.csv"
    case nyaa = "Nyaa"
    case eztv = "EZTV"
    case yts = "YTS"

    var id: String { rawValue }

    var summary: String {
        switch self {
        case .pirateBay:
            "General-purpose torrent index."
        case .knaben:
            "Broad metasearch with safety filtering."
        case .magnetz:
            "Fast general magnet search."
        case .torrentsCsv:
            "Open torrent database with broad coverage."
        case .nyaa:
            "Anime-focused RSS search."
        case .eztv:
            "Recent television releases."
        case .yts:
            "Movie releases in compact formats."
        }
    }
}

struct SavedSearch: Identifiable, Codable, Equatable {
    var id = UUID()
    var name: String
    var query: String
    var minimumSeeders: Int
    var isEnabled = true
    var lastCheckedAt: Date?
    var knownMagnets: Set<String> = []
}

struct PutIoTransfer: Identifiable, Hashable, Sendable {
    let id: Int64
    let name: String
    let status: String
    let percentDone: Double
    let size: Int64
    let fileId: Int64?

    var isActive: Bool {
        !["COMPLETED", "ERROR", "CANCELLED", "SEEDING"].contains(status.uppercased())
    }
}

struct PutIoFile: Identifiable, Hashable, Sendable {
    let id: Int64
    let name: String
    let size: Int64
    let contentType: String
    let isDirectory: Bool

    var isPlayable: Bool {
        contentType.lowercased().hasPrefix("video/")
            || ["mp4", "mkv", "mov", "m4v", "avi", "webm"]
                .contains((name as NSString).pathExtension.lowercased())
    }
}

struct PutIoFileListing: Sendable {
    let parentId: Int64
    let files: [PutIoFile]
}

struct PutIoDeviceCode: Sendable {
    let code: String
    let expiresIn: Int
    let interval: Int
}

enum PutIoSection: String, CaseIterable, Identifiable {
    case transfers = "Transfers"
    case files = "Files"

    var id: String { rawValue }
}

enum AppTab: Hashable {
    case search
    case saved
    case putio
    case sources
}
