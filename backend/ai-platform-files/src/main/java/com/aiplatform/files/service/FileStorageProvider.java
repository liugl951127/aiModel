package com.aiplatform.files.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Storage provider abstraction. The default implementation writes to a
 * configurable local directory; production deployments can swap in an
 * S3 / MinIO / Aliyun OSS provider by registering a different bean.
 *
 * <p>Implementations MUST be thread-safe — the controller, the
 * knowledge-base ingestor, and the trainer can all hit the same
 * provider concurrently.
 */
public interface FileStorageProvider {

    /**
     * Persist a stream under {@code objectKey}. The provider owns the
     * destination path/URL — callers should not assume a 1:1 mapping
     * between {@code objectKey} and the on-disk location.
     *
     * @param objectKey logical key, e.g. {@code "kb/2025/12/abc.pdf"}
     * @param in        stream of bytes; fully consumed before returning
     * @param sizeBytes expected size (used for progress reporting; -1 if unknown)
     * @return actual bytes written
     * @throws IOException on any I/O failure
     */
    long put(String objectKey, InputStream in, long sizeBytes) throws IOException;

    /**
     * Open a stream for reading. The caller closes the stream.
     *
     * @param objectKey key returned by {@link #put}
     * @return input stream
     * @throws IOException if the object is missing or unreadable
     */
    InputStream get(String objectKey) throws IOException;

    /**
     * Delete an object. Idempotent: deleting a missing key is not an error.
     *
     * @param objectKey key to delete
     * @return true if a key was actually removed, false if it was already absent
     * @throws IOException on backend failure
     */
    boolean delete(String objectKey) throws IOException;

    /**
     * Whether this provider can serve the bytes directly via HTTP
     * (e.g. local disk under a static-resource handler). When true the
     * controller returns a relative URL the gateway can proxy.
     */
    default boolean supportsDirectHttp() {
        return false;
    }

    /**
     * The HTTP path prefix under which the object is served when
     * {@link #supportsDirectHttp()} is true. Returns {@code null} otherwise.
     */
    default String httpPathPrefix() {
        return null;
    }

    /**
     * Resolve the public download URL for an object. May be a local
     * gateway path or a pre-signed external URL.
     */
    default String resolveUrl(String objectKey) {
        if (supportsDirectHttp()) {
            return httpPathPrefix() + "/" + objectKey;
        }
        return null;
    }
}
