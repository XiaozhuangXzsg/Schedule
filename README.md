# 娅娅课表 · UniversityTimetable

一款面向大学生的 Android 课表应用。使用 **Kotlin + Jetpack Compose** 打造，
深度适配 **一加 15 / ColorOS 16（水生设计语言）**，并免费开放同类应用（如 WakeUp 课程表）的付费高级能力。

> **v3.0.0 正式版** — 首个正式版：**应用内更新** + **数据备份/恢复** + **GitHub Actions 自动发布**
> 版本号从 2.2.1 直接进入 **3.x**（`versionCode 30000`），界面上的版本号统一带「正式版」标识，
> 避免和 2.x 的四位版本号混淆：
> ① **应用内更新**：以 GitHub Releases 为唯一渠道（`XiaozhuangXzsg/Schedule`，仓库地址集中在
> `gradle.properties` 的 `timetable.github.owner/repo` → `BuildConfig` → `UpdateConfig.REPO_URL`，
> 全项目只有这一处来源）。启动后延迟 2.5s 后台检查，正常情况**每 24 小时最多一次**；
> 设置页新增「软件更新」组（检查更新 / 当前版本号 / 自动检查开关 / 更新日志 / GitHub 发布页）。
> 是否新版本一律用 **versionCode**（不是版本名）判断，忽略 draft 与 prerelease。
> ② **安全校验**：下载后先实算 SHA-256 与发布元数据比对，再读 APK 归档里的包名与 versionCode，
> 还要求签名与已安装版一致、且 versionCode 严格大于当前版本（阻止降级）。
> 只有同时带 `version.json` 元数据的 Release 才算候选——1.0.0 之前用的是另一套 versionCode 算法
> （v2.2.1 = 221），老包没有元数据，否则会永远排在前面诱导下载 58MB 再校验失败。
> ③ **无损更新**：`applicationId` 保持 `com.university.timetable`、沿用原签名密钥（证书 SHA-256
> `938d9f3f…4de9f5`）、versionCode 单调递增，**全程不卸载、不清数据**；Room 把
> `fallbackToDestructiveMigration()` 收窄成 `fallbackToDestructiveMigrationFrom(true, 1, 2, 3)`
> ——只有没有迁移路径的 v1–v3 才允许重建，v4→v5 走正常迁移，升级前还会把老库另存到
> `files/backup/legacy-vN-<ts>.db`。
> ④ **数据备份 / 恢复**：设置页「数据与备份」可**导出 .zip 到用户自选位置**（SAF `CreateDocument`，
> 不落缓存），内含 `manifest.json` + 数据库（含 `-wal`/`-shm`，导出前 `wal_checkpoint(TRUNCATE)`）
> + 两个 DataStore 的 `preferences_pb`。恢复前自动把当前数据另存到 `files/backup/pre-restore-*.zip`
> （只留最近 3 份），校验包名 / 数据库版本 / SQLite 头 / `user_version`，拒绝来自更高版本或
> 无法无损迁移的备份；写盘用临时文件 + 原子改名。
> ⑤ **恢复后要重启进程**：Room 与 DataStore 都持有文件句柄，只能退出重进。真机实测 Android 14+
> 的后台启动限制让「自己把自己拉回前台」彻底不可行（`startActivity`、`AlarmManager` 的 PendingIntent
> 都被 `Background activity launch blocked` 拦下，精确闹钟还要 `SCHEDULE_EXACT_ALARM`），
> 所以恢复完成后应用自动关闭，另发一条「数据已恢复」通知做兜底（ColorOS 会拦截低优先级通知）。
> ⑥ **CI**：`.github/workflows/build.yml` 跑单测 + 打 debug 包；`release.yml` 在推送
> `vX.Y.Z` Tag 时用 Secret 里的正式签名密钥构建、校验包名/版本号/签名、算 SHA-256、
> 生成 `version.json` 并自动建 Release 上传（APK + `.sha256` + `version.json`）。
> ⑦ **修死链**：设置页与关于页里写错的 `https://github.com/XiaozhuangXzsg/-Schedule`
> （多一个 `-`）改成 `UpdateConfig.REPO_URL`。
> ⑧ 构建 **225 用例 / 0 失败**，`versionCode = 30000`。
>
> **v2.2.1** — 桌面小组件去掉上下长横线并重划可点击区域 + 高校目录按新表补到 1358 所本科：
> ① **小组件**：删掉 `WidgetTheme` 的 `rimTop` / `rimBottom` 与 6 处画横线的 `Box`
> （CompactContent / FullContent / TomorrowContent 各上下一条），卡片只剩底色与圆角。
> 可点击区域从「整张卡片（含课程名与地点）」改为「**除课程行以外的全部区域**」：外层容器统一挂
> `openTodayIntent()`，`ClassRow` / `TomorrowClassCell` 以及 CompactContent 的课名、时间行改挂新增的
> `ConsumeTapAction`（空动作，把点击挡在课程格内）——RemoteViews 里没有「只吃掉点击」的修饰符，
> 所以用一个什么都不做的 `ActionCallback` 来挡。课程行之间的 4dp 间距仍会进软件（间距不属于课程行）。
> ② **课程列表分成两条路径**：只有一两节课时用普通 `Column`（不占满父容器），列表下方的留白仍属于外层
> 可点击区域；三节以上才用 `LazyColumn`——它编译成 ListView，会连同自身背景一起吞掉点击，但那时列表本来
> 就占满了，不影响体感。没有待办也没有待办图片时，待办列与竖分隔线整块隐藏（v2.1.9 已有，本版继续生效）。
> ③ **高校目录按新版 xlsx 重建**：只收 `办学层次 = 本科`——表内 3377 行里跳过专科 1540、办学层次未载明 425、
> 本科但无任何可用入口 54，最终 **1358 所 / 31 个省级地区 / 107 KB**（`app/src/main/assets/university_directory.tsv`）。
> 入口优先取「网页导入优先入口」(202 条)，为空退回「原目录入口」(1156 条)；其中 543 条是 http 站点。
> 与 v2.2.0 的 347 所相比移除 145 所专科、新增 1210 所本科。
> ④ **文案不再写死学校数**：选校页标题与导入弹窗副标题都改成从资产实时统计（`全国 N 所本科高校`）。
> ⑤ **首字母补两个字**：新数据里 `漯（漯河食品工程职业大学）` 与 `暨（暨南大学）` 落在 GB2312 二级区，
> 区位法判不出来，补进 `PinyinIndex.OVERRIDES` 后 1358 所**全部有首字母**，`#` 组为空。
> ⑥ 构建 **202 用例 / 0 失败**，`versionCode = 221`。
>
> **v2.2.0** — 「从教务系统导入课表」从兰州大学扩展到**全国 347 所高校**：
> ① **选校页**：新增 `ui/screen/UniversityPickerActivity.kt`。点「登录教务系统导入」不再直接打开兰大教务，
> 而是先进选校列表——**兰州大学置顶**（带「常用 / 置顶」标记），其余按拼音首字母分 A–Z 组、组头显示该组数量；
> 顶部搜索栏支持**校名 / 省份 / 拼音首字母**三种匹配（输入 `bjdx` 能找到北京大学）。选中后带着该校入口 URL
> 打开内置浏览器。
> ② **首字母不引第三方拼音库**：用 GB2312 一级汉字区位法——GB2312 的 3755 个一级汉字（`0xB0A1`–`0xD7F9`）
> 本身就按拼音排序，每个首字母对应一段连续编码，把首字编码成 GBK 查区间即可。
> `util/PinyinIndex.kt` 的 `RANGES` 存 23 个起始码（汉语拼音没有 I / U / V 开头的音节），
> 并且用**区间判定**而不是 Collator 锚点比较：开发中实测 zh_CN 的 `Collator` 会把同音不同字按次级权重
> 分出先后（`西` 排在 `夕` 前面），锚点法因此把 `西安交通大学` 判成 W 组。二级汉字覆盖不到的
> `亳 / 濮 / 衢` 补进 `OVERRIDES`；多音字 `重庆`（单字「重」读 zhòng、落在 Z 区）用 `PREFIX_OVERRIDES` 修到 C 组。
> 排序同样走 GBK 字节比较，与分组共用同一个键，两者自洽。
> ③ **数据资产**：`app/src/main/assets/university_directory.tsv`（347 行，`校名 ⇥ 省份 ⇥ 入口URL`，28 KB），
> 由《全国高校课表网址目录_2026-10-20.xlsx》的「网页导入优先核验」表导出，省份 100% 覆盖、校名无重复。
> ④ **内置浏览器不再假定兰大**：`LzuImportActivity` 新增 `EXTRA_UNIVERSITY_NAME`，标题先显示校名；
> 顶部提示条改为「已进入「XX」的教务系统，请先登录，再进入你的『课表 / 个人课表』页面……」——
> 全国各校菜单名不统一，所以提示只讲**要做的三步**，不假定菜单叫什么名字。
> ⑤ 构建 **202 用例 / 0 失败**（新增 `PinyinIndexTest` 8 条、`UniversityDirectoryTest` 6 条；
> 后者直接读资产文件校验 347 所、URL 合法、首字母全覆盖、兰大在位且 URL 正确、整体按拼音序排列），
> `versionCode = 220`。

> **v2.1.9** — ColorOS 17「流体设计」深度版 + 桌面卡片液态玻璃 + 4×2 小组件版式重构：
> ① **流体动效**：`TransitionStyle` 新增 `FLUID`，子页面转场不再用固定时长 tween，改由弹簧驱动
> `slideInHorizontally(it / 3)` + `scaleIn(0.94f)` + `fadeIn`，进入用
> `spring(DampingRatioLowBouncy, StiffnessMediumLow)`、退出用 `spring(NoBouncy, StiffnessMedium)`。
> 保留的约 5% 过冲是**故意的**——页面滑到位后「顿一下再吸住」才有质量感，换成 `NoBouncy`
> 就和普通减速没有体感差别。设置页「页面转场动画」因此从两档变三档。
> ② **柔性反馈**：新增 `ui/component/SoftHaptics.kt`，只用 `CLOCK_TICK` / `CONTEXT_CLICK` /
> `KEYBOARD_TAP` 三个**极轻**的系统 tick，刻意不用 `LONG_PRESS`（那一下是重震，落在开关和切页上太暴躁）。
> 通过 `LocalHapticsEnabled` + `rememberSoftHaptics()` 下发，接线在 `LiquidGlassSwitch`（所有设置开关、
> AI 多模态开关）、`CapsuleTabRow`（切标签页）、`CourseCard` / `CourseListRow`（长按进编辑）三处；
> 新增偏好 `hapticFeedback`（默认开）与设置页「触感反馈」开关。
> ③ **顺带修掉一个既有 bug**：`TodayScreen.kt` 的 `CourseListRow` 声明了 `onLongClick`、调用方也传了值，
> 但行内只有 `.clickable(onClick = …)` —— **今日页长按课程进编辑从来没生效过**。改为
> `combinedClickable` 后恢复。
> ④ **桌面卡片液态玻璃**：Glance 的 `background()` 只接受 `ColorProvider`、拿不到 `Brush`，做不出
> Compose 那种多层渐变玻璃，所以改用「底色 + 两条 1dp 边缘」表达厚度：`WidgetTheme` 新增 `rimTop` /
> `rimBottom`，深色取白 26% / 黑 30%，浅色取黑 7% / 黑 5%（浅色卡片是纯白，白色高光看不见）。
> 三张卡片顶部画高光条、底部画暗边。
> ⑤ **4×2 小组件版式重构**：`showTodoPane = todos.isNotEmpty() || images.isNotEmpty()`，无待办时
> **整条待办列与分隔线不渲染**，课程栏独占全宽（原来的「暂无待办 / 点卡片添加」分支已删除）。
> 点击区域同时重做——外层 `Column` 摘掉 `.clickable`（原来整卡可点，会和课程栏滑动抢手势），
> 只留标题行 `Row(fillMaxWidth().clickable(openTodayIntent()))` 可点。真机实测：点课程行
> `topResumedActivity` 仍是 launcher，点标题行才拉起 `MainActivity`。
> ⑥ 构建 **188 用例 / 0 失败**（本次未新增用例），`versionCode = 219`。

> **v2.1.8** — 面向 **Android 17 / ColorOS 17** 的「聚焦版」适配 + 凝光玻璃材质铺满全应用：
> ① **修掉一个会杀进程的隐患**：Android 17（API 37）起，系统对 `RemoteViews` 里 Bitmap 与 Icon
> 的**总内存**施加硬限制 `1.5 × 屏宽 × 屏高 × 4` 字节，超出时 `updateAppWidget` 抛
> `IllegalArgumentException` 并**直接杀掉应用进程**——不是忽略、不是降级。本应用最多往卡片里放
> 3 张待办图片，原先进程与卡片共用 1440px 上限：单张 `1440×1440×4 ≈ 8.3 MB`、三张 ≈ 24.9 MB，
> 而本机上限只有约 21.2 MB，**已经超了**。新增 `widget/WidgetImageBudget.kt` 给卡片单开一条边长通路：
> `remoteViewsLimitBytes()` 复刻系统公式，`maxDimension()` 按 `SHARE = 0.25` 的份额与 `imageCount`
> 反推单张边长（`sqrt(bytes / 4)`，夹在 `MIN_PX = 128` 与 `HARD_CAP_PX = 512` 之间）。本机实测
> 512 px 封顶，相比 1440 px 降到约 1/2.8 边长、约 1/8 内存。`util/ImageLoader.kt` 增加 `maxDimension`
> 参数（默认仍 1440，应用内其他调用点行为不变），`widget/GlanceWidgets.kt` 的 `loadTodoImages`
> 改走新预算。新增 `WidgetImageBudgetTest.kt`（6 例）锁定公式、份额切分、上下限与不超总额。
> ② **`targetSdk` 36 → 37**，正式对 Android 17 声明适配。
> ③ **凝光玻璃材质**（ColorOS 17「流体设计」三大核心之一）：新增 `ui/component/GlassSurface.kt`，
> 提供 `GlassSurface`（大块卡片用）与 `Modifier.glassLayer`（已有 `.background()` 的场景直接替换、
> 不改布局结构）两种入口，质感由「底色 + 纵向厚度渐变 + 1dp 上亮下暗光感描边」构成；
> 大块卡片用 `glassCardAlpha()`（深 0.78 / 浅 0.82），弹窗用 `glassPanelAlpha()`（深 0.90 / 浅 0.92）
> 保证正文可读。落地范围：`SettingsScreen.kt` 的 `SettingsGroup`（设置页 6 个分组卡片）、
> `CapsuleTabRow.kt` 的底栏胶囊改为 `GlassSurface`，另有 **22 处 `.glassLayer(...)` 分布在 16 个
> 对话框/浮层文件**；全项目 `background(MaterialTheme.colorScheme.surface)` 直接调用**残留 0 处**，
> 合计触及 **19 个文件**。
> ④ **踩坑**：底色不透明度第一版写成 `tint.copy(alpha = tintAlpha)`，而 `copy(alpha =)` 是**覆盖**
> 不是相乘——`capsuleContainer` 这类本身就带透明度的主题色被 `copy(alpha = 1f)` 变成实色，
> 底栏胶囊在真机上直接变成一整块不透明粉色。改为 `tint.copy(alpha = tint.alpha * tintAlpha)`
> 后两种场景都对：实色传默认值即可，半透明色传 `1f` 表示「保持原样，只叠玻璃层」。
> 这个 bug 单元测试覆盖不到，是**真机截图对比**发现的。
> ⑤ **`ColorOS 17` 的流体动效与柔性反馈、以及把卡片加进负一屏，本版都没做**。负一屏经查
> 架构上进不去：`dumpsys appwidget` 显示设备上唯一的 `AppWidgetHost` 是 `com.android.launcher`，
> 负一屏 `com.coloros.assistantscreen` **不是** `AppWidgetHost`；可行路径只有 OPPO 的「智慧服务卡片」
> （快应用卡片 + `com.oplus.seedling.action.SEEDLING_CARD`，需在 open.oppomobile.com 注册并过审），
> 方案已写成 `deliverables/娅娅课表-v2.1.8/OPPO-负一屏卡片接入方案.md`。
> ⑥ **测试**：16 个测试类 / **188 个用例 / 0 failures / 0 errors**（v2.1.7 为 182，本版 +6）。

