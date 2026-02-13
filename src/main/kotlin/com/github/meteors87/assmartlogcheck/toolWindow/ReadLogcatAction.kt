package com.github.meteors87.assmartlogcheck.toolWindow

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import java.io.BufferedReader
import java.io.InputStreamReader

class ReadLogcatAction : AnAction("读取 Logcat") {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        Thread {
            try {
                val process = ProcessBuilder("adb", "logcat", "-v", "threadtime")
                    .redirectErrorStream(true)
                    .start()

                BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        println(line)
                    }
                }
            } catch (ex: Exception) {
                ex.printStackTrace()
                // 在 EDT 上显示错误对话框
                com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                    com.intellij.openapi.ui.Messages.showErrorDialog(
                        project,
                        "执行 adb 失败: ${ex.message}\n请确保 adb 已添加到系统 PATH",
                        "错误"
                    )
                }
            }
        }.start()
    }
}