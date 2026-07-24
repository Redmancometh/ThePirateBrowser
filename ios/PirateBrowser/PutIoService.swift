import Foundation

struct PutIoService {
    private let apiBase = URL(string: "https://api.put.io/v2")!

    func verifyToken(_ token: String) async throws {
        _ = try await HTTPClient.get(
            apiBase.appending(path: "account/info"),
            bearerToken: required(token)
        )
    }

    func addTransfer(token: String, magnet: String) async throws {
        _ = try await HTTPClient.postForm(
            apiBase.appending(path: "transfers/add"),
            token: required(token),
            values: ["url": magnet]
        )
    }

    func transfers(token: String) async throws -> [PutIoTransfer] {
        let data = try await HTTPClient.get(
            apiBase.appending(path: "transfers/list"),
            bearerToken: required(token)
        )
        let root = try HTTPClient.jsonObject(from: data)
        return (root["transfers"] as? [[String: Any]] ?? []).map { item in
            PutIoTransfer(
                id: number(item["id"]),
                name: text(item["name"], fallback: "Unnamed transfer"),
                status: text(item["status"], fallback: "UNKNOWN"),
                percentDone: decimal(item["percent_done"]),
                size: number(item["size"]),
                fileId: optionalNumber(item["file_id"])
            )
        }
    }

    func cancelTransfer(token: String, id: Int64) async throws {
        _ = try await HTTPClient.postForm(
            apiBase.appending(path: "transfers/cancel"),
            token: required(token),
            values: ["transfer_ids": String(id)]
        )
    }

    func files(token: String, parentId: Int64) async throws -> PutIoFileListing {
        var components = URLComponents(
            url: apiBase.appending(path: "files/list"),
            resolvingAgainstBaseURL: false
        )!
        components.queryItems = [URLQueryItem(name: "parent_id", value: String(parentId))]
        guard let url = components.url else { throw HTTPError.invalidURL }
        let data = try await HTTPClient.get(url, bearerToken: required(token))
        let root = try HTTPClient.jsonObject(from: data)
        let parent = (root["parent"] as? [String: Any]).map { number($0["id"]) } ?? parentId
        let items = (root["files"] as? [[String: Any]] ?? []).map { item in
            let contentType = text(item["content_type"])
            return PutIoFile(
                id: number(item["id"]),
                name: text(item["name"], fallback: "Unnamed file"),
                size: number(item["size"]),
                contentType: contentType,
                isDirectory: contentType == "application/x-directory"
                    || text(item["file_type"]).uppercased() == "FOLDER"
            )
        }
        return PutIoFileListing(
            parentId: parent,
            files: items.sorted {
                if $0.isDirectory != $1.isDirectory { return $0.isDirectory }
                return $0.name.localizedCaseInsensitiveCompare($1.name) == .orderedAscending
            }
        )
    }

    func deleteFile(token: String, id: Int64) async throws {
        _ = try await HTTPClient.postForm(
            apiBase.appending(path: "files/delete"),
            token: required(token),
            values: ["file_ids": String(id)]
        )
    }

    func renameFile(token: String, id: Int64, name: String) async throws {
        _ = try await HTTPClient.postForm(
            apiBase.appending(path: "files/rename"),
            token: required(token),
            values: ["file_id": String(id), "name": name]
        )
    }

    func hlsURL(token: String, fileId: Int64) throws -> URL {
        var components = URLComponents(
            url: apiBase.appending(path: "files/\(fileId)/hls/media.m3u8"),
            resolvingAgainstBaseURL: false
        )!
        components.queryItems = [
            URLQueryItem(name: "subtitle_key", value: "all"),
            URLQueryItem(name: "oauth_token", value: try required(token))
        ]
        guard let url = components.url else { throw HTTPError.invalidURL }
        return url
    }

    func downloadURL(token: String, fileId: Int64) throws -> URL {
        var components = URLComponents(
            url: apiBase.appending(path: "files/\(fileId)/download"),
            resolvingAgainstBaseURL: false
        )!
        components.queryItems = [
            URLQueryItem(name: "oauth_token", value: try required(token))
        ]
        guard let url = components.url else { throw HTTPError.invalidURL }
        return url
    }

    func requestDeviceCode(clientId: String) async throws -> PutIoDeviceCode {
        let clientId = try required(clientId)
        var components = URLComponents(
            string: "https://api.put.io/v2/oauth2/oob/code"
        )!
        components.queryItems = [URLQueryItem(name: "app_id", value: clientId)]
        guard let url = components.url else { throw HTTPError.invalidURL }
        let root = try HTTPClient.jsonObject(from: try await HTTPClient.get(url))
        return PutIoDeviceCode(
            code: text(root["code"]),
            expiresIn: Int(number(root["expires_in"])),
            interval: max(1, Int(number(root["interval"])))
        )
    }

    func deviceToken(clientId: String, code: String) async throws -> String? {
        var components = URLComponents(
            string: "https://api.put.io/v2/oauth2/oob/code/\(code)"
        )!
        components.queryItems = [
            URLQueryItem(name: "app_id", value: try required(clientId))
        ]
        guard let url = components.url else { throw HTTPError.invalidURL }
        do {
            let root = try HTTPClient.jsonObject(from: try await HTTPClient.get(url))
            let token = text(root["oauth_token"])
            return token.isEmpty ? nil : token
        } catch HTTPError.rejected(let status, _) where status == 400 || status == 404 {
            return nil
        }
    }

    private func required(_ value: String) throws -> String {
        let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            throw HTTPError.rejected(400, "A put.io OAuth token is required.")
        }
        return trimmed
    }
}

private func text(_ value: Any?, fallback: String = "") -> String {
    if let value = value as? String, !value.isEmpty { return value }
    if let value = value as? NSNumber { return value.stringValue }
    return fallback
}

private func number(_ value: Any?) -> Int64 {
    if let value = value as? NSNumber { return value.int64Value }
    return Int64(text(value)) ?? 0
}

private func optionalNumber(_ value: Any?) -> Int64? {
    let result = number(value)
    return result > 0 ? result : nil
}

private func decimal(_ value: Any?) -> Double {
    if let value = value as? NSNumber { return value.doubleValue }
    return Double(text(value)) ?? 0
}