> **v2.1.7** — 三处改动：
> ① **教务系统导入入口改到手机版**：`LzuImportActivity.START_URL` 由 `http://my.lzu.edu.cn/`（个人工作台）
> 改为 **`https://jwk.lzu.edu.cn/academic/index_frame.jsp`**。工作台是桌面布局的门户，手机上要缩放拖拽
> 才能点，登录后还得自己找教务入口；`index_frame.jsp` 是教务系统的移动版框架页，进去就是课表菜单。
> 同时 WebView 的 UA **按入口分流**：`jwk.lzu.edu.cn` 用**手机 UA**（手机版框架页只在手机 UA 下
> 才渲染移动布局），其余页面仍沿用原来的桌面 UA（这些页面反过来在手机 UA 下会错乱）。
> 导入页提示条与新手引导的步骤文案已由「四步」简化为「登录 → 打开个人课表 → 导入」三步。
> ② **「桌面卡片背景透明度」并入「个性化」分组**：原先是设置页里独立的「桌面卡片」分组，现移入
> **个性化**模块、紧跟在「背景与透明度」入口之后，标题补全为「桌面卡片背景透明度」以免脱离分组标题后
> 失去上下文。写入路径未变（拖动只改草稿、松手才写 DataStore 并请求刷新卡片）。
> ③ **今日页已结束的课真正「置灰」**：之前只是给整张卡片降不透明度（`.alpha(0.55f)`）并挂一个
> 「已结束」小字，色相还在、看着像渲染出错。新增 `ui/theme/ColorExt.kt` 的 **`Color.desaturated(amount)`**
> —— 用 Rec.601 亮度权重把颜色往中性灰拉，**保留亮度、抽掉色相**：对比度不变（浅色主题下正文不会
> 糊在底色上），但颜色身份消失。今日页 `CourseListRow` 用 `desaturated(0.9f)` / `desaturated(0.78f)`
> 分别处理底色与文字，周视图 `CourseCard` 的 `isPast` 分支同步改用同一套处理，避免两处观感不一致。
> ④ **测试**：新增 `ui/theme/ColorExtTest.kt`（5 例）锁定「amount=0 原样返回」「全去饱和为真灰」
> 「亮度守恒」「alpha 不变」「通道差收窄」五条性质。

