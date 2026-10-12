package com.university.timetable.importer

/**
 * 课表文本的通用语义识别工具：地点判定、课程名清洗、单元格切分。
 * HTML / CSV / OCR / 纯文本解析器共用同一套规则。
 */
object ScheduleSemantics {

    /**
     * 地点关键词。
     *
     * 注意：**不要**使用单字「教」作为关键词——「高等代数」也含有「教」，
     * 会误判为地点。这里使用更明确的复合词。
     */
    private val locationKeywords = listOf(
        "天山堂", "秦岭堂", "厚德楼", "主教", "电教", "教学楼", "实验楼", "图书馆",
        "机房", "网络中心", "体育馆", "游泳馆", "报告厅", "阶梯教室",
        "楼", "馆", "室", "中心", "校区", "栋", "座", "区"
    )

    private val weekPattern = Regex("""\d+\s*[-~－—]?\s*\d*\s*周""")
    private val teacherLabel = Regex("""^\s*(授课教师|任课教师|教师|老师)\s*[:：]?""")
    private val locationPrefix = Regex("""^[@@]\s*""")

    /** 是否是周次描述，例如「1-16周」「2-18周(双)」。 */
    fun isWeek(text: String): Boolean =
        text.length <= 20 && weekPattern.containsMatchIn(text)

    /** 是否是地点。 */
    fun isLocation(text: String): Boolean {
        if (text.isBlank() || text.length > 28) return false
        val t = locationPrefix.replace(text, "")
        return locationKeywords.any { t.contains(it) }
    }

    /** 是否带有「授课教师：」这样的前缀。 */
    fun hasTeacherLabel(text: String): Boolean = teacherLabel.containsMatchIn(text)

    /** 去掉教师标签前缀。 */
    fun stripTeacherLabel(text: String): String = teacherLabel.replace(text, "").trim()

    /** 去掉地点前缀「@」。 */
    fun stripLocationPrefix(text: String): String = locationPrefix.replace(text, "").trim()

    /**
     * 判断是否像是课程名：不是纯数字、不是周次、不是地点，长度适中。
     */
    fun looksLikeCourseName(text: String): Boolean {
        val t = text.trim()
        if (t.isBlank()) return false
        if (t.toDoubleOrNull() != null) return false
        if (isWeek(t)) return false
        if (isLocation(t)) return false
        return t.length in 1..40
    }

    /**
     * 清洗课程名：剥离首尾书名/圆括号包裹、去掉学分数字与备注标记。
     * 括号为成对剥离，避免出现「高等代数（一」这类残缺结果。
     */
    fun cleanCourseName(raw: String): String {
        var t = raw.trim()
        // 去掉「(xxx) : 4」这类学分后缀
        t = t.replace(Regex("""\s*[:：]\s*\d+(\.\d+)?\s*$"""), "")
        // 去掉【】备注
        t = t.replace(Regex("""【[^】]*】"""), "")
        // 成对剥离最外层括号（半角或全角）
        t = stripWrappingPair(t, '(', ')')
        t = stripWrappingPair(t, '（', '）')
        return t.trim()
    }

    private fun stripWrappingPair(text: String, open: Char, close: Char): String {
        var t = text.trim()
        while (t.length >= 2 && t.first() == open && t.last() == close) {
            t = t.substring(1, t.length - 1).trim()
        }
        return t
    }

    /** 从「(高等代数（一）) : 4」中提取括号内的课程名。 */
    fun extractParenthesizedName(line: String): String? {
        // 优先取最外层括号内容
        val inner = Regex("""[（(](.+)[）)]""").find(line)?.groupValues?.get(1)?.trim()
        if (!inner.isNullOrBlank()) return cleanCourseName(inner)
        return null
    }

    /** 按多种分隔符把一行切成若干片段。 */
    fun splitFragments(raw: String): List<String> = raw
        .split('\n', '\t', '|', '｜', ';', '；')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
}
