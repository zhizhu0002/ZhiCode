package com.termux.app.zhicode.storage;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;

/** Atomic checkpoint files used to avoid replaying every old event on resume. */
final class SessionSnapshotStore {
    private static final int MAX_SNAPSHOT_BYTES = 32 * 1024 * 1024;
    private static final int MAX_RETAINED_SNAPSHOTS = 3;
    private final File directory;

    SessionSnapshotStore(File sessionDirectory) {
        directory = new File(sessionDirectory, "snapshots");
    }

    File write(long sequence, JSONArray messages) throws Exception {
        if (messages == null) messages = new JSONArray();
        JSONObject snapshot = new JSONObject()
                .put("version", 1)
                .put("sequence", sequence)
                .put("messages", messages);
        byte[] bytes = snapshot.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (bytes.length > MAX_SNAPSHOT_BYTES) throw new IOException("session snapshot is too large");
        File target = new File(directory, String.format(java.util.Locale.US, "%020d.snap", sequence));
        AtomicFiles.publish(target, bytes);
        pruneOldSnapshots(target);
        return target;
    }

    private void pruneOldSnapshots(File newest) {
        File[] files = directory.listFiles((dir, name) -> name.endsWith(".snap"));
        if (files == null || files.length <= MAX_RETAINED_SNAPSHOTS) return;
        java.util.Arrays.sort(files, (a, b) -> Long.compare(parseSequence(b.getName()), parseSequence(a.getName())));
        for (int i = MAX_RETAINED_SNAPSHOTS; i < files.length; i++) {
            if (!files[i].equals(newest)) files[i].delete();
        }
    }

    JSONObject readLatest() throws Exception {
        File[] files = directory.listFiles((dir, name) -> name.endsWith(".snap"));
        if (files == null || files.length == 0) return null;
        File latest = null;
        long latestSequence = -1L;
        for (File file : files) {
            long sequence = parseSequence(file.getName());
            if (sequence < 0L) continue;
            if (sequence > latestSequence) {
                latestSequence = sequence;
                latest = file;
            }
        }
        if (latest == null) return null;
        JSONObject snapshot = new JSONObject(new String(AtomicFiles.readBytes(latest, MAX_SNAPSHOT_BYTES),
                java.nio.charset.StandardCharsets.UTF_8));
        if (snapshot.optLong("sequence", -1L) != latestSequence) {
            throw new IOException("session snapshot sequence mismatch");
        }
        return snapshot;
    }

    private static long parseSequence(String name) {
        if (name == null || !name.endsWith(".snap")) return -1L;
        try {
            return Long.parseLong(name.substring(0, name.length() - 5));
        } catch (NumberFormatException invalid) {
            return -1L;
        }
    }
}