> **v2.1.6** — 全应用的开关键换成 iOS 26 / 27 的「液态玻璃（Liquid Glass）」样式：
> ① **新增 `ui/component/LiquidGlassSwitch.kt`**，按 Apple 对 Liquid Glass 的三条描述做：
> **半透明材质**（底色用真 alpha，深色关态 `White 15%`、开态 `primary 94%`，能透出下方卡片色与
> 用户自定义的页面背景图）、**镜面高光边**（1dp 描边，纵向 `White 62% → 10%`，配上沿聚光的下沿压暗
> 与斜向反光）、**厚度与折射感**（玻璃球用偏左上的径向高光 + 球底透主题色 + 0.8dp 玻璃壳 + 柔和投影）。
> ② **液态手感的做法**：拨动时玻璃球沿运动方向拉伸（总宽 +30%、总高 −9%，球心前移拉伸量的一半，
> 于是「后缘不动、前缘探出去」）。拉伸量取 **`|sin(πt)|`** —— 它在**行程两端恒为 0、只在中间最大**，
> 所以静止态与普通开关的外观完全一致，不会出现停住时球被拉扁的怪异效果；位移用
> `spring(DampingRatioLowBouncy, StiffnessMediumLow)`，到位有轻微回弹，按下时球放大 6%。
> ③ **尺寸完全对齐 Material3**：轨道同为 52 × 32 dp，并同样用 `minimumInteractiveComponentSize()`
> 把触摸区撑到 48 dp，所以**换样式没有改变任何页面的排版高度**。
> ④ **接线只动两处**：`ui/screen/dialog/TimetableSettingsDialog.kt` 的 `SettingSwitch`
> （**全应用所有设置项开关的唯一入口**，改它即覆盖 `SettingsScreen.kt` 的 12 个调用点与
> `TimetableSettingsDialog.kt` 的 7 个调用点）与 `ui/ai/AiConfigScreen.kt` 的「支持图片输入（多模态）」。
> 全项目至此**没有任何 `androidx.compose.material3.Switch` 引用**（也本来就没有 Checkbox /
> RadioButton / SegmentedButton / ToggleButton 和 XML 布局）。`SettingSwitch` 上补了 KDoc，
> 注明新增设置项请继续调用它、不要直接用 Material3 的 `Switch`。
> ⑤ **没有做真正的背景模糊**：Compose 的 `Modifier.blur` 只能模糊自身内容、`RenderEffect` 不跨窗口，
> 这里只用高光与半透明近似 —— 好处是**没有引入任何第三方依赖**。
> ⑥ **已真机验证**（OnePlus 15 / PLK110 / density 560）：设置页「显示」6 个 + 「课表显示」2 个开关
> 与模型配置页的多模态开关全部换新，开/关两种状态、点击切换均正确，行高未变；
> 把系统 `animator_duration_scale` 临时调到 10 后抓到了**拨动中玻璃球被拉成横向椭圆的帧**，
> 证明液态拉伸真实生效（验证后已还原为 1）。
> ⑦ **测试**：**177 tests / 0 failures / 0 errors**，release 59,406 KB、debug 67,262 KB。
> ⑧ **边界**：浅色模式与哥伦比娅风格档未实机验证（开关取 `MaterialTheme.colorScheme.primary`，
> 理论上自动跟随）；静止态未与 Material3 `Switch` 做像素级比对；未构造系统「移除动画」场景。
>
> **v2.1.5** — 时间轴条的圆球改 5dp、圆球左边不再有线，并顺手把线的纵向定位摆正：
> ① **圆球 3dp → 5dp**。v2.1.4 取的是「与色条同宽」，但同宽时圆球看着只是色条截面的一小截、
> 没有封口的实心感；改成比色条两边各探出 1dp，像那一列的顶端帽。
> ② **色条中线要用「色条半宽」算**。`BarCenterFromDayStart` 原来写的是 `… + BallSize / 2`，
> 球与色条同宽时正好等于色条中线，现在两者不等宽了，继续用半个球会让圆心整体**右偏 1dp**。
> 已改成 `… + CourseCardMetrics.barWidth(compact = true) / 2`，并加了一条单测把这两种算法钉开。
> ③ **圆球左边不再有线**：新增 `WeekNowLineMetrics.lineStartFromDayStart(ballSize)`
> = 色条中线 − 半个球，即线的左端就是圆球左边缘；再配 `lineWidthFromDayStart(dayWidth, ballSize)`
> 把右端算到日列右边界（列太窄兜底 0，不会出现负宽度）。`WeekNowLine` 因此**去掉了 `ballCenterX` 参数** ——
> 圆球就是那个 Box 的第一个子节点，少一个能算错的输入。没有圆球的 `PLAIN` 样式退回色条中线，
> 仍然不是从日列最左边起画，换样式时线头不会突然多出一截。
> ④ **线的纵向定位摆正**：原来让这段高度的**上边缘**贴住 `offsetY`，于是球越大线被压得越往下
> （7dp 球偏 3.5dp、5dp 球仍偏 2.5dp）。改成让这段高度的**中线**落在 `offsetY` 上 ——
> 「现在」那条线才真的在现在这个点上；也避免「球从 3dp 变 5dp」把线额外往下拖 1dp。
> ⑤ **测试**：**177 tests / 0 failures / 0 errors**（`WeekGridMetricsTest` 由 16 例增至 20 例，
> 新增「圆球略宽于色条且恰为 5dp」「球心不能用半个球来算」「线左端 = 圆球左边缘」「线右端 = 日列右边界」
> 「PLAIN 样式从色条中线起画且列太窄兜底 0」）。
> ⑥ **已真机验证**（OnePlus 15 / PLK110 / density 560，1dp = 3.5px），逐像素扫描：
> 圆球纵向 18px = 5.14dp、课程色条 11px = 3.14dp、圆球圆心 x≈199.5 与色条中心 x=199.0 重合、
> 线的左端 x=191 正好是圆球左边缘（**全图最靠左的红像素就是它，圆球左边干净**）、
> 线的右端 x=328 未越到周二列（周一列右边界 328.7）。
> ⑦ **边界**：只验证了默认的 DOT 样式，`BOLD` / `DASHED` / `PLAIN` 未在真机逐个切换（仅有单测覆盖算术）；
> v2.1.4 未留下截图，故无「3dp → 5dp」的直接前后对照图；横滑切周时线不跟手是改动前就有的行为。
>
> **v2.1.4** — 周视图「当前时间轴条」收进今天那一列，左端圆球与课程左侧色条对齐：
> ① **改前**：`WeekNowLine` 是 `Box` 的直接子节点，用 `fillMaxWidth()` 横贯整个宽度 ——
> 红线从最左边（压在固定时间轴上）一直拉到屏幕右边缘，左端圆球也贴在 x=0。
> 既看不出「现在对应的是哪一天」，圆球（7dp / 加粗 11dp）又比课程卡左侧色条（3dp）胖一圈、位置也对不上。
> ② **限宽**：`WeekScreen` 里那个外层 `Box` 改成 `BoxWithConstraints`，据此算出
> `gridWidth = maxWidth - GridEndPadding - TimeColumnWidth`，再按今天在第几列得到
> `columnStart / columnWidth`，只画今天那一列。今天不在显示范围里（隐藏了周末又正好是周末）就**不画**。
> 列宽算法与 `WeekDayGrid` 的 `maxWidth / visibleDays.size` 完全一致，所以红条与日列严格重合。
> ③ **圆球对齐色条**：圆球直径改为**与课程卡左侧色条同宽**（`CourseCardMetrics.barWidth(compact = true)` = 3dp），
> 圆心落在色条中线上 = 日列左边 + `CardInset`(1.5dp) + `barStart`(4dp) + 半个色条宽。
> 为此把色条的 `barStart` / `barWidth` 抽成 `ui\component\CourseCard.kt` 的 `CourseCardMetrics` 并让卡片自己引用，
> 日列给卡片的 `padding(horizontal = 1.5.dp)` 也改成引用 `WeekNowLineMetrics.CardInset` ——
> 三处数字同源，以后改色条不会再让圆球错位。
> ④ **纯算术抽出来可单测**：新增 `ui\screen\WeekGridMetrics.kt` 的 `WeekNowLineMetrics`
> （`CardInset` / `BallSize` / `BarCenterFromDayStart` / `dayStart(dayIndex, gridWidth, columns)` /
> `dayWidth(gridWidth, columns)`），沿用 v2.1.0 抽 `WeekGridMetrics.kt` 的先例。
> 下标会被夹在 `0 until columns` 内，列数为 0 时返回 0，越界也不会把红条画到网格区外面。
> ⑤ **今天在第几列的判定**：直接用本周 `dates.indexOfFirst { it == today }`，
> **刻意不用** `dateOf()` —— 它在数据没加载出来时会退回「今天」，7 天全等于今天，红条会错误地画到第 0 列。
> ⑥ **测试**：**173 tests / 0 failures / 0 errors**（`WeekGridMetricsTest` 由 10 例增至 16 例，
> 新增「圆球与色条同宽」「圆球压在色条中线上」「日列刚好铺满网格区」「隐藏周末后列宽变宽」「列数 / 下标退化不越界」5 条）。
> ⑦ **边界（如实记录）**：按用户要求**未做实机测试**（本轮 `adb devices` 也为空，设备当时未连接），
> 因此这一版**没有真机截图**，观感以单元测试锁住的几何为准；手机上仍装着 v2.1.3，
> 需要时再 `adb install -r` 升级后自行查看。
>
> **v2.1.3** — 修掉「改了卡片透明度 / 改了课表，桌面卡片不动，必须完全退出应用再进来才生效」；
> 次日课程预览字号调大且不越界：
> ① **根因是 Glance 的会话缓存，不是刷新时机**。`GlanceAppWidget.provideGlance()` **每个会话只执行一次**，
> 会话在应用进程存活期间一直复用；之后调用的 `updateAll()` 只是把**已经存在的组合重新渲染一遍**。
> 原来的写法把数据读在 `provideGlance` 里、再被 `provideContent { FullContent(data) }` 捕获成常量，
> 于是这份数据跟着会话被永久缓存 —— 卡片一直显示旧值，**只有杀掉进程（会话重建）才会重新读一次**。
> 插桩后的 `logcat` 直接拍到：进程刚起那次刷新有 `provideGlance(Full) 进入 id=AppWidgetId(appWidgetId=41)` +
> `loadToday 读取偏好：alpha=1.0`，同一进程稍后的两轮刷新**完全没有 `provideGlance`**，推的还是旧 RemoteViews。
> ② **修法**：新增 `widget\WidgetRenderCache.kt`（`today` / `week` 两个 `mutableStateOf<WidgetData?>`）。
> 四张卡片的 `provideGlance` 统一改成「先写缓存、组合读缓存」：
> `val initial = runCatching { WidgetDataLoader.loadToday(context) }.getOrElse { … }` →
> `WidgetRenderCache.today.value = initial` → `provideContent { FullContent(WidgetRenderCache.today.value ?: initial) }`。
> `WidgetRefresher.refreshAllOnce` 在每轮 `updateAll` 之前调新增的 `publishLatestData()`，
> 把刚读到的数据写进缓存（只有桌面上确实存在对应卡片时才读，`loadWeek` 这类较重的更是如此），
> 再 `delay(STATE_SETTLE_MS = 40L)` 让组合先重跑。**状态一变组合就失效，下一轮渲染必然是刚写进去的数据。**
> ③ **顺带修掉一个真 bug**：`refreshAllOnce` 的重试循环原来是
> `repeat(MAX_ATTEMPTS) { … if (result.isSuccess) { ok = true; return@repeat } … }` ——
> Kotlin 的 `return@repeat` 等于 `continue` 而非 `break`，**成功之后还会把 `updateAll` 白做满 3 次**，
> 反而让启动器拿到多份互相覆盖的 RemoteViews。已改为 `for (attempt in 0 until MAX_ATTEMPTS) { …; break }`。
> ④ **新增兜底监听**：`widget\WidgetVisiblePrefs.kt`（`widgetBgAlpha` / `themePreset` / `columbinaStyle` /
> `darkTheme` / `followSystemTheme` / `semesterStart` / `totalWeeks`，即卡片真正读的那几个偏好）+
> `TimetableApp.watchWidgetPreferences()`：收集偏好流、`map` 成 `WidgetVisiblePrefs`、`distinctUntilChanged()`、`drop(1)`
> （第一个值是当前值，冷启动那轮已推过），一变就 `WidgetRefresher.request(this, "偏好变更")`。
> 这样「某个设置改了卡片不动」不再依赖每个 setter 记得手动刷新；以后往卡片加一项受偏好控制的显示，
> 只要在这个 data class 补一个字段就自动生效。**故意只放卡片真正读的字段**，改昵称 / 提醒时间不会白唤醒启动器。
> ⑤ **次日课程预览字号调大**：「课名」`10.sp → 11.sp`、「时间 + 地点」`8.sp → 9.sp`，
> 可滑动版式的固定行高 `CellRowHeight` `46.dp → 50.dp`。两个字号抽成 `TomorrowGridLayout.CellNameSp / CellDetailSp`
> 常量，单元测试改为用真实常量估算「行高还放得下两行字」，并新增一例盯住「课名 ≥ 10sp、时间地点 ≥ 8sp、课名 > 时间地点」。
> 最长的课名「生物多样性与保护(通识)」在 11sp 下仍完整落在格内（格内可用约 135dp，该名字约 117dp）。
> ⑥ **真机证据**（一加 15 / ColorOS 16，2026-10-11 周日）：**同一个进程（pid 9165）内**把透明度先拖到 35%、再拖回 100%，
> 桌面卡片两次都立刻跟着变（半透明 ↔ 不透明），全程没有杀进程、没有重开应用 —— 这正是 v2.1.2 做不到的。
> 每轮刷新前都重新打出 `loadToday 读取偏好：alpha=…`，值就是刚改的那个；每轮推的都是活实例 `实例 id=[41]`。
> 另外用 AI 给 2026-10-12 加了一节 TEST-FONT，按 HOME 后卡片立刻由「共有 4 门课要上」变「共有 5 门课要上」，
> 撤销后立刻变回 4 门（数据变更路径同样即时）。
> 见 `deliverables\娅娅课表-v2.1.3\`（含 `刷新日志摘录.txt`）。
> ⑦ **边界（如实记录）**：本次只实机复看了「明日预告」这一种版式，`当前课程` / `今日课表` / `本周课表`
> 三张卡片的**排版**没有一一复看（刷新逻辑是同一条）；`WidgetRefreshWorker` 的 30 分钟兜底与跨日切换未构造场景；
> 250×110 dp 这个最小尺寸未复测（v2.1.1 时已知该尺寸本身就会被裁）；`dumpsys` 里仍能看到一个历史遗留的失效实例 id=38
> （系统里已无此实例，不影响刷新，会随进程重启消失）。
> ⑧ **测试**：**168 tests / 0 failures / 0 errors**（比 v2.1.2 多 1 例：`TomorrowGridLayoutTest` 由 7 例增至 8 例）。
>
> **v2.1.2** — 桌面卡片刷新时机补齐「退出应用」这一条（改透明度 / 改课表后卡片迟迟不更新）：
> ① **问题**。原来只在「冷启动」「回到前台」「数据变更」三处推卡片，`onActivityStopped` 里**只递减 Activity 计数、完全不刷新**。
> 于是「在应用里改完东西再回桌面」这条最常见的路径，完全依赖改动当时那一次推送；那一次如果被启动器丢掉或延迟，
> 卡片就要等到 30 分钟后的兜底 Worker 才会更新 —— 也就是用户说的「要过很久才能应用到位」。
> ② **改动**（`app\src\main\java\com\university\timetable\TimetableApp.kt`）：在 `onActivityStopped` 里补上
> `if (startedActivities == 0 && !activity.isChangingConfigurations) WidgetRefresher.request(activity.applicationContext, "退到后台")`。
> 用 `startedActivities` 计数保证只有真正的「应用整体退到后台」（1 → 0）才触发，应用内部页面互切（1 ↔ 2）不触发；
> 用 `isChangingConfigurations` 排除旋转 / 深浅色切换引起的 Activity 重建，避免误报。仍然用**非阻塞**的 `WidgetRefresher.request`
> （应用级协程），因为回调在主线程，且进程退后台后还会存活一小段时间。
> ③ 现在共四条显式刷新时机：**冷启动 / 回到前台 / 退到后台 / 数据变更**，外加 30 分钟后台 Worker 兜底。
> ④ **真机证据**（一加 15 / ColorOS 16，2026-10-11 周日）：`logcat` 依次打出
> `刷新请求 reason=冷启动` → `回到前台` → `退到后台` → `回到前台` → `退到后台`，每次都在约 20~30 毫秒后
> `已提交 1 个实例的更新`，无失败轮次；用 AI 把明天的课从 5 门加到 6 门后按 HOME，连拍帧显示
> **桌面一露出来卡片就是「共有 6 门课要上」**，没有先显示旧值再跳变；把透明度从 100% 拖到 35% 后同样即时生效。
> 见 `deliverables\娅娅课表-v2.1.2\真机验证截图\`。
> ⑤ **边界（如实记录）**：设备 `screencap` 单张约 1.1 秒（1272×2772），本机也没有可用的 `cmd appwidget` / 视频解码器，
> 因此**测不出毫秒级延迟**，也**没有**做 v2.1.1 ↔ v2.1.2 的对照实验（本机测到的 1~2 秒里绝大部分是应用自身的退场动画，
> 对照区分度不足）。本版只改刷新时机，未改任何渲染或数据逻辑。
> ⑥ **测试**：**167 tests / 0 failures / 0 errors**（本版无新增用例，改动集中在生命周期回调，既有 `WidgetRefresh` / `RefreshCoalescer` 用例继续覆盖）。
>
> **v2.1.1** — 桌面卡片两处体验修正（次日课程预览要留白、且课多于 4 门时可上下滑动 / 卡片背景可调透明度）：
> ① **次日课程预览不再边贴边**。`widget\GlanceWidgets.kt` 的 `TomorrowClassCell` 圆角 `10.dp → 12.dp`、
> 内边距由 `padding(end = 6.dp, top = 3.dp, bottom = 3.dp)` 放宽到 `padding(end = 9.dp, top = 7.dp, bottom = 7.dp)`，
> 彩条与文字间距 `6.dp → 8.dp`；格子之间、行与行之间都插入 `TomorrowGridLayout.CellGap = 8.dp` 的 `Spacer`。
> ② **课多于 4 门时自动改为可上下滑动**。`TomorrowGrid` 变成一个调度器：
> `if (TomorrowGridLayout.needsScroll(classes.size)) ScrollableTomorrowGrid(...) else StaticTomorrowGrid(...)`。
> ≤4 门走 `StaticTomorrowGrid`（`Column` + 每行 `Row(fillMaxWidth().defaultWeight())`，两格各 `defaultWeight().fillMaxHeight()`，
> 把 4×2 的高度平分给两行）；>4 门走 `ScrollableTomorrowGrid`（`LazyColumn`，每格固定 `CellRowHeight = 46.dp`）。
> **原来「`classes.take(4)` + 还有 N 门课」的截断已删除** —— 第 5 门起不再被丢掉，而是可以滑出来。
> ③ **把这条曾经容易写错的规则抽成纯声明以便单测**：新增 `widget\TomorrowGridLayout.kt`
> （`CellGap` / `CellRowHeight` / `MaxStaticCells = 4` / `Columns = 2` / `needsScroll(classCount)` / `rowCount(classCount)`），
> 配套新增 `app\src\test\java\com\university\timetable\widget\TomorrowGridLayoutTest.kt`（7 例，
> 其中明确钉住「第 5 门课起必须滚动」）。做法沿用 v2.1.0 抽 `ui\screen\WeekGridMetrics.kt` 的先例。
> ④ **卡片背景透明度可调**。`PreferencesRepository.UiPreferences` 新增 `widgetBgAlpha: Float = 1f` 与
> `MIN_WIDGET_BG_ALPHA = 0.35f`，`widgetThemeOf(prefs, context)` 的 `cardBg` 改为
> `FixedColorProvider(withAlpha(scheme.extras.surfaceCard.toArgb(), prefs.widgetBgAlpha))` —— **只改 alpha 通道，色相仍由主题决定**。
> 设置页在「课表显示」与「提醒与流体云」之间新增 **`桌面卡片`** 分组，里面是 `WidgetAlphaSlider`：
> 拖动时只改本地草稿、`onValueChangeFinished` 才落库（否则拖一次会连写十几次 DataStore 并排队十几次卡片刷新），
> 松手后经 `MainViewModel.setWidgetBgAlpha` 写库并触发一次卡片刷新。
> ⑤ **真机证据**（一加 15 / ColorOS 16，2026-10-11 周日，次日=10-12 第 7 周周一 4 门课）：见
> `deliverables\娅娅课表-v2.1.1\真机验证截图\`。同一张卡片被渲染在 100% 与 35% 两档下（洋红→青渐变底色在 35% 时透出、
> 文字与色条仍清晰可读）；用 AI 临时加出第 5 门课 TEST-SCROLL 后，卡片标题变为「共有 5 门课要上」、
> 第 3 行被裁在卡片下沿，**向上滑动后第 5 门课完整可见**；验证完毕已撤销该临时课并把透明度还原为 100%。
> ⑥ **测试**：新增 `TomorrowGridLayoutTest`（7 例），总数 **167 tests / 0 failures / 0 errors**。
>
> **v2.1.0** — 六项体验改造（组件即时刷新 / 卡片课表可滑动 / 周视图行高等比 / iOS 式时间轴条 / 今日页波浪进度 / 新手引导）：
> ① **桌面组件刷新链路加固**。`widget\WidgetRefresh.kt` 的 `refreshAllOnce` 对四张卡片（当前课程 / 待办+课表 / 今日课表 / 本周课表）
> 改为「最多 3 次重试 + 递增退避（120ms × 次数）」，全部失败才计失败；成功后调新增的
> `notifyCollections(context, widgetClass, ids)`：Glance 的 `LazyColumn` 在 AppWidget 里落地为集合视图（`android.R.id.list`），
> 只 `updateAll` 时部分启动器不会重绘子项，所以再用
> `AppWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, android.R.id.list)` 催一次。仍然**只**走 `updateAll`，
> 绝不叠加逐个 `update(context, id)`——两条路径重复刷新会让启动器拿到未绑定 widgetId 的 RemoteViews，触发常驻的
> 「无法添加微件。」错误视图。写入路径不变：`MainViewModel` 改库后由 `TimetableApp` 的 `DataChangeBus` 监听统一触发
> `WidgetRefresher.request(this, "数据变更:${change.kind.name}")`，`RefreshCoalescer` 用「脏标记 + tryLock + 退出前再确认」合并请求。
> ② **4×2 卡片右侧课程栏可上下滑动**：右列由 `Column + data.classes.take(5)` 改为 `LazyColumn`（Glance 里唯一能纵向滚动的容器），
> 并在数据层就把**已上完的课过滤掉**：`WidgetDataLoader.loadToday` 先算出完整一天的 `allClasses`，
> `classes = allClasses.filter { !it.isPast }`，副标题相应变成「今天没有课 / 今天的课已结束 / 共 N 节课」。
> 注意 `allClassesEnded` 必须仍用**未过滤**的 `allClasses` 判定，否则「明日预告」版式永远不会出现。
> ③ **周视图行高改为按节次自身时长等比**，基准 45 分钟。原实现用「到下一节开始」的间隔，把课间与晚饭时间也算进了行高，
> 于是 17:25-18:10 的第 10 节量到 19:00 的晚课、得到 95 分钟，行高 101.4dp，而普通 45 分钟行只有 58.6dp —— 正是用户报的
> 「第十课这一栏上下太高了」。现抽出 `ui\screen\WeekGridMetrics.kt`（`rowHeightFor` / `nowLineOffset` / `currentMinutesOfDay`，
> 纯函数、可单测），行高只由 `period.endMinutes - period.startMinutes` 决定。真机复测：45 分钟行全部 224px（=64dp，完全等高），
> 第 5、6 节（55 分钟）274px = 64 × 55/45。
> ④ **iOS 日历式时间轴条**：`WeekScreen` 新增 `WeekNowLine`，在时间轴与所有日列之上横贯一条当前时刻线，左端带圆球；
> 颜色默认取主题 `nowIndicator`，可在「个性化 → 课表显示」里改成四种线型（细线圆点 / 加粗 / 仅细线 / 虚线）或任选主题色；
> 用 `produceState` 每 30 秒重算一次位置，只在「正在看本周」且当前时刻落在网格可视范围内时显示。
> ⑤ **今日页正在上的课显示波浪进度**：新增 `ui\component\WaveProgress.kt` 的 `Modifier.waveProgress(...)`，
> 用 `rememberInfiniteTransition` 驱动正弦波（28 段、1.5 个周期），按「已上时长 ÷ 总时长」填充，
> 方向可在设置里选「从左到右 / 从下到上」。做成 Modifier 而不是包裹 Box，避免打乱课程行紧凑的测量顺序。
> ⑥ **新手引导**（新增 `ui\tutorial\` 五个文件）：欢迎卡片 + 聚光灯高亮 + 分步卡片，共 10 步，覆盖导入课表、AI 助手、
> 个性化、调课、桌面卡片与结束页（作者 QQ / 验证信息 / 复制按钮）。遮罩用**四块矩形围出中间的洞**而不是整屏 scrim 挖透明洞，
> 保证高亮目标仍可点击；需要实际操作的步骤靠**锚点真实出现或消失**推进（`Modifier.tutorialAnchor(id)` + `onGloballyPositioned`
> 上报 `boundsInRoot()`），**没有任何定时器或固定延时**。首次启动在原有 `IntroDialog` 之后自动弹出（`prefs.introSeen && !prefs.tutorialSeen`），
> 完成或跳过后写 `prefs.tutorialSeen`、后续启动不再自动弹，设置页「新手引导」入口可随时重看。
> 导入页顶部提示同步改为四步说明，并明确区分教务系统的「个人课表」与本软件的「导入课表」。
> ⑦ **真机证据**（一加 15 / ColorOS 16，2026-10-09，第 6 周）：行高实测间距 224/224/224/249/274/249/224/224 px；
> 时间轴条在周视图下滑后于 `y=2186-2210, x=0-1269` 检出一条横贯整屏的红线（当时 21:43，正落在第 13/14 节之间）；
> 桌面卡片显示「共 1 节课 / TEST-WAVE」，当天更早的 4 门课全部不显示；新手引导的欢迎卡片、①-⑩ 步、
> 复制 QQ 号（Toast「已复制 QQ：2741369898（验证信息：课表）」）与结束页均与文案一致。
> ⑧ **测试**：新增 `app\src\test\java\com\university\timetable\ui\screen\WeekGridMetricsTest.kt`（11 例），
> 总数 **160 tests / 0 failures / 0 errors**。
>
> **v2.0.1** — 补掉「提醒」这条最后不一致的链路，并加固 AI 动作解析：
> ① **提醒原来读的是旧路径**。`notification\NextClassResolver.resolve()` 用的是 `repo.classesOn(...)`——
> 这条路径既不读节假日也不读日期变更，于是出现「AI 刚把课调到别的教室/别的日子，通知栏与流体云还在按旧课表提醒」。
> 现在今天与顺延 7 天的两处查询都改为 `repo.effectiveBlocksOnce(date, week)`（与界面同一条 `DayPlanner` 链路），
> 并把今天的分支条件收紧为 `todayWeek in 1..prefs.totalWeeks`。副作用是好的：调休补课日不再把星期标错，
> 因为 `pinToDate()` 之后 `block.dayOfWeek` 已经是**日期自己的**星期。
> ② **新增 `notification\FluidCloudRefresher`**（应用级协程 + `running`/`pending` 双标志做请求合并与串行）。
> 在此之前只有 `MainViewModel` 会刷新流体云，而**内置 AI 是直接调仓库改库、绕开 ViewModel 的**，
> 必然漏刷新。现在 `TimetableApp` 的 `DataChangeBus` 监听里加了
> `if (change.kind.affectsSchedule) FluidCloudRefresher.request(this, "数据变更:${change.kind.name}")`，
> `MainViewModel.refreshFluidCloud()` 也改成转调同一个入口，两条写入路径合一。
> `DataKind.affectsSchedule` 只在 `TODO` 时为 false —— 待办变化不影响「下一节课是哪一节」。
> ③ **AI 上下文里的「今天 / 本周」也换到了实际课表**：`AiContextBuilder.todaySummary` 改读
> `repo.daySnapshotOnce(...)`，先给**实际课表**；若当天有基础排课但实际为空（放假/停课/整天调出），
> 会额外说明原因并列出**基础课表**——否则模型会以为「今天什么都没排」而拒绝调课。
> `weekSummary` 改走新增的 `repo.weekBlocksOnce(week, semesterStart, totalWeeks)`。
> ④ **动作解析加固**：`FlatJson` 新增 `stringAny(...)`，8 处课程名解析由严格的 `string("name")` 改为
> `stringAny("name", "course_name", "courseName", "title")`。实测模型会写成 `course_name`，
> 而单键解析失败是**静默丢弃整条动作**，用户只看到「AI 说它要改，确认框却没出现」。同时在 `AiActionParser.parse`
> 里加了日志：解析失败打 `动作块解析失败，已忽略。原始内容：…`，成功打 `已解析动作：<类型>`，可观测性到位。
> ⑤ **真机证据**（一加 15 / ColorOS 16，2026-10-08 22:59）：让 AI 把 2026-10-09 第 1-2 节「解析几何」的教室改成
> `TEST-BUILD-B999`，日志依次为 `DataChange: 数据已提交 kind=OVERRIDE id=15 version=1 source=换教室 2026-10-09`、
> `FluidCloud: 已刷新（reason=数据变更:OVERRIDE）：下一节 · 解析几何 · 周五 08:30-10:10 · TEST-BUILD-B999`；
> `dumpsys notification` 的 `android.subText` 从 `周五 08:30-10:10 · 天山堂A408` 变为 `…TEST-BUILD-B999`；
> 桌面 4×2 卡片「明日周五」三格中的解析几何地点同步变为 `TEST-BUILD-B999`。点「撤销」后
> `DataChange: … source=撤销 2026-10-09 的调课`、`FluidCloud: 已刷新（reason=数据变更:OVERRIDE）：…· 天山堂A408`，
> 通知与卡片均还原。
> ⑤′ **停课取证更硬**：让 AI「停课 2026-10-09」，日志为 `source=停课 2026-10-09` →
> `FluidCloud: 已刷新（reason=数据变更:OVERRIDE）：下一节 · 晚自习 · 周六 19:00-20:40 · 明远楼A424`。
> 提醒没有停在被停课的周五，而是落到 10-10（按调休规则上**周四**的课）的晚自习——旧实现不可能得出这个结果。
> ⑥ **提示词侧兜底**：`change_room` / `cancel_day` 实测都出现过「模型只在文字里说『我将为你…，请确认』，
> 整条回复却**没有 action 块**」——这种情况解析器两条日志都不打，界面表现就是「说了一句话，没有确认按钮」。
> `FlatJson.stringAny` 只能救「块在、键名不对」，这一类只能在提示词里堵死：`AiChatRepository` 的 `allowTools`
> 分支现在写明「只要你打算修改任何数据，回复末尾就必须带一个 action 块」以及「只在文字里说而不输出块 = 什么都不会发生」。
> 加固后同一请求立刻输出 `已解析动作：CancelDay`，确认卡片正常弹出。
>
> **v2.0.0** — 「基础课表 / 实际课表」分离 + AI 全学期读写 + 快捷调休：
> ① **根因**：AI 的上下文只注入「本周」（`AiChatRepository.buildSystemPrompt` 里算完 `weekOf(today)` 就只调
> `todaySummary` / `weekSummary`），且这两者都走 `repo.classesOn()`——`classesOn` 首行
> `effectiveDayOfWeek(date) ?: return emptyList()` 让**放假日直接返回空**，于是「10-07 国庆当天原本有哪些课」
> 在 AI 眼里是「没有课」，无从调课。同时动作词表里**根本没有单日调课**，模型只能拼 `clear_day(3)` +
> `add_course(day_of_week=6)`，正是用户禁止的「误改整学期规则」。
> ② **新增 `schedule_overrides` 表**（`data\entity\ScheduleOverride.kt`，`OverrideType = CANCEL / MOVE_OUT / MOVE_IN / ADD / ROOM`）
> 与 `ScheduleOverrideDao`（含幂等判重 `find(date, type, sessionId)`）。**排课规则表 `course_sessions` 一律不动**，
> 日期变更只记录在这张表里，所以调课只影响指定日期、重复执行不会重复加课。数据库 `version 4 → 5`，
> 补了显式 `MIGRATION_4_5`（只加表加索引，不清库；升级后用户数据完好）。
> ③ **`DayResolver` / `DayPlanner` 成为唯一权威**：`DaySnapshot(date, week, holiday, effectiveDayOfWeek, blocks, baseBlocks, overrides)`
> —— `baseBlocks` 是「按学期起始、周次、星期、节次、单双周、课程有效周次算出来的原始排课」，**放假也不会清空**；
> `blocks` 是在它之上应用放假/停课/调课/补课/换教室之后的「当天实际要上的课」。`internal object DayPlanner` 是可脱离
> Room/Context 在 JVM 单测里跑的纯函数。`effectiveDayOfWeek` 与 `baseDayOfWeek` 的区别是：前者认放假，后者不认
> （调休补课日要按「被补的那一天」的规则排课，即使那天本身放假）。
> ④ **界面与 AI 共用同一套查询**。周视图原来走 `schedulesByDay`（只 `combine(occurrences, periods)`，既不读节假日也不读
> 日期变更），所以 AI 写完的调课在周视图里**完全不可见**——这是本次最隐蔽的一处不一致。现在删掉 `schedulesByDay`，
> 改为 `plannerInputs`（四张表合流）+ 顶层 `weekBlocksOf(week, semesterStart, totalWeeks, inputs)`，
> 逐日 `DayPlanner.snapshotOf(...).blocks` 再 `pinToDate()`（把调休补课日课程的 `dayOfWeek` 钉到**该日期自己的列**，
> 否则周六补周一的课会画到周一那列去）。今日页、桌面卡片、AI 全部读同一条链路。
> ⑤ **AI 能力扩展**。系统提示现在注入整学期的排课规则、节假日与已有日期变更，并明确区分「基础课表 / 实际课表」，
> 可回答任意日期（实测：界面停在第 6 周时问 2026-09-23，AI 自己推出「学期 08-31 开始 → 第 4 周 →
> 2026-09-23 是周三」，再按单双周逐条判定，结果与快捷调休对 2026-10-07 列出的 5 门课完全一致）。
> 新增动作 `query_schedule`（只读）/ `move_day` / `move_course` / `cancel_day` / `add_one_off_course` /
> `change_room` / `edit_course_on_date` / `undo_day_changes` / `update_course_rule` / `delete_course`，
> 全部走 `AiActionExecutor.execute(action, ctx)` → **与手动编辑同一套 `ScheduleRepository` 接口与规则校验**。
> 执行前弹确认卡片展示影响范围，执行后底部出结果横幅并带**撤销**按钮（实测撤销会写回并发布 `撤销调课` 变更事件）。
> 查不到课程时明确说明原因，绝不编造（实测：让 AI 把 09-24「调入」的数学分析再调出，AI 回
> 「2026-09-24 原本的课程里没有找到「数学分析（一）」，未做修改。当天的课程是：晚自习」）。
> ⑥ **快捷调休**：本周课表 ⋮ 菜单里的「小组件」入口换成了「调休」（**只换入口，小组件功能没删**——今日页顶栏菜单
> 与 设置 → 添加桌面卡片 仍可进入）。选择「原上课日期 / 调休至」两个日期（同时显示年份、星期、第几周，避免跨年歧义），
> 确认前展示「将移动 N 门课程（按 YYYY-MM-DD 第 N 周的排课）」逐条课名/节次/时间/地点、
> 「目标日期已有 M 门课程，将保留、不会被覆盖」以及红字 `⚠ 时间冲突：<课程名>`。源日期按**它自己**的周次与单双周筛选
> （跨周不漏课、不套用目标日期规则），目标日期已有课程保留不覆盖，重复操作返回「无需重复调休……本次未新增课程」。
> ⑦ **次日课程预告的每格加左侧课程色条**（与今日课表卡片一致）。
> ⑧ 单测 146 个全绿（新增 `DayPlannerTest` 15 / `WeekPlannerTest` 7 / `AiActionParserTest` 由 17 增至 35）。
>
> **v1.9.1** — 版本号发布（`versionCode 191`）。**代码与 v1.9.0 完全一致，没有任何功能改动**，
> 只是为了让产出的安装包被识别为一个更新的版本（装上会覆盖 1.9.0）。功能与修复清单见下面的 v1.9.0。
>
> **v1.9.0** — 数据刷新链路重构：让界面与桌面卡片都跟着数据库走：
> ① **根因一：今日页读的是一次性查询**。`todayState` 原来只 `combine(_currentDate, prefs, periodTimes, _selectedWeek)`，
> 课程数据在 `buildTodayState` 里用**挂起查询** `repo.classesOn()` 读，课程表不是流的上游 → 改课不会触发重发。
> 更隐蔽的是 `afterMutation()` 想用 `_currentDate.value = LocalDate.now()` 强制刷新，而 `MutableStateFlow`
> 对**相等的值直接忽略**（`StateFlowImpl.updateState` 的 `if (oldState == newState) return true`），
> `LocalDate` 是值相等 → 同一天内这个赋值是**空操作**。反证：`weekState` 用的是 `repo.schedulesByDay`（真的 Flow），
> 所以一直「周视图正常、今日页陈旧」。现在新增 `ScheduleRepository.daySnapshot(date, week): Flow<DaySnapshot>`
> （`observeOccurrences()` + `observeAll()` + `holidayDao.observeAll()` 三个 Room Flow 组合），
> `todayState` 改为 `flatMapLatest { combine(daySnapshot(今天), daySnapshot(明天), timeTick) }`，
> `buildTodayState` 变成**纯函数、不再碰数据库**。实测：长按课程改教室后，**不重启、不离开今日页**，列表立刻变成新值；
> 删除课程后该行立刻消失，且用户翻到的「第 8 周」不会被拉回本周（`selectedWeek` 与现实教学周是两个字段）；
> ② **根因二：没有冷启动/回前台的小组件刷新**。原来 `refreshAllWidgets` 只在 4 个待办写入里调用，
> `MainActivity.onResume()` 只刷流体云。现在把刷新收敛到唯一入口 `WidgetRefresher`，
> 由 `TimetableApp` 的应用级生命周期驱动：`DataChangeBus` 监听器（数据变更）、`onCreate`（冷启动）、
> `registerActivityLifecycleCallbacks` 的 **0→1 started 计数**（回到前台，应用内翻页不会重复触发）；
> ③ **仓库写入统一发布变更事件**。`DataChangeBus.publish` 在 DAO 调用**返回之后**（事务已提交）才发，
> 带单调递增 `version`，日志只写「类型/记录编号/版本/来源」，不写待办正文。小组件与页面都据此更新，
> 不会再漏掉某一条写入路径。`replaceSchedule` 也补上了 `db.withTransaction`，导入过程不再暴露「课表全空」的中间态；
> ④ **请求合并与串行** `RefreshCoalescer`：脏标记 + `tryLock` + 退出前再确认。
> 一轮执行期间到达的请求会被合并进来（**保留最后一次**，不丢事件），同一时刻只有一轮在跑，
> 单轮失败只记日志不中断循环。实测冷启动时的 3 次请求只产生 2 轮刷新；50 次连续请求合并成 2 轮有单测覆盖；
> ⑤ **小卡片的主题跟随软件内主题**（本次需求点之一）。原来 `WidgetPalette` 是 5 个写死的颜色常量，
> 只按系统深浅色切换；现在把配色计算抽成纯函数 `timetableScheme(dark, presetIndex, columbina)`
> （`ui/theme/TimetableScheme.kt`），**界面与卡片调用同一份逻辑**，卡片底色取 `extras.surfaceCard`、
> 标题取 `colorScheme.onSurface`、次要文字取 `extras.mutedText`。实测关掉哥伦比娅风格后卡片底色
> 从 `#201628` 变为 `#1B1C21`，与代码里的两套 `surfaceCard` 完全一致；
> ⑥ **卡片上加了手动刷新入口**：标题行右侧一个 ⟳（`actionRunCallback<RefreshWidgetsAction>()`，
> Glance 自带广播接收器执行，无需改清单），只重读数据库重绘、不改任何业务数据；
> 「小组件设置」弹窗里也有「立即刷新桌面卡片」按钮，并明确提示「刷新请求已提交」而不谎称「桌面已重绘」；
> ⑦ **修掉一个真实缺陷：启动时重复播种**。`bootstrap()` 用 `prefs.value` 判断 `seeded`，
> 而 `prefs` 是 `stateIn(..., Eagerly, UiPreferences())`——DataStore 首次发射前它是默认值（`seeded=false`），
> 于是**每次 Activity 重建都会误判成首次启动**，重复 `replaceHolidays(示例节假日)`，把用户自建的节假日清掉。
> 改为 `prefsRepo.preferences.first()` 后，升级安装与重启都不再出现 `替换节假日`；
> ⑧ **写入失败不再吞掉**：新增 `launchWrite`，失败时发 Snackbar「…失败，数据未改动，请重试」，
> 成功时**不提示**（不存在「显示虚假的成功」）。写入本身不是乐观更新，界面数据一律来自 Room 的 Flow，
> 所以失败时界面自然还是数据库里的旧值；
> ⑨ **后台兜底**：`WidgetRefreshWorker`（30 分钟周期，只读本地库、不要求网络）在应用被清理后补一次刷新。
> ColorOS 会限制后台任务，**不保证准点**，应用内刷新不依赖它。它与四个 `widget_*_info.xml` 里本来就有的
> `android:updatePeriodMillis="1800000"` 是**部分重叠**的两条兜底路径，两条都受各自限制；
> 应用内的数据变更 / 冷启动 / 回到前台一律**立即**刷新，不等这 30 分钟；
> ⑩ **`campusState` 的时间推进**：它原来在 `combine` 里调 `LocalTime.now()`，只在数据变化时才重算，
> 停留在这个页面上跨过某个设施的开放时间点时「开放中 / 未开放」不会翻转。已改为与 `timeTick`（30 秒脉冲）
> 组合，和今日页的 `nowBlock`/`nextBlock` 同一套写法。
> 新增 19 个单元测试（合并器/日期规则/变更总线/配色），总计 106 个用例全绿。
>
> **v1.8.8** — 改名「娅娅课表」+ 换图标 + 哥伦比娅风格 + 设置页头像昵称：
> ① **应用更名**为「娅娅课表」（`app_name`、AI 助手人设、关于页、卡片帮助文案里的旧名一并改掉）；
> ② **新桌面图标**：自适应图标改为「矢量渐变底板 + 角色位图前景」。之前的前景是矢量图，
> 换成角色图后就不能再做主题图标，因此删掉了 `<monochrome>`；五个密度各生成一张
> `mipmap-*dpi/ic_launcher_fg.png`（108dp 画布，内容约 74dp 居中）；
> ③ **哥伦比娅（娅娅）主题风格**，可在「设置 → 个性化 → 哥伦比娅风格（娅娅）」开关（默认开启）：
> 新增 `ThemePresets.columbina`（蔷薇 `#B3266B` / 丁香 `#F4A6C8` / 挑染玫红 `#E0407E`），
> 深色下背景走 `#140E18`、卡片 `#221629`，并由 `PageBackground` 叠加**羽紫与玫红光晕 + 两枚金色菱形星钻**
> （`LocalColumbinaStyle` 供各页面读取）。它**不进**设置页的主题色色板，所以色板仍是 7 个；
> 反过来，**点任意主题色色板就等于切回普通风格**（自动关掉该开关），避免「点了没反应」的困惑；
> ④ **设置页顶部新增居中的头像 + 昵称**：默认头像即应用图标、默认昵称「娅娅」（小一号字），
> 右侧一个淡灰色极简铅笔键。点头像从相册选图（`OpenDocument` + 持久化读取权限，重启后仍在，
> 长按头像恢复默认），点铅笔弹出「修改昵称」（最多 16 字，留空回落默认）。
>
> **v1.8.7** — 第七轮真机反馈的桌面卡片修复：
> ① **修复「卡片里的课程没有彩条」**。真凶是颜色转换：`Color.value` 是 `ULong`（sRGB 的 32 位色值在**高 32 位**），
> `Color.value.toInt()` 取到的是低 32 位的**色彩空间 id，也就是 0**（透明）。此前 `accentOf()` 一直返回 0，
> 于是课程色条实际是「透明」或（在 `withAlpha` 之后）纯黑，看起来就是没有彩条。**现在统一用
> `Color.toArgb()`**。另外色条不再复用文字用的前景色——自定义深色课程色的前景色接近纯白，
> 画出来是一条白条；新增 `WidgetClass.barArgb`（自定义色取课程色本身，预设色取对应主题下的柔和色）
> 专供色条与格底使用，已上完的课保留课程色、只降到 55% 不透明度；
> ② **「明日预告」的触发条件放宽**：以前只有「今天既没课也没待办」才整卡切换，
> 现在**今天的课全部上完且没有未完成的待办**就切换（已完成待办不占版面）；
> ③ **修复明日课程格文字被裁掉半截**：`ranges` 里「中间有课间休息」的连堂课是被 `\n` 拼成多行的，
> 直接塞进 `maxLines = 1` 的格子里，只会显示第一段、后面的地点被省略号吃掉。现在取
> 「第一段开始 - 最后一段结束」拼成整体区间，字号 8sp、单空格分隔，课名与「起止时间 + 地点」两行都完整可见；
> ④ 明日格底纹同样改用 `barArgb`（原来是 0），彩色底纹这才真正显出来。
> **v1.8.6** — 第六轮真机反馈的修复：
> ① **彻底修复桌面卡片「载入窗口小部件时出现问题」**。根因不是布局层级，而是颜色写法：
> Glance 的 `ColorProvider(...)` 有多个重载，`ColorProvider(0xFF4C7DFF.toInt())` 匹配到的是
> **`@ColorRes Int` 重载**（把 Int 当颜色资源 id），运行期执行 `context.getColor(id)` 直接抛
> `Resources.NotFoundException`；异常发生在启动器应用 RemoteViews 的阶段，OEM 版
> `AppWidgetHostView` **不打印任何堆栈**，只留一行 `mViewMode == VIEW_MODE_ERROR`，所以一直被误判成
> 「LazyColumn 的锅 / 布局太复杂」。四张卡片正文里所有 `ColorProvider(<ARGB 字面量>)` 已统一改为
> 安全工厂 `FixedColorProvider(argb) = ColorProvider(day = Color(argb), night = Color(argb))`。
> 顺带确认：**只要发出的 RemoteViews 能正常 inflate，桌面上已经处于错误态的卡片会自己恢复**，
> 不需要长按删除重加；
> ② 修复**深色模式下多处文字仍是黑色**：根因是 Compose 的 `LocalContentColor` 默认 `Color.Black`，
> 而 `UniversityTimetableTheme` 从未提供它，且 `TimetableAppRoot` 里的各种 `Dialog` 是 `Scaffold` 的
> **兄弟节点**、不在其 `Surface` 内，于是 Dialog 内所有没显式指定颜色的 `Text`/`Icon` 都落到纯黑。
> 现在主题根提供 `LocalContentColor = colorScheme.onBackground`，并给 `Scaffold` 补上
> `contentColor = MaterialTheme.colorScheme.onBackground`。受影响并已修复：今日待办编辑页的
> 「待办事项」「今天」、设置 → 添加桌面卡片的说明文字、⋮ → 课表设置里的 `2026-08-31`；
> ③ 修复**⋮ 菜单里的「小组件设置」点击无效**：`TodayScreen` 从未把 `onOpenWidgetHelp` 传给 `AppTopBar`，
> 参数默认空 lambda 所以点了没反应；现已接线到 `widgetHelpVisible`；
> ④ **日历改为 `VerticalPager`**：整月卡片放进 `VerticalPager` 做原生跟手滚动 + 自动吸附，
> 翻月从「累计拖动 56dp 才跳变」变成**平滑连贯**；月份由 `pagerState.currentPage` 派生，
> 以 2000-01 为基准共 1200 个月，`settledPage` 变化时把选中日期收进当月。
> **v1.8.5** — 第五轮真机反馈的桌面卡片修复与增强：
> ① 修复**卡片正文出现「无法添加微件」**：根因是 Glance 的 `LazyColumn` / `items`。启动器在渲染 RemoteViews 集合时，会用 `RemoteCollectionItemsAdapter.getView()` 把每个条目当成**独立的子组件**去 `updateAppWidget`，而这个子 `AppWidgetHostView` 的 `appWidgetId` 始终是 `INVALID_APPWIDGET_ID`（`AppWidgetHostView.AdapterChildHostView` 不设置它），于是每个条目都被换成「无法添加微件。」错误视图；错误视图一旦出现就**常驻不恢复**（`applyContent` 里 `mViewMode == VIEW_MODE_ERROR` 直接 return）。现在四张卡片的正文一律改用**静态 `Column` + `take(n)`** 展开，不再使用任何 `LazyColumn`/`items`。**同时不要在列表 `Column` 尾部追加「还有 N 项」这类兄弟节点**——实测多一个兄弟 `Text` 会让整块 RemoteViews inflate 失败，卡片退化为「载入窗口小部件时出现问题」；
> ② 补上**真实位图预览**：四个 provider 之前把 `android:previewLayout` 指向 Glance 的占位布局，卡片内容又是异步渲染的，启动器在添加流程里读不到可绘制内容；现在改为 `android:previewImage` 指向 `mipmap-*/widget_preview_full.png`（250×110dp）与 `widget_preview_compact.png`（110×110dp），各密度一套、深色版；
> ③ 待办写入后卡片**定向刷新**：`refreshAllWidgets` 先用 `GlanceAppWidgetManager.getGlanceIds()` 查出桌面上真实存在的实例并写日志（哪个卡片、几个实例、有没有失败），再统一走 `updateAll()`。**不再对 id 逐个 `update()`**——与 `updateAll` 重复刷新同一实例时，启动器会拿到尚未绑定 widgetId 的 RemoteViews（`appWidgetId: 0`）并报「无法添加微件」，且错误视图常驻不恢复；
> ④ 新增**「明日预告」整卡模式**：当今天既没有课也没有待办时，4×2 卡片整卡切换为「明日周X，共有 **N** 门课要上」（数字红色强调）＋ 右上角「9.28 / 第4周」＋ **2×2 明日课程格**（课程色底圆角块，课名粗体在上、起止时间与地点在下；一天超过 4 门时显示「还有 N 门课」）。
> ⚠️ 升级提示：已经显示「无法添加微件」的卡片**不会自动恢复**。请**长按卡片 → 移除插件**，再重新添加一次。
> **v1.8.4** — 第四轮真机反馈的功能补充与修复：
> ① **日历页支持上下滑动切换月份**：向上滑进入下一个月、向下滑返回上一个月，月份标题与网格内容同步更新；月份标题两侧的箭头按钮保留；
> ② 日历页在日期下方**显示节日**（应用内登记的**休假日**优先，其次回退到公历固定节日「元旦 / 劳动节 / 国庆节」，标红显示），顶栏副标题同时汇总「本月日程数 · 本月节日」。调休补课日（如 9/26、10/10）不算节日、不标名，避免被截成「国庆」而误以为当天放假；
> ③ 本周课表左侧时间轴的每个节次改为**上下两行**显示开始与结束时间（如 `08:30` / `09:15`），不再用连字符挤在一行；
> ④ 左右滑动切换周次时**左侧时间轴固定不动**，只有右侧日期栏与课程网格水平滑动，时间轴与网格仍共用同一份行高与分隔线；
> ⑤ 修复 **AI 助手键盘遮挡输入栏**：输入框获得焦点、键盘弹出时整列向上收缩（`imePadding`），输入栏与底部按钮始终完整可见可点；收起键盘后自动回到原位，不跳动。
> **v1.8.3** — 第三轮真机反馈的功能补充与修复：
> ① 设置「关于」页重写：标注作者（兰大数学院 zza）与联系方式（邮箱 / B 站），说明软件开源，并提供**可点击的 GitHub 链接**（仓库地址统一取自 `gradle.properties` 的 `timetable.github.owner/repo`，当前为 `https://github.com/XiaozhuangXzsg/Schedule`）；
> ② 状态栏文字与图标颜色**跟随深浅色模式**（`isAppearanceLightStatusBars` / `isAppearanceLightNavigationBars`），两种模式下都清晰；
> ③ 在应用内新增/删除/修改今日待办后，**桌面卡片立即刷新**（四处待办写入口统一调用 Glance `updateAll`）；
> ④ 修复今日页顶栏**灰色左右箭头**：此前标题读的是"今天所在的自然周"，点箭头只改了选中周却不刷新标题，表现为"按钮无效"；现在标题与箭头都读 `selectedWeek`，可以正常切周；
> ⑤ 今日页左上角「周次 + 日期」变为**可点击**（新增「点击查看日历 ›」提示），进入**日历页**并在授权后读取手机系统日历（月网格 + 当日日程，应用只读不写）；
> ⑥ 本周课表左侧时间轴**恢复分隔线**，并显示**完整起止时间范围**（如 `08:30–09:15`），时间轴加宽到 50dp，与右侧网格严格对齐；
> ⑦ 删除今日页「⋯」菜单里**无效的周数滑动条**及其逻辑（周视图居中面板的周数滑块保留）；
> ⑧ 周视图左右滑动切换周次改用 **`HorizontalPager` 原生跟手滚动 + 自动吸附**，连续流畅、不跳变不闪烁；
> ⑨ 首次启动说明弹窗加入**吉祥物图片**，内容超出时可上下滑动（细滚动条不压正文），关闭后不重复显示；
> ⑩ 设置「关于」下方新增隐藏彩蛋「**不要点我**」：点击后播放内置音频，只播一遍即停止。
> **v1.8.2** — 真机（一加 15 / ColorOS 16）验证的第二轮修复与完善：
> ① 本周课表左侧时间轴现在会显示**全部节次**（不再只到第 9 节），且时间轴与课程网格**共用同一个滚动状态与行高**，滚到顶部/底部时分界线也完全对齐；
> ② 左右滑动切换周次改为**连续水平翻页动画**（先同步选中周再播放动画，不会跳变或被拉回）；
> ③ 「作息时间设置」支持**增/减节数**，保存后时间轴与本周课表立即适配，超出节次的课程会被同步裁剪；
> ④ 设置里「清空全部课程」现在**立即生效**（同步刷新日期、时间轴与桌面卡片/流体云，无需重启）；
> ⑤ AI 助手的「历史对话」每条记录右侧新增**独立删除按钮**（带二次确认）；
> ⑥ 周视图 ⋮ 菜单的「联系我们」改为打开**独立弹窗**（不再跳转到校园生活）；
> ⑦ 首次启动显示一次**软件说明浮窗**（可滚动 + 细滚动条 + 可点 GitHub 链接 + 关闭按钮，关闭后不再重复弹出）；
> ⑧ 周视图课程卡片的**教师与教室**分行完整显示（不再被截断）。
> **v1.8.1** — 真机（一加 15 / ColorOS 16）截图验证修复：本周课表顶栏恢复 **AI 助手** 入口；
> 系统返回键/返回手势现在优先关闭子页面（AI 助手、校园生活）而不再直接退出应用；
> 「校园生活」页顶栏补齐状态栏安全区；AI 输入框提示文案不再折行；
> 修复 AI 助手 ⋮ 菜单里「模型配置」**点了没反应**（配置页被 AI 全屏覆盖层盖住，见下方 zIndex 说明）。
> **v1.7.0** — 新增 **AI 助手**：可配置兼容 OpenAI 接口的模型（默认适配 DeepSeek），
> 支持文字/图片/文件对话、在**用户确认后**修改课表与待办、启动洗衣计时；
> 并修复了大量夜间模式下的深色文字（弹窗、菜单、导入、作息、卡片指引等页面）。
> **v1.6.0** — 修正桌面卡片尺寸（2×2 / 4×2 / 4×4），修复周视图空格异常灰块。
> **v1.5.0** — 新增「登录教务系统导入」（兰州大学），默认作息改为兰大 14 节。
> **v1.4.0** — 新增待办事项；桌面卡片新增 2×2（当前/下一节课）与 4×2（待办 + 课表，各自独立滑动）。
> **v1.3.0** — 洗衣倒计时改为 2×2 网格、添加按钮移到顶栏、移除无意义的「时间轴」设置。
> **v1.2.0** — 修复夜间模式文字颜色、今日课表改为课程列表、子页面加入滑入动画。
> **v1.1.0** — 新增「校园生活」子页面、本周课表居中面板菜单、长按空白格添加课程。
> **v1.0.1** — 修复启动闪退（WorkManager 未初始化）。 详见文末「故障排查」。

