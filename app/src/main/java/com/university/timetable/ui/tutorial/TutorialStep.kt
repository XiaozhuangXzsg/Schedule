package com.university.timetable.ui.tutorial

/**
 * 引导中的一步。
 *
 * 设计约定（很重要，直接决定「操作完成后自动继续」能不能成立）：
 * - [anchor] 是本步要高亮的目标锚点 id。锚点不存在（用于还没导航到目标页面、或目标组件当前没被渲染）时，
 *   引导层会**退化成不拦截点击的浮层**，用户仍可正常翻页、返回、操作，不会出现「遮罩卡死」。
 * - [requiresAction] = true 表示这一步需要用户真的去操作，不允许只靠点「下一步」糊弄过去。
 *   自动继续由两个可观测信号驱动，**不使用任何定时器**：
 *     1. [advanceAnchor] 出现 —— 说明用户已经导航/打开了下一个界面；
 *     2. [anchor] 从「出现过」变成「消失」—— 说明用户点掉了当前高亮目标（例如点了导入弹窗里的入口）。
 * - [hideNext] 用于结束页：不显示「下一步」，只显示结束按钮。
 * - [bodyWhenNoData] 用于**没有课表数据**时的诚实文案。需求明确要求「没有课表数据时不要伪造导入结果」，
 *   因此结果页/调课页在无数据时会改用它，而不是照抄「导入成功」。
 */
data class TutorialStep(
    val id: String,
    val title: String,
    val body: String,
    val anchor: String?,
    val requiresAction: Boolean = false,
    val actionHint: String? = null,
    val hideNext: Boolean = false,
    /** 该锚点出现时自动进入下一步；null 表示不依赖「出现」信号。 */
    val advanceAnchor: String? = null,
    /** 没有课表数据时替换 [body] 的文案；null 表示本步与课表数据无关。 */
    val bodyWhenNoData: String? = null
)

/**
 * 锚点 id 常量表。
 *
 * 这些 id 必须与实际接线处一一对应，改名字会静默失效（找不到锚点只会退化成居中卡片，不会崩）。
 */
object TutorialAnchors {
    /** 设置入口：底部胶囊导航的「设置」标签。 */
    const val SETTINGS_ENTRY = "settings_entry"

    /** 设置页「课表数据」分组里的「智能导入课表」行。 */
    const val SETTINGS_IMPORT = "settings_import"

    /**
     * 教务导入页（[com.university.timetable.ui.screen.LzuImportActivity]）顶部的提示条。
     *
     * ⚠️ 注意：那是**独立 Activity**，与本引导层不在同一个 Compose 组合里，
     * 因此这个锚点**不能**被主界面的引导层观测到（登记了也不会被读取）。
     * 保留它只是为了语义完整 + 将来若把引导层也搬进导入页时可直接复用。
     */
    const val IMPORT_PAGE_HINT = "import_page_hint"

    /**
     * 导入弹窗里的「登录教务系统导入」入口行。
     *
     * ⚠️ 只用作「出现即推进」的**信号**，不用于画高亮。
     *
     * [com.university.timetable.ui.screen.dialog.ImportDialog] 内部是
     * `Dialog(...)`（见 `ImportDialog.kt:172`），也就是**独立窗口**：它的子节点能读到
     * [LocalTutorialRegistry]（Compose 的 Dialog 会继承 CompositionLocal，所以 `report()` 会真的发生，
     * 作为推进信号完全可靠），但 `boundsInRoot()` 返回的是**弹窗自己那个窗口**里的坐标，
     * 与主窗口引导层的坐标系不一致，拿它去挖洞会画到错误的位置。
     *
     * 因此：本锚点只喂给 [TutorialStep.advanceAnchor]，**不要**把它设成某一步的 [TutorialStep.anchor]。
     */
    const val IMPORT_DIALOG_ENTRY = "import_dialog_entry"

    /** AI 助手入口：今日/本周页顶栏的 ✨，或设置页「AI 助手」分组里的「打开 AI 助手」行。 */
    const val AI_ENTRY = "ai_entry"

    /** 个性化入口：设置页「个性化」分组的容器。 */
    const val PERSONALIZE_ENTRY = "personalize_entry"

    /** 课程卡片容器：今日页/周视图里的课程块（用于说明「长按课程可编辑」）。 */
    const val COURSE_LONG_PRESS = "course_long_press"

