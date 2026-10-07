package com.termux.app.zhicode.storage;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.termux.shared.termux.TermuxConstants;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Small global index for session list rendering.
 *
 * <p>The conversation payload never lives here. This database only answers the cheap question
 * "which sessions exist and when were they last active?" so opening the sidebar does not scan
 * every segment or blob.
 */
final class SessionIndex {
    private static final Object LOCK = new Object();
    private static final int VERSION = 1;
    private static final int MAX_PRUNE_ROWS = 256;
    private static final String TABLE = "sessions";

    static final class Entry {
        final String path;
        final String project;
        final String title;
        final String note;
        final String titleOverride;
        final long createdAt;
        final long updatedAt;
        final int messageCount;

        Entry(String path, String project, String title, String note, String titleOverride,
              long createdAt, long updatedAt, int messageCount) {
            this.path = path == null ? "" : path;
            this.project = project == null ? "" : project;
            this.title = title == null ? "" : title;
            this.note = note == null ? "" : note;
            this.titleOverride = titleOverride == null ? "" : titleOverride;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
            this.messageCount = Math.max(0, messageCount);
        }
    }

    private SessionIndex() {}

    static void upsert(Entry entry) {
        if (entry == null || entry.path.isEmpty()) return;
        synchronized (LOCK) {
            SQLiteDatabase db = null;
            try {
                db = open();
                ContentValues values = new ContentValues();
                values.put("path", entry.path);
                values.put("project", entry.project);
                values.put("title", entry.title);
                values.put("note", entry.note);
                values.put("title_override", entry.titleOverride);
                values.put("created_at", entry.createdAt);
                values.put("updated_at", entry.updatedAt);
                values.put("message_count", entry.messageCount);
                db.insertWithOnConflict(TABLE, null, values, SQLiteDatabase.CONFLICT_REPLACE);
            } catch (Exception ignored) {
                // The index is derived data. The session itself remains authoritative.
            } finally {
                if (db != null) db.close();
            }
        }
    }

    static void pruneMissing() {
        synchronized (LOCK) {
            SQLiteDatabase db = null;
            Cursor cursor = null;
            try {
                db = open();
                cursor = db.query(TABLE, new String[]{"path"}, null, null, null, null, null);
                ArrayList<String> missing = new ArrayList<>();
                while (cursor.moveToNext() && missing.size() < MAX_PRUNE_ROWS) {
                    String path = cursor.getString(0);
                    if (!new File(path).isFile()) missing.add(path);
                }
                for (String path : missing) db.delete(TABLE, "path = ?", new String[]{path});
            } catch (Exception ignored) {
                // The index is derived data.
            } finally {
                if (cursor != null) cursor.close();
                if (db != null) db.close();
            }
        }
    }

    static void remove(File file) {
        if (file == null) return;
        synchronized (LOCK) {
            SQLiteDatabase db = null;
            try {
                db = open();
                db.delete(TABLE, "path = ?", new String[]{file.getAbsolutePath()});
            } catch (Exception ignored) {
                // A later directory scan can rebuild the missing row.
            } finally {
                if (db != null) db.close();
            }
        }
    }

    static List<Entry> list(String project) {
        ArrayList<Entry> result = new ArrayList<>();
        synchronized (LOCK) {
            SQLiteDatabase db = null;
            Cursor cursor = null;
            try {
                db = open();
                cursor = db.query(TABLE,
                        new String[]{"path", "project", "title", "note", "title_override",
                                "created_at", "updated_at", "message_count"},
                        project == null ? null : "project = ?",
                        project == null ? null : new String[]{project},
                        null, null, "updated_at DESC");
                while (cursor.moveToNext()) {
                    result.add(new Entry(
                            cursor.getString(0), cursor.getString(1), cursor.getString(2),
                            cursor.getString(3), cursor.getString(4), cursor.getLong(5),
                            cursor.getLong(6), cursor.getInt(7)));
                }
            } catch (Exception ignored) {
                result.clear();
            } finally {
                if (cursor != null) cursor.close();
                if (db != null) db.close();
            }
        }
        return result;
    }

    private static SQLiteDatabase open() {
        File file = new File(TermuxConstants.dataDir(), "session-index.db");
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory()) parent.mkdirs();
        SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(file, null);
        if (db.getVersion() < VERSION) db.setVersion(VERSION);
        db.execSQL("CREATE TABLE IF NOT EXISTS sessions ("
                + "path TEXT PRIMARY KEY, project TEXT NOT NULL, title TEXT NOT NULL,"
                + "note TEXT NOT NULL, title_override TEXT NOT NULL, created_at INTEGER NOT NULL,"
                + "updated_at INTEGER NOT NULL, message_count INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE INDEX IF NOT EXISTS sessions_project_updated"
                + " ON sessions(project, updated_at DESC)");
        return db;
    }
}