---

## 一、已实现功能

### 1. 今日课表（课程列表）
不再画整条时间轴，直接列出今天要上的课：

- 每行：左侧**起止时间**（如 08:00 / 09:50），右侧**课程名 + 地点 + 教师**，
  最右为课序号与「已结束」标记；正在上的课带「进行中」徽章。
- 顶部摘要卡：日期、第几周，以及「下一节 18:30 生物多样性…」或「正在上：…」。
- 放假、调休补课横幅提示；没有课时给出空状态与明日首节课提示。
- 点击课程卡片 → 快捷操作面板（编辑 / 删除该时段 / 清空当天）。
- 底部为「校园生活」卡片与洗衣机倒计时。

### 2. 本周课表（WakeUp 风格网格）
- 顶部周一至周日表头（带日期，今天高亮为圆形徽章）。
- 左侧节次 + 起始时间栏，跨节课程**合并为一张卡片**并自动撑高。
- **左右滑动**切换上一周 / 下一周（72dp 阈值判定，不与纵向滚动冲突）。
- 自动高亮今天所在列与当前节次行。
- **严格区分单双周**：`1-16周(单)` / `2-18周(双)` 等模式精确解析与过滤。
- **中国法定节假日与调休**：放假日期不上课；调休日按指定星期上课。
- 长按日期表头 → 一键清空该天所有课程。
- 三种信息密度：普通网格 / 简洁模式（极简色块）/ 隐藏周末。

