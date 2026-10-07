# NOTICE — TextMate 语法、语言配置与主题

本目录（`app/src/main/assets/sora/textmate/`）里的语法与主题文件**不是本工程自己写的**，
它们来自若干上游项目，按各自的许可随应用一起分发。本文件按「可核对」的方式列出它们：
来源、许可、对应文件、已知的本地修改，以及**哪些项没能核实**。

> 本目录由应用在运行时读取（`languages.json` 是清单，Java/Editor 侧按它加载语法），
> 因此它是**发行物的一部分**，不是开发期素材。

---

## 1. 谁在用哪些文件

`languages.json` 是唯一清单，逐条给出了语言名、TextMate scope 与对应的语法文件：

| 语言 | scope | 语法文件 | 语言配置 |
| --- | --- | --- | --- |
| java | `source.java` | `java/syntaxes/java.tmLanguage.json` | `java/language-configuration.json` |
| kotlin | `source.kotlin` | `kotlin/syntaxes/Kotlin.tmLanguage` | `kotlin/language-configuration.json` |
| javascript | `source.js` | `javascript/syntaxes/JavaScript.tmLanguage.json` | `javascript/language-configuration.json` |
| python | `source.python` | `python/syntaxes/python.tmLanguage.json` | `python/language-configuration.json` |
| xml | `text.xml` | `xml/syntaxes/xml.tmLanguage.json` | `xml/language-configuration.json` |
| markdown | `text.html.markdown` | `markdown/syntaxes/markdown.tmLanguage.json` | `markdown/language-configuration.json` |
| shell | `source.shell` | `extra/generic.tmLanguage.json` | — |
| c / cpp / css / html | `source.c` / `source.cpp` / `source.css` / `text.html.basic` | `grammars/{c,cpp,css,html}.tmLanguage.json` | — |
| json / sql / typescript | `source.json` / `source.sql` / `source.ts` | `grammars/{json,sql,typescript}.tmLanguage.json` | — |
| log | `text.log` | `grammars/log.tmLanguage.json` | — |
| yaml / yaml-1.2 / yaml-embedded | `source.yaml` 等 | `grammars/yaml*.tmLanguage.json` | — |

主题：`themes/dark_vs.json`、`themes/light_vs.json`、`darcula.json`、`quietlight.json`。

> 注意 `grammars/` 与各语言子目录下**存在同名语言的两份文件**（例如 `grammars/java.tmLanguage.json`
> 与 `java/syntaxes/java.tmLanguage.json`），它们**不是同一份内容**（字节数不同）。
> 运行时用的是上表里 languages.json 指向的那一份；改动时请以清单为准，别只改其中一份。

---

## 2. 上游与许可（逐项）

### 2.1 Visual Studio Code — MIT（✅ 已核实）

- 来源：<https://github.com/microsoft/vscode>
- 许可：MIT，`Copyright (c) 2015 - present Microsoft Corporation`
- 核对：`curl -s https://raw.githubusercontent.com/microsoft/vscode/main/LICENSE.txt | head -4`
- 对应文件：`grammars/{c,cpp,css,html,json,sql,typescript,xml,shellscript}.tmLanguage.json`、
  `java/syntaxes/java.tmLanguage.json`、`javascript/syntaxes/JavaScript.tmLanguage.json`、
  `python/syntaxes/python.tmLanguage.json`、`markdown/syntaxes/markdown.tmLanguage.json`、
  `themes/dark_vs.json`、`themes/light_vs.json`，以及各 `language-configuration.json`

### 2.2 vscode-kotlin — MIT（✅ 已核实）

- 来源：<https://github.com/fwcd/vscode-kotlin>
- 许可：MIT，`Copyright (c) 2016 George Fraser` / `Copyright (c) 2018 fwcd`
- 核对：`curl -s https://raw.githubusercontent.com/fwcd/vscode-kotlin/main/LICENSE | head -5`
- 对应文件：`kotlin/syntaxes/Kotlin.tmLanguage`（上游为 plist 格式，本目录保留该格式）、
  `kotlin/language-configuration.json`

### 2.3 vscode-logfile-highlighter — MIT（✅ 已核实）

- 来源：<https://github.com/emilast/vscode-logfile-highlighter>
- 许可：MIT，`Copyright (c) 2015 emilast`
- 核对：`curl -s https://raw.githubusercontent.com/emilast/vscode-logfile-highlighter/master/LICENSE | head -4`
- 对应文件：`grammars/log.tmLanguage.json`

### 2.4 YAML-Syntax-Highlighter — MIT（✅ 已核实）

- 来源：<https://github.com/RedCMD/YAML-Syntax-Highlighter>
- 许可：**MIT**，`Copyright 2024 RedCMD`
- 核对（注意许可文件叫 `LICENSE.md`）：
  ```bash
  curl -s https://api.github.com/repos/RedCMD/YAML-Syntax-Highlighter/license \
    | grep -o '"spdx_id": *"[^"]*"' | head -1      # → "spdx_id": "MIT"
  curl -s https://raw.githubusercontent.com/RedCMD/YAML-Syntax-Highlighter/main/LICENSE.md | head -3
  ```
