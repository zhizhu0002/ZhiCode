package com.termux.app.zhicode.tools;

import com.termux.app.zhicode.model.SessionConfig;
import com.termux.app.zhicode.model.ToolExecutionResult;
import com.termux.shared.termux.TermuxConstants;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;

/**
 * 载入一个技能（{@code SKILL.md}）的正文。
 *
 * <h3>查两个位置</h3>
 * 技能可以是「某个项目专用」的，也可以是「所有项目通用」的，所以两处都要查。
 *
 * <p>优先级按「越具体越优先」排列：
 * <ol>
 *   <li>{@code <项目>/.zhicode/skills/<名>/SKILL.md} —— 项目级</li>
 *   <li>{@code $HOME/.zhicode/skills/<名>/SKILL.md} —— 用户级</li>
 * </ol>
 * 同一个技能名在两处都有时，项目级的那份生效。
 */
public final class SkillTool implements ZhiTool {

    private static final String SKILL_FILE = "SKILL.md";
    private static final String SKILLS_DIR = "skills";

    @Override
    public String name() {
        return "Skill";
    }

    @Override
    public String description() {
        return "Load a ZhiCode skill's SKILL.md instructions from the project or user skill library.";
    }

    @Override
    public JSONObject inputSchema() {
        // JSONObject.put 声明的是受检 JSONException，而接口签名不接受抛出它；
        // 这里包一层 IllegalStateException，与其它工具（EnterWorktreeTool 等）一致。
        try {
            JSONObject properties = new JSONObject();
            properties.put("skill", ToolSchemas.string("Skill name to load."));
            properties.put("args", ToolSchemas.string("Optional arguments for the skill."));
            return ToolSchemas.object(properties, "skill");
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }

    @Override
    public PermissionKind permissionKind() {
        return PermissionKind.READ;
    }

    @Override
    public ToolExecutionResult execute(SessionConfig config, JSONObject input) throws Exception {
        String name = input.optString("skill", "").trim();
        // 名字直接拼进路径，因此必须挡掉穿越。白名单式地禁掉分隔符与 ".."
        // 比事后校验规范路径更直接，也不依赖文件系统行为。
        if (name.isEmpty() || name.contains("..") || name.contains("/") || name.contains("\\")) {
            return ToolExecutionResult.error("Invalid skill name");
        }

        File project = config == null || config.projectDirectory == null
                ? null
                : new File(config.projectDirectory);
        File[] candidates = {
                project == null ? null : new File(project, TermuxConstants.DATA_DIR_NAME + "/" + SKILLS_DIR + "/" + name + "/" + SKILL_FILE),
                new File(new File(TermuxConstants.dataDir(), SKILLS_DIR), name + "/" + SKILL_FILE),
        };

        for (File file : candidates) {
            if (file == null || !file.isFile()) continue;
            String args = input.optString("args", "");
            String header = "Loaded skill `" + name + "` from " + file.getAbsolutePath() + "\n\n";
            return ToolExecutionResult.ok(header + read(file) + (args.isEmpty() ? "" : "\n\nSkill arguments: " + args));
        }
        return ToolExecutionResult.error("Skill not found: " + name);
    }

    private static String read(File file) throws Exception {
        byte[] data = new byte[(int) file.length()];
        try (FileInputStream in = new FileInputStream(file)) {
            int offset = 0;
            int count;
            while (offset < data.length && (count = in.read(data, offset, data.length - offset)) > 0) {
                offset += count;
            }
        }
        return new String(data, StandardCharsets.UTF_8);
    }
}