### 3. 设置与智能导入

**① 登录教务系统导入（兰州大学，推荐）**

在「智能导入课表」中点击 **「登录教务系统导入」**，应用内会打开兰州大学教务网站
（默认入口 `http://my.lzu.edu.cn/`）：

1. 在页面里正常登录（支持统一身份认证 / 验证码跳转）；
2. 进入 **「个人课表 / 本学期课表」** 页面，等课表完整显示；
   课表页形如
   `https://jwk.lzu.edu.cn/academic/manager/coursearrange/showTimetable.do?id=…&timetableType=STUDENT&sectionType=BASE`
   （「大节课表」`sectionType=COMBINE` 同样支持）；
3. 点右下角浮动按钮 **「导入课表」**；
4. 应用自动识别课表并**替换**当前课表，同时把页面表头里的**作息时间同步到本地设置**。

实现方式与注意点：
- 用 **WebView 内嵌页面**，登录、验证码、CAS 跳转全部交给网页自己完成；
  Cookie 由系统持久化，**下次打开仍是登录态**。
- 取页面内容用 `evaluateJavascript` 读取 DOM：JS 字符串本身是 Unicode，
  **天然绕开教务系统的 GBK 编码问题**，不需要额外转码。
- 用 `LzuTimetableParser` 解析：按单元格 `id="天-节"` 定位，读取
  `<<课程名>>;课序号 / 地点 / 教师 / 4-19周全周 / 讲课学时`，
  支持**同格多门课**（如 4-8 周 @A312 与 9-19 周 @B106）、
  **单双周**（`周单周` / `周双周`）、以及把连续节次**合并为一条**
  （周一 9-11 节连上）。
