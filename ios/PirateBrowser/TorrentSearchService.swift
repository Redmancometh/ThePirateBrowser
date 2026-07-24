import Foundation

struct TorrentSearchService {
    func search(query: String, enabledSources: Set<TorrentSource>) async -> SearchOutcome {
        await withTaskGroup(
            of: (TorrentSource, Result<[TorrentResult], Error>).self,
            returning: SearchOutcome.self
        ) { group in
            for source in enabledSources {
                group.addTask {
                    do {
                        return (source, .success(try await search(source, query: query)))
                    } catch {
                        return (source, .failure(error))
                    }
                }
            }

            var combined: [TorrentResult] = []
            var failures: [String] = []
            for await (source, result) in group {
                switch result {
                case let .success(items):
                    combined.append(contentsOf: items)
                case let .failure(error):
                    failures.append("\(source.rawValue): \(error.localizedDescription)")
                }
            }

            var deduplicated: [String: TorrentResult] = [:]
            for result in combined {
                let key = infoHash(from: result.magnet) ?? result.magnet.lowercased()
                if deduplicated[key, default: result].seeders <= result.seeders {
                    deduplicated[key] = result
                }
            }
            let results = deduplicated.values.sorted { $0.seeders > $1.seeders }
            return SearchOutcome(results: results, failures: failures.sorted())
        }
    }

    private func search(_ source: TorrentSource, query: String) async throws -> [TorrentResult] {
        switch source {
        case .pirateBay:
            try await pirateBay(query)
        case .knaben:
            try await knaben(query)
        case .magnetz:
            try await magnetz(query)
        case .torrentsCsv:
            try await torrentsCsv(query)
        case .nyaa:
            try await nyaa(query)
        case .eztv:
            try await eztv(query)
        case .yts:
            try await yts(query)
        }
    }

    private func pirateBay(_ query: String) async throws -> [TorrentResult] {
        let data = try await HTTPClient.get(try url(
            "https://apibay.org/q.php",
            queryItems: [URLQueryItem(name: "q", value: query)]
        ))
        guard let items = try JSONSerialization.jsonObject(with: data) as? [[String: Any]] else {
            throw HTTPError.invalidResponse
        }
        return items.compactMap { item in
            let hash = string(item["info_hash"])
            guard !hash.isEmpty, string(item["id"]) != "0" else { return nil }
            let name = string(item["name"], fallback: "Untitled torrent")
            return TorrentResult(
                name: name,
                source: .pirateBay,
                magnet: magnet(hash: hash, name: name),
                size: int64(item["size"]),
                seeders: int(item["seeders"]),
                leechers: int(item["leechers"])
            )
        }
    }

    private func knaben(_ query: String) async throws -> [TorrentResult] {
        let data = try await HTTPClient.postJSON(
            URL(string: "https://api.knaben.org/v1")!,
            object: [
                "search_type": "100%",
                "search_field": "title",
                "query": query,
                "order_by": "seeders",
                "order_direction": "desc",
                "from": 0,
                "size": 150,
                "hide_unsafe": true,
                "hide_xxx": true
            ]
        )
        let root = try HTTPClient.jsonObject(from: data)
        let hits = root["hits"] as? [[String: Any]] ?? []
        return hits.compactMap { item in
            let hash = string(item["hash"])
            guard !hash.isEmpty else { return nil }
            let name = string(item["title"], fallback: "Untitled torrent")
            return TorrentResult(
                name: name,
                source: .knaben,
                magnet: magnet(hash: hash, name: name),
                size: int64(item["bytes"]),
                seeders: int(item["seeders"]),
                leechers: int(item["peers"])
            )
        }
    }

