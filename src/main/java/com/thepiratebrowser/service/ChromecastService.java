package com.thepiratebrowser.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Service;

import java.awt.Desktop;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

@Service
public class ChromecastService implements DisposableBean {
    private final ObjectMapper objectMapper;
    private final ExecutorService executor;
    private HttpServer server;
    private volatile byte[] senderPage = new byte[0];

    public ChromecastService(ObjectMapper objectMapper, ExecutorService executor) {
        this.objectMapper = objectMapper;
        this.executor = executor;
    }

    public synchronized URI prepareSender(String title, String streamUrl) {
        senderPage = senderHtml(title, streamUrl).getBytes(StandardCharsets.UTF_8);
        if (server == null) {
            try {
                server = HttpServer.create(
                        new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
                server.createContext("/cast", this::serveSender);
                server.setExecutor(executor);
                server.start();
            } catch (IOException exception) {
                throw new IllegalStateException("Could not start the local Chromecast sender.", exception);
            }
        }
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/cast");
    }

    public void openSender(String title, String streamUrl) {
        URI sender = prepareSender(title, streamUrl);
        try {
            Path chrome = findChrome();
            if (chrome != null) {
                new ProcessBuilder(chrome.toString(), sender.toString()).start();
            } else if (Desktop.isDesktopSupported()
                    && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(sender);
            } else {
                throw new IllegalStateException("Open this address in Chrome: " + sender);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Could not open the Chromecast sender in Chrome.", exception);
        }
    }

    @Override
    public synchronized void destroy() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    private void serveSender(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            exchange.close();
            return;
        }
        byte[] page = senderPage;
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(200, page.length);
        exchange.getResponseBody().write(page);
        exchange.close();
    }

    private String senderHtml(String title, String streamUrl) {
        try {
            String titleJson = jsonForHtml(title);
            String urlJson = jsonForHtml(streamUrl);
            return """
                    <!doctype html>
                    <html lang="en">
                    <head>
                      <meta charset="utf-8">
                      <meta name="viewport" content="width=device-width,initial-scale=1">
                      <title>Cast from Pirate Browser</title>
                      <style>
                        body{margin:0;background:#0d1117;color:#dbe4ef;font:15px "Segoe UI",sans-serif}
                        main{max-width:620px;margin:10vh auto;padding:28px;background:#111821;
                             border:1px solid #293342;border-radius:12px}
                        h1{margin-top:0;color:#f4c95d} p{color:#9eacbd;line-height:1.5}
                        button{border:0;border-radius:6px;padding:11px 18px;background:#e7b84b;
                               color:#12161d;font-weight:700;cursor:pointer}
                        button:disabled{opacity:.45;cursor:wait} #status{margin-top:16px}
                      </style>
                      <script>
                        const mediaTitle = %s;
                        const mediaUrl = %s;
                        window.__onGCastApiAvailable = function(available) {
                          const initialize = function() {
                            const button = document.getElementById('cast');
                            if (!available) {
                              document.getElementById('status').textContent =
                                'Google Cast is not available in this browser. Open this page in Chrome.';
                              return;
                            }
                            cast.framework.CastContext.getInstance().setOptions({
                              receiverApplicationId: chrome.cast.media.DEFAULT_MEDIA_RECEIVER_APP_ID,
                              autoJoinPolicy: chrome.cast.AutoJoinPolicy.ORIGIN_SCOPED
                            });
                            button.disabled = false;
                          };
                          if (document.readyState === 'loading') {
                            document.addEventListener('DOMContentLoaded', initialize, {once:true});
                          } else {
                            initialize();
                          }
                        };
                        async function startCast() {
                          const button = document.getElementById('cast');
                          const status = document.getElementById('status');
                          button.disabled = true;
                          status.textContent = 'Choose a Chromecast…';
                          try {
                            const context = cast.framework.CastContext.getInstance();
                            await context.requestSession();
                            const mediaInfo = new chrome.cast.media.MediaInfo(
                              mediaUrl, 'application/x-mpegURL');
                            const metadata = new chrome.cast.media.GenericMediaMetadata();
                            metadata.title = mediaTitle;
                            mediaInfo.metadata = metadata;
                            await context.getCurrentSession().loadMedia(
                              new chrome.cast.media.LoadRequest(mediaInfo));
                            status.textContent = 'Casting “' + mediaTitle + '”.';
                          } catch (error) {
                            status.textContent = error && error.message
                              ? error.message : 'Casting was cancelled or failed.';
                            button.disabled = false;
                          }
                        }
                      </script>
                      <script src="https://www.gstatic.com/cv/js/sender/v1/cast_sender.js?loadCastFramework=1"></script>
                    </head>
                    <body><main>
                      <h1>Cast from Pirate Browser</h1>
                      <p id="title"></p>
                      <button id="cast" disabled onclick="startCast()">Choose Chromecast</button>
                      <p id="status">Loading Google Cast…</p>
                      <script>document.getElementById('title').textContent = mediaTitle;</script>
                    </main></body>
                    </html>
                    """.formatted(titleJson, urlJson);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not prepare Chromecast media.", exception);
        }
    }

    private String jsonForHtml(String value) throws JsonProcessingException {
        return objectMapper.writeValueAsString(value)
                .replace("<", "\\u003c")
                .replace(">", "\\u003e")
                .replace("&", "\\u0026");
    }

    private static Path findChrome() {
        List<Path> candidates = new ArrayList<>();
        addCandidate(candidates, System.getenv("LOCALAPPDATA"),
                "Google", "Chrome", "Application", "chrome.exe");
        addCandidate(candidates, System.getenv("PROGRAMFILES"),
                "Google", "Chrome", "Application", "chrome.exe");
        addCandidate(candidates, System.getenv("PROGRAMFILES(X86)"),
                "Google", "Chrome", "Application", "chrome.exe");
        return candidates.stream().filter(Files::isRegularFile).findFirst().orElse(null);
    }

    private static void addCandidate(List<Path> candidates, String root, String... parts) {
        if (root == null || root.isBlank()) {
            return;
        }
        Path candidate = Path.of(root);
        for (String part : parts) {
            candidate = candidate.resolve(part);
        }
        candidates.add(candidate);
    }
}
