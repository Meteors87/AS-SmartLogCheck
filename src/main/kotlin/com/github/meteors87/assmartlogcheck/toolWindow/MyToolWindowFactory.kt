package com.github.meteors87.assmartlogcheck.toolWindow

import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.content.ContentFactory
import com.github.meteors87.assmartlogcheck.MyBundle
import com.github.meteors87.assmartlogcheck.services.MyProjectService
import javax.swing.JButton


class MyToolWindowFactory : ToolWindowFactory {

    init {
        thisLogger().warn("Don't forget to remove all non-needed sample code files with their corresponding registration entries in `plugin.xml`.")
    }

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        // 创建我们的自定义面板
        val myToolWindow = MyToolWindowPanel(project)
        val content = ContentFactory.getInstance().createContent(myToolWindow.getContent(), null, false)
        toolWindow.contentManager.addContent(content)
        // 可选：设置 Tool Window 的标题按钮
        setupToolWindowActions(toolWindow)
    }
    private fun setupToolWindowActions(toolWindow: ToolWindow) {
        // 可以在这里添加刷新按钮等
        val refreshAction = object : com.intellij.openapi.actionSystem.AnAction(
            "Refresh",
            "Refresh panel content",
            com.intellij.icons.AllIcons.Actions.Refresh
        ) {
            override fun actionPerformed(e: com.intellij.openapi.actionSystem.AnActionEvent) {
            }
        }

        toolWindow.setTitleActions(listOf(refreshAction))
    }

    override fun shouldBeAvailable(project: Project) = true
}
