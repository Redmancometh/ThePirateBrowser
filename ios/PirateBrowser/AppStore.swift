import Combine
import Foundation

@MainActor
final class AppStore: ObservableObject {
    @Published var selectedTab: AppTab = .search
    @Published var query = ""
    @Published var minimumSeeders = 0
    @Published var results: [TorrentResult] = []
    @Published var searchFailures: [String] = []
    @Published var isSearching = false
    @Published var addedMagnets: Set<String> = []

    @Published var savedSearches: [SavedSearch]
    @Published var savedNewResults: [UUID: Int] = [:]
    @Published var enabledSources: Set<TorrentSource>

    @Published var token: String
    @Published var tokenDraft: String
    @Published var isConnecting = false
    @Published var putIoSection: PutIoSection = .transfers
    @Published var transfers: [PutIoTransfer] = []
    @Published var files: [PutIoFile] = []
    @Published var directoryStack: [(id: Int64, name: String)] = [(0, "Files")]
    @Published var isLoadingPutIo = false
    @Published var presentedVideoURL: URL?
    @Published var message: String?

    private let searchService = TorrentSearchService()
    private let putIoService = PutIoService()
    private let defaults = UserDefaults.standard
    private var transferRefreshTask: Task<Void, Never>?

    private static let savedSearchesKey = "saved_searches"
    private static let sourcesKey = "enabled_sources"

    init() {
        let storedToken = TokenStore.load()
        token = storedToken
        tokenDraft = storedToken

        if let data = UserDefaults.standard.data(forKey: Self.savedSearchesKey),
           let decoded = try? JSONDecoder().decode([SavedSearch].self, from: data) {
            savedSearches = decoded
        } else {
            savedSearches = []
        }

        if let names = UserDefaults.standard.array(forKey: Self.sourcesKey) as? [String] {
            enabledSources = Set(names.compactMap(TorrentSource.init(rawValue:)))
        } else {
            enabledSources = Set(TorrentSource.allCases)
        }
    }

    deinit {
        transferRefreshTask?.cancel()
    }

    var canary: String {
        let raw = Bundle.main.object(forInfoDictionaryKey: "BuildCanary") as? String
        return (raw?.isEmpty == false ? raw! : "local").uppercased()
    }

    var isConnected: Bool {
        !token.isEmpty
    }

    var currentDirectoryName: String {
        directoryStack.last?.name ?? "Files"
    }

    func search(_ override: String? = nil) async {
        let requested = (override ?? query).trimmingCharacters(in: .whitespacesAndNewlines)
        guard !requested.isEmpty else {
            message = "Enter a movie, show, or anime to search."
            return
        }
        if enabledSources.isEmpty {
            message = "Enable at least one torrent source."
            return
        }
        query = requested
        isSearching = true
        searchFailures = []
        let outcome = await searchService.search(query: requested, enabledSources: enabledSources)
        results = outcome.results.filter { $0.seeders >= minimumSeeders }
        searchFailures = outcome.failures
        isSearching = false
    }

    func addToPutIo(_ result: TorrentResult) async {
        guard isConnected else {
            selectedTab = .putio
            message = "Connect put.io before adding a transfer."
            return
        }
        do {
            try await putIoService.addTransfer(token: token, magnet: result.magnet)
            addedMagnets.insert(result.magnet)
            message = "Added “\(result.name)” to put.io."
            await loadTransfers()
        } catch {
            message = error.localizedDescription
        }
    }

    func saveCurrentSearch(name: String) {
        let trimmedQuery = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedQuery.isEmpty else {
            message = "Run or enter a search before saving it."
            return
        }
        let trimmedName = name.trimmingCharacters(in: .whitespacesAndNewlines)
        savedSearches.append(SavedSearch(
            name: trimmedName.isEmpty ? trimmedQuery : trimmedName,
            query: trimmedQuery,
            minimumSeeders: minimumSeeders,
            knownMagnets: Set(results.map(\.magnet))
        ))
        persistSavedSearches()
    }

    func updateSavedSearch(_ search: SavedSearch) {
        guard let index = savedSearches.firstIndex(where: { $0.id == search.id }) else { return }
        savedSearches[index] = search
        persistSavedSearches()
    }

    func deleteSavedSearch(at offsets: IndexSet) {
        for index in offsets.sorted(by: >) {
            savedSearches.remove(at: index)
        }
        persistSavedSearches()
    }

    func runSavedSearch(_ saved: SavedSearch) async {
        query = saved.query
        minimumSeeders = saved.minimumSeeders
        selectedTab = .search
        await search(saved.query)
    }

