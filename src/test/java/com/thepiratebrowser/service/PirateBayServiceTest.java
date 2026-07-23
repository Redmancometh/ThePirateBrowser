package com.thepiratebrowser.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.thepiratebrowser.model.TorrentResult;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class PirateBayServiceTest {
    @Test
    void parsesFiltersSortsAndDecodesApiResults() throws Exception {
        String json = """
                [
                  {
                    "id":"10",
                    "name":"Lower &amp; Older",
                    "info_hash":"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
                    "leechers":"2",
                    "seeders":"5",
                    "size":"1024",
                    "username":"one",
                    "added":"1700000000",
                    "status":"vip",
                    "category":"200"
                  },
                  {
                    "id":"11",
                    "name":"Best Result",
                    "info_hash":"BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB",
                    "leechers":"1",
                    "seeders":"20",
                    "size":"2048",
                    "username":"two",
                    "added":"1700000001",
                    "status":"trusted",
                    "category":"200"
                  },
                  {
                    "id":"12",
                    "name":"Filtered",
                    "info_hash":"CCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC",
                    "leechers":"0",
                    "seeders":"1",
                    "size":"512",
                    "username":"three",
                    "added":"1700000002",
                    "status":"member",
                    "category":"200"
                  }
                ]
                """;
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        PirateBayService service = new PirateBayService(HttpClient.newHttpClient(), mapper, null);

        List<TorrentResult> results = service.parseResults(json, 5);

        assertEquals(2, results.size());
        assertEquals("Best Result", results.getFirst().name());
        assertEquals("Lower & Older", results.getLast().name());
        assertFalse(results.getFirst().newMatch());
    }
}
