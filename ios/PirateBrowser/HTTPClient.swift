import Foundation

enum HTTPError: LocalizedError {
    case invalidURL
    case invalidResponse
    case rejected(Int, String)

    var errorDescription: String? {
        switch self {
        case .invalidURL:
            "The service address is invalid."
        case .invalidResponse:
            "The service returned an unreadable response."
        case let .rejected(status, message):
            message.isEmpty ? "The service returned HTTP \(status)." : message
        }
    }
}

enum HTTPClient {
    static func data(for request: URLRequest) async throws -> Data {
        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse else {
            throw HTTPError.invalidResponse
        }
        guard (200..<300).contains(http.statusCode) else {
            let message = String(data: data, encoding: .utf8) ?? ""
            throw HTTPError.rejected(http.statusCode, sanitized(message))
        }
        return data
    }

    static func get(_ url: URL, bearerToken: String? = nil) async throws -> Data {
        var request = URLRequest(url: url)
        request.timeoutInterval = 20
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue("PirateBrowser-iOS/1.0", forHTTPHeaderField: "User-Agent")
        if let bearerToken, !bearerToken.isEmpty {
            request.setValue("Bearer \(bearerToken)", forHTTPHeaderField: "Authorization")
        }
        return try await data(for: request)
    }

    static func postJSON(_ url: URL, object: Any) async throws -> Data {
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.timeoutInterval = 20
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue("PirateBrowser-iOS/1.0", forHTTPHeaderField: "User-Agent")
        request.httpBody = try JSONSerialization.data(withJSONObject: object)
        return try await data(for: request)
    }

    static func postForm(
        _ url: URL,
        token: String,
        values: [String: String]
    ) async throws -> Data {
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.timeoutInterval = 30
        request.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        var components = URLComponents()
        components.queryItems = values.map { URLQueryItem(name: $0.key, value: $0.value) }
        request.httpBody = components.percentEncodedQuery?.data(using: .utf8)
        return try await data(for: request)
    }

    static func jsonObject(from data: Data) throws -> [String: Any] {
        guard let root = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw HTTPError.invalidResponse
        }
        return root
    }

    private static func sanitized(_ value: String) -> String {
        value
            .replacingOccurrences(
                of: #"(?i)(oauth_token(?:%3D|=))[^&\s"]+"#,
                with: "$1[REDACTED]",
                options: .regularExpression
            )
            .replacingOccurrences(
                of: #"(?i)(Bearer\s+)[A-Za-z0-9._~+/=-]+"#,
                with: "$1[REDACTED]",
                options: .regularExpression
            )
    }
}
