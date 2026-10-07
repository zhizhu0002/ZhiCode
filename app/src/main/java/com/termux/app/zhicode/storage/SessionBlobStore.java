package com.termux.app.zhicode.storage;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Content-addressed storage for large session output and attachments. */
final class SessionBlobStore {
    private static final int MAX_BLOB_BYTES = 64 * 1024 * 1024;
    private final File root;

    SessionBlobStore(File sessionDirectory) {
        root = new File(sessionDirectory, "blobs");
    }

    String putText(String text) throws IOException {
        byte[] data = (text == null ? "" : text).getBytes(StandardCharsets.UTF_8);
        return put(data);
    }

    String put(byte[] data) throws IOException {
        if (data == null) data = new byte[0];
        if (data.length > MAX_BLOB_BYTES) throw new IOException("session blob exceeds size limit");
        String hash = sha256(data);
        File target = fileFor(hash);
        if (target.isFile()) {
            if (target.length() == data.length) return hash;
            if (!target.delete()) throw new IOException("conflicting session blob");
        }
        File parent = target.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs() && !parent.isDirectory()) {
            throw new IOException("unable to create blob directory");
        }
        AtomicFiles.publish(target, data);
        return hash;
    }

    byte[] get(String hash) throws IOException {
        if (hash == null || !hash.matches("[0-9a-f]{64}")) throw new IOException("invalid blob hash");
        File file = fileFor(hash);
        byte[] data = AtomicFiles.readBytes(file, MAX_BLOB_BYTES);
        if (!sha256(data).equals(hash)) throw new IOException("session blob checksum mismatch");
        return data;
    }

    private File fileFor(String hash) {
        return new File(new File(new File(root, hash.substring(0, 2)), hash.substring(2, 4)), hash);
    }

    private static String sha256(byte[] data) throws IOException {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(data);
            StringBuilder result = new StringBuilder(64);
            for (byte value : digest) result.append(String.format("%02x", value & 255));
            return result.toString();
        } catch (Exception e) {
            throw new IOException("unable to hash session blob", e);
        }
    }
}