- 教务系统仍是 http 站点，已通过 `network_security_config` 仅对 `*.lzu.edu.cn` 放开明文流量。

**② 文件导入**

- **HTML 文件导入**：解析教务系统导出的 `<table>` 课表（自动识别「星期表头 + 逐行属性」网格结构）。
  兰大导出的 GBK 文件建议直接用上面的「登录教务系统导入」。
- **CSV 文件导入**：支持带表头明细格式与无表头网格格式。
- **图片 OCR 导入**：**Google ML Kit 中文文字识别**（`text-recognition-chinese`，完全免费、离线可用），
  识别课表截图后交给同一套语义解析器结构化。
- **粘贴文本导入**：直接粘贴网页课表内容。
- 导入前展示解析预览（课程数 / 时段数 / 警告信息）。

### 4.0 数据刷新链路（v1.9.0 重构，改这块前先读这一节）

一条数据从「用户点保存」到「界面/桌面卡片都变新」经过这些环节：

```
用户操作
  └─ MainViewModel.launchWrite { ... }        Dispatchers.IO，失败发 Snackbar 且不提示成功
       └─ ScheduleRepository.<写方法>          DAO 调用返回 = 事务已提交
            └─ DataChangeBus.publish(kind, recordId, source)
                 ├─ version 单调递增（排查「旧结果覆盖新结果」用）
                 └─ TimetableApp 里注册的监听器 → WidgetRefresher.request(context, "数据变更:XXX")
页面（同一个事务提交后的瞬间）
  └─ Room 的 Flow 失效 → daySnapshot / todosOn / allTodos 重新发射
       └─ todayState / weekState / campusState / todayTodos 基于 Flow 组合，自动重算
```

四个必须守住的不变量：

1. **页面的数据只能来自 Room 的 Flow。** 任何在 `combine` 里调用 `suspend` 一次性查询的写法，
   都会让「这张表根本不是流的上游」，改数据不触发重算。今日页原来就是这么坏的。
2. **不要用给 `MutableStateFlow` 赋一个可能相等的值来「催」刷新。** `StateFlow` 对相等值直接忽略，
   `_currentDate.value = LocalDate.now()` 这类代码在当天是空操作，长期看很容易被误认为有效。
3. **小组件刷新必须走 `WidgetRefresher`，不要各处直接调 `updateAll`。**
   它负责：应用级作用域（不随页面销毁被取消）、请求合并（保住最后一次）、串行（不让旧的一轮后完成）、
   空实例跳过、失败只记日志。触发点见 `TimetableApp.onCreate`（冷启动 + `DataChangeBus` 监听）
   与 `registerActivityLifecycleCallbacks` 里的 0→1 started 计数（回到前台）。
   **不要绑定到某个页面**，否则「保存后立刻回桌面」会把没跑完的刷新掐掉。
4. **卡片配色取自 `ui/theme/TimetableScheme.kt`**，不要在 `GlanceWidgets.kt` 里写死颜色常量，
   否则设置里换主题/切深浅色/开关哥伦比娅风格时卡片不会跟着变。

排查用日志（只记类型与编号，不记待办正文）：

```bash
adb logcat -v time WidgetRefresh:D DataChange:D TimetableApp:D MainViewModel:E "*:S"
```

会看到 `数据已提交 kind=... id=... version=...`、`刷新请求 reason=...`、`第 N 轮：<卡片> 桌面上没有卡片实例，跳过`。
**注意日志里「已提交 N 个实例的更新」只代表更新请求已提交给启动器，不等于桌面已经重绘**——
要确认显示效果，必须自己回桌面看一眼。

### 4. 桌面卡片（Jetpack Glance）
提供 **4 种**卡片，列表均支持在卡片内滑动：

| 卡片 | 规格 | 内容 |
| --- | --- | --- |
| **当前/下一节课** | **2×2**（2 列×2 行） | 正在上的课（大字课程名 + 时间地点），下方显示「接下来 …」；无课时自动切换为「下一节课」 |
| **待办 + 课表** | **4×2**（4 列×2 行） | **左侧待办、右侧课程，两块各自独立滑动**；课程只显示课名与地点 |
| 今日课表 | 4×2（4 列×2 行） | 横向卡片，按时间顺序列出今天的课程（含时间） |
| 本周课表 | 4×4（4 列×4 行） | 显示本周每天的课程概览 |

> **尺寸配置**：每张卡片的 `minWidth/minHeight` 与 `targetCellWidth/targetCellHeight` 严格对应
> （宽度 dp = 70×列数−30，高度 dp = 70×行数−30），避免启动器按 `minWidth` 重新归格、
> 导致尺寸标签与预期不符（例如 2×4 显示成 3×4）。

2×4 卡片的细节：
- 上半显示当天的待办事项（最多 3 条），有图片时标注「含 N 张图片」；
- **若当天没有待办文字，上半改为圆角显示待办里的第一张图片**；
- 下半显示当天课程（最多 5 条），正在上的课以主色标注时间；
- 两块之间为横向分隔线。**注意：卡片的正文一律是静态 `Column`，不能用 `LazyColumn`**——
  原因见文末「关于卡片正文（易踩坑）」，用 `LazyColumn` 会让每个条目变成「无法添加微件」。

### 4.1 待办事项（备忘）
今日页右上角的**日历图标**（带未完成数量角标）进入待办编辑：

- 顶部**日期条**：昨天 / 今天 / 明天 … 未来一周，可左右切换，**支持添加今日及后面几天的事项**；
- 每条待办可勾选完成、编辑、删除；
- **每条可附加 1-3 张图片**（相册选择，已申请持久化读权限，重启后卡片仍可显示）；
- 没有待办的日子会提示「点下方按钮添加」；
- 今日页也有「今日待办」卡片，可直接勾选完成；首页角标与卡片同步。

### 5. ColorOS 16 流体云（重点系统交互）
流体云 API 未公开，因此按需求采用**高优先级常驻通知**平替：
- `setOngoing(true)` + `PRIORITY_HIGH` + `CATEGORY_STATUS` + `VISIBILITY_PUBLIC`，
  ColorOS 会自动把这类通知收纳进流体云。
- **模拟外卖进度效果**：
  - 正在上课 → 显示「课程进行中」**进度条**；
  - 距上课 1–120 分钟 → 使用系统 `Chronometer` 倒计时，文案「距离下一节[课程]还有 X 分钟」，**系统自动走秒**；
  - 点击 → 拉起 App 并直达「今日课表」。
- 由 `WorkManager` 每 15 分钟刷新，App 在前台时每 30 秒刷新，开机/应用更新后自动重建任务。

### 6. 校园生活（今日页「校园生活」卡片 → 子页面）
点击今日页的「校园生活」卡片（或三点菜单 → 联系我们）进入子页面，**按今天的日期与节假日**实时显示：

| 项目 | 位置 | 时间 |
| --- | --- | --- |
| 公寓开关门 | 公寓大门 | 6:00 开门，23:00 锁门 |
| 宿舍供电 | 宿舍 | 周日-周四 6:00-23:15；周五-周六 6:00-24:00 |
| 浴室开放 | 公共浴室 | 周一 16:30-23:00（上午停开检修）；周二至周日 11:00-14:00 & 16:30-23:00 |
| 滚筒洗衣机 | 1-6 层 | 7:30-23:00 |
| 烘干机 | 1 层 | 7:30-23:00 |
| 换桶机 | 2 层、5 层 | 7:30-12:00 & 14:00-23:00 |
| 洗鞋机 | 3 层 | 7:30-23:00 |
| 吹风机 | 各层公共区 | 7:00-13:00 & 14:00-23:45 |
| 饮水机 | 各层 | 6:00-24:00 |
| 自习室 | 公寓自习室 | 6:00-24:00 |

页面能力：
- 每项显示 **开放中 / 未开放 / 已结束 / 休息**、今天的完整时间段、数据来源（默认安排 / 周X 安排 / 节假日安排），
  以及「还有 Xh Ym 关闭」倒计时。
- 顶部概览给出「今天的校园作息」与**最近一次状态变化**提醒。
- 节假日自动切换为节假日时间表（未单独设置则沿用默认）。
- **所有条目与时间都可自由增删改**：名称、位置、类型、默认时间、**按星期单独覆盖**、节假日时间、备注；
  每项支持**多段**时间（如 11:00-14:00 与 16:30-23:00）。右上角可一键恢复学校默认数据。
- 子页面底部仍保留洗衣机倒计时器。

### 12. AI 助手

入口：**今日课表右上角 ✨ 图标**，或「设置 → AI 助手 → 打开 AI 助手」。

#### 12.1 模型配置
「AI 助手 → ⋮ → 模型配置」中可**添加 / 编辑 / 删除多个模型**，并设置其一为「使用中」：

| 参数 | 说明 |
| --- | --- |
| 名称 | 自定义，仅用于区分 |
| API 地址 | 填到 `/v1` 为止，例如 `https://api.deepseek.com/v1`（会自动补 `/chat/completions`） |
| 模型名称 | 例如 `deepseek-chat`、`deepseek-reasoner` |
| API Key | 经 **Android Keystore（AES-256-GCM）加密**后存入本机 DataStore |
| 支持图片输入 | 多模态模型（gpt-4o / qwen-vl 等）才打开；DeepSeek 文本模型请关闭 |
| 最大输出 / 随机性 | max_tokens 与 temperature |

