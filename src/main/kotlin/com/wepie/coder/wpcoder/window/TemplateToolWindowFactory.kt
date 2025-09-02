package com.wepie.coder.wpcoder.window

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import javax.swing.JPanel
import java.awt.BorderLayout

class TemplateToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val toolWindowContent = TemplateToolWindowContent(project)
        val content = ContentFactory.getInstance().createContent(
            toolWindowContent.getContent(),
            "",  // 不显示标签页标题
            false
        )
        toolWindow.contentManager.addContent(content)
    }
}
class TemplateToolWindowContent(private val project: Project) {
    private val panel = TemplateToolWindowPanel(project)

    fun getContent() = panel
}

