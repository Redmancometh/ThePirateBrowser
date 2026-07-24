import AVKit
import SwiftUI

private let pirateNavy = Color(red: 0.055, green: 0.09, blue: 0.15)
private let pirateSurface = Color(red: 0.09, green: 0.15, blue: 0.24)
private let pirateGold = Color(red: 0.95, green: 0.72, blue: 0.28)

struct RootView: View {
    @EnvironmentObject private var store: AppStore
    @AppStorage("putio.first_run_prompt_seen") private var firstRunPromptSeen = false
    @State private var showFirstRunPrompt = false

    var body: some View {
        TabView(selection: $store.selectedTab) {
            SearchScreen()
                .tabItem { Label("Search", systemImage: "magnifyingglass") }
                .tag(AppTab.search)
            SavedSearchesScreen()
                .tabItem { Label("Saved", systemImage: "bookmark") }
                .tag(AppTab.saved)
            PutIoScreen()
                .tabItem { Label("put.io", systemImage: "arrow.down.circle") }
                .tag(AppTab.putio)
            SourcesScreen()
                .tabItem { Label("Sources", systemImage: "slider.horizontal.3") }
                .tag(AppTab.sources)
        }
        .tint(pirateGold)
        .alert("Connect put.io?", isPresented: $showFirstRunPrompt) {
            Button("Skip for now", role: .cancel) {}
            Button("Set up put.io") { store.selectedTab = .putio }
        } message: {
            Text("Pirate Browser works without an OAuth token. Connect one to manage transfers and files inside the app.")
        }
        .alert(
            "Pirate Browser",
            isPresented: Binding(
                get: { store.message != nil },
                set: { if !$0 { store.message = nil } }
            )
        ) {
            Button("OK") { store.message = nil }
        } message: {
            Text(store.message ?? "")
        }
        .sheet(
            isPresented: Binding(
                get: { store.presentedVideoURL != nil },
                set: { if !$0 { store.presentedVideoURL = nil } }
            )
        ) {
            if let url = store.presentedVideoURL {
                NavigationStack {
                    VideoPlayerView(url: url)
                        .ignoresSafeArea(edges: .bottom)
                        .navigationTitle("Now Playing")
                        .navigationBarTitleDisplayMode(.inline)
                        .toolbar {
                            ToolbarItem(placement: .confirmationAction) {
                                Button("Done") { store.presentedVideoURL = nil }
                            }
                        }
                }
            }
        }
        .task {
            if !store.isConnected && !firstRunPromptSeen {
                firstRunPromptSeen = true
                showFirstRunPrompt = true
            }
        }
    }
}

struct PirateHeader: View {
    @EnvironmentObject private var store: AppStore

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: "sailboat.fill")
                .font(.title2)
                .foregroundStyle(pirateGold)
                .frame(width: 44, height: 44)
                .background(pirateSurface, in: RoundedRectangle(cornerRadius: 12))
            VStack(alignment: .leading, spacing: 2) {
                Text("Pirate Browser")
                    .font(.headline)
                Text("One search. Every source.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            Spacer()
            Text("CANARY \(store.canary)")
                .font(.system(.caption2, design: .monospaced, weight: .bold))
                .foregroundStyle(pirateGold)
                .padding(.horizontal, 9)
                .padding(.vertical, 6)
                .background(pirateGold.opacity(0.12), in: Capsule())
                .accessibilityLabel("Pirate Browser build canary \(store.canary)")
        }
        .padding(.horizontal)
        .padding(.top, 6)
    }
}

struct SearchScreen: View {
    @EnvironmentObject private var store: AppStore
    @State private var showSave = false
    @State private var savedName = ""