    private func magnetz(_ query: String) async throws -> [TorrentResult] {
        let data = try await HTTPClient.get(try url(
            "https://magnetz.eu/api/magnets/search",
            queryItems: [
                URLQueryItem(name: "query", value: query),
                URLQueryItem(name: "page", value: "1")
            ]
        ))
        let root = try HTTPClient.jsonObject(from: data)
        let items = root["data"] as? [[String: Any]] ?? []
        return items.compactMap { item in
            let hash = string(item["info_hash"])
            guard !hash.isEmpty else { return nil }
            let name = string(item["name"], fallback: "Untitled torrent")
            return TorrentResult(
                name: name,
                source: .magnetz,
                magnet: magnet(hash: hash, name: name),
                size: int64(item["size"]),
                seeders: int(item["seeders"]),
                leechers: int(item["leechers"])
            )
        }
    }

    private func torrentsCsv(_ query: String) async throws -> [TorrentResult] {
        let data = try await HTTPClient.get(try url(
            "https://torrents-csv.com/service/search",
            queryItems: [
                URLQueryItem(name: "q", value: query),
                URLQueryItem(name: "size", value: "50"),
                URLQueryItem(name: "page", value: "1")
            ]
        ))
        let root = try HTTPClient.jsonObject(from: data)
        let items = root["torrents"] as? [[String: Any]] ?? []
        return items.compactMap { item in
            let hash = string(item["infohash"])
            guard !hash.isEmpty else { return nil }
            let name = string(item["name"], fallback: "Untitled torrent")
            return TorrentResult(
                name: name,
                source: .torrentsCsv,
                magnet: magnet(hash: hash, name: name),
                size: int64(item["size_bytes"]),
                seeders: int(item["seeders"]),
                leechers: int(item["leechers"])
            )
        }
    }

    private func nyaa(_ query: String) async throws -> [TorrentResult] {
        let data = try await HTTPClient.get(try url(
            "https://nyaa.si/",
            queryItems: [
                URLQueryItem(name: "page", value: "rss"),
                URLQueryItem(name: "q", value: query)
            ]
        ))
        let delegate = NyaaRSSDelegate()
        let parser = XMLParser(data: data)
        parser.delegate = delegate
        guard parser.parse() else {
            throw parser.parserError ?? HTTPError.invalidResponse
        }
        return delegate.items
    }

    private func eztv(_ query: String) async throws -> [TorrentResult] {
        let data = try await HTTPClient.get(try url(
            "https://eztvx.to/api/get-torrents",
            queryItems: [
                URLQueryItem(name: "limit", value: "100"),
                URLQueryItem(name: "page", value: "1")
            ]
        ))
        let root = try HTTPClient.jsonObject(from: data)
        let normalized = query.lowercased()
        return (root["torrents"] as? [[String: Any]] ?? []).compactMap { item in
            let name = string(item["filename"])
            guard name.lowercased().contains(normalized) else { return nil }
            return TorrentResult(
                name: name,
                source: .eztv,
                magnet: string(item["magnet_url"]),
                size: int64(item["size_bytes"]),
                seeders: int(item["seeds"]),
                leechers: int(item["peers"])
            )
        }
    }

    private func yts(_ query: String) async throws -> [TorrentResult] {
        let data = try await HTTPClient.get(try url(
            "https://movies-api.accel.li/api/v2/list_movies.json",
            queryItems: [
                URLQueryItem(name: "limit", value: "50"),
                URLQueryItem(name: "query_term", value: query)
            ]
        ))
        let root = try HTTPClient.jsonObject(from: data)
        let movies = (root["data"] as? [String: Any])?["movies"] as? [[String: Any]] ?? []
        return movies.flatMap { movie -> [TorrentResult] in
            let title = string(movie["title_long"], fallback: string(movie["title"]))
            return (movie["torrents"] as? [[String: Any]] ?? []).compactMap { torrent in
                let hash = string(torrent["hash"])
                guard !hash.isEmpty else { return nil }
                let quality = string(torrent["quality"])
                let name = quality.isEmpty ? title : "\(title) \(quality)"
                return TorrentResult(
                    name: name,
                    source: .yts,
                    magnet: magnet(hash: hash, name: name),
                    size: int64(torrent["size_bytes"]),
                    seeders: int(torrent["seeds"]),
                    leechers: int(torrent["peers"])
                )
            }
        }
    }

