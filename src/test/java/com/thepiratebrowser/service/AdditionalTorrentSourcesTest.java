package com.thepiratebrowser.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.thepiratebrowser.model.TorrentResult;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AdditionalTorrentSourcesTest {
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void normalizesNyaaRss() {
        String xml = """
                <rss xmlns:nyaa="https://nyaa.si/xmlns/nyaa"><channel><item>
                  <title>Example Anime 01</title>
                  <guid>https://nyaa.si/view/123</guid>
                  <pubDate>Thu, 23 Jul 2026 19:43:47 +0000</pubDate>
                  <nyaa:seeders>42</nyaa:seeders>
                  <nyaa:leechers>7</nyaa:leechers>
                  <nyaa:infoHash>aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa</nyaa:infoHash>
                  <nyaa:category>Anime</nyaa:category>
                  <nyaa:size>1.5 GiB</nyaa:size>
                  <nyaa:trusted>Yes</nyaa:trusted>
                </item></channel></rss>
                """;

        List<TorrentResult> results = new NyaaService(HttpClient.newHttpClient())
                .parseResults(xml, 10);

        assertEquals(1, results.size());
        assertEquals("Nyaa", results.getFirst().source());
        assertEquals("nyaa:123", results.getFirst().stableId());
        assertEquals(1_610_612_736L, results.getFirst().size());
        assertFalse(Instant.EPOCH.equals(results.getFirst().added()));
    }

    @Test
    void filtersAndNormalizesEztvLatestResults() throws Exception {
        String json = """
                {"torrents":[
                  {"id":10,"hash":"BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB",
                   "title":"Example Show S01E02 1080p","seeds":25,"peers":3,
                   "size_bytes":"2048","date_released_unix":1700000000},
                  {"id":11,"hash":"CCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC",
                   "title":"Different Show S01E01","seeds":50,"peers":2,
                   "size_bytes":"1024","date_released_unix":1700000001}
                ]}
                """;

        List<TorrentResult> results = new EztvService(HttpClient.newHttpClient(), mapper)
                .parseResults(json, "example show", 5);

        assertEquals(1, results.size());
        assertEquals("EZTV", results.getFirst().source());
        assertEquals("eztv:10", results.getFirst().stableId());
    }

    @Test
    void expandsYtsMovieQualitiesIntoNormalizedRows() throws Exception {
        String json = """
                {"status":"ok","data":{"movies":[{
                  "id":77,"title":"Example Movie","title_long":"Example Movie (2026)",
                  "url":"https://yts.mx/movies/example-movie-2026",
                  "torrents":[
                    {"hash":"DDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDD",
                     "quality":"1080p","type":"web","seeds":100,"peers":20,
                     "size_bytes":4096,"date_uploaded_unix":1700000000}
                  ]
                }]}}
                """;

        List<TorrentResult> results = new YtsService(HttpClient.newHttpClient(), mapper)
                .parseResults(json, 10);

        assertEquals(1, results.size());
        assertEquals("Example Movie (2026) [1080p web]", results.getFirst().name());
        assertEquals("YTS", results.getFirst().source());
        assertEquals("Movies", results.getFirst().category());
    }

    @Test
    void normalizesBroadTorrentsCsvResults() throws Exception {
        String json = """
                {"torrents":[{
                  "id":91,
                  "infohash":"EEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEE",
                  "name":"Ubuntu Desktop",
                  "size_bytes":8192,
                  "created_unix":1700000000,
                  "seeders":80,
                  "leechers":9
                }]}
                """;

        List<TorrentResult> results =
                new TorrentsCsvService(HttpClient.newHttpClient(), mapper).parseResults(json, 10);

        assertEquals(1, results.size());
        assertEquals("Torrents.csv", results.getFirst().source());
        assertEquals("torrents-csv:91", results.getFirst().stableId());
        assertEquals(80, results.getFirst().seeders());
    }

    @Test
    void normalizesKnabenAggregatorResults() throws Exception {
        String json = """
                {"hits":[{
                  "id":"knaben-1",
                  "hash":"FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF",
                  "title":"Ubuntu Desktop",
                  "bytes":4096,
                  "seeders":75,
                  "peers":8,
                  "tracker":"1337x",
                  "category":"Software",
                  "date":"2026-07-24T00:00:00",
                  "details":"https://knaben.org/example"
                }]}
                """;

        List<TorrentResult> results =
                new KnabenService(HttpClient.newHttpClient(), mapper).parseResults(json, 10);

        assertEquals(1, results.size());
        assertEquals("Knaben", results.getFirst().source());
        assertEquals("knaben:knaben-1", results.getFirst().stableId());
        assertEquals(75, results.getFirst().seeders());
    }

    @Test
    void normalizesMagnetzResults() throws Exception {
        String json = """
                {"data":[{
                  "sqid":"magnet-1",
                  "name":"Ubuntu Desktop",
                  "info_hash":"9999999999999999999999999999999999999999",
                  "size":8192,
                  "seeders":60,
                  "leechers":4,
                  "is_verified":true,
                  "created_at":"2026-07-24T00:00:00+00:00",
                  "web_url":"https://magnetz.eu/magnet/magnet-1"
                }]}
                """;

        List<TorrentResult> results =
                new MagnetzService(HttpClient.newHttpClient(), mapper).parseResults(json, 10);

        assertEquals(1, results.size());
        assertEquals("Magnetz", results.getFirst().source());
        assertEquals("magnetz:magnet-1", results.getFirst().stableId());
        assertEquals("verified", results.getFirst().status());
    }
}
