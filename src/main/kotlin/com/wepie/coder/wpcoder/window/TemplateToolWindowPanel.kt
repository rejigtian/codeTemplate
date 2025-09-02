package com.wepie.coder.wpcoder.window

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import javax.swing.JComponent

class TemplateToolWindowPanel(project: Project) : SimpleToolWindowPanel(true, true) {
    init {
        // 设置工具栏
        val actionManager = ActionManager.getInstance()
        val actionGroup = DefaultActionGroup().apply {
            add(actionManager.getAction("WPCoder.FileTemplateSettings"))
            add(actionManager.getAction("WPCoder.LiveTemplateSettings"))
            addSeparator()
            add(actionManager.getAction("WPCoder.ImportTemplate"))
            add(actionManager.getAction("WPCoder.ExportTemplate"))
        }
        val toolbar = actionManager.createActionToolbar(
            ActionPlaces.TOOLWINDOW_CONTENT,
            actionGroup,
            true
        )
        toolbar.targetComponent = this
        setToolbar(toolbar.component)
    }

    override fun getComponent(): JComponent = super.getComponent()!!
}