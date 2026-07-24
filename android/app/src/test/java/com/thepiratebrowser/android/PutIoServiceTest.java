package com.thepiratebrowser.android;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PutIoServiceTest {
    @Test
    public void parsesTransferControlsAndTerminalState() throws Exception {
        String json = """
                {
                  "status":"OK",
                  "transfers":[
                    {"id":11,"file_id":22,"name":"Active","status":"DOWNLOADING",
                     "percent_done":45,"error_message":""},
                    {"id":12,"file_id":23,"name":"Ready","status":"SEEDING",
                     "percent_done":100,"error_message":""}
                  ]
                }
                """;

        var transfers = PutIoService.parseTransfers(json);

        assertEquals(2, transfers.size());
        assertEquals(11, transfers.get(0).id);
        assertFalse(transfers.get(0).isDone());
        assertTrue(transfers.get(1).isDone());
    }

    @Test
    public void parsesFileNavigationAndCapabilities() throws Exception {
        String json = """
                {
                  "status":"OK",
                  "parent":{"id":7,"parent_id":3,"name":"Shows"},
                  "files":[
                    {"id":8,"parent_id":7,"name":"Season 1",
                     "content_type":"application/x-directory","file_type":"FOLDER"},
                    {"id":9,"parent_id":7,"name":"Episode.mp4","size":1048576,
                     "content_type":"video/mp4","file_type":"VIDEO",
                     "is_mp4_available":true,"need_convert":false}
                  ]
                }
                """;

        PutIoService.FileListing listing = PutIoService.parseFiles(json, 7);

        assertEquals(7, listing.directoryId);
        assertEquals(3, listing.parentDirectoryId);
        assertEquals("Shows", listing.directoryName);
        assertTrue(listing.files.get(0).isDirectory());
        assertTrue(listing.files.get(1).isVideo());
        assertEquals(1_048_576, listing.files.get(1).size);
    }
}
