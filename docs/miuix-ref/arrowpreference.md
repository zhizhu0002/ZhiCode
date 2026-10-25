ArrowPreference 
​
ArrowPreference
 是 Miuix 中的箭头指示组件，通常用于导航或展示更多内容。提供了标题、摘要和右侧箭头图标，支持点击交互，常用于设置项、菜单项或列表项中。
引入 
​
kotlin

```
import top.yukonga.miuix.kmp.preference.ArrowPreference
```

基本用法 
​
ArrowPreference 组件提供了基本的点击导航功能：
kotlin

```
ArrowPreference(
    title = "设置项",
    onClick = { /* 处理点击事件 */ }
)
```

带摘要的箭头 
​
kotlin

```
ArrowPreference(
    title = "无线网络",
    summary = "已连接到 WIFI-HOME",
    onClick = { /* 处理点击事件 */ }
)
```

组件状态 
​
禁用状态 
​
kotlin

```
ArrowPreference(
    title = "禁用项",
    summary = "此项目当前不可用",
    enabled = false,
    onClick = { /* 不会被触发 */ }
)
```

按下状态 
​
ArrowPreference 支持通过 
holdDownState
 参数控制按下状态，通常用于显示弹出对话框时的视觉反馈：
kotlin

```
var showDialog by remember { mutableStateOf(false) }

Scaffold {
    ArrowPreference(
        title = "打开对话框",
        summary = "点击显示对话框",
        onClick = { showDialog = true },
        holdDownState = showDialog
    )
    // 在其他地方定义对话框
    OverlayDialog(
        title = "对话框",
        show = showDialog,
        onDismissRequest = { showDialog = false } // 关闭对话框
    ) {
        // 对话框内容
    }
}
```

属性 
​
ArrowPreference 属性 
​
属性名
类型
说明
默认值
是否必须
title
String
箭头项的标题
-
是
modifier
Modifier
应用于组件的修饰符
Modifier
否
titleColor
BasicComponentColors
标题文本的颜色配置
BasicComponentDefaults.titleColor()
否
summary
String?
箭头项的摘要说明
null
否
summaryColor
BasicComponentColors
摘要文本的颜色配置
BasicComponentDefaults.summaryColor()
否
startAction
@Composable (() -> Unit)?
左侧自定义内容
null
否
endActions
@Composable RowScope.() -> Unit
右侧自定义内容插槽（slot）
{}
否
bottomAction
@Composable (() -> Unit)?
底部自定义内容
null
否
insideMargin
PaddingValues
组件内部内容的边距
BasicComponentDefaults.InsideMargin
否
onClick
(() -> Unit)?
点击时触发的回调
null
否
holdDownState
Boolean
组件是否处于按下状态
false
否
enabled
Boolean
组件是否可交互
true
否
ArrowPreferenceDefaults 对象 
​
ArrowPreferenceDefaults 对象提供右侧箭头图标的默认颜色配置。
ArrowPreferenceDefaults 方法 
​
方法名
类型
说明
endActionColors
EndActionColors
返回用于右侧箭头图标的着色（tint）配置
箭头着色说明 
​
右侧箭头图标始终显示，并根据 
enabled
 自动着色。
当 
enabled = true
 时使用 
MiuixTheme.colorScheme.onSurfaceVariantActions
。
当 
enabled = false
 时使用 
MiuixTheme.colorScheme.disabledOnSecondaryVariant
。
进阶用法 
​
带左侧图标 
​
kotlin

```
ArrowPreference(
    title = "个人信息",
    summary = "查看和修改您的个人资料",
    startAction = {
        Icon(
            imageVector = MiuixIcons.Contacts,
            contentDescription = "个人图标",
            tint = MiuixTheme.colorScheme.onBackground,
            modifier = Modifier.padding(end = 16.dp)
        )
    },
    onClick = { /* 处理点击事件 */ }
)
```

带右侧操作 
​
kotlin

```
ArrowPreference(
    title = "存储空间",
    summary = "管理应用存储空间",
    endActions = {
        Text("12.5 GB")
    },
    onClick = { /* 处理点击事件 */ }
)
```

结合对话框使用 
​
kotlin

```
var showDialog by remember { mutableStateOf(false) }
var language by remember { mutableStateOf("简体中文") }

Scaffold {
ArrowPreference(
    title = "语言设置",
    summary = "选择应用显示语言",
    endActions = {
        Text(language)
    },
    onClick = { showDialog = true },
    holdDownState = showDialog
)
    OverlayDialog(
        title = "选择语言",
        show = showDialog,
        onDismissRequest = { showDialog = false } // 关闭对话框
    ) {
        // 对话框内容
        Card {
            ArrowPreference(
                title = "简体中文",
                onClick = {
                    language = "简体中文"
                    showDialog = false // 关闭对话框
                }
            )
            ArrowPreference(
                title = "English",
                onClick = {
                    language = "English"
                    showDialog = false // 关闭对话框
                }
            )
        }
        Row(
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(
                text = "取消",
                onClick = { showDialog = false },  // 关闭对话框
                modifier = Modifier.weight(1f).padding(top = 8.dp)
            )
        }
    }
}
```

变更日志 
最后编辑于 5 个月前
查看完整变更日志
0b7ec07
-
library: refactor: unify dropdown and spinner components (
#315
)
ee05b13
-
library!: restructure modules into miuix-ui and miuix-preference (
#282
)
2781770
-
library: refactor overlay components and optimize Scaffold popup management (
#251
)
a8fca42
-
library: Rename: Left -> Start & Right -> End
6633088
-
build(fmt): Enable more ktlint checks 3
1769fbb
-
refactor: Move extended icon to a separate library
bdab7b3
-
library: Remove 
onClick
 from some composite components
97d2fba
-
library: Add 
bottomAction
 param to BasicComponent
c3b712e
-
library: add rightActions to SuperArrow
752f3df
-
docs: Preview for components in documentation website (
#80
)
1fb088b
-
library: Refactor dialog/popup layout
5ec7367
-
library: Move 
holdDownState 
 param to BasicComponent
7b99e64
-
docs: Add English version through AI translation
ab6bb69
-
chore: Add documents (
#63
)