    var body: some View {
        NavigationStack {
            ScrollView {
                LazyVStack(spacing: 14) {
                    PirateHeader()
                    VStack(alignment: .leading, spacing: 12) {
                        Text("SET A COURSE")
                            .font(.caption.bold())
                            .foregroundStyle(pirateGold)
                        TextField("Movie, show, or anime", text: $store.query)
                            .textFieldStyle(.roundedBorder)
                            .textInputAutocapitalization(.never)
                            .submitLabel(.search)
                            .onSubmit { Task { await store.search() } }
                        Stepper(
                            "Minimum seeders: \(store.minimumSeeders)",
                            value: $store.minimumSeeders,
                            in: 0...10_000
                        )
                        Button {
                            Task { await store.search() }
                        } label: {
                            if store.isSearching {
                                ProgressView().frame(maxWidth: .infinity)
                            } else {
                                Label(
                                    "Search \(store.enabledSources.count) sources",
                                    systemImage: "magnifyingglass"
                                )
                                .frame(maxWidth: .infinity)
                            }
                        }
                        .buttonStyle(PiratePrimaryButtonStyle())
                        .disabled(store.isSearching)

                        Button("Save this search") {
                            savedName = store.query
                            showSave = true
                        }
                        .disabled(store.query.trimmingCharacters(in: .whitespaces).isEmpty)
                    }
                    .pirateCard()
                    .padding(.horizontal)

                    if !store.searchFailures.isEmpty {
                        VStack(alignment: .leading, spacing: 4) {
                            Text("Some sources did not answer")
                                .font(.subheadline.bold())
                            ForEach(store.searchFailures, id: \.self) {
                                Text($0).font(.caption).foregroundStyle(.secondary)
                            }
                        }
                        .pirateCard()
                        .padding(.horizontal)
                    }

                    if !store.isSearching && store.results.isEmpty {
                        ContentUnavailableView(
                            "Nothing in the nets yet",
                            systemImage: "sailboat",
                            description: Text("Search every enabled source at once.")
                        )
                        .padding(.top, 30)
                    } else {
                        ForEach(store.results) { result in
                            TorrentResultCard(result: result)
                                .padding(.horizontal)
                        }
                    }
                }
                .padding(.bottom, 20)
            }
            .background(pirateNavy.opacity(0.04))
            .navigationBarHidden(true)
        }
        .sheet(isPresented: $showSave) {
            NavigationStack {
                Form {
                    TextField("Saved search name", text: $savedName)
                    LabeledContent("Query", value: store.query)
                    LabeledContent("Minimum seeders", value: "\(store.minimumSeeders)")
                }
                .navigationTitle("Save Search")
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("Cancel") { showSave = false }
                    }
                    ToolbarItem(placement: .confirmationAction) {
                        Button("Save") {
                            store.saveCurrentSearch(name: savedName)
                            showSave = false
                        }
                    }
                }
            }
            .presentationDetents([.medium])
        }
    }
}

struct TorrentResultCard: View {
    @EnvironmentObject private var store: AppStore
    let result: TorrentResult

    var body: some View {
        VStack(alignment: .leading, spacing: 9) {
            HStack {
                Text(result.source.rawValue.uppercased())
                    .font(.caption2.bold())
                    .foregroundStyle(pirateGold)
                Spacer()
                Text("\(result.seeders) seeds")
                    .font(.caption.bold())
                    .foregroundStyle(.green)
            }
            Text(result.name).font(.headline)
            Text(result.metadata).font(.caption).foregroundStyle(.secondary)
            HStack {
                Button {
                    Task { await store.addToPutIo(result) }
                } label: {
                    Label(
                        store.addedMagnets.contains(result.magnet) ? "Added" : "Add",
                        systemImage: "arrow.down.circle"
                    )
                }
                .buttonStyle(.borderedProminent)
                .tint(pirateGold)
                .disabled(store.addedMagnets.contains(result.magnet))
                ShareLink(item: result.magnet) {
                    Label("Share", systemImage: "square.and.arrow.up")
                }
                .buttonStyle(.bordered)
            }
        }
        .pirateCard()
    }
}

