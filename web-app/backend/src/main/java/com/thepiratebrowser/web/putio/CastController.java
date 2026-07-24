package com.thepiratebrowser.web.putio;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.UUID;

@RestController
@RequestMapping("/api/cast")
public class CastController {
    private final CastGrantService grants;
    private final PutIoService putio;

    public CastController(CastGrantService grants, PutIoService putio) {
        this.grants = grants;
        this.putio = putio;
    }

    @GetMapping("/{token}")
    public ResponseEntity<StreamingResponseBody> content(
            @PathVariable UUID token,
            HttpServletRequest request
    ) {
        long fileId = grants.requireFile(token);
        PutIoService.RemoteContent remote = putio.content(fileId, request.getHeader("Range"));
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CONTENT_TYPE, remote.contentType());
        headers.set(HttpHeaders.ACCEPT_RANGES, remote.acceptRanges());
        headers.set(HttpHeaders.CACHE_CONTROL, "private, no-store");
        if (remote.contentLength() != null) {
            headers.set(HttpHeaders.CONTENT_LENGTH, remote.contentLength());
        }
        if (remote.contentRange() != null) {
            headers.set(HttpHeaders.CONTENT_RANGE, remote.contentRange());
        }
        StreamingResponseBody body = output -> {
            try (var input = remote.body()) {
                input.transferTo(output);
            }
        };
        return new ResponseEntity<>(body, headers, HttpStatus.valueOf(remote.status()));
    }
}
