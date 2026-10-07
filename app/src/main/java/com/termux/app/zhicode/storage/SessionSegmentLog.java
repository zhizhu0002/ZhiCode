package com.termux.app.zhicode.storage;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Append-only length-framed event segment. Payload remains JSON for migration/debug tooling. */
final class SessionSegmentLog {
    private static final int MAGIC = 0x5A4C4F47; // ZLOG
    private static final short VERSION = 1;
    private static final int MAX_FRAME_BYTES = 64 * 1024 * 1024;
    private final File file;

    SessionSegmentLog(File file) {
        this.file = file;
    }

    static final class Frame {
        final int type;
        final long sequence;
        final long timestamp;
        final JSONObject payload;

        Frame(int type, long sequence, long timestamp, JSONObject payload) {
            this.type = type;
            this.sequence = sequence;
            this.timestamp = timestamp;
            this.payload = payload;
        }
    }

    long fileLength() {
        return file.length();
    }

    synchronized void append(int type, long sequence, long timestamp, JSONObject payload) throws IOException {
        appendBatch(java.util.Collections.singletonList(new Frame(type, sequence, timestamp, payload)));
    }

    synchronized void appendBatch(List<Frame> frames) throws IOException {
        if (frames == null || frames.isEmpty()) return;
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs() && !parent.isDirectory()) {
            throw new IOException("unable to create session segment directory");
        }
        boolean fresh = !file.isFile() || file.length() == 0;
        try (FileOutputStream raw = new FileOutputStream(file, true);
             BufferedOutputStream buffered = new BufferedOutputStream(raw);
             DataOutputStream out = new DataOutputStream(buffered)) {
            if (fresh) {
                out.writeInt(MAGIC);
                out.writeShort(VERSION);
            }
            for (Frame frame : frames) {
                byte[] body = (frame.payload == null ? new JSONObject() : frame.payload).toString()
                        .getBytes(StandardCharsets.UTF_8);
                if (body.length > MAX_FRAME_BYTES) throw new IOException("session event is too large");
                out.writeInt(body.length);
                out.writeInt(frame.type);
                out.writeLong(frame.sequence);
                out.writeLong(frame.timestamp);
                out.write(body);
            }
            // Segment appends intentionally do not fsync every event. The engine already serializes
            // events, and syncing each progress/tool marker would turn a long run into a flash-write
            // loop. The next snapshot/manifest publication provides an atomic recovery point.
            out.flush();
        }
    }

    List<JSONObject> readAll() throws Exception {
        ArrayList<JSONObject> result = new ArrayList<>();
        if (!file.isFile()) return result;
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(file)))) {
            if (in.readInt() != MAGIC || in.readShort() != VERSION) throw new IOException("invalid session segment");
            long previousSequence = -1L;
            while (true) {
                int length;
                try {
                    length = in.readInt();
                } catch (EOFException end) {
                    // A process kill may leave only part of the next length prefix.
                    break;
                }
                if (length < 0 || length > MAX_FRAME_BYTES) throw new IOException("invalid session frame");
                try {
                    int type = in.readInt();
                    long sequence = in.readLong();
                    long timestamp = in.readLong();
                    if (sequence <= previousSequence) throw new IOException("session frame sequence is not increasing");
                    previousSequence = sequence;
                    byte[] body = new byte[length];
                    in.readFully(body);
                    result.add(new JSONObject(new String(body, StandardCharsets.UTF_8))
                            .put("_frame_type", type)
                            .put("_sequence", sequence)
                            .put("_timestamp", timestamp));
                } catch (EOFException truncatedTail) {
                    // Ignore only the final incomplete frame; all complete earlier events remain usable.
                    break;
                }
            }
        }
        return result;
    }
}