内置常用服务商预设，点一下即自动填好地址与模型名：
**DeepSeek（推荐）/ DeepSeek 推理 / OpenAI / 通义千问（兼容模式）/ Kimi / 智谱 GLM / 自定义**。

配置页提供 **「测试连接」**：会真的发一条极短请求，并按错误码给出中文提示
（401 Key 无效、402 余额不足、404 地址或模型名不对、429 频率超限等）。

> 安全说明：Key 用系统密钥库加密，**不会上传到任何第三方**（只有发起对话时才会随请求发给
> 你自己配置的那个服务）。但它仍会在请求时解密到内存，root 设备或整机备份场景下请自行评估风险。

#### 12.2 对话
- 支持**文字、图片、文件**三种输入；流式输出（边生成边显示）、**可随时取消**、失败有明确提示。
- 输入框左侧三个按钮：上传图片、上传文件、**语音输入**（调用系统语音识别，转成文字）。
- 顶部明确提示：**对话内容（含上传的图片/文件）会发送给你所配置的第三方模型服务**。
- 会话管理：新建对话、历史对话切换、清空当前对话；首条消息自动作为会话标题。

#### 12.3 支持的文件类型
| 类型 | 处理方式 |
| --- | --- |
| 图片（jpg/png/webp…） | 多模态模型：压缩到 1024px 后以 base64 data URL 发送；文本模型：忽略并提示 |
| 文本类（`txt` `md` `csv` `json` `html` `htm` `xml` `log`） | 读取内容（截断至 8000 字）拼进消息 |
| 其他（pdf/docx/xlsx…） | **不解析**，会提示改传截图或用其他方式 |

#### 12.4 授权操作（重要：未经确认不落库）
模型**只提出动作**，App 解析后在弹窗里逐条列出「将要进行的修改」，**用户点确认才执行**：

| 动作 | 说明 |
| --- | --- |
| `add_course` | 新增课程（名称/教师/地点/星期/节次/周次/单双周） |
| `clear_day` | 清空某天课程（会额外红色警示「不可撤销」） |
| `clear_all_courses` | 清空全部课程（同上） |
| `add_todo` | 添加待办（可带日期） |
| `update_facility_hours` | 修改校园生活设施开放时间 |
| `start_laundry_timer` | 启动洗衣机倒计时 |

可在配置中关闭「允许 AI 修改数据」，此时助手变为**只读**。执行结果会作为系统消息回写到对话里。

#### 12.5 课表导入
在对话里**直接发课表截图**，让 AI 识别整理；或在「智能导入课表」里用 HTML/CSV/OCR/文本导入。
无论哪种方式，**导入前都会展示识别结果供核对，确认后才写入课表**。

#### 12.6 已知限制
1. **需要自备 API Key**：应用不含任何内置额度，费用由你的服务商账户承担。
2. **图片识别不保证 100% 准确**：模型读图可能认错课程名或节次，务必在确认页核对。
3. **不支持文件解析**：PDF/Word/Excel 需自行转成图片或复制文本。
4. **语音输入依赖系统服务**：设备没有语音识别组件时会提示失败，可改用文字。
5. **不联网不可用**：所有 AI 能力都走你配置的远程接口；课表本身的管理功能完全离线。
6. **上下文长度**：只发送最近 20 条消息，长对话的早期内容不会带上。
7. **动作 JSON 格式**：若模型不按约定格式输出，动作会被忽略（宁可不执行，也不猜测）。

### 7. 本周课表的三点菜单（居中面板）
点击本周课表右上角「⋮」弹出**居中面板**（非右上角下拉）：
- 上方：**周数拖动条**（实时切换周次）＋「修改当前周」快速回到本周
- 中部：当前课表卡片
- 下方 2×4 功能网格：`上课时间`、`课表设置`、`已添课程`、`小组件`、
  `联系我们`、`上课提醒`、`夜间模式`、`简洁模式`

### 8. 长按编辑（本周课表）
- **长按空白格** → 直接在该天该节次**添加课程**
- **长按已有课程** → 编辑 / 删除该时段 / 清空当天
- 点击课程同样打开操作面板

### 9. 校园生活助手（今日页卡片）
- 显示前 4 项设施的实时状态，底部为**洗衣机倒计时器**（30/40/45/60 分钟 + 完成通知）。


### 10. 个性化
- 7 套主题色预设、跟随系统深浅色、手动日间/夜间切换。
- **独立设置每个页面的背景色**（今日 / 本周 / 设置）。
- **从相册选择自定义背景图**，可调**透明度**与**模糊度**。
- 显示项开关：教师、地点、单双周标记、周末、当前时间线、生活助手。
- 上课提醒可设提前 5/10/15/30 分钟。

> **配色约定（重要）**：所有界面文字颜色都取自 `MaterialTheme.colorScheme` 或
> `LocalTimetableColors`，**不要在页面里直接写 `Color(0xFF...)`**——那会导致夜间模式下
> 出现深色文字看不清的问题。语义化颜色（成功/警告/中性/节假日/调休）统一集中在
> `ui/theme/Theme.kt` 的 `TimetableColors` 中，深浅色各一套。桌面卡片使用 Glance 的
> `ColorProvider(day, night)` 由系统自动选择。

### 11. 手势与导航
- 底部**悬浮胶囊式切换器**（参考「蜜蜂记账」），指示器**跟随滑动进度联动动画**（缩放 + 位移 + 颜色渐变）。
- `HorizontalPager` 支持三页左右无缝滑动切换。
- 顶栏左侧「第 N 周 · 周三 + 年月日」，右侧 `[添加] [AI 助手] [待办] [导入] [三点菜单]`。
- 三点菜单内含：**周数进度条拖动切换**、日间/夜间切换、简洁模式、作息时间设置、小组件设置、课表设置。
- **返回行为**：应用已声明 `android:enableOnBackInvokedCallback="true"`，系统返回键 /
  侧滑返回会**优先关闭当前子页面**（AI 助手、校园生活），只有在主页时才退出应用。
  子页面另外支持从屏幕左/右边缘向内拖动返回，拖动过程跟手缩放淡出。

> **关于边缘手势**：ColorOS 的「侧滑返回」由系统在窗口之前接管，任何应用都无法在不开启
> 系统「三键导航」的情况下独占这段边缘热区。因此应用内边缘滑动只在三键导航或热区之外生效，
> **系统返回才是最可靠的返回方式**，两者都已接入同一套返回逻辑。

> **关于全屏覆盖层的层叠（易踩坑）**：AI 助手、校园生活是全屏覆盖层，直接放在根 `Box` 里；
> 而「模型配置」页也是全屏 `Composable`（不是 `Dialog`）。同一 `Box` 内**后声明的兄弟节点绘制在上层**，
> 所以从 AI 助手内部打开模型配置时，配置页会被 AI 页面完全遮住——表现出来就是「点了没反应」。
> 现在 `AiConfigScreen` 外层显式加了 `Modifier.zIndex(1f)`。以后新增可被覆盖层打开的全屏页面，
> 要么调整声明顺序，要么同样提升 zIndex。

> **关于本周课表的时间轴（易踩坑）**：左侧时间轴放在 `HorizontalPager` **外面**，因此横向切周时
> 它保持固定，只有右侧的日期栏与课程网格滑动。纵向则由时间轴与网格**共用同一个 `ScrollState`**，
> 行高来自同一份 `rowHeights`，所以任何滚动位置的分隔线都左右贯通。若以后要把时间轴重新塞回
> Pager 内部，这两条对齐保证会同时失效。

> **关于键盘与输入栏（易踩坑）**：应用是 edge-to-edge（`decorFitsSystemWindows=false`），
> Android 15+ 会**忽略 `android:windowSoftInputMode="adjustResize"`**，窗口不会自动变小。
> 因此 AI 助手输入区必须自己声明 `Modifier.imePadding()`，否则键盘会直接盖住输入栏。
> 新增带输入框的全屏页面时同样需要加 `imePadding()`。

> **关于 `HorizontalPager` 与 `PagerState`（易踩坑）**：日期栏**不要**再单独开第二个 `HorizontalPager`。
> 两个 Pager 共用同一个 `PagerState` 时，第二个 Pager 的 LazyLayout 只会组合初始页（page 0/1），
> 拿到的是 `currentPage` 还是 0 时的数据，不会跟着 `animateScrollToPage` 更新——
> 曾经表现为周视图日期栏 7 个格子**全部显示「26」**（本周日期的回退值）。
> 现在日期栏由 `pagerState.currentPage` 推导周次、用普通 `Row` 渲染，只有课程网格由真正的 Pager 承载。
> 另外，Pager 的 page 内容若用 `Modifier.fillMaxSize()`，会把同一 `Column` 里后面的兄弟挤到零高度
> （曾导致时间轴与课程网格**整块消失**），page 根节点必须给确定高度（如 `.height(DayHeaderHeight)`）。

### 4.1 基础课表 / 实际课表与日期变更（v2.0.0，改 AI、调休、周视图前先读这一节）

**两张课表，一个快照。**

```
DaySnapshot(date, week, holiday, effectiveDayOfWeek, baseBlocks, blocks, overrides)
                                                      ^^^^^^^^^  ^^^^^^  ^^^^^^^^^
                                                      原始排课    当天实际  该日的日期变更记录
```

- `baseBlocks` 只由**排课规则**算出：学期起始日 + 总周数 + 星期 + 节次 + 单双周 + 课程起止周
  + 「调休补课日按被补的那一天的星期排课」。**放假不会清空它**——这正是「10-07 放假看不到课，
  但仍然能把当天原本的课调走」的依据。
- `blocks` 是 `baseBlocks` 依次应用 `CANCEL`（停课/放假）→ `MOVE_OUT`（整天调出）→ `MOVE_IN`（整天调入）
  → `ADD`（单日补课）→ `ROOM`（换教室）之后的「当天实际要上的课」。
- 两者同时存在于快照里，所以任何调用方都能自己决定要哪一张。

**日期变更只写 `schedule_overrides` 一张表，`course_sessions`（整学期排课规则）一行都不动。**
这是「调课只影响指定日期、不会误改整学期」的物理保证。
`ScheduleOverrideDao.find(date, type, sessionId)` 在写入前判重，所以重复执行同一条调课指令不会重复添加课程。

**唯一权威是 `DayPlanner`。** 今日页、周视图、桌面小组件、AI 四处**必须**走同一条链路：

```
Room 四张表（course_sessions / period_times / holidays / schedule_overrides）
  └─ ScheduleRepository.plannerInputs : Flow<PlannerInputs>      ← 四流合流
       ├─ daySnapshot(date, week) : Flow<DaySnapshot>  → DayPlanner.snapshotOf(...)   （今日页、卡片）
       └─ weekBlocksOf(week, semesterStart, totalWeeks, inputs)                       （周视图）
            └─ 逐日 DayPlanner.snapshotOf(...).blocks，再 pinToDate() 后按 星期/节次 排序
```

- `DayPlanner` 是 `internal object`，纯函数、不碰 Room / Context，所以能在 JVM 单测里覆盖跨周、单双周、
  放假、调休补课、重复调课等边界（见 `DayPlannerTest` / `WeekPlannerTest`）。
- `pinToDate()` 把块的 `dayOfWeek` 改写成**该日期自己的星期**。不改写的话，周六补周一的课会被画到周一那一列去。
- **不要再新增第二条查询路径。** v2.0.0 之前周视图走的 `schedulesByDay` 只 `combine(occurrences, periods)`，
  既不读节假日也不读日期变更，于是「AI 调完课，周视图里什么都没有」——这是本项目出现过的最隐蔽的一类不一致。
  任何新的课表读取需求，都应该先问「它是不是应该基于 `DayPlanner`」。

**AI 侧对应关系**：`AiContextBuilder.semesterOverview` 注入整学期规则与已有日期变更，
`dayDetail(repo, date, ctx, base = true)` 同时输出基础课表与实际课表；
`AiActionExecutor.execute(action, ctx)` 的每个动作最终都调用与手动编辑同一批 `ScheduleRepository` 方法。

---

## 二、技术栈

| 项目 | 选型 |
| --- | --- |
| 语言 | Kotlin 2.3.21 |
| UI | Jetpack Compose（Compose BOM 2026.09.00，Material 3） |
| 构建 | AGP **9.4.1**（内置 Kotlin 支持）+ Gradle 9.6.0 + Version Catalog |
| 本地存储 | Room 2.8.5（KSP）+ DataStore Preferences 1.2.1 |
| 异步 | Coroutines / Flow 1.10.2 |
| 桌面小组件 | Jetpack Glance 1.2.0（appwidget + material3） |
| 后台与提醒 | WorkManager 2.12.0 + 通知渠道 |
| OCR | Google ML Kit `text-recognition-chinese` 16.0.1（离线免费） |
| 其他 | Navigation Compose、DocumentFile、Core Library Desugaring（java.time） |

> **重要**：AGP 9.x 起 **不再允许** 应用 `org.jetbrains.kotlin.android` 插件（Kotlin 已内置）。
> 本项目的 `app/build.gradle.kts` 只应用 `com.android.application`、`kotlin.plugin.compose`、`ksp`。
> 同时 `compileSdk = 37`（Android 17 平台）以满足 Compose 1.12 / core 1.19 的 AAR 元数据要求。

---

## 三、项目结构

```
app/src/main/java/com/university/timetable/
├── TimetableApp.kt                  Application + ServiceLocator（轻量 DI）+ 通知渠道
├── MainActivity.kt                  edge-to-edge 入口、通知权限、主题装配
├── data/
│   ├── AppDatabase.kt               Room 数据库
│   ├── entity/                      Course、CourseSession、PeriodTime、Holiday、DormService
│   ├── dao/                         CourseDao、PeriodTimeDao、HolidayDao、DormServiceDao
│   ├── prefs/PreferencesRepository  DataStore 偏好（主题/周次/背景/提醒…）
│   ├── repository/ScheduleRepository 周次换算、单双周过滤、节假日生效日、增删改查
│   └── seed/SeedSchedule            示例课表 / 默认作息 / 节假日 / 生活服务
├── util/                            SemesterWeeks、WeekPatternParser、ImageLoader
├── importer/                        ScheduleTextParser、ScheduleHtmlParser、ScheduleCsvParser、OcrScheduleParser
├── notification/                    NextClassResolver、FluidCloudNotification、Notifiers
├── work/                            FluidCloudWorker、ClassReminderWorker、LaundryTimerWorker、BootReceiver
├── widget/                          Glance 今日/本周小组件
└── ui/
    ├── MainViewModel.kt             状态中枢（今日/本周/偏好/洗衣计时）
    ├── TimetableAppRoot.kt          Pager + 胶囊导航 + 全部弹窗编排
    ├── PageBackground.kt            页面背景（纯色 / 自定义图 + 透明度 + 模糊）
    ├── component/                   CapsuleTabRow、AppTopBar、CourseCard、LifeAssistantCard
    ├── screen/                      TodayScreen、WeekScreen、SettingsScreen
    │   ├── dialog/                  CourseEditorSheet、ImportDialog、PeriodTimeEditor、TimetableSettingsDialog
    │   └── sheet/ClassActionsSheet
    └── theme/                       Theme、ThemePresets、CoursePalette
```

