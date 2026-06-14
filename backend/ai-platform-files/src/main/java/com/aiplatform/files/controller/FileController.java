package com.aiplatform.files.controller;

import com.aiplatform.common.result.Result;
import com.aiplatform.files.dto.FileDtos.FileInfo;
import com.aiplatform.files.dto.FileDtos.InitUploadRequest;
import com.aiplatform.files.dto.FileDtos.InitUploadResponse;
import com.aiplatform.files.service.FileService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * REST surface for the file server.
 *
 * <h2>Endpoints</h2>
 * <ul>
 *   <li>{@code POST   /api/files/upload?bucket=kb}        multipart upload</li>
 *   <li>{@code POST   /api/files/init}                    init a streaming upload</li>
 *   <li>{@code PUT    /api/files-stream/{id}}             commit streaming bytes</li>
 *   <li>{@code GET    /api/files/{id}}                    metadata only</li>
 *   <li>{@code GET    /api/files/{id}/download}           stream bytes back</li>
 *   <li>{@code GET    /api/files/list?bucket=kb}          paginated listing</li>
 *   <li>{@code DELETE /api/files/{id}}                    soft-delete + remove bytes</li>
 *   <li>{@code GET    /api/files/health}                  liveness probe</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    /** Max single-shot upload size in MB. */
    @Value("${aiplatform.files.max-upload-mb:200}")
    private int maxUploadMb;

    /**
     * Multipart upload endpoint. Body must be {@code multipart/form-data}
     * with a {@code file} part and an optional {@code bucket} form field.
     */
    @PostMapping("/upload")
    public Result<FileInfo> upload(@RequestParam(value = "bucket", required = false) String bucket,
                                   @RequestPart("file") MultipartFile file) {
        log.info("[FILE] upload bucket={} name={} size={}", bucket, file.getOriginalFilename(), file.getSize());
        return Result.success(fileService.upload(bucket, file));
    }

    /**
     * Initialise a streaming upload. Returns the file id and the URL the
     * client should PUT raw bytes to.
     */
    @PostMapping("/init")
    public Result<InitUploadResponse> init(@RequestBody InitUploadRequest req) {
        return Result.success(fileService.initUpload(req));
    }

    /** Metadata lookup by id. Returns 404 if not found. */
    @GetMapping("/{id}")
    public Result<FileInfo> get(@PathVariable("id") Long id) {
        FileInfo info = fileService.get(id);
        if (info == null) {
            return Result.fail(404, "file not found: " + id);
        }
        return Result.success(info);
    }

    /**
     * Stream the bytes back to the client. Sets
     * {@code Content-Disposition: attachment; filename*=UTF-8''<name>} so
     * browsers download with the original filename.
     */
    @GetMapping("/{id}/download")
    public void download(@PathVariable("id") Long id, HttpServletResponse response) throws IOException {
        FileInfo info = fileService.get(id);
        if (info == null) {
            response.setStatus(404);
            return;
        }
        response.setContentType(info.getContentType());
        response.setContentLengthLong(info.getSizeBytes());
        String encoded = URLEncoder.encode(info.getOriginalName(), StandardCharsets.UTF_8)
                .replace("+", "%20");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encoded);
        try (InputStream in = fileService.openStream(id);
             OutputStream out = response.getOutputStream()) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
    }

    /**
     * Paginated list. {@code page} is 1-based; {@code size} defaults to 20.
     */
    @GetMapping("/list")
    public Result<List<FileInfo>> list(@RequestParam(value = "bucket", required = false) String bucket,
                                       @RequestParam(value = "page", defaultValue = "1") int page,
                                       @RequestParam(value = "size", defaultValue = "20") int size) {
        return Result.success(fileService.list(bucket, Math.max(1, page), Math.min(100, size)));
    }

    /** Soft-delete. The bytes are removed from the provider as well. */
    @DeleteMapping("/{id}")
    public Result<Boolean> delete(@PathVariable("id") Long id) throws IOException {
        return Result.success(fileService.delete(id));
    }

    /** Liveness probe for k8s / actuator. */
    @GetMapping("/health")
    public Result<String> health() {
        return Result.success("files OK");
    }
}
