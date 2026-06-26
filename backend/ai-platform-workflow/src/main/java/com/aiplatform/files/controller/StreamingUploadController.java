package com.aiplatform.files.controller;

import com.aiplatform.common.result.Result;
import com.aiplatform.files.dto.FileDtos.FileInfo;
import com.aiplatform.files.service.FileService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;

/**
 * Streaming-upload endpoint, kept separate from {@link FileController} so
 * the request body is read as a raw byte stream without multipart parsing.
 *
 * <p>URL: {@code PUT /api/files-stream/{id}}.
 */
@Slf4j
@RestController
@RequestMapping("/api/files-stream")
@RequiredArgsConstructor
public class StreamingUploadController {

    private final FileService fileService;

    /**
     * Persist the request body under the given file id. The size is taken
     * from the {@code Content-Length} header, defaulting to {@code -1}
     * (unknown) when absent — chunked transfer.
     *
     * @param id      file id returned by {@code POST /api/files/init}
     * @param request current HTTP request
     * @return metadata of the committed file
     */
    @PutMapping("/{id}")
    public Result<FileInfo> commit(@PathVariable("id") Long id, HttpServletRequest request) throws IOException {
        long size = request.getContentLengthLong();
        log.info("[FILE] stream commit fileId={} size={}", id, size);
        try (InputStream in = request.getInputStream()) {
            FileInfo info = fileService.commitStream(id, in, size);
            return Result.success(info);
        }
    }
}
