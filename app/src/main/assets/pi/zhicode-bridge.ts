import type { ExtensionAPI } from "@earendil-works/pi-coding-agent";
import { Type } from "typebox";
import * as fs from "node:fs";

const RUNTIME_STATE_PATH = "/etc/zhicode/runtime-state.json";

interface RuntimeState {
  permissionMode: string;
  rootEnabled: boolean;
  shizukuEnabled: boolean;
  webSearchEnabled: boolean;
}

function readRuntimeState(): RuntimeState {
  const fallback: RuntimeState = {
    permissionMode: process.env.ZHICODE_PERMISSION_MODE || "default",
    rootEnabled: process.env.ZHICODE_ROOT_ENABLED === "1",
    shizukuEnabled: process.env.ZHICODE_SHIZUKU_ENABLED === "1",
    webSearchEnabled: process.env.ZHICODE_WEB_SEARCH_ENABLED !== "0",
  };
  try {
    const raw = fs.readFileSync(RUNTIME_STATE_PATH, "utf8");
    const parsed = JSON.parse(raw);
    return {
      permissionMode: String(parsed.permissionMode || fallback.permissionMode),
      rootEnabled: Boolean(parsed.rootEnabled ?? fallback.rootEnabled),
      shizukuEnabled: Boolean(parsed.shizukuEnabled ?? fallback.shizukuEnabled),
      webSearchEnabled: Boolean(parsed.webSearchEnabled ?? fallback.webSearchEnabled),
    };
  } catch {
    return fallback;
  }
}

function isRiskyCommand(cmd: string): boolean {
  const trimmed = (cmd || "").trim();
  if (!trimmed) return false;
  const patterns = [
    /\b(sudo|su|doas|run-as|reboot|shutdown|halt|poweroff|mkfs\S*|fdisk|parted|shred|fastboot|setprop|svc|mount|umount|insmod|rmmod|modprobe)\b/i,
    /\brm\s+(-[A-Za-z]*r[A-Za-z]*f|-[A-Za-z]*f[A-Za-z]*r|--recursive\s+--force|--force\s+--recursive)\b/,
    /\bdd\b[^\n]*\bof=\/dev\//,
    /\bpm\s+(install|uninstall|clear|enable|disable|grant|revoke)\b/,
    /\bam\s+(start|startservice|force-stop|kill|broadcast)\b/,
    /(?:curl|wget)[^\n|]*\|\s*(?:sh|bash)\b/,
    /\bgit\s+(push\s+--force|push\s+-f|reset\s+--hard|clean\s+-[A-Za-z]*f)\b/,
    /(?:^|\s)\/(system|vendor|product|data\/adb|init\.rc|boot)\b/,
  ];
  return patterns.some((re) => re.test(trimmed));
}

function classifyTool(toolName: string, input: Record<string, unknown>): {
  kind: "read" | "write" | "exec" | "system";
  highRisk: boolean;
  summary: string;
} {
  const lower = toolName.toLowerCase();
  if (lower === "read" || lower === "grep" || lower === "find" || lower === "ls" || lower === "websearch" || lower === "webfetch") {
    const target = String(input.path ?? input.query ?? input.url ?? "");
    return { kind: "read", highRisk: false, summary: target };
  }
  if (lower === "write" || lower === "edit") {
    return { kind: "write", highRisk: false, summary: String(input.path ?? "") };
  }
  if (lower === "bash") {
    const cmd = String(input.command ?? "");
    return { kind: "exec", highRisk: isRiskyCommand(cmd), summary: cmd };
  }
  if (lower === "root") {
    const cmd = String(input.command ?? "");
    return { kind: "system", highRisk: true, summary: cmd };
  }
  if (lower === "shizuku") {
    const cmd = String(input.command ?? "");
    return { kind: "system", highRisk: isRiskyCommand(cmd), summary: cmd };
  }
  if (lower === "ui_canvas") {
    const op = String(input.operation ?? "get");
    const readonly = op === "get" || op === "preview" || op === "export";
    return { kind: readonly ? "read" : "write", highRisk: false, summary: op };
  }
  if (lower === "sandbox") {
    const action = String(input.action ?? "").toLowerCase();
    const readonly = ["status", "list", "inspect", "screenshot", "dump_ui", "find_node", "debug_snapshot"].includes(action);
    const high = action === "uninstall" || action === "clear_data";
    return { kind: readonly ? "read" : "system", highRisk: high, summary: `${action} ${String(input.package ?? input.path ?? "")}`.trim() };
  }
  if (lower === "debug") {
    const scope = String(input.scope ?? "sandbox").toLowerCase();
    const action = String(input.action ?? "").toLowerCase();
    const readonly = ["process_list", "process_info", "maps", "modules", "threads", "thread_dump", "frida_status", "frida_events"].includes(action);
    const high = scope === "host" || action === "memory_write" || action === "load_library" || action === "signal";
    return { kind: readonly && scope !== "host" ? "read" : "system", highRisk: high, summary: `${scope}:${action} ${String(input.package ?? input.pid ?? "")}`.trim() };
  }
  if (lower === "androidintent") {
    const op = String(input.operation ?? "intent");
    return { kind: "system", highRisk: op === "install_apk", summary: `${op} ${String(input.uri ?? input.package ?? input.path ?? "")}`.trim() };
  }
  return { kind: "system", highRisk: false, summary: JSON.stringify(input).slice(0, 180) };
}

