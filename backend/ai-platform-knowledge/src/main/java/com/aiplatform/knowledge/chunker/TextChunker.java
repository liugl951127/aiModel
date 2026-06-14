package com.aiplatform.knowledge.chunker;

import org.apache.tika.Tika;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Extract plain text from a file using Apache Tika, then split into overlapping chunks
 * suitable for embedding / ES indexing.
 */
@Component
public class TextChunker {

    private static final int CHUNK_SIZE = 500;
    private static final int CHUNK_OVERLAP = 80;

    private final Tika tika = new Tika();

    public String extract(File file) throws IOException {
        try (InputStream in = Files.newInputStream(file.toPath())) {
            try {
                return tika.parseToString(in);
            } catch (Exception e) {
                throw new IOException("Tika extract failed: " + e.getMessage(), e);
            }
        }
    }

    public List<String> chunk(String text) {
        if (text == null || text.isBlank()) return List.of();
        String clean = text.replaceAll("\\s+", " ").trim();
        List<String> out = new ArrayList<>();
        int n = clean.length();
        int i = 0;
        while (i < n) {
            int end = Math.min(i + CHUNK_SIZE, n);
            // break on punctuation if possible to avoid mid-word slices
            if (end < n) {
                int dot = clean.lastIndexOf('。', end);
                int dotEn = clean.lastIndexOf('.', end);
                int best = Math.max(dot, dotEn);
                if (best > i + CHUNK_SIZE / 2) {
                    end = best + 1;
                }
            }
            out.add(clean.substring(i, end));
            if (end >= n) break;
            i = end - CHUNK_OVERLAP;
        }
        return out;
    }
}
