import XCTest
@testable import PirateBrowser

final class PirateBrowserTests: XCTestCase {
    func testMagnetRoundTripPreservesInfoHash() {
        let service = TorrentSearchService()
        let hash = "0123456789abcdef0123456789abcdef01234567"
        let magnet = service.magnet(hash: hash, name: "A title & sequel")

        XCTAssertEqual(service.infoHash(from: magnet), hash)
        XCTAssertTrue(magnet.contains("A%20title%20%26%20sequel"))
    }

    func testSavedSearchPersistsMonitoringState() throws {
        let original = SavedSearch(
            name: "Weekly show",
            query: "example",
            minimumSeeders: 12,
            isEnabled: false,
            lastCheckedAt: Date(timeIntervalSince1970: 1_700_000_000),
            knownMagnets: ["magnet:?xt=urn:btih:abc"]
        )

        let data = try JSONEncoder().encode(original)
        let restored = try JSONDecoder().decode(SavedSearch.self, from: data)

        XCTAssertEqual(restored, original)
    }

    func testPlayableFileDetectionUsesContentTypeAndExtension() {
        XCTAssertTrue(PutIoFile(
            id: 1,
            name: "video.bin",
            size: 1,
            contentType: "video/mp4",
            isDirectory: false
        ).isPlayable)
        XCTAssertTrue(PutIoFile(
            id: 2,
            name: "movie.mkv",
            size: 1,
            contentType: "application/octet-stream",
            isDirectory: false
        ).isPlayable)
        XCTAssertFalse(PutIoFile(
            id: 3,
            name: "notes.txt",
            size: 1,
            contentType: "text/plain",
            isDirectory: false
        ).isPlayable)
    }
}