struct SavedSearchesScreen: View {
    @EnvironmentObject private var store: AppStore
    @State private var editing: SavedSearch?

    var body: some View {
        NavigationStack {
            List {
                Section {
                    PirateHeader()
                        .listRowInsets(EdgeInsets())
                        .listRowBackground(Color.clear)
                }
                if store.savedSearches.isEmpty {
                    ContentUnavailableView(
                        "No saved searches",
                        systemImage: "bookmark",
                        description: Text("Save a search from the Search tab.")
                    )
                } else {
                    ForEach(store.savedSearches) { saved in
                        VStack(alignment: .leading, spacing: 8) {
                            HStack {
                                VStack(alignment: .leading) {
                                    Text(saved.name).font(.headline)
                                    Text(saved.query).font(.subheadline).foregroundStyle(.secondary)
                                }
                                Spacer()
                                if let count = store.savedNewResults[saved.id], count > 0 {
                                    Text("\(count) new")
                                        .font(.caption.bold())
                                        .padding(6)
                                        .background(pirateGold.opacity(0.18), in: Capsule())
                                }
                            }
                            Text("Minimum \(saved.minimumSeeders) seeders")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                            HStack {
                                Button("Run") { Task { await store.runSavedSearch(saved) } }
                                Button("Check") { Task { await store.checkSavedSearch(saved) } }
                                Button("Edit") { editing = saved }
                                Spacer()
                                Toggle(
                                    "Enabled",
                                    isOn: Binding(
                                        get: { saved.isEnabled },
                                        set: {
                                            var changed = saved
                                            changed.isEnabled = $0
                                            store.updateSavedSearch(changed)
                                        }
                                    )
                                )
                                .labelsHidden()
                            }
                            .buttonStyle(.bordered)
                        }
                        .padding(.vertical, 6)
                    }
                    .onDelete(perform: store.deleteSavedSearch)
                }
            }
            .navigationTitle("Saved Searches")
        }
        .sheet(item: $editing) { saved in
            SavedSearchEditor(search: saved)
        }
    }
}

struct SavedSearchEditor: View {
    @EnvironmentObject private var store: AppStore
    @Environment(\.dismiss) private var dismiss
    @State var search: SavedSearch

    var body: some View {
        NavigationStack {
            Form {
                TextField("Name", text: $search.name)
                TextField("Query", text: $search.query)
                Stepper(
                    "Minimum seeders: \(search.minimumSeeders)",
                    value: $search.minimumSeeders,
                    in: 0...10_000
                )
                Toggle("Monitoring enabled", isOn: $search.isEnabled)
            }
            .navigationTitle("Edit Saved Search")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        store.updateSavedSearch(search)
                        dismiss()
                    }
                }
            }
        }
    }
}

struct SourcesScreen: View {
    @EnvironmentObject private var store: AppStore

    var body: some View {
        NavigationStack {
            List {
                Section {
                    PirateHeader()
                        .listRowInsets(EdgeInsets())
                        .listRowBackground(Color.clear)
                }
                Section("Torrent sources") {
                    ForEach(TorrentSource.allCases) { source in
                        Toggle(
                            isOn: Binding(
                                get: { store.enabledSources.contains(source) },
                                set: { store.setSource(source, enabled: $0) }
                            )
                        ) {
                            VStack(alignment: .leading) {
                                Text(source.rawValue)
                                Text(source.summary)
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                        }
                    }
                }
            }
            .navigationTitle("Sources")
        }
    }
}

struct PutIoScreen: View {
    @EnvironmentObject private var store: AppStore
    @State private var showWizard = false
    @State private var renameTarget: PutIoFile?
    @State private var renameText = ""
    @State private var deleteTarget: PutIoFile?

