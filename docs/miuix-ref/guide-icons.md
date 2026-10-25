图标系统 
​
Miuix 提供了一套丰富的内置图标系统，满足大多数应用的主要设计需求。这些图标提供五种粗细：Light、Normal、Regular、Medium 和 Demibold。
安装 
​
基础的 
miuix-ui
 库仅包含少量核心图标。如需使用完整的 Miuix 图标库，您需要将 
miuix-icons
 依赖添加到您的项目中。
kotlin

```
// build.gradle.kts
commonMain.dependencies {
    implementation("top.yukonga.miuix.kmp:miuix-ui:<version>")
    // 添加扩展图标库
    implementation("top.yukonga.miuix.kmp:miuix-icons:<version>")
}
```

使用方法 
​
要在项目中使用 Miuix 图标，首先需要正确导入：
kotlin

```
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Settings
```

然后，你可以像下面这样使用图标：
kotlin

```
// 使用 Ok 图标 (默认为 Regular 粗细)
Icon(
    imageVector = MiuixIcons.Ok,
    contentDescription = "Ok",
    modifier = Modifier.size(24.dp)
)

// 使用 Settings 图标
Icon(
    imageVector = MiuixIcons.Settings,
    contentDescription = "Settings",
    modifier = Modifier.size(24.dp)
)

// 使用特定粗细 (例如 Light)
Icon(
    imageVector = MiuixIcons.Light.Settings,
    contentDescription = "Settings (Light)",
    modifier = Modifier.size(24.dp)
)
```

图标粗细 
​
Miuix 图标支持五种粗细，对应字体源 
misymbol_vf.ttf
 的 5 个命名实例：
MiuixIcons.Light
: 细图标 (wght=250)
MiuixIcons.Normal
: 偏细图标 (wght=305)
MiuixIcons.Regular
: 常规图标 (wght=330) - 直接访问 
MiuixIcons.IconName
 时的默认值
MiuixIcons.Medium
: 偏粗图标 (wght=430)
MiuixIcons.Demibold
: 粗图标 (wght=500)
可用图标 
​
Basic（基础图标） 
​
基础图标包含一些常用的基本界面元素，如箭头、勾选等，这些图标在 Miuix 本身的组件中也会使用到。以下是完整的列表：
图标名称
Light
Normal
Regular
Medium
Demibold
ArrowRight
-
-
-
-
ArrowUpDown
-
-
-
-
Check
-
-
-
-
Close
-
-
-
-
Search
-
-
-
-
SearchCleanup
-
-
-
-
Sidebar
-
-
-
-
Extended (扩展图标) 
​
扩展图标包含更多场景下的图标。以下是完整的列表：
图标名称
Light
Normal
Regular
Medium
Demibold
Add
AddCircle
AddFolder
Alarm
Album
All
Answer
AppRecording
Back
Background
Backup
BankCards
Blocklist
CallRecording
Carrier
ChevronBackward
ChevronForward
Clear
Close
Close2
CloudFill
Community
Contacts
ContactsBook
ContactsCircle
ConvertFile
Copy
Create
Cut
Delete
Download
Edit
Email
ExpandLess
ExpandMore
Favorites
FavoritesFill
File
FileDownloads
Filter
Folder
FolderFill
Forward
GridView
Help
Hide
Home
HorizontalSplit
Image
Import
Info
Layers
Link
ListView
Location
Lock
MapAlbum
Merge
Messages
Mic
MicSlash
MindMap
Months
More
MoreCircle
MoveFile
Music
Notes
NotesFill
Ok
Paste
Pause
Phone
Photos
Pin
Play
Playlist
Promotions
Recent
Recording
RecordingTape
Redo
Refresh
Remove
RemoveContact
Rename
Replace
Reply
ReplyAll
Report
Reset
RotateLeft
Scan
ScreenCapture
ScreenMirroring
Search
SearchDevice
SelectAll
Send
Settings
Share
Show
Sidebar
Sort
Stopwatch
Store
Tasks
Th1
Th10
Th11
Th12
Th13
Th14
Th15
Th16
Th17
Th18
Th19
Th2
Th20
Th21
Th22
Th23
Th24
Th25
Th26
Th27
Th28
Th29
Th3
Th30
Th31
Th4
Th5
Th6
Th7
Th8
Th9
Theme
Timer
TopDownloads
Translate
Trim
Tune
Undo
Unlock
Unpin
Update
UploadCloud
VerticalSplit
VolumeOff
VolumeUp
Weeks
WorldClock
Years
ZoomOut
变更日志 
最后编辑于 3 个月前
查看完整变更日志
d440ae8
-
library: Add Home icon and optically align extended icon set
14e6f36
-
docs: align components/guides with current API
20d960b
-
library: Refactor miuix-icons
881f769
-
docs: update module names and dependency coordinates after restructure
bd0faaa
-
library: add support for indeterminate state in Checkbox (
#241
)
1769fbb
-
refactor: Move extended icon to a separate library
40e255a
-
docs: Add icon preview in docs (
#112
)
7b99e64
-
docs: Add English version through AI translation
ab6bb69
-
chore: Add documents (
#63
)
