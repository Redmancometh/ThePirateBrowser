package com.thepiratebrowser.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TorrentResultTest {
    @Test
    void createsPutIoReadyMagnetUri() {
        TorrentResult result = new TorrentResult(
                "1",
                "Ubuntu 24.04 Desktop",
                "4A3F5E08BCEF825718EDA30637230585E3330599",
                100,
                10,
                1,
                "uploader",
                "trusted",
                "303",
                Instant.EPOCH,
                false
        );

        String magnet = result.magnetUri();

        assertTrue(magnet.startsWith(
                "magnet:?xt=urn:btih:4A3F5E08BCEF825718EDA30637230585E3330599"));
        assertTrue(magnet.contains("dn=Ubuntu+24.04+Desktop"));
        assertTrue(magnet.contains("&tr="));
    }
}