async function callBridge(
  tool: string,
  input: Record<string, unknown>,
  signal?: AbortSignal,
): Promise<{
  content: Array<{ type: "text"; text: string } | { type: "image"; data: string; mimeType: string }>;
  details: { exitCode: number; isError: boolean };
}> {
  const baseUrl = process.env.ZHICODE_BRIDGE_URL;
  const token = process.env.ZHICODE_BRIDGE_TOKEN;
  if (!baseUrl || !token) {
    return {
      content: [{ type: "text", text: `Android Host Bridge 未初始化，无法调用 ${tool}` }],
      details: { exitCode: -1, isError: true },
    };
  }
  const resp = await fetch(`${baseUrl}/invoke`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "X-ZhiCode-Token": token,
    },
    body: JSON.stringify({ tool, input }),
    signal,
  });
  const payload = (await resp.json()) as {
    ok?: boolean;
    isError?: boolean;
    exitCode?: number;
    text?: string;
    error?: string;
    images?: Array<{ data: string; mimeType?: string }>;
  };
  const isError = Boolean(payload.isError ?? !resp.ok);
  const text = payload.text ?? payload.error ?? (resp.ok ? "" : `HTTP ${resp.status}`);
  const content: Array<{ type: "text"; text: string } | { type: "image"; data: string; mimeType: string }> = [
    { type: "text", text },
  ];
  if (Array.isArray(payload.images)) {
    for (const img of payload.images) {
      if (img?.data) {
        content.push({ type: "image", data: img.data, mimeType: img.mimeType || "image/png" });
      }
    }
  }
  return {
    content,
    details: { exitCode: typeof payload.exitCode === "number" ? payload.exitCode : isError ? 1 : 0, isError },
  };
}

