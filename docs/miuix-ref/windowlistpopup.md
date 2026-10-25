WindowListPopup 
​
WindowListPopup
 是一个基于 
Dialog
 在窗口层级渲染的弹出列表组件。与 
OverlayListPopup
 不同，它不需要 
Scaffold
 或 
MiuixPopupHost
。
提示
该组件不依赖 
Scaffold
，可在任意 Composable 作用域中使用。
引入 
​
kotlin

```
import top.yukonga.miuix.kmp.window.WindowListPopup
import top.yukonga.miuix.kmp.basic.ListPopupColumn
```

基本用法 
​
WindowListPopup 组件可用于在没有 
Scaffold
 的情况下创建下拉菜单：
kotlin

```
var showPopup by remember { mutableStateOf(false) }
var selectedIndex by remember { mutableStateOf(0) }
val items = listOf("选项 1", "选项 2", "选项 3")

Box {
    TextButton(
        text = "点击显示菜单",
        onClick = { showPopup = true }
    )
    WindowListPopup(
        show = showPopup,
        alignment = PopupPositionProvider.Align.Start,
        onDismissRequest = { showPopup = false }
    ) {
        val dismiss = LocalDismissState.current
        ListPopupColumn {
            items.forEachIndexed { index, string ->
                DropdownImpl(
                    text = string,
                    optionSize = items.size,
                    isSelected = selectedIndex == index,
                    index = index,
                    onSelectedIndexChange = {
                        selectedIndex = index
                        dismiss?.invoke()
                    }
                )
            }
        }
    }
}
```

属性 
​
WindowListPopup 属性 
​
属性名
类型
说明
默认值
show
Boolean
是否显示弹窗
-
popupModifier
Modifier
应用于弹窗容器的修饰符
Modifier
popupPositionProvider
PopupPositionProvider
提供弹窗的位置计算逻辑
ListPopupDefaults.DropdownPositionProvider
alignment
PopupPositionProvider.Align
指定弹窗相对于锚点的对齐方式
PopupPositionProvider.Align.Start
enableWindowDim
Boolean
是否在弹窗显示时使背景变暗
true
onDismissRequest
(() -> Unit)?
当用户请求关闭（例如点击外部）时触发
null
onDismissFinished
(() -> Unit)?
关闭动画完成后调用；若关闭过程被中途取消（例如 
show
 被设回 true），则不会触发
null
maxHeight
Dp?
弹窗内容的最大高度
null
minWidth
Dp
弹窗内容的最小宽度
ListPopupDefaults.MinWidth
content
@Composable () -> Unit
要在弹窗内显示的内容
-
ListPopupColumn 属性 
​
属性名
类型
说明
默认值
content
@Composable () -> Unit
要在列内显示的列表内容
-
DropdownImpl 
​
DropdownImpl
 可作为 
ListPopupColumn
 内的标准选项行使用。设置 
enabled = false
 可以禁用某一行；禁用行不可点击，并使用禁用文本颜色。
kotlin

```
DropdownImpl(
    text = "禁用选项",
    optionSize = items.size,
    isSelected = false,
    index = 1,
    enabled = false,
    onSelectedIndexChange = {}
)
```

属性名
类型
说明
默认值
text
String
选项显示文本
-
optionSize
Int
选项总数
-
isSelected
Boolean
此选项是否被选中
-
index
Int
此选项的索引
-
dropdownColors
DropdownColors
选项颜色配置
DropdownDefaults.dropdownColors()
enabled
Boolean
此选项是否可点击
true
onSelectedIndexChange
(Int) -> Unit
点击此选项时的回调
-
PopupPositionProvider.Align 
​
值
说明
Start
将弹窗对齐到锚点的起始端
End
将弹窗对齐到锚点的结束端
TopStart
将弹窗对齐到锚点的顶部起始端
TopEnd
将弹窗对齐到锚点的顶部结束端
BottomStart
将弹窗对齐到锚点的底部起始端
BottomEnd
将弹窗对齐到锚点的底部结束端
LocalDismissState 
​
提供一个 
(() -> Unit)?
 函数，用于从内容内部关闭当前弹窗。这是所有弹出组件提供的统一关闭状态。
kotlin

```
val dismiss = LocalDismissState.current
TextButton(
    text = "关闭",
    onClick = { dismiss?.invoke() }
)
```

变更日志 
最后编辑于 5 个月前
查看完整变更日志
7186380
-
docs: clarify onDismissFinished cancel semantics + ListPopup defaults
0b7ec07
-
library: refactor: unify dropdown and spinner components (
#315
)
81ad71b
-
library: add grouped DropdownPreference (
#310
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
c8075f9
-
library: Improve RTL support
bdab7b3
-
library: Remove 
onClick
 from some composite components
d308e66
-
docs: Add missing 
onDismissFinished
2a2a021
-
library: Add WindowListPopup/WindowDropdown/WindowSpinner component (
#174
)