    func checkSavedSearch(_ saved: SavedSearch) async {
        guard saved.isEnabled else { return }
        let outcome = await searchService.search(
            query: saved.query,
            enabledSources: enabledSources
        )
        let matches = outcome.results.filter { $0.seeders >= saved.minimumSeeders }
        let magnets = Set(matches.map(\.magnet))
        savedNewResults[saved.id] = magnets.subtracting(saved.knownMagnets).count
        var updated = saved
        updated.knownMagnets.formUnion(magnets)
        updated.lastCheckedAt = Date()
        updateSavedSearch(updated)
    }

    func setSource(_ source: TorrentSource, enabled: Bool) {
        if enabled {
            enabledSources.insert(source)
        } else {
            enabledSources.remove(source)
        }
        defaults.set(enabledSources.map(\.rawValue).sorted(), forKey: Self.sourcesKey)
    }

    func connect() async {
        let candidate = tokenDraft.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !candidate.isEmpty else {
            message = "Paste a put.io OAuth token first."
            return
        }
        isConnecting = true
        defer { isConnecting = false }
        do {
            try await putIoService.verifyToken(candidate)
            try TokenStore.save(candidate)
            token = candidate
            tokenDraft = candidate
            message = "put.io connected."
            await loadTransfers()
        } catch {
            message = error.localizedDescription
        }
    }

    func disconnect() {
        transferRefreshTask?.cancel()
        TokenStore.delete()
        token = ""
        tokenDraft = ""
        transfers = []
        files = []
        directoryStack = [(0, "Files")]
        putIoSection = .transfers
    }

    func loadPutIo() async {
        guard isConnected else { return }
        switch putIoSection {
        case .transfers:
            await loadTransfers()
        case .files:
            await loadFiles(directoryStack.last?.id ?? 0)
        }
    }

    func loadTransfers() async {
        guard isConnected else { return }
        isLoadingPutIo = true
        defer { isLoadingPutIo = false }
        do {
            transfers = try await putIoService.transfers(token: token)
            scheduleTransferRefreshIfNeeded()
        } catch {
            message = error.localizedDescription
        }
    }

    func cancelTransfer(_ transfer: PutIoTransfer) async {
        do {
            try await putIoService.cancelTransfer(token: token, id: transfer.id)
            await loadTransfers()
        } catch {
            message = error.localizedDescription
        }
    }

    func openDirectory(_ file: PutIoFile) async {
        directoryStack.append((file.id, file.name))
        await loadFiles(file.id)
    }

    func navigateUp() async {
        guard directoryStack.count > 1 else { return }
        directoryStack.removeLast()
        await loadFiles(directoryStack.last?.id ?? 0)
    }

    func loadFiles(_ parentId: Int64) async {
        guard isConnected else { return }
        isLoadingPutIo = true
        defer { isLoadingPutIo = false }
        do {
            let listing = try await putIoService.files(token: token, parentId: parentId)
            files = listing.files
        } catch {
            message = error.localizedDescription
        }
    }

    func deleteFile(_ file: PutIoFile) async {
        do {
            try await putIoService.deleteFile(token: token, id: file.id)
            await loadFiles(directoryStack.last?.id ?? 0)
        } catch {
            message = error.localizedDescription
        }
    }

    func renameFile(_ file: PutIoFile, to name: String) async {
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        do {
            try await putIoService.renameFile(token: token, id: file.id, name: trimmed)
            await loadFiles(directoryStack.last?.id ?? 0)
        } catch {
            message = error.localizedDescription
        }
    }

    func play(_ file: PutIoFile) {
        do {
            presentedVideoURL = try putIoService.hlsURL(token: token, fileId: file.id)
        } catch {
            message = error.localizedDescription
        }
    }

    func shareURL(for file: PutIoFile) -> URL? {
        try? putIoService.downloadURL(token: token, fileId: file.id)
    }

    private func scheduleTransferRefreshIfNeeded() {
        transferRefreshTask?.cancel()
        guard transfers.contains(where: \.isActive) else { return }
        transferRefreshTask = Task { [weak self] in
            try? await Task.sleep(for: .seconds(3))
            guard !Task.isCancelled else { return }
            await self?.loadTransfers()
        }
    }

    private func persistSavedSearches() {
        if let data = try? JSONEncoder().encode(savedSearches) {
            defaults.set(data, forKey: Self.savedSearchesKey)
        }
    }
}
