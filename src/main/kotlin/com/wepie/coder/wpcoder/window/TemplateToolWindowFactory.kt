package com.wepie.coder.wpcoder.window

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import javax.swing.JTabbedPane

class TemplateToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val contentFactory = ContentFactory.getInstance()
        val tabbedPane = JTabbedPane()

        // 添加代码模板面板
        val liveTemplatePanel = TemplatePanel(project, "live")
        tabbedPane.addTab("代码模板", liveTemplatePanel)

        // 添加文件模板面板
        val fileTemplatePanel = TemplatePanel(project, "file")
        tabbedPane.addTab("文件模板", fileTemplatePanel)

        // 添加 tab 切换监听器
        tabbedPane.addChangeListener { e ->
            val selectedPanel = (e.source as JTabbedPane).selectedComponent as? TemplatePanel
            selectedPanel?.onTabSelected()
        }

        val content = contentFactory.createContent(tabbedPane, "", false)
        toolWindow.contentManager.addContent(content)
    }
}