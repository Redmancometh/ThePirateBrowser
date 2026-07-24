package com.thepiratebrowser.web.putio;

import com.thepiratebrowser.web.audit.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.List;
import java.util.Map;
import java.time.Instant;

@RestController
@RequestMapping("/api/putio")
public class PutIoController {
    private final PutIoService putio;
    private final AuditService audit;
    private final CastGrantService castGrants;

    public PutIoController(
            PutIoService putio,
            AuditService audit,
            CastGrantService castGrants
    ) {
        this.putio = putio;
        this.audit = audit;
        this.castGrants = castGrants;
    }

    @GetMapping("/status")
    public Map<String, Boolean> status() {
        return Map.of("configured", putio.configured());
    }

    @PostMapping("/transfers")
    public ResponseEntity<Void> addTransfer(
            Authentication authentication,
            @RequestBody MagnetRequest request
    ) {
        putio.addTransfer(request.magnet());
        audit.record(authentication.getName(), "TRANSFER_ADD", "transfer", null, null);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @GetMapping("/transfers")
    public List<PutIoService.TransferView> transfers() {
        return putio.transfers();
    }

    @DeleteMapping("/transfers/{id}")
    public ResponseEntity<Void> cancelTransfer(
            Authentication authentication,
            @PathVariable long id
    ) {
        putio.cancelTransfer(id);
        audit.record(authentication.getName(), "TRANSFER_CANCEL", "transfer", id, null);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/files/{parentId}")
    public PutIoService.FileListing files(@PathVariable long parentId) {
        return putio.files(parentId);
    }

    @PatchMapping("/files/{id}")
    public PutIoService.FileListing renameFile(
            Authentication authentication,
            @PathVariable long id,
            @RequestBody RenameRequest request
    ) {
        putio.renameFile(id, request.name());
        audit.record(authentication.getName(), "FILE_RENAME", "file", id, null);
        return putio.files(request.parentId());
    }

    @DeleteMapping("/files/{id}")
    public ResponseEntity<Void> deleteFile(
            Authentication authentication,
            @PathVariable long id
    ) {
        putio.deleteFile(id);
        audit.record(authentication.getName(), "FILE_DELETE", "file", id, null);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/files/{id}/content")
    public ResponseEntity<StreamingResponseBody> content(
            @PathVariable long id,
            HttpServletRequest request
    ) {
        PutIoService.RemoteContent remote = putio.content(id, request.getHeader("Range"));
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

    @PostMapping("/files/{id}/cast")
    public CastGrantView cast(
            Authentication authentication,
            @PathVariable long id
    ) {
        CastGrant grant = castGrants.create(id, authentication.getName());
        audit.record(authentication.getName(), "CAST_GRANT_CREATE", "file", id, null);
        return new CastGrantView(
                "/api/cast/" + grant.getToken(),
                grant.getExpiresAt()
        );
    }

    public record MagnetRequest(String magnet) {
    }

    public record RenameRequest(String name, long parentId) {
    }

    public record CastGrantView(String url, Instant expiresAt) {
    }
}
