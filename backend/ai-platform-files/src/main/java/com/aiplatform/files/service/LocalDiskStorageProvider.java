package com.aiplatform.files.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Default {@link FileStorageProvider} implementation. Writes to a configurable
 * local directory and exposes the contents at a static HTTP path so the
 * gateway can serve them without bouncing through the JVM.
 *
 * <p>Layout: {@code <root>/<bucket>/yyyy/MM/<uuid>-<original>}.
 *
 * <p>Activated automatically when no other {@link FileStorageProvider} bean
 * is present ({@link ConditionalOnMissingBean}).
 */
@Slf4j
@Component
public class LocalDiskStorageProvider implements FileStorageProvider {

    /** Root directory for all stored objects. */
    @Value("${aiplatform.files.root:/opt/ai-platform/files}")
    private String root;

    /** HTTP path prefix the gateway proxies. */
    @Value("${aiplatform.files.http-path:/files}")
    private String httpPath;

    private Path rootPath;
    private final AtomicLong bytesWritten = new AtomicLong();

    @PostConstruct
    public void init() throws IOException {
        this.rootPath = Paths.get(root).toAbsolutePath();
        Files.createDirectories(rootPath);
        log.info("[FILE-STORE] local disk provider: root={} httpPath={}", rootPath, httpPath);
    }

    /**
     * Persist {@code in} under {@code objectKey}. The stream is fully consumed
     * and the file is committed atomically via a temp-file rename.
     *
     * @return number of bytes written
     */
    @Override
    public long put(String objectKey, InputStream in, long sizeBytes) throws IOException {
        Path target = rootPath.resolve(objectKey).normalize();
        if (!target.startsWith(rootPath)) {
            throw new IOException("objectKey escapes the storage root: " + objectKey);
        }
        Files.createDirectories(target.getParent());
        Path tmp = Files.createTempFile(target.getParent(), ".upload-", ".part");
        long written;
        try (OutputStream out = Files.newOutputStream(tmp, StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING)) {
            written = copy(in, out);
        }
        Files.move(tmp, target,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        bytesWritten.addAndGet(written);
        log.info("[FILE-STORE] wrote {} bytes -> {}", written, target);
        return written;
    }

    @Override
    public InputStream get(String objectKey) throws IOException {
        Path target = rootPath.resolve(objectKey).normalize();
        if (!target.startsWith(rootPath)) {
            throw new IOException("objectKey escapes the storage root: " + objectKey);
        }
        return Files.newInputStream(target);
    }

    @Override
    public boolean delete(String objectKey) throws IOException {
        Path target = rootPath.resolve(objectKey).normalize();
        if (!target.startsWith(rootPath)) {
            return false;
        }
        return Files.deleteIfExists(target);
    }

    @Override
    public boolean supportsDirectHttp() {
        return true;
    }

    @Override
    public String httpPathPrefix() {
        return httpPath;
    }

    @Override
    public String resolveUrl(String objectKey) {
        return httpPath + "/" + objectKey;
    }

    /** Bytes written since JVM start (observability). */
    public long bytesWritten() { return bytesWritten.get(); }

    private static long copy(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[64 * 1024];
        long total = 0;
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
            total += n;
        }
        return total;
    }
}