    var body: some View {
        NavigationStack {
            VStack(spacing: 12) {
                PirateHeader()
                if store.isConnected {
                    connectedContent
                } else {
                    connectionContent
                }
            }
            .navigationBarHidden(true)
            .task { await store.loadPutIo() }
        }
        .sheet(isPresented: $showWizard) {
            PutIoWizard()
        }
        .alert("Rename file", isPresented: Binding(
            get: { renameTarget != nil },
            set: { if !$0 { renameTarget = nil } }
        )) {
            TextField("File name", text: $renameText)
            Button("Cancel", role: .cancel) { renameTarget = nil }
            Button("Rename") {
                if let file = renameTarget {
                    Task { await store.renameFile(file, to: renameText) }
                }
                renameTarget = nil
            }
        }
        .confirmationDialog(
            "Delete this item from put.io?",
            isPresented: Binding(
                get: { deleteTarget != nil },
                set: { if !$0 { deleteTarget = nil } }
            ),
            titleVisibility: .visible
        ) {
            Button("Delete", role: .destructive) {
                if let file = deleteTarget {
                    Task { await store.deleteFile(file) }
                }
                deleteTarget = nil
            }
            Button("Cancel", role: .cancel) { deleteTarget = nil }
        }
    }

    private var connectionContent: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                Text("CONNECT PUT.IO").font(.caption.bold()).foregroundStyle(pirateGold)
                Text("Paste your OAuth token")
                    .font(.title2.bold())
                Text("Manual token entry is the default. Your token is stored only in this iPhone’s Keychain.")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                SecureField("OAuth token", text: $store.tokenDraft)
                    .textFieldStyle(.roundedBorder)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                Button {
                    Task { await store.connect() }
                } label: {
                    if store.isConnecting {
                        ProgressView().frame(maxWidth: .infinity)
                    } else {
                        Text("Test and save token").frame(maxWidth: .infinity)
                    }
                }
                .buttonStyle(PiratePrimaryButtonStyle())
                .disabled(store.isConnecting)
                Divider()
                Text("Need a token?").font(.headline)
                Button("Use the setup wizard") { showWizard = true }
                    .buttonStyle(.bordered)
            }
            .pirateCard()
            .padding()
        }
    }

    private var connectedContent: some View {
        VStack(spacing: 10) {
            HStack {
                Picker("put.io section", selection: $store.putIoSection) {
                    ForEach(PutIoSection.allCases) { Text($0.rawValue).tag($0) }
                }
                .pickerStyle(.segmented)
                .onChange(of: store.putIoSection) {
                    Task { await store.loadPutIo() }
                }
                Button {
                    Task { await store.loadPutIo() }
                } label: {
                    Image(systemName: "arrow.clockwise")
                }
                .buttonStyle(.bordered)
            }
            .padding(.horizontal)

            if store.isLoadingPutIo && store.transfers.isEmpty && store.files.isEmpty {
                Spacer()
                ProgressView()
                Spacer()
            } else if store.putIoSection == .transfers {
                transfersList
            } else {
                filesList
            }

            Button("Disconnect put.io", role: .destructive) { store.disconnect() }
                .padding(.bottom, 8)
        }
    }

    private var transfersList: some View {
        List(store.transfers) { transfer in
            VStack(alignment: .leading, spacing: 6) {
                Text(transfer.name).font(.headline)
                HStack {
                    Text(transfer.status.replacingOccurrences(of: "_", with: " ").capitalized)
                    Spacer()
                    Text("\(Int(transfer.percentDone))%")
                }
                .font(.caption)
                .foregroundStyle(.secondary)
                ProgressView(value: min(100, max(0, transfer.percentDone)), total: 100)
                if transfer.isActive {
                    Button("Cancel transfer", role: .destructive) {
                        Task { await store.cancelTransfer(transfer) }
                    }
                    .font(.caption)
                }
            }
            .padding(.vertical, 4)
        }
        .overlay {
            if store.transfers.isEmpty {
                ContentUnavailableView("No transfers", systemImage: "arrow.down.circle")
            }
        }
    }

    private var filesList: some View {
        List {
            if store.directoryStack.count > 1 {
                Button {
                    Task { await store.navigateUp() }
                } label: {
                    Label("Back from \(store.currentDirectoryName)", systemImage: "arrow.up.left")
                }
            }
            ForEach(store.files) { file in
                HStack(spacing: 12) {
                    Image(systemName: file.isDirectory ? "folder.fill" : "doc.fill")
                        .foregroundStyle(file.isDirectory ? pirateGold : Color.secondary)
                    VStack(alignment: .leading) {
                        Text(file.name)
                        if !file.isDirectory {
                            Text(ByteCountFormatter.string(fromByteCount: file.size, countStyle: .file))
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                    }
                    Spacer()
                    if file.isDirectory {
                        Button {
                            Task { await store.openDirectory(file) }
                        } label: {
                            Image(systemName: "chevron.right")
                        }
                    } else {
                        Menu {
                            if file.isPlayable {
                                Button {
                                    store.play(file)
                                } label: {
                                    Label("Play / AirPlay", systemImage: "play.rectangle")
                                }
                            }
                            if let url = store.shareURL(for: file) {
                                ShareLink(item: url) {
                                    Label("Share", systemImage: "square.and.arrow.up")
                                }
                            }
                            Button {
                                renameTarget = file
                                renameText = file.name
                            } label: {
                                Label("Rename", systemImage: "pencil")
                            }
                            Button(role: .destructive) {
                                deleteTarget = file
                            } label: {
                                Label("Delete", systemImage: "trash")
                            }
                        } label: {
                            Image(systemName: "ellipsis.circle")
                        }
                    }
                }
                .contentShape(Rectangle())
                .onTapGesture {
                    if file.isDirectory {
                        Task { await store.openDirectory(file) }
                    } else if file.isPlayable {
                        store.play(file)
                    }
                }
            }
        }
        .overlay {
            if store.files.isEmpty {
                ContentUnavailableView("No files here", systemImage: "folder")
            }
        }
    }
}