    /** 小组件入口：设置页「添加桌面卡片」行。 */
    const val WIDGET_ENTRY = "widget_entry"
}

/**
 * 引导的全部文案与步骤。
 *
 * 文案里的功能描述**只写本项目真实存在的能力**：
 * - AI 助手（[com.university.timetable.data.ai.AiAction]）支持：查询任意日期/周次的课表、
 *   新增课程、修改课程（改整学期规则 / 只改某一天）、删除课程、调课（整天 / 单门）、补课、
 *   停课、换教室、加待办、改校园生活设施时间、启动洗衣机倒计时；使用前需要先在模型配置里填 API。
 * - 调课的两条真实路径：长按课程块编辑；周视图三点菜单里的「调休」（整天调课）；AI 也能调课。
 * - 调课只写进本 App 的数据库，**不会**同步回教务系统。
 * - 桌面内容是**手机桌面小组件**（不是应用内页面）：设置页「添加桌面卡片」里有说明弹窗，
 *   系统侧流程是「桌面长按 → 微件/小组件 → 找到娅娅课表」。
 */
object TutorialSteps {

    /** 作者 QQ（结束页展示，也是「复制 QQ 号」复制的内容）。 */
    const val AUTHOR_QQ = "2741369898"

    /** 好友验证信息（结束页必须显著展示）。 */
    const val AUTHOR_QQ_VERIFY = "课表"

    const val WELCOME_TITLE = "欢迎使用课表！"
    const val WELCOME_BODY = "欢迎使用课表！先导入本学期课表，再一起了解几个常用功能。"

    // 结束页拆成三段，方便界面把「作者 QQ / 好友验证信息」单独做成醒目区块（不重复渲染同一句话）。
    const val FINISH_TITLE = "准备就绪，开始使用课表吧！"
    const val FINISH_INTRO = "使用过程中遇到问题，或有建议，欢迎联系作者。"
    const val FINISH_OUTRO = "当前版本为测试版。你可以等待正式版发布，也可以联系作者获取临时更新版本。"

