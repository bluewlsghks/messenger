package com.individual.messenger.controller;
import com.individual.messenger.service.AttachmentService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.Principal;

@RestController
@RequestMapping("/api/files")
public class AttachmentController {
    private final AttachmentService files;
    public AttachmentController(AttachmentService files) { this.files = files; }
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE) @ResponseStatus(HttpStatus.CREATED)
    public AttachmentService.View upload(Principal p, @RequestParam String roomId, @RequestParam MultipartFile file) throws IOException {
        return files.upload(p, roomId, file);
    }
    @GetMapping("/{id}") public AttachmentService.View metadata(Principal p, @PathVariable String id) { return files.view(files.require(p, id)); }
    @GetMapping("/{id}/content") public ResponseEntity<InputStreamResource> download(Principal p, @PathVariable String id) throws IOException {
        var file = files.require(p, id); var info = files.view(file);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentLength(info.size())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(info.name(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff").header("Content-Security-Policy", "default-src 'none'; sandbox")
                .body(new InputStreamResource(files.stream(file)));
    }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Principal p, @PathVariable String id) { files.deletePending(p, id); }
}
