package com.github.meteors87.assmartlogcheck.toolWindow

import com.github.meteors87.assmartlogcheck.contants.WindowsConstants
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionAdapter
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JSplitPane
import javax.swing.JTextPane
import javax.swing.SwingUtilities
import javax.swing.text.BadLocationException
import javax.swing.text.SimpleAttributeSet
import javax.swing.text.StyleConstants
import javax.swing.text.StyledDocument
import java.util.regex.Pattern
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class MyToolWindowPanel(private val project: Project) {

    private val mainPanel: JPanel
    private lateinit var logDoc: StyledDocument
    private val linkPositions = mutableListOf<Triple<Int, Int, String>>()

    // MODIFIED: 添加成员变量以便在其他方法中控制
    private lateinit var mainSplitPane: JSplitPane
    private lateinit var criteriaWrapper: JPanel

    // 搜索框组件
    private lateinit var criteriaContainer: JPanel
    private lateinit var searchBox: JPanel
    private lateinit var filterBox: JPanel
    private val searchFields = mutableListOf<JBTextField>()
    private val filterFields = mutableListOf<JBTextField>()
    private var isSearchVisible = false
    private var isFilterVisible = false

    private var searchBoxHeight = 90
    private var filterBoxHeight = 90
    private val minBoxHeight = 60   // 最小高度（约2行）
    private val maxBoxHeight = 250  // 最大高度（约8行）


    private val filterStringSet = mutableSetOf<String>()
    private val searchStringSet = mutableSetOf<String>()

    init {
        mainPanel = createPanel()
    }

    fun getContent(): JPanel = mainPanel

    private fun createPanel(): JPanel {
        val panel = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            border = JBUI.Borders.empty(10)
        }
        val contentPanel = JBPanel<JBPanel<*>>(GridBagLayout()).apply {
            border = JBUI.Borders.emptyTop(10)
        }

        val gbc = GridBagConstraints().apply {
            fill = GridBagConstraints.HORIZONTAL
            gridx = 0
            gridy = 0
            weightx = 1.0
            insets = JBUI.insets(5)
        }

        // 按钮区域
        val buttonPanel = JBPanel<JBPanel<*>>(java.awt.FlowLayout(java.awt.FlowLayout.LEFT))

        // 创建搜索框（初始隐藏）
        searchBox = createCriteriaBox(
            "Search 条件",
            WindowsConstants.STRING_TYPE_SEARCH,
            searchFields,
            { hideSearchBox() },
            { searchBoxHeight },
            { searchBoxHeight = it })
        filterBox = createCriteriaBox(
            "Filter 条件",
            WindowsConstants.STRING_TYPE_FILTER,
            filterFields,
            { hideFilterBox() },
            { filterBoxHeight },
            { filterBoxHeight = it })

        criteriaContainer = JBPanel<JBPanel<*>>(BorderLayout())

        // MODIFIED: 创建包装器，用于控制整体显示/隐藏
        criteriaWrapper = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            add(criteriaContainer, BorderLayout.CENTER)
            minimumSize = Dimension(0, 0)
        }

        val searchButton = JButton("Search").apply {
            addActionListener { toggleSearchBox() }
        }

        val filterButton = JButton("Filter").apply {
            addActionListener { toggleFilterBox() }
        }

        val clearButton = JButton("清除日志").apply {
            addActionListener {
                try {
                    logDoc.remove(0, logDoc.length)
                    linkPositions.clear()
                } catch (e: BadLocationException) {
                    e.printStackTrace()
                }
            }
        }

        buttonPanel.add(searchButton)
        buttonPanel.add(filterButton)
        buttonPanel.add(clearButton)

        contentPanel.add(buttonPanel, gbc.apply { gridy = 2 })

        // 日志显示区域
        val logPane = JTextPane().apply {
            isEditable = false
            font = java.awt.Font("Monospaced", java.awt.Font.PLAIN, 12)
            background = JBColor(Color(0x2B, 0x2B, 0x2B), Color(0x2B, 0x2B, 0x2B))
        }

        logDoc = logPane.styledDocument

        logPane.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                val pos = logPane.viewToModel2D(e.point)
                linkPositions.forEach { (start, end, filePath) ->
                    if (pos in start..end) {
                        openFileAtPath(filePath)
                    }
                }
            }

            override fun mouseMoved(e: MouseEvent) {
                val pos = logPane.viewToModel2D(e.point)
                val isOverLink = linkPositions.any { pos in it.first..it.second }
                logPane.cursor = if (isOverLink) {
                    java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR)
                } else {
                    java.awt.Cursor.getDefaultCursor()
                }
            }
        })

        val scrollPane = JBScrollPane(logPane)
        scrollPane.border = BorderFactory.createTitledBorder("Logcat 日志")

        // MODIFIED: 使用成员变量保存 splitPane 引用，优化配置
        mainSplitPane = JSplitPane(JSplitPane.VERTICAL_SPLIT, criteriaWrapper, scrollPane).apply {
            dividerSize = 4  // MODIFIED: 从 1 改为 4，便于拖动
            resizeWeight = 1.0  // MODIFIED: 底部（日志区域）优先获得额外空间
            isOneTouchExpandable = true  // MODIFIED: 启用一键展开/收起按钮（在分隔条上）
            border = JBUI.Borders.empty()
            // 确保日志区域有最小高度
            scrollPane.minimumSize = Dimension(0, 100)
        }

        contentPanel.add(mainSplitPane, gbc.apply {
            gridy = 4
            weighty = 1.0
            fill = GridBagConstraints.BOTH
        })

        // MODIFIED: 初始化时更新一次 splitPane 状态（默认隐藏）
        updateSplitPaneVisibility()

        startReadingLogcat(logPane)

        panel.add(contentPanel, BorderLayout.CENTER)

        val statusLabel = JBLabel("就绪 - 正在监听 Logcat").apply {
            border = JBUI.Borders.empty(5)
            foreground = java.awt.Color.GRAY
        }
        panel.add(statusLabel, BorderLayout.SOUTH)

        return panel
    }

    /**
     * MODIFIED: 新增方法 - 根据条件更新 SplitPane 的可见性和位置
     */
    private fun updateSplitPaneVisibility() {
        val hasCriteria = isSearchVisible || isFilterVisible

        if (hasCriteria) {
            criteriaWrapper.isVisible = true
            // 根据是一个还是两个条件，设置合适的高度
            val targetHeight = if (isSearchVisible && isFilterVisible) 200 else 120
            mainSplitPane.dividerLocation = targetHeight
        } else {
            // MODIFIED: 完全隐藏顶部区域，将分隔条移到最顶部
            criteriaWrapper.isVisible = false
            mainSplitPane.dividerLocation = 0
        }

        // 强制刷新布局
        SwingUtilities.invokeLater {
            mainSplitPane.revalidate()
            mainSplitPane.repaint()
        }
    }

    /**
     * 创建搜索条件框组件（支持高度自适应和拖拽调整）
     */
    private fun createCriteriaBox(
        title: String,
        type: String,
        fields: MutableList<JBTextField>,
        onHide: () -> Unit,
        getHeightRef: () -> Int,
        setHeightRef: (Int) -> Unit
    ): JPanel {
        val panel = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            border = BorderFactory.createTitledBorder(title)
        }

        // 行列表面板
        val rowsPanel = JBPanel<JBPanel<*>>().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
        }

        val scrollPane = JBScrollPane(rowsPanel).apply {
            border = JBUI.Borders.empty(2)
            verticalScrollBar.unitIncrement = 30
            // 关键：设置初始首选高度
            preferredSize = Dimension(200, getHeightRef())
        }
        panel.add(scrollPane, BorderLayout.CENTER)

        // 底部面板：包含拖拽条和按钮
        val bottomPanel = JBPanel<JBPanel<*>>(BorderLayout())

        // 拖拽条（分割线样式）
        val resizeHandle = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            preferredSize = Dimension(0, 8)
            cursor = java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.S_RESIZE_CURSOR)
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, JBColor.GRAY),
                JBUI.Borders.empty(2)
            )
            background = JBColor.PanelBackground

            // 中间的小横线表示可以拖拽
            add(JPanel().apply {
                preferredSize = Dimension(40, 2)
                background = JBColor.GRAY
                alignmentX = java.awt.Component.CENTER_ALIGNMENT
            }, BorderLayout.CENTER)
        }

        // 拖拽逻辑 - 修复：确保正确捕获引用
        var dragStartY = 0
        var dragStartHeight = 0

        resizeHandle.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                dragStartY = e.yOnScreen
                dragStartHeight = scrollPane.height
            }
        })

        resizeHandle.addMouseMotionListener(object : MouseMotionAdapter() {
            override fun mouseDragged(e: java.awt.event.MouseEvent) {
                val delta = e.yOnScreen - dragStartY
                val newHeight = (dragStartHeight + delta).coerceIn(minBoxHeight, maxBoxHeight)

                if (newHeight != getHeightRef()) {
                    setHeightRef(newHeight)

                    // 关键：在 EDT 中同步更新 UI
                    SwingUtilities.invokeLater {
                        // 设置 scrollPane 的首选大小
                        scrollPane.preferredSize = Dimension(scrollPane.width.coerceAtLeast(100), newHeight)
                        scrollPane.revalidate()

                        // 强制向上传播布局更新
                        panel.revalidate()
                        panel.repaint()

                        // 如果是分屏模式，需要更新父容器
                        criteriaContainer.revalidate()
                        criteriaContainer.repaint()
                    }
                }
            }
        })

        bottomPanel.add(resizeHandle, BorderLayout.NORTH)

        // 控制按钮 - 修复：添加行时正确计算高度
        val controlPanel = JBPanel<JBPanel<*>>(java.awt.FlowLayout(java.awt.FlowLayout.RIGHT))

        val addButton = JButton("+").apply {
            toolTipText = "添加条件"
            addActionListener {
                addCriteriaRow(rowsPanel, fields, type)

                // 关键：先强制布局，确保新行被正确计算
                rowsPanel.revalidate()
                rowsPanel.repaint()

                // 计算新高度：每行28px + 上下边距各10px + 额外空间
                val rowCount = fields.size
                val contentHeight = (rowCount * 28 + 40).coerceIn(minBoxHeight, maxBoxHeight)

                // 更新高度引用
                setHeightRef(contentHeight)

                // 关键：立即应用新高度
                scrollPane.preferredSize = Dimension(scrollPane.width.coerceAtLeast(100), contentHeight)
                scrollPane.revalidate()
                panel.revalidate()
                panel.repaint()

                // 向上传播到主容器
                criteriaContainer.revalidate()
                criteriaContainer.repaint()

                // 滚动到底部显示新添加的行
                SwingUtilities.invokeLater {
                    scrollPane.verticalScrollBar.value = scrollPane.verticalScrollBar.maximum
                }
            }
        }

        val clearAllButton = JButton("清除全部").apply {
            toolTipText = "清除所有条件"
            addActionListener {
                rowsPanel.removeAll()
                fields.clear()
                // 重置高度到默认
                setHeightRef(90)
                scrollPane.preferredSize = Dimension(scrollPane.width.coerceAtLeast(100), 90)
                scrollPane.revalidate()
                panel.revalidate()
                panel.repaint()
                onHide()
            }
        }

        controlPanel.add(clearAllButton)
        controlPanel.add(addButton)
        bottomPanel.add(controlPanel, BorderLayout.SOUTH)

        panel.add(bottomPanel, BorderLayout.SOUTH)

        // 初始添加一行
        if (fields.isEmpty()) {
            addCriteriaRow(rowsPanel, fields, type)
        }

        return panel
    }

    /**
     * 添加一行搜索条件（带减号删除按钮）
     */
    private fun addCriteriaRow(rowsPanel: JPanel, fields: MutableList<JBTextField>, type: String) {
        val rowPanel = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            border = JBUI.Borders.empty(2)
            maximumSize = Dimension(Int.MAX_VALUE, 28)
            preferredSize = Dimension(Int.MAX_VALUE, 28)
        }

        val textField = JBTextField().apply {
            emptyText.text = "输入条件..."
            var previousText = ""

            document.addDocumentListener(object : DocumentListener {
                override fun insertUpdate(e: DocumentEvent?) = onTextChanged()
                override fun removeUpdate(e: DocumentEvent?) = onTextChanged()
                override fun changedUpdate(e: DocumentEvent?) = onTextChanged()

                private fun onTextChanged() {
                    val currentText = text?.trim() ?: ""

                    if (currentText == previousText) return

                    if (previousText.isNotEmpty()) {
                        when (type) {
                            WindowsConstants.STRING_TYPE_SEARCH -> searchStringSet.remove(previousText)
                            WindowsConstants.STRING_TYPE_FILTER -> filterStringSet.remove(previousText)
                        }
                    }

                    if (currentText.isNotEmpty()) {
                        when (type) {
                            WindowsConstants.STRING_TYPE_SEARCH -> searchStringSet.add(currentText)
                            WindowsConstants.STRING_TYPE_FILTER -> filterStringSet.add(currentText)
                        }
                    }
                }
            })
        }

        // 减号按钮（删除当前行）
        val removeButton = JButton("−").apply {
            preferredSize = Dimension(28, 24)
            toolTipText = "删除此行"
            addActionListener {
                rowsPanel.remove(rowPanel)
                fields.remove(textField)
                rowsPanel.revalidate()
                rowsPanel.repaint()

                // 如果删除后没有行了，隐藏整个搜索框
                if (fields.isEmpty()) {
                    when {
                        rowsPanel.parent?.parent?.parent == searchBox -> hideSearchBox()
                        rowsPanel.parent?.parent?.parent == filterBox -> hideFilterBox()
                    }
                }
            }
        }

        rowPanel.add(textField, BorderLayout.CENTER)
        rowPanel.add(removeButton, BorderLayout.EAST)

        fields.add(textField)
        rowsPanel.add(rowPanel)
    }

    /**
     * MODIFIED: 简化切换逻辑 - 直接取反状态
     */
    private fun toggleSearchBox() {
        isSearchVisible = !isSearchVisible
        updateCriteriaLayout()
    }

    /**
     * MODIFIED: 简化切换逻辑 - 直接取反状态
     */
    private fun toggleFilterBox() {
        isFilterVisible = !isFilterVisible
        updateCriteriaLayout()
    }

    /**
     * 隐藏 Search 框
     */
    private fun hideSearchBox() {
        isSearchVisible = false
        updateCriteriaLayout()
    }

    /**
     * 隐藏 Filter 框
     */
    private fun hideFilterBox() {
        isFilterVisible = false
        updateCriteriaLayout()
    }

    /**
     * 更新搜索框布局（分屏/单屏切换）
     * MODIFIED: 添加对 mainSplitPane 的控制
     */
    private fun updateCriteriaLayout() {
        criteriaContainer.removeAll()

        when {
            isSearchVisible && isFilterVisible -> {
                val splitPane = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, searchBox, filterBox).apply {
                    setDividerLocation(0.5)
                    resizeWeight = 0.5
                    dividerSize = 4
                    border = JBUI.Borders.empty()
                }
                criteriaContainer.add(splitPane, BorderLayout.CENTER)
            }

            isSearchVisible -> {
                criteriaContainer.add(searchBox, BorderLayout.CENTER)
            }

            isFilterVisible -> {
                criteriaContainer.add(filterBox, BorderLayout.CENTER)
            }
        }

        updateSplitPaneVisibility()

        criteriaContainer.revalidate()
        criteriaContainer.repaint()
    }

    private fun startReadingLogcat(textPane: JTextPane) {
        Thread {
            try {
                val process = ProcessBuilder("adb", "logcat", "-v", "threadtime")
                    .redirectErrorStream(true)
                    .start()

                BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        val logLine = line ?: ""
                        SwingUtilities.invokeLater {
                            appendStyledLog(textPane, logLine)
                        }
                    }
                }
            } catch (ex: Exception) {
                ex.printStackTrace()
                SwingUtilities.invokeLater {
                    insertColoredText("错误: ${ex.message}\n请确保 adb 已添加到系统 PATH\n", Color.RED)
                }
            }
        }.start()
    }

    private fun appendStyledLog(textPane: JTextPane, line: String) {
        try {
            val logPattern =
                Pattern.compile("""^(\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d{3})\s+(\d+)\s+(\d+)\s+([VDIWEF])\s+([^:]+):\s*(.*)$""")
            val matcher = logPattern.matcher(line)

            if (matcher.find()) {
                val timestamp = matcher.group(1)
                val pid = matcher.group(2)
                val tid = matcher.group(3)
                val level = matcher.group(4)
                val tag = matcher.group(5)
                val message = matcher.group(6)

                searchStringSet.remove("")
                if (searchStringSet.isNotEmpty() && !searchStringSet.parallelStream().anyMatch { message.contains(it) }) {
                    return
                }
                filterStringSet.remove("")
                if (filterStringSet.isNotEmpty() && !filterStringSet.parallelStream().anyMatch { tag.contains(it) }) {
                    return
                }

                insertColoredText("$timestamp ", JBColor.GRAY)
                insertColoredText("$pid $tid ", JBColor.GRAY)
                insertLevelText(level)
                insertColoredText("$tag: ", JBColor(Color(0xFF, 0xA5, 0x00), Color(0xFF, 0xA5, 0x00)))
                insertColoredText(message, getLevelColor(level))
                insertColoredText("\n", JBColor.GRAY)
            } else {
                handleStackTraceOrRawLine(line)
            }

            trimLogToSize(1000)
            textPane.caretPosition = logDoc.length

        } catch (e: BadLocationException) {
            e.printStackTrace()
        }
    }

    private fun insertLevelText(level: String) {
        val attrs = SimpleAttributeSet().apply {
            StyleConstants.setBold(this, true)
            StyleConstants.setForeground(this, getLevelColor(level))
        }
        logDoc.insertString(logDoc.length, "$level ", attrs)
    }

    private fun insertColoredText(text: String, color: Color) {
        val attrs = SimpleAttributeSet().apply {
            StyleConstants.setForeground(this, color)
        }
        logDoc.insertString(logDoc.length, text, attrs)
    }

    private fun getLevelColor(level: String): Color {
        return when (level) {
            "V" -> JBColor.GRAY
            "D" -> JBColor(Color(0x00, 0x7A, 0xCC), Color(0x00, 0x7A, 0xCC))
            "I" -> JBColor(Color(0x3C, 0xD1, 0x41), Color(0x3C, 0xD1, 0x41))
            "W" -> JBColor(Color(0xFF, 0x99, 0x00), Color(0xFF, 0x99, 0x00))
            "E" -> JBColor(Color(0xFF, 0x44, 0x44), Color(0xFF, 0x44, 0x44))
            "F" -> JBColor(Color(0xFF, 0x00, 0x00), Color(0xFF, 0x00, 0x00))
            else -> JBColor.GRAY
        }
    }

    private fun handleStackTraceOrRawLine(line: String) {
        val stackPattern = Pattern.compile("""^\s*at\s+[\w.$]+\(([^:]+):(\d+)\)\s*$""")
        val matcher = stackPattern.matcher(line)

        if (matcher.find()) {
            val fileName = matcher.group(1)
            val lineNum = matcher.group(2)

            val atIndex = line.indexOf("(")
            if (atIndex > 0) {
                insertColoredText(line.substring(0, atIndex + 1), JBColor.GRAY)

                val linkText = "$fileName:$lineNum"
                val startPos = logDoc.length
                val linkAttrs = SimpleAttributeSet().apply {
                    StyleConstants.setForeground(this, JBColor.BLUE)
                    StyleConstants.setUnderline(this, true)
                }
                logDoc.insertString(startPos, linkText, linkAttrs)

                val endPos = logDoc.length
                linkPositions.add(Triple(startPos, endPos, "$fileName:$lineNum"))

                insertColoredText(")\n", JBColor.GRAY)
            } else {
                insertColoredText("$line\n", JBColor.GRAY)
            }
        } else {
            val isException = line.contains("Exception") || line.contains("Error") ||
                    line.contains("Caused by:") || line.startsWith("\tat")
            val color = if (isException) JBColor.RED else JBColor.GRAY
            insertColoredText("$line\n", color)
        }
    }

    private fun trimLogToSize(maxLines: Int) {
        try {
            val text = logDoc.getText(0, logDoc.length)
            val lines = text.split("\n")
            if (lines.size > maxLines) {
                val removeCount = lines.size - maxLines
                var removeLength = 0
                for (i in 0 until removeCount) {
                    removeLength += lines[i].length + 1
                }
                logDoc.remove(0, removeLength)

                linkPositions.removeAll { it.first < removeLength }
                val updatedLinks = linkPositions.map {
                    Triple(it.first - removeLength, it.second - removeLength, it.third)
                }
                linkPositions.clear()
                linkPositions.addAll(updatedLinks)
            }
        } catch (e: BadLocationException) {
            e.printStackTrace()
        }
    }

    private fun openFileAtPath(filePathWithLine: String) {
        val parts = filePathWithLine.split(":")
        if (parts.size < 2) return

        val fileName = parts[0]
        val lineNum = parts[1].toIntOrNull() ?: 1

        val basePath = project.basePath ?: return
        val possiblePaths = listOf(
            "$basePath/app/src/main/java/$fileName",
            "$basePath/app/src/main/kotlin/$fileName",
            "$basePath/$fileName",
            "$basePath/src/main/java/$fileName",
            "$basePath/src/main/kotlin/$fileName"
        )

        possiblePaths.forEach { path ->
            val virtualFile = LocalFileSystem.getInstance().findFileByPath(path)
            if (virtualFile != null && virtualFile.exists()) {
                val descriptor = OpenFileDescriptor(project, virtualFile, lineNum - 1, 0)
                FileEditorManager.getInstance(project).openTextEditor(descriptor, true)
                return
            }
        }

        com.intellij.openapi.ui.Messages.showWarningDialog(
            project,
            "找不到文件: $fileName\n请确保项目在标准 Android 项目结构中",
            "文件未找到"
        )
    }
}