- 对应文件：`grammars/yaml.tmLanguage.json`、`grammars/yaml-1.2.tmLanguage.json`、`grammars/yaml-embedded.tmLanguage.json`

> **本节此前写的是错的**：它称该仓库 `main` / `master` 的 `LICENSE` 路径都返回 404，
> 因此「不要把这一项当成已经确认的 MIT」。真实原因是**上游的许可文件叫 `LICENSE.md`** ——
> 找错了文件名得到 404，被误读成了「这个仓库没有许可」。
> **「核不到」不等于「没有」**：换文件名、换分支、换 API 端点各试一次再下结论。

### 2.5 主题 `darcula.json`、`quietlight.json` — ⚠️ 来源未核实

- 这两个文件是 TextMate / VS Code 生态里常见的主题（`darcula`、`QuietLight`），
  但**本仓库里没有记录它们的取用出处**。
- 收口方式：确认上游仓库与其许可，或替换为可核实来源的主题文件。
  （**注意 2.4 的教训**：先试 `LICENSE`，再试 `LICENSE.md` / `LICENSE.txt`，别把 404 当成「没有许可」。）

### 2.6 `extra/generic.tmLanguage.json`、`languages.json` — ⚠️ 来源未核实

- `extra/generic.tmLanguage.json` 的 `name` 是 `Additional programming languages`、`scopeName` 是 `source.shell`，
  用来给 `shell` 提供最小高亮；`languages.json` 是清单文件。
- 两者**都缺少取用出处的记录**。若它们来自某个上游项目，请补记来源与许可；若是本工程自己写的，
  请在这里明确写成「本工程原创」（这样它归 MIT，且不需要第三方声明）。

### 2.7 `grammars/generic-programming.tmLanguage.json` — ⚠️ 来源未核实，且**未被引用**

- 这个文件既**不在** `languages.json` 的清单里，也没有出现在上表任何一行的「对应文件」中 ——
  也就是说运行时不会加载它（清单见第 1 节）。
- 它是**来源未记录的孤儿文件**。两种收口方式：确认出处与许可后补进本文件与 `NOTICE`；
  或者如果确认无用，直接删掉（删之前请先跑第 4 节 ③ 的清单命令确认没有别处引用它）。

---

## 3. 已知的本地修改

按 Apache-2.0 / MIT 的通例，**改过的第三方文件要说明"我们改了什么"**。目前能确证的只有一条：

| 文件 | 修改 | 怎么核对 |
| --- | --- | --- |
| `grammars/markdown.tmLanguage.json` | 含一条额外的 `fenced_code_block_kotlin` 规则（用于在 Markdown 里高亮 Kotlin 代码块） | `grep -c fenced_code_block_kotlin grammars/markdown.tmLanguage.json` → 2 |

其余文件**没有逐条比对过上游**，因此不能声明"未修改"。要做出这个声明，需要按下节的方法
逐文件与上游 diff，并把结论写回本表。

---

## 4. 怎么重新核对（任何人都能重跑）

```bash
cd app/src/main/assets/sora/textmate

# ① 上游许可（注意 YAML 那条：许可文件叫 LICENSE.md，不是 LICENSE）
for u in \
  https://raw.githubusercontent.com/microsoft/vscode/main/LICENSE.txt \
  https://raw.githubusercontent.com/fwcd/vscode-kotlin/main/LICENSE \
  https://raw.githubusercontent.com/emilast/vscode-logfile-highlighter/master/LICENSE \
  https://raw.githubusercontent.com/RedCMD/YAML-Syntax-Highlighter/main/LICENSE.md ; do
  echo "--- $u"; curl -s "$u" | head -4
done

# ② 上游语法文件（以 java 为例）：把上游那份取下来逐行 diff
curl -s https://raw.githubusercontent.com/microsoft/vscode/main/extensions/java/syntaxes/java.tmLanguage.json \
  > /tmp/java.upstream.json
diff -u /tmp/java.upstream.json java/syntaxes/java.tmLanguage.json | head -40

# ③ 哪些语言实际在用（改文件前先看清单）
node -e 'for (const l of require("./languages.json").languages) console.log(l.name, l.grammar)'
```

---

## 5. MIT 许可原文

上面 2.1–2.4 各项按 MIT 分发，随附以下原文（各自 `Copyright` 行见对应小节：
VS Code `2015 - present Microsoft Corporation`、vscode-kotlin `2016 George Fraser` / `2018 fwcd`、
vscode-logfile-highlighter `2015 emilast`、YAML-Syntax-Highlighter `2024 RedCMD`）：

```
MIT License

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

仓库根目录另有汇总清单 [`NOTICE`](../../../../../../NOTICE) 与许可原文目录
[`THIRD-PARTY-LICENSES/`](../../../../../../THIRD-PARTY-LICENSES)。

> **本文件此前至少错过两次**：
> 1. 曾把这一目录说成属于「Zalith Launcher 2」，并漏掉了主题与清单文件的来源问题；
> 2. 曾把 YAML 语法的许可写成「核不到」—— 实际是**找错了许可文件名**（上游叫 `LICENSE.md`），
>    见 2.4 节。
>
> 现在按实际内容逐项重写。规矩是：**未核实不等于有问题，但把它写成已核实（或把「没找到文件」
> 当成「没有许可」）就是问题。**