---

## 四、构建与安装

### 方式一：Android Studio（推荐）
1. `File → Open` 选择本目录（`UniversityTimetable`）。
2. 等待 Gradle Sync 完成（需联网下载依赖，首次约 3–8 分钟）。
3. `Run ▶` 直接裝到手机；或 `Build → Generate Signed App Bundle / APK`。

> `local.properties` 中的 `sdk.dir` 需指向你自己的 Android SDK。

### 方式二：命令行
```bash
# Windows
set JAVA_HOME=D:\Android Studio\jbr
gradlew.bat assembleDebug

# macOS / Linux
export JAVA_HOME=/Applications/Android\ Studio.app/Contents/jbr/Contents/Home
./gradlew assembleDebug
```

产物位置：
- Debug：`app/build/outputs/apk/debug/app-debug.apk`
- Release（已签名）：`app/build/outputs/apk/release/app-release.apk`

### 签名信息

> **本仓库不包含签名密钥。** 密钥库与口令**故意不入库**（见 `.gitignore`）—— 一旦公开，任何人都能签出与正式版同签名的 APK，已安装的用户会被静默「升级」到伪造版本。

本机要出正式包，在项目根目录建一个 `keystore.properties`（同样已被忽略）：

```properties
storeFile=keystore/university-timetable.jks
storePassword=你的密钥库口令
keyAlias=timetable
keyPassword=你的密钥口令
```

`app/build.gradle.kts` 会自动读取它；**读不到时退回 debug 签名**，所以任何人 clone 下来都能直接 `gradlew assembleRelease` 跑通构建 —— 只是产物签名不同，不能覆盖安装正式版。

生成一套自己的密钥库：

```bash
keytool -genkeypair -v -keystore keystore/university-timetable.jks \
  -alias timetable -keyalg RSA -keysize 2048 -validity 10000
```

### 安装
```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

---

## 五、课表数据说明

首次启动会写入一套**示例课表**（作息与课程取自你提供的课表截图 / HTML，作为时间轴排版参考），
包括上午 1–4 节（08:00 起）、下午 5–8 节（13:30 起）、晚上 9–12 节（18:30 起）。

- 教师姓名与部分地点在截图中不够清晰，已标注或留空，**可直接在应用内修改**。
- 在「设置 → 清空全部课程」后，用「智能导入」导入你自己的课表即可。
- 作息时间可在「三点菜单 / 设置 → 作息时间设置」中逐节修改，**时间轴与周视图会立即重新排版**。
- 学期起始日与总周数可在「课表设置」中调整，示例以 **2026-08-31 为第 1 周周一**（第 4 周 = 2026/09/21–09/27）。

### 支持的导入格式示例

CSV（带表头，推荐）：
```csv
课程名称,授课教师,星期,节次,周次,上课地点
高等代数（一）,林艳,星期三,3-4节,1-16周,天山堂A212
大学物理,张文博,星期二,1-2节,1-16周,电教202
```

CSV（无表头网格）：
```csv
星期一,高等代数（一）,3-4,1-16周,天山堂A212
星期四,高等代数（一）,3-4,1-16周,天山堂B307
```

HTML：教务系统「导出 / 打印」保存的网页文件，含 `<table>` 且表头为「星期一…星期日」即可。

图片：课表截图（含课程名、星期、周次、地点最佳），走 ML Kit 中文 OCR。

---

## 六、权限说明

| 权限 | 用途 |
| --- | --- |
| `POST_NOTIFICATIONS` | 上课提醒、流体云常驻通知、洗衣完成提醒 |
| `VIBRATE` | 提醒震动 |
| `RECEIVE_BOOT_COMPLETED` | 开机后重建后台任务 |
| `SCHEDULE_EXACT_ALARM` | 精确上课提醒（被拒绝时自动降级为 WorkManager 周期任务） |
| `READ_MEDIA_IMAGES` / `READ_EXTERNAL_STORAGE` | 选择课表截图做 OCR |

**无需网络权限**：OCR 与全部解析均在本地完成。

---

## 七、已知限制与后续可做

1. **流体云**为「高优先级常驻通知」平替方案。ColorOS 是否展示为流体云卡片取决于系统版本与用户设置
   （需在系统设置中允许该应用通知为「常驻/重要」）。若后续获得 ColorOS 官方 SDK，可替换 `FluidCloudNotification`。
2. 上课提醒采用 WorkManager 15 分钟周期 + 通知时间窗判断，极端情况下可能有几分钟偏差；
   如需秒级精确，可接入 `AlarmManager.setExactAndAllowWhileIdle` 为每节课精确排程。
3. 周视图左右滑动为**拖动阈值**切换（非跟手分页），这是为了与纵向滚动共存；
   如需完全跟手，可改为 `HorizontalPager` 包裹每周网格。
4. 示例课表中的教师名与个别地点为占位/推断值，请以教务系统为准。

---

## 八、故障排查（FAQ）

### 启动闪退：WorkManager is not initialized properly
**已在 v1.0.1 修复。**
早期版本在 `AndroidManifest.xml` 中用 `tools:node="remove"` 移除了
`androidx.work.WorkManagerInitializer`，但 `TimetableApp` 既没有手动调用
`WorkManager.initialize()`，也没有实现 `Configuration.Provider`，
导致 `Application.onCreate()` 中调度周期任务时抛出：

```
java.lang.RuntimeException: Unable to create application
  ...IllegalStateException: WorkManager is not initialized properly
  at com.university.timetable.work.FluidCloudWorker$Companion.schedule
  at com.university.timetable.TimetableApp.onCreate
```

修复方式：**不要**移除 `WorkManagerInitializer`，使用 androidx.startup 默认初始化即可。
如需自定义配置，请让 `TimetableApp` 实现 `Configuration.Provider` 并移除默认初始化。

> 排查这类「一打开就闪退」最快的方法：
> ```bash
> adb logcat -c && adb shell am start -n <包名>/<包名>.MainActivity
> adb shell dumpsys dropbox --print | findstr /i <包名>
> ```
> `dumpsys dropbox` 能直接打印出完整的崩溃堆栈。

### 桌面卡片显示「载入窗口小部件时出现问题」
按以下顺序处理：

1. **先打开一次应用**：首次启动需要写入课表与校园生活数据，卡片才有内容可渲染。
2. **删除旧卡片重新添加**：如果卡片是在旧版本（或应用重装前）添加的，其 widgetId 已失效，
   ColorOS 会一直显示这个错误。长按卡片 → 移除，然后重新从「小组件」列表拖一张新的。
3. **按指引重新添加**：应用内「设置 → 添加桌面卡片」有完整的分步说明与排错提示。
4. 仍不行时，到系统「设置 → 应用 → 娅娅课表 → 存储」清除缓存后重试。

卡片自身已做容错：数据读取失败时会显示「读取课表失败」而不是空白；两种卡片都显式使用
`PreferencesGlanceStateDefinition`，避免状态不一致。

> **关于卡片正文（易踩坑，v1.8.5 修复）**：卡片正文里**绝对不要用 `LazyColumn` / `items`**。
> 启动器渲染 RemoteViews 集合时，`RemoteCollectionItemsAdapter.getView()` 会把每个条目当成
> **独立子组件**去 `newView.updateAppWidget(item)`，而 `AppWidgetHostView.AdapterChildHostView`
> 从不设置 `appWidgetId`（一直是 `INVALID_APPWIDGET_ID`），于是每个条目都被换成
> 「无法添加微件。」错误视图（日志 `getErrorView, appWidgetId0 ... RemoteCollectionItemsAdapter.getView:193`）。
> 更糟的是错误视图**常驻不恢复**：`AppWidgetHostView.applyContent()` 在 `mViewMode == VIEW_MODE_ERROR`
> 时直接 return，之后所有更新都被吞掉。**修法：正文改用静态 `Column` + `take(n)` 展开**。
> 另外也不要在列表 `Column` 尾部追加「还有 N 项」这类**兄弟节点**——实测多一个兄弟 `Text` 会让
> 整块 RemoteViews inflate 失败，卡片退化成「载入窗口小部件时出现问题」。
>
> **关于颜色：`ColorProvider(Int)` 是「颜色资源 id」而不是 ARGB（最坑，v1.8.6 修复）**。
> Glance 里 `ColorProvider(...)` 有多个重载，**只有 `Color(day = Color(...), night = Color(...))`
> 形式接受字面量颜色**；`ColorProvider(0xFF4C7DFF.toInt())` 会匹配到 `@ColorRes Int` 重载，
> 运行期执行 `context.getColor(0xFF4C7DFF)`，必然抛 `Resources.NotFoundException`。
> 异常发生在**启动器的 RemoteViews 应用阶段**（`RemoteViews.applyAsync` 的 `onError`），
> 表现是卡片整块变成「载入窗口小部件时出现问题」，而且
> **OEM 版 `AppWidgetHostView` 不打印任何堆栈**，只留一行 `mViewMode == VIEW_MODE_ERROR, appWidgetId: N`，
> 极易被误判为「布局太复杂 / LazyColumn 的锅」。
> **正确写法**（本仓库 `GlanceWidgets.kt` 里的 `FixedColorProvider(argb: Int)`）：
> ```kotlin
> private fun FixedColorProvider(argb: Int) =
>     ColorProvider(day = Color(argb), night = Color(argb))
> ```
> 排查这类问题的最快办法是**二分法**：把卡片正文逐步换成「外壳 → 标题行 → 两列骨架 → 单个列表项」，
> 每次只 `:app:assembleDebug` + `adb install -r -d` + 在应用里触发一次刷新
> （`adb logcat -s WidgetRefresh` 能看到 `待办+课表：桌面上有 N 个实例，刷新中`），
> 登录器侧只看 `adb logcat -s AppWidgetHostView`。
> **另一个重要结论：卡片一旦成功 apply，会自动从错误态恢复**——`applyContent()` 只在
> `content == null` 时才因为 `VIEW_MODE_ERROR` 提前返回；只要发出的 RemoteViews 能正常 inflate，
> 桌面上的坏卡片会自己变好，**不需要用户长按删除后重新添加**。
>
> **关于 `Color` 转 Int：必须用 `Color.toArgb()`，不要用 `Color.value.toInt()`（v1.8.7 修复）**。
> Compose 的 `Color.value` 是 `ULong`，sRGB 的 32 位色值放在**高 32 位**，低 32 位是色彩空间 id。
> 所以 `Color(0xFFF3F3F7).value.toInt()` 的结果是 **0**（透明），不是 `0xFFF3F3F7`：
> 拿它去画色条，经过 `withAlpha()` 之后会变成**纯黑**，看起来就是「课程没有彩条」；
> 拿它当文字颜色，则因为透明而回退成 RemoteViews 的默认文字色，反而「看起来正常」，
> 所以这个 bug 极难从界面上察觉。
> ```kotlin
> // 错误：低 32 位是色彩空间 id，恒为 0
> val argb = color.value.toInt()
> // 正确
> val argb = color.toArgb()
> ```
> 另外**色条不能用「文字前景色」**：`CoursePalette.colorsFor(...)` 返回 `(容器色, 前景色)`，
> 自定义深色课程色的前景色接近纯白，用它画色条就是一条白条。色条要的是 `barArgb`
> （自定义色取课程色本身，预设色取对应主题下的柔和色）。
>
> **关于卡片预览（易踩坑，v1.8.5 修复）**：v1.8.4 及更早版本把 provider 的
> `android:previewLayout` 指向 Glance 的占位布局（`@layout/widget_loading`）。卡片内容是
> **异步**渲染的 RemoteViews，启动器在「添加卡片」流程里会去 inflate 预览布局，拿不到可绘制内容
> 时就显示「无法添加微件」。**修法：改成 `android:previewImage` 指向真实位图**
> （`mipmap-*/widget_preview_full.png` / `widget_preview_compact.png`，每个密度一套）。
> 教训：**不要给 Glance 卡片配 `previewLayout`**，用位图预览最稳。
>
> **关于卡片刷新（易踩坑）**：卡片走 RemoteViews 通道，**不会**自动跟随 Room/DataStore 变化。
> 所有写入口都要显式刷新；`WidgetRefresh.refreshAllWidgets()` 先用
> `GlanceAppWidgetManager.getGlanceIds()` 查出桌面上真实存在的实例并写日志，再统一 `updateAll()`。
> **不要**再对同一个实例额外 `update(context, id)`：重复刷新会让启动器的 `AppWidgetHostView`
> 拿到 `appWidgetId: 0` 的 RemoteViews，报 `getErrorView ... 无法添加微件`，且该错误视图常驻、
> 不会自行恢复（只能长按删除卡片后重新添加）。排查时 `adb logcat -s WidgetRefresh` 看有没有命中实例。

### 通过 adb 安装 release 包失败（ColorOS）
ColorOS 可能拦截 adb 安装非调试包，日志出现
`OPLUS_ADB_INSTALL_CANCEL`。此时请在手机上手动点击「继续安装 / 允许」，
或直接把 APK 传到手机用文件管理器安装。

### 桌面出现两个「娅娅课表」图标
Debug 包（`com.university.timetable.debug`）与 Release 包
（`com.university.timetable`）是相互独立的应用，因此会同时存在两个图标，
便于对比测试。只保留 Release 时卸载 debug 包即可：
```bash
adb uninstall com.university.timetable.debug
```

### 夜间模式下有些文字看不清（v1.1.0 及更早版本）
**已在 v1.2.0 修复。** 原因是若干页面把颜色硬编码成了深色（如 `Color(0xFF6B6B73)`、
`Color(0xFFA3232F)`、`Color(0xFFB76A1E)`），切到夜间模式时背景变深、文字仍是深色。

修复方式：新增语义化颜色令牌并全部改为随主题变化——
`success / warning / neutral`、`holidayContainer / onHolidayContainer`、
`makeupContainer / onMakeupContainer`，深浅色各一套（见 `ui/theme/Theme.kt`）。
桌面卡片也由固定浅色改为 Glance 的 `ColorProvider(day, night)`。
如后续新增界面，请避免直接写 `Color(0xFF...)`。

### 「校园生活」点击没反应（v1.0.1 及更早版本）
旧版本的「校园生活助手」卡片是纯展示，点击无效。v1.1.0 起卡片**标题行整行可点**，
并新增独立的「校园生活」子页面；v1.2.0 起该子页面带滑入/滑出动画。