    private func url(_ address: String, queryItems: [URLQueryItem]) throws -> URL {
        guard var components = URLComponents(string: address) else {
            throw HTTPError.invalidURL
        }
        components.queryItems = queryItems
        guard let result = components.url else {
            throw HTTPError.invalidURL
        }
        return result
    }

    func magnet(hash: String, name: String) -> String {
        var allowed = CharacterSet.urlQueryAllowed
        allowed.remove(charactersIn: "&=+")
        let encodedName = name.addingPercentEncoding(withAllowedCharacters: allowed) ?? name
        return "magnet:?xt=urn:btih:\(hash)&dn=\(encodedName)"
    }

    func infoHash(from magnet: String) -> String? {
        guard let range = magnet.range(
            of: #"(?i)btih:([a-z0-9]{32,40})"#,
            options: .regularExpression
        ) else {
            return nil
        }
        return String(magnet[range]).dropFirst(5).lowercased()
    }
}

private final class NyaaRSSDelegate: NSObject, XMLParserDelegate {
    private var field = ""
    private var current: [String: String] = [:]
    private var text = ""
    var items: [TorrentResult] = []

    func parser(
        _ parser: XMLParser,
        didStartElement elementName: String,
        namespaceURI: String?,
        qualifiedName qName: String?,
        attributes attributeDict: [String: String] = [:]
    ) {
        field = normalized(qName ?? elementName)
        text = ""
        if field == "item" {
            current = [:]
        }
    }

    func parser(_ parser: XMLParser, foundCharacters string: String) {
        text += string
    }

    func parser(
        _ parser: XMLParser,
        didEndElement elementName: String,
        namespaceURI: String?,
        qualifiedName qName: String?
    ) {
        let closing = normalized(qName ?? elementName)
        let value = text.trimmingCharacters(in: .whitespacesAndNewlines)
        if closing == "item" {
            appendCurrent()
        } else if !value.isEmpty {
            current[closing] = value
        }
        field = ""
        text = ""
    }

    private func appendCurrent() {
        let hash = current["infohash"] ?? ""
        guard !hash.isEmpty else { return }
        let name = current["title"] ?? "Untitled torrent"
        let service = TorrentSearchService()
        items.append(TorrentResult(
            name: name,
            source: .nyaa,
            magnet: service.magnet(hash: hash, name: name),
            size: parseSize(current["size"] ?? ""),
            seeders: Int(current["seeders"] ?? "") ?? 0,
            leechers: Int(current["leechers"] ?? "") ?? 0
        ))
    }

    private func normalized(_ value: String) -> String {
        value.split(separator: ":").last.map(String.init)?.lowercased() ?? value.lowercased()
    }

    private func parseSize(_ value: String) -> Int64 {
        let parts = value.split(separator: " ")
        guard let number = parts.first.flatMap({ Double($0) }) else { return 0 }
        let unit = parts.dropFirst().first?.lowercased() ?? ""
        let multiplier: Double
        switch unit {
        case "kib", "kb": multiplier = 1_024
        case "mib", "mb": multiplier = 1_024 * 1_024
        case "gib", "gb": multiplier = 1_024 * 1_024 * 1_024
        case "tib", "tb": multiplier = 1_024 * 1_024 * 1_024 * 1_024
        default: multiplier = 1
        }
        return Int64(number * multiplier)
    }
}

private func string(_ value: Any?, fallback: String = "") -> String {
    if let value = value as? String, !value.isEmpty { return value }
    if let value = value as? NSNumber { return value.stringValue }
    return fallback
}

private func int(_ value: Any?) -> Int {
    if let value = value as? NSNumber { return value.intValue }
    return Int(string(value)) ?? 0
}

private func int64(_ value: Any?) -> Int64 {
    if let value = value as? NSNumber { return value.int64Value }
    return Int64(string(value)) ?? 0
}