struct PutIoWizard: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.openURL) private var openURL

    var body: some View {
        NavigationStack {
            List {
                Section("1. Open put.io API") {
                    Text("Sign in, create an application, and open its Secrets page.")
                    Button("Open put.io API") {
                        openURL(URL(string: "https://app.put.io/oauth")!)
                    }
                }
                Section("2. Create a personal token") {
                    Text("Use put.io’s OAuth tools to create a token for your own account. Pirate Browser does not ship with a private client secret.")
                }
                Section("3. Paste and test") {
                    Text("Return to Pirate Browser, paste the token into the default field, then tap Test and save token.")
                }
            }
            .navigationTitle("put.io Setup")
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
        }
    }
}

struct VideoPlayerView: UIViewControllerRepresentable {
    let url: URL

    func makeUIViewController(context: Context) -> AVPlayerViewController {
        let controller = AVPlayerViewController()
        let player = AVPlayer(url: url)
        player.allowsExternalPlayback = true
        controller.allowsPictureInPicturePlayback = true
        controller.player = player
        player.play()
        return controller
    }

    func updateUIViewController(_ controller: AVPlayerViewController, context: Context) {}

    static func dismantleUIViewController(
        _ controller: AVPlayerViewController,
        coordinator: ()
    ) {
        controller.player?.pause()
        controller.player = nil
    }
}

struct PiratePrimaryButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.headline)
            .foregroundStyle(pirateNavy)
            .padding(.vertical, 12)
            .padding(.horizontal, 16)
            .background(
                configuration.isPressed ? pirateGold.opacity(0.75) : pirateGold,
                in: RoundedRectangle(cornerRadius: 12)
            )
    }
}

private extension View {
    func pirateCard() -> some View {
        padding(16)
            .background(pirateSurface.opacity(0.08), in: RoundedRectangle(cornerRadius: 18))
            .overlay(
                RoundedRectangle(cornerRadius: 18)
                    .stroke(pirateGold.opacity(0.14), lineWidth: 1)
            )
    }
}