    /** ①..⑩，顺序即需求里的顺序；⑩ 是结束页。 */
    val ALL: List<TutorialStep> = listOf(
        TutorialStep(
            id = "settings_entry",
            title = "① 设置入口",
            body = "先从这里进入设置，导入你的课表。",
            anchor = TutorialAnchors.SETTINGS_ENTRY,
            requiresAction = true,
            actionHint = "点一下底部胶囊里的「设置」。",
            advanceAnchor = TutorialAnchors.SETTINGS_IMPORT
        ),
        TutorialStep(
            id = "settings_import",
            title = "② 导入课表",
            body = "点击这里，从教务系统导入本学期课表。",
            anchor = TutorialAnchors.SETTINGS_IMPORT,
            requiresAction = true,
            actionHint = "点「智能导入课表」，会弹出导入方式。",
            advanceAnchor = TutorialAnchors.IMPORT_DIALOG_ENTRY
        ),
        TutorialStep(
            id = "import_flow",
            title = "③ 怎么从教务系统导入",
            body = "请先登录教务系统，进入本学期课表页面，点击「个人课表」，" +
                "然后点击本软件的「导入课表」按钮。\n\n" +
                "完整顺序是：\n" +
                "1. 登录教务系统\n" +
                "2. 进入本学期课表\n" +
                "3. 点击教务系统的「个人课表」\n" +
                "4. 点击本软件的「导入课表」按钮\n\n" +
                "注意：「个人课表」是**教务系统网页里**的菜单，" +
                "「导入课表」是**本软件右下角**的按钮，两个不是同一个东西。",
            // anchor = null：本步讲的是「教务导入页」上的操作，而那是**独立 Activity**，
            // 主窗口的引导层根本盖不到它；导入弹窗又是独立 Dialog 窗口。所以这一步做成居中卡片，
            // 真正给用户看的提示写在导入页自己的提示条里（接线时替换 LzuImportActivity.kt:407-409 的文案）。
            anchor = null,
            actionHint = "接下来会在设置页弹出导入方式，选「登录教务系统导入」。" +
                "进入导入页后，页面顶部的提示条就是这四步。"
        ),
        TutorialStep(
            id = "import_result",
            title = "④ 导入结果",
            body = "课表导入成功！接下来了解几个常用功能。",
            anchor = null,
            bodyWhenNoData = "你现在还没有课表数据，这一步就先跳过。" +
                "之后随时可以在「设置 → 智能导入课表」里导入，导入后本教程可以在设置里重看。"
        ),
        TutorialStep(
            id = "later_import",
            title = "⑤ 稍后导入也没关系",
            body = "导入失败时，导入页会给出失败说明，并可以回到课表页重试；" +
                "点「稍后导入」也能继续往下看。\n\n" +
                "想重新导入：设置 → 智能导入课表。导入前如果担心重复，" +
                "可以先在同一个分组里点「清空全部课程」。",
            anchor = null
        ),
        TutorialStep(
            id = "ai_entry",
            title = "⑥ AI 助手入口",
            body = "从这里打开 AI 助手，用自然语言描述你的需求。\n\n" +
                "它真的能改数据（改完会弹确认框，可以撤销）：查询任意日期、任意周次的课表、" +
                "新增课程、修改课程、删除课程、调课、补课、停课、换教室、加待办。\n\n" +
                "第一次使用需要先配置模型 API（在 AI 助手页右上角进入模型配置）。",
            anchor = TutorialAnchors.AI_ENTRY,
            actionHint = "今日 / 本周页在右上角 ✨；设置页在「AI 助手」分组里。"
        ),
        TutorialStep(
            id = "personalize_entry",
            title = "⑦ 个性化",
            body = "在这里调整课表的外观，设置你喜欢的显示方式。\n\n" +
                "目前真实可调的有：主题色（7 套）、娅娅·哥伦比娅风格、跟随系统深浅色、" +
                "日间 / 夜间切换、背景与透明度（背景图、模糊度）、页面转场动画、已上课程如何显示；" +
                "「显示」分组里还能开关授课教师、上课地点、单双周标记、周末、当前时间线等。",
            anchor = TutorialAnchors.PERSONALIZE_ENTRY,
            actionHint = "这一块就在设置页的「个性化」分组。"
        ),
        TutorialStep(
            id = "course_edit",
            title = "⑧ 改课与调课",
            body = "在「今日」或「本周」页面：\n" +
                "• 长按（或点一下）课程块 → 编辑 / 删除这一门课；\n" +
                "• 本周页右上角 ⋮ →「调休」→ 把某一天原本要上的课整体挪到另一天（整天调课）；\n" +
                "• 也可以用 AI 助手说「把 10 月 7 日的课调到 10 月 10 日」。\n\n" +
                "重要：这些改动只保存在本软件里，**不会同步到教务系统**，" +
                "教务系统上的课表不会被修改。",
            anchor = TutorialAnchors.COURSE_LONG_PRESS,
            bodyWhenNoData = "你还没有课表数据，所以现在长按不到课程块。" +
                "导入课表后，长按课程块就能编辑它。\n\n" +
                "先记住一点：本软件里的调课只改软件内的课表，**不会同步到教务系统**。",
            actionHint = "先切到「今日」或「本周」页，再长按课程块试试（放心，长按只是打开菜单，不会自动改数据）。"
        ),
        TutorialStep(
            id = "widget_entry",
            title = "⑨ 桌面小组件",
            body = "课表可以放到手机桌面（是**手机桌面小组件**，不是应用内页面）。\n\n" +
                "添加方式：回到手机桌面 → 长按空白处 → 微件 / 小组件 → 找到「娅娅课表」→ 拖到桌面。\n\n" +
                "设置页里的「添加桌面卡片」有详细的尺寸说明与常见问题。",
            anchor = TutorialAnchors.WIDGET_ENTRY,
            actionHint = "设置页「课表数据」分组里的「添加桌面卡片」。"
        ),
        TutorialStep(
            id = "finish",
            title = FINISH_TITLE,
            body = "$FINISH_INTRO\n\n作者 QQ：$AUTHOR_QQ\n好友验证信息：$AUTHOR_QQ_VERIFY\n\n$FINISH_OUTRO",
            anchor = null,
            hideNext = true
        )
    )

    /** 结束页（最后一步）。 */
    val last: TutorialStep get() = ALL.last()

    /**
     * 取当前步骤实际要显示的正文。
     *
     * 只做一件事：在没有课表数据、且该步声明了 [TutorialStep.bodyWhenNoData] 时换成诚实文案，
     * 避免出现「明明没导入成功，引导却说导入成功」这种假结果。
     */
    fun bodyOf(step: TutorialStep, hasCourseData: Boolean): String =
        if (!hasCourseData && step.bodyWhenNoData != null) step.bodyWhenNoData else step.body
}
