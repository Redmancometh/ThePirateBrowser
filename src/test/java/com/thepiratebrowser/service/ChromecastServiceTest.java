package com.thepiratebrowser.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChromecastServiceTest {
    @Test
    void servesEscapedDefaultReceiverSenderPageOnLoopback() throws Exception {
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        ChromecastService service = new ChromecastService(new ObjectMapper(), executor);
        try {
            var sender = service.prepareSender(
                    "</script><script>alert(1)</script>",
                    "https://api.put.io/video.m3u8?oauth_token=test");
            HttpResponse<String> response = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(sender).GET().build(),
                    HttpResponse.BodyHandlers.ofString());

            assertEquals("127.0.0.1", sender.getHost());
            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("DEFAULT_MEDIA_RECEIVER_APP_ID"));
            assertTrue(response.body().contains("application/x-mpegURL"));
            assertTrue(response.body().contains("\\u003c/script\\u003e"));
            assertFalse(response.body().contains("</script><script>alert(1)</script>"));
        } finally {
            service.destroy();
            executor.shutdownNow();
        }
    }
}
