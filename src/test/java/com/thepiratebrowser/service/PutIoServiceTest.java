package com.thepiratebrowser.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.thepiratebrowser.model.TorrentResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PutIoServiceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void sendsExactBearerAndEncodedMagnetAndParsesAllSuccessPayloads() throws Exception {
        HttpServer server = server();
        AtomicReference<String> addBody = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> accountAuthorization = new AtomicReference<>();
        server.createContext("/v2/transfers/add", exchange -> {
            assertEquals("POST", exchange.getRequestMethod());
            assertEquals("application/x-www-form-urlencoded", exchange.getRequestHeaders()
                    .getFirst("Content-Type"));
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            addBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, 200, """
                    {"status":"OK","transfer":{"id":42,"name":"Ubuntu transfer"}}
                    """);
        });
        server.createContext("/v2/transfers/list", exchange -> respond(exchange, 200, """
                {"status":"OK","transfers":[
                  {"id":42,"file_id":0,"name":"Ubuntu transfer","status":"DOWNLOADING","percent_done":25,
                   "error_message":null}
                ]}
                """));
        server.createContext("/v2/files/list", exchange -> {
            assertEquals("parent_id=0&per_page=1000&mp4_status=true",
                    exchange.getRequestURI().getQuery());
            respond(exchange, 200, """
                    {"status":"OK","parent":{"id":0,"parent_id":0,"name":"Your Files"},
                     "files":[{"id":99,"parent_id":0,"name":"Movie.mkv",
                     "content_type":"video/x-matroska","file_type":"VIDEO","size":1234,
                     "is_mp4_available":true,"need_convert":false}]}
                    """);
        });
        server.createContext("/v2/account/info", exchange -> {
            accountAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            respond(exchange, 200, """
                    {"status":"OK","info":{"username":"local-user"}}
                    """);
        });
        server.createContext("/v2/oauth2/oob/code", exchange -> {
            if (exchange.getRequestURI().getPath().endsWith("/ABC123")) {
                respond(exchange, 200, """
                        {"oauth_token":"linked-token"}
                        """);
                return;
            }
            assertTrue(exchange.getRequestURI().getQuery().contains("app_id=1234"));
            assertTrue(exchange.getRequestURI().getQuery()
                    .contains("client_name=Pirate+Browser"));
            respond(exchange, 200, """
                    {"code":"ABC123","qr_code_url":"https://example.test/qr/ABC123"}
                    """);
        });
        server.start();
        try {
            PutIoService service = service(server);
            TorrentResult torrent = torrent();

            assertTrue(service.browserHandoffUrl(torrent)
                    .startsWith("https://put.io/default/magnet?url=magnet%3A%3F"));
            assertEquals("ABC123", service.requestLinkCode("1234").code());
            assertEquals("linked-token", service.pollLinkToken("ABC123").orElseThrow());
            assertEquals("Ubuntu transfer", service.add(torrent));
            assertEquals("Bearer test-token", authorization.get());
            assertEquals("url=" + URLEncoder.encode(torrent.magnetUri(), StandardCharsets.UTF_8),
                    addBody.get());
            assertEquals("local-user", service.validateAccount());
            assertEquals("Bearer test-token", accountAuthorization.get());
            assertEquals("local-user", service.validateAccount("  wizard-token  "));
            assertEquals("Bearer wizard-token", accountAuthorization.get());
            assertEquals(1, service.transfers().size());
            assertEquals(25, service.transfers().getFirst().percentDone());
            assertEquals(0, service.transfers().getFirst().fileId());
            assertEquals(false, service.transfers().getFirst().isDone());
            assertEquals("Movie.mkv", service.files(0).files().getFirst().name());
            assertTrue(service.files(0).files().getFirst().isVideo());
            assertEquals("Your Files", service.files(0).directoryName());
            assertEquals("http://127.0.0.1:" + server.getAddress().getPort()
                            + "/v2/files/99/hls/media.m3u8"
                            + "?subtitle_key=all&oauth_token=test-token",
                    service.hlsStreamUrl(99));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void preservesJsonApiErrorAndNonJsonHttpStatus() throws Exception {
        HttpServer server = server();
        server.createContext("/v2/account/info", exchange ->
                respond(exchange, 401, """
                        {"status":"ERROR","error_message":"Token rejected"}
                        """));
        server.createContext("/v2/transfers/list", exchange ->
                respond(exchange, 503, "temporarily unavailable"));
        server.start();
        try {
            PutIoService service = service(server);

            IllegalStateException rejected =
                    assertThrows(IllegalStateException.class, service::validateAccount);
            assertTrue(rejected.getMessage().contains("Token rejected"));

            IllegalStateException unavailable =
                    assertThrows(IllegalStateException.class, service::transfers);
            assertTrue(unavailable.getMessage().contains("HTTP 503"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void rejectsSuccessfulStatusWithMissingEndpointPayload() throws Exception {
        HttpServer server = server();
        server.createContext("/v2/transfers/add", exchange ->
                respond(exchange, 200, "{" + "\"status\":\"OK\"" + "}"));
        server.start();
        try {
            IllegalStateException missing =
                    assertThrows(IllegalStateException.class, () -> service(server).add(torrent()));
            assertTrue(missing.getMessage().contains("missing transfer"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void rejectsNonCanonicalSuccessStatus() throws Exception {
        HttpServer server = server();
        server.createContext("/v2/account/info", exchange ->
                respond(exchange, 200, """
                        {"status":"ok","info":{"username":"wrong-contract"}}
                        """));
        server.start();
        try {
            IllegalStateException invalid =
                    assertThrows(IllegalStateException.class, () -> service(server).validateAccount());
            assertTrue(invalid.getMessage().contains("Unexpected response status"));
        } finally {
            server.stop(0);
        }
    }

    private PutIoService service(HttpServer server) {
        String original = System.getProperty("piratebrowser.dataDir");
        try {
            System.setProperty("piratebrowser.dataDir", temporaryDirectory.toString());
            ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
            LocalSettingsService settings = new LocalSettingsService(mapper);
            settings.get().setPutIoToken("  test-token  ");
            return new PutIoService(
                    HttpClient.newHttpClient(),
                    mapper,
                    settings,
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/v2");
        } finally {
            if (original == null) {
                System.clearProperty("piratebrowser.dataDir");
            } else {
                System.setProperty("piratebrowser.dataDir", original);
            }
        }
    }

    private static HttpServer server() throws IOException {
        return HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    }

    private static TorrentResult torrent() {
        return new TorrentResult(
                "1",
                "Ubuntu 24.04",
                "4A3F5E08BCEF825718EDA30637230585E3330599",
                100,
                10,
                1,
                "tester",
                "trusted",
                "303",
                Instant.EPOCH,
                false);
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