export default function (pi: ExtensionAPI) {
  const state = readRuntimeState();

  pi.on("tool_call", async (event, ctx) => {
    const live = readRuntimeState();
    const input = (event.input ?? {}) as Record<string, unknown>;
    const info = classifyTool(event.toolName, input);

    if (event.toolName === "Root" && !live.rootEnabled) {
      return { block: true, reason: "Agent Root 未在设置中启用。" };
    }
    if (event.toolName === "Shizuku" && !live.shizukuEnabled) {
      return { block: true, reason: "Shizuku 未在设置中启用。" };
    }
    if ((event.toolName === "WebSearch" || event.toolName === "WebFetch") && !live.webSearchEnabled) {
      return { block: true, reason: "联网搜索已在设置中关闭。" };
    }

    if (info.kind === "read") return;

    const mode = live.permissionMode || "default";
    if (mode === "plan") {
      return { block: true, reason: `Plan 只读模式已拦截 ${event.toolName}` };
    }
    if (mode === "bypassPermissions") {
      return;
    }
    if (mode === "dontAsk" && !info.highRisk) {
      return;
    }
    if ((mode === "acceptEdits" || mode === "auto") && info.kind === "write") {
      return;
    }

    const title = `zhi-perm:${event.toolName}:${info.highRisk ? "1" : "0"}`;
    const allowed = await ctx.ui.confirm(title, info.summary || event.toolName);
    if (!allowed) {
      return { block: true, reason: "用户拒绝了本次操作" };
    }
  });

  if (state.rootEnabled) {
    pi.registerTool({
      name: "Root",
      label: "Root 命令",
      description: "Execute a command on the Android host as root (uid 0) via su.",
      promptSnippet: "Run Android host superuser commands when explicitly needed",
      promptGuidelines: ["Use Root only when app-level Debian bash is insufficient and user requested privileged host operations."],
      parameters: Type.Object({
        command: Type.String({ description: "Command to execute as Android root (uid 0)" }),
        cwd: Type.Optional(Type.String()),
        timeout_ms: Type.Optional(Type.Number()),
      }),
      async execute(_id, params, signal) {
        return callBridge("Root", params as unknown as Record<string, unknown>, signal);
      },
    });
  }

  if (state.shizukuEnabled) {
    pi.registerTool({
      name: "Shizuku",
      label: "Shizuku 命令",
      description: "Execute a command on the Android host as the shell user (uid 2000) via Shizuku.",
      promptSnippet: "Run Android shell commands via Shizuku without requiring Root",
      parameters: Type.Object({
        command: Type.String({ description: "Command to execute via Shizuku" }),
        cwd: Type.Optional(Type.String()),
        timeout_ms: Type.Optional(Type.Number()),
      }),
      async execute(_id, params, signal) {
        return callBridge("Shizuku", params as unknown as Record<string, unknown>, signal);
      },
    });
  }

  pi.registerTool({
    name: "AndroidIntent",
    label: "手机操作",
    description: "Launch Android intents, open URLs or apps, or trigger APK installation.",
    parameters: Type.Object({
      operation: Type.Optional(Type.String()),
      action: Type.Optional(Type.String()),
      uri: Type.Optional(Type.String()),
      path: Type.Optional(Type.String()),
      package: Type.Optional(Type.String()),
      app_name: Type.Optional(Type.String()),
      component: Type.Optional(Type.String()),
      mime_type: Type.Optional(Type.String()),
      categories: Type.Optional(Type.Array(Type.String())),
      flags: Type.Optional(Type.Number()),
    }),
    async execute(_id, params, signal) {
      return callBridge("AndroidIntent", params as unknown as Record<string, unknown>, signal);
    },
  });

  pi.registerTool({
    name: "Sandbox",
    label: "Sandbox",
    description: "Control and inspect ZhiCode's built-in Android virtual app sandbox.",
    parameters: Type.Object({
      action: Type.String(),
      path: Type.Optional(Type.String()),
      package: Type.Optional(Type.String()),
      node: Type.Optional(Type.String()),
      text: Type.Optional(Type.String()),
      match: Type.Optional(Type.String()),
      annotate: Type.Optional(Type.Boolean()),
      with_ui: Type.Optional(Type.Boolean()),
      full_tree: Type.Optional(Type.Boolean()),
      x: Type.Optional(Type.Number()),
      y: Type.Optional(Type.Number()),
      x1: Type.Optional(Type.Number()),
      y1: Type.Optional(Type.Number()),
      x2: Type.Optional(Type.Number()),
      y2: Type.Optional(Type.Number()),
      duration_ms: Type.Optional(Type.Number()),
    }),
    async execute(_id, params, signal) {
      return callBridge("Sandbox", params as unknown as Record<string, unknown>, signal);
    },
  });

  pi.registerTool({
    name: "Debug",
    label: "Debug",
    description: "Inspect Android sandbox guest or host process maps, modules, threads, memory, and Frida runtime.",
    parameters: Type.Object({
      scope: Type.Optional(Type.String()),
      action: Type.String(),
      package: Type.Optional(Type.String()),
      pid: Type.Optional(Type.Number()),
      filter: Type.Optional(Type.String()),
      address: Type.Optional(Type.String()),
      size: Type.Optional(Type.Number()),
      data: Type.Optional(Type.String()),
      format: Type.Optional(Type.String()),
      path: Type.Optional(Type.String()),
      script: Type.Optional(Type.String()),
      signal: Type.Optional(Type.Number()),
      max_lines: Type.Optional(Type.Number()),
      max_modules: Type.Optional(Type.Number()),
      max: Type.Optional(Type.Number()),
      timeout_ms: Type.Optional(Type.Number()),
    }),
    async execute(_id, params, signal) {
      return callBridge("Debug", params as unknown as Record<string, unknown>, signal);
    },
  });

  pi.registerTool({
    name: "ui_canvas",
    label: "ui_canvas",
    description: "Inspect or customize ZhiCode's live UI canvas palette and layout parameters.",
    parameters: Type.Object({
      operation: Type.String(),
      palette: Type.Optional(Type.Record(Type.String(), Type.Unknown())),
      operations: Type.Optional(Type.Array(Type.Record(Type.String(), Type.Unknown()))),
    }),
    async execute(_id, params, signal) {
      return callBridge("ui_canvas", params as unknown as Record<string, unknown>, signal);
    },
  });

  if (state.webSearchEnabled) {
    pi.registerTool({
      name: "WebSearch",
      label: "WebSearch",
      description: "Search the web using ZhiCode's configured search backend.",
      parameters: Type.Object({
        query: Type.String({ description: "Search query" }),
      }),
      async execute(_id, params, signal) {
        return callBridge("WebSearch", params as unknown as Record<string, unknown>, signal);
      },
    });

    pi.registerTool({
      name: "WebFetch",
      label: "WebFetch",
      description: "Fetch and extract readable text from a web URL.",
      parameters: Type.Object({
        url: Type.String({ description: "HTTP/HTTPS URL to fetch" }),
      }),
      async execute(_id, params, signal) {
        return callBridge("WebFetch", params as unknown as Record<string, unknown>, signal);
      },
    });
  }
}
