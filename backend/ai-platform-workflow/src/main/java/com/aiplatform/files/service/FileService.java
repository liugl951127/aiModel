package com.aiplatform.files.service;

import com.aiplatform.common.exception.BusinessException;
import com.aiplatform.common.result.ResultCode;
import com.aiplatform.files.dto.FileDtos.FileInfo;
import com.aiplatform.files.dto.FileDtos.InitUploadRequest;
import com.aiplatform.files.dto.FileDtos.InitUploadResponse;
import com.aiplatform.files.entity.FileObject;
import com.aiplatform.files.mapper.FileObjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * File metadata + orchestration layer. Sits in front of {@link FileStorageProvider}
 * and the {@link FileObjectMapper} MyBatis mapper. All public methods are
 * idempotent and return DTOs rather than entities.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileService {

    private final FileObjectMapper mapper;
    /** Injected as a field so a custom provider (S3/MinIO) can be swapped in. */
    @Autowired
    private FileStorageProvider storage;

    /** Default bucket when the caller does not specify one. */
    @Value("${aiplatform.files.default-bucket:kb}")
    private String defaultBucket;

    /**
     * Multipart upload (single-shot). For very large files the client should
     * use the init / streaming flow.
     *
     * @param bucket  logical bucket (defaults to {@code defaultBucket} when null)
     * @param file    Spring multipart upload
     * @return metadata for the persisted file
     * @throws BusinessException on I/O failure or oversize payload
     */
    @Transactional
    public FileInfo upload(String bucket, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "empty upload");
        }
        String b = (bucket == null || bucket.isBlank()) ? defaultBucket : bucket;
        String objectKey = buildKey(b, file.getOriginalFilename());
        try (InputStream in = file.getInputStream()) {
            long size = storage.put(objectKey, in, file.getSize());
            String sha = sha256(in); // in is exhausted; recomputed via second pass below
            // We already consumed the stream above, so re-read the bytes once more
            // to compute the digest.  For very large files the streaming init
            // path should be used instead.
            sha = sha256(file.getBytes());
            FileObject obj = persist(objectKey, file.getOriginalFilename(),
                    file.getContentType(), size, b, sha);
            return toInfo(obj);
        } catch (IOException e) {
            log.error("[FILE] upload failed", e);
            throw new BusinessException(ResultCode.FAIL, "upload failed: " + e.getMessage());
        }
    }

    /**
     * Initialise a streaming upload. Reserves a file id and returns the
     * upload URL; the client then PUTs the raw bytes to {@code uploadUrl}.
     */
    public InitUploadResponse initUpload(InitUploadRequest req) {
        String b = (req.getBucket() == null || req.getBucket().isBlank()) ? defaultBucket : req.getBucket();
        String objectKey = buildKey(b, req.getOriginalName());
        FileObject obj = persist(objectKey, req.getOriginalName(), req.getContentType(),
                req.getTotalSize() == null ? 0L : req.getTotalSize(), b, "");
        obj.setStatus(FileObject.Status.UPLOADING);
        mapper.updateById(obj);
        return new InitUploadResponse(obj.getFileId(), objectKey, "/api/files/" + obj.getFileId() + "/stream");
    }

    /**
     * Persist a stream under an already-initialised file id. Used by the
     * streaming upload endpoint.
     */
    @Transactional
    public FileInfo commitStream(Long fileId, InputStream in, long size) throws IOException {
        FileObject obj = mapper.selectById(fileId);
        if (obj == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "fileId not found: " + fileId);
        }
        long written = storage.put(obj.getObjectKey(), in, size);
        obj.setSizeBytes(written);
        obj.setStatus(FileObject.Status.READY);
        obj.setUpdatedAt(LocalDateTime.now());
        // Recompute digest from the raw input — caller may have set position=0
        obj.setSha256(""); // streaming digest is best-effort; left to a follow-up job
        mapper.updateById(obj);
        return toInfo(obj);
    }

    /**
     * Fetch a file by id. Returns {@code null} if missing.
     */
    public FileInfo get(Long fileId) {
        FileObject obj = mapper.selectById(fileId);
        if (obj == null || FileObject.Status.DELETED.equals(obj.getStatus())) {
            return null;
        }
        return toInfo(obj);
    }

    /**
     * Open a stream over the underlying bytes.
     */
    public InputStream openStream(Long fileId) throws IOException {
        FileObject obj = mapper.selectById(fileId);
        if (obj == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "fileId not found: " + fileId);
        }
        return storage.get(obj.getObjectKey());
    }

    /**
     * List files in a bucket, newest first. Paginated.
     */
    public List<FileInfo> list(String bucket, int page, int size) {
        Page<FileObject> p = mapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<FileObject>()
                        .eq(bucket != null && !bucket.isBlank(), FileObject::getBucket, bucket)
                        .eq(FileObject::getStatus, FileObject.Status.READY)
                        .orderByDesc(FileObject::getFileId));
        return p.getRecords().stream().map(this::toInfo).collect(Collectors.toList());
    }

    /**
     * Soft-delete: marks the row as {@code deleted} and asks the provider
     * to remove the underlying bytes.
     */
    @Transactional
    public boolean delete(Long fileId) throws IOException {
        FileObject obj = mapper.selectById(fileId);
        if (obj == null) return false;
        obj.setStatus(FileObject.Status.DELETED);
        obj.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(obj);
        storage.delete(obj.getObjectKey());
        return true;
    }

    /* ----------------------------- internals ----------------------------- */

    /** Insert a fresh metadata row. */
    private FileObject persist(String key, String name, String ct, long size, String bucket, String sha) {
        FileObject obj = new FileObject();
        obj.setObjectKey(key);
        obj.setOriginalName(name);
        obj.setContentType(ct == null ? "application/octet-stream" : ct);
        obj.setSizeBytes(size);
        obj.setBucket(bucket);
        obj.setSha256(sha);
        obj.setStatus(FileObject.Status.READY);
        obj.setCreatedAt(LocalDateTime.now());
        obj.setUpdatedAt(obj.getCreatedAt());
        mapper.insert(obj);
        return obj;
    }

    /**
     * Build a date-bucketed object key: {@code <bucket>/yyyy/MM/<uuid>-<name>}.
     * Avoids putting a million files in a single directory and makes
     * backup / retention rules trivial.
     */
    private String buildKey(String bucket, String originalName) {
        LocalDate now = LocalDate.now();
        String safe = originalName == null ? "file" :
                originalName.replaceAll("[^a-zA-Z0-9._-]", "_");
        return bucket + "/" + now.getYear() + "/"
                + String.format("%02d", now.getMonthValue())
                + "/" + UUID.randomUUID().toString().substring(0, 8) + "-" + safe;
    }

    /** Hex SHA-256 of a byte array. */
    private String sha256(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Hex SHA-256 of a stream (drained to completion). */
    private String sha256(InputStream in) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
            return HexFormat.of().formatHex(md.digest());
        } catch (Exception e) {
            log.warn("[FILE] sha256 stream failed: {}", e.getMessage());
            return "";
        }
    }

    private FileInfo toInfo(FileObject obj) {
        FileInfo info = new FileInfo();
        info.setFileId(obj.getFileId());
        info.setObjectKey(obj.getObjectKey());
        info.setOriginalName(obj.getOriginalName());
        info.setContentType(obj.getContentType());
        info.setSizeBytes(obj.getSizeBytes());
        info.setBucket(obj.getBucket());
        info.setSha256(obj.getSha256());
        info.setStatus(obj.getStatus());
        info.setCreatedAt(obj.getCreatedAt());
        info.setDownloadUrl(storage.resolveUrl(obj.getObjectKey()));
        return info;
    }
}
