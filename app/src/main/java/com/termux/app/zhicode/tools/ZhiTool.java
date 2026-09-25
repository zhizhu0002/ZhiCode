package com.termux.app.zhicode.tools;

import com.termux.app.zhicode.model.SessionConfig;
import com.termux.app.zhicode.model.ToolExecutionResult;

import org.json.JSONObject;

public interface ZhiTool {
    String name();
    String description();
    JSONObject inputSchema();
    PermissionKind permissionKind();
    ToolExecutionResult execute(SessionConfig config, JSONObject input) throws Exception;

    /** Optional live progress channel. Tools that do not stream can keep the normal execute() implementation. */
    default ToolExecutionResult execute(SessionConfig config, JSONObject input, ProgressListener progress) throws Exception {
        return execute(config, input);
    }

    interface ProgressListener {
        /** chunk may be empty for a heartbeat; elapsedMs always reflects current tool runtime. */
        void onProgress(String chunk, boolean stderr, long elapsedMs);
    }

    enum PermissionKind {
        INTERNAL,
        READ,
        NETWORK,
        WRITE,
        SHELL,
        SYSTEM
    }
}
