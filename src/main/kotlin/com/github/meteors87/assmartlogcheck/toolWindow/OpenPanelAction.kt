package com.github.meteors87.assmartlogcheck.toolWindow

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.wm.ToolWindowManager

class OpenPanelAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        
        // 获取 Tool Window 并激活
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("MySidePanel")
        toolWindow?.activate(null, true, true)
    }
    
    override fun update(e: AnActionEvent) {
        // 只有在有项目打开时才启用
        e.presentation.isEnabled = e.project != null
    }
}