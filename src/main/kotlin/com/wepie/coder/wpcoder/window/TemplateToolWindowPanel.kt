package com.wepie.coder.wpcoder.window

import com.intellij.icons.AllIcons
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
        
        // 创建文件模板组
        val fileTemplateGroup = DefaultActionGroup("文件模板", true).apply {
            templatePresentation.icon = AllIcons.Actions.MenuOpen
            add(actionManager.getAction("WPCoder.FileTemplateSettings"))
            addSeparator()
            add(actionManager.getAction("WPCoder.ImportTemplate"))
            add(actionManager.getAction("WPCoder.ExportTemplate"))
        }
        
        // 创建实时模板组
        val liveTemplateGroup = DefaultActionGroup("实时模板", true).apply {
            templatePresentation.icon = AllIcons.FileTypes.Text
            add(actionManager.getAction("WPCoder.LiveTemplateSettings"))
            addSeparator()
            add(actionManager.getAction("WPCoder.ImportLiveTemplate"))
            add(actionManager.getAction("WPCoder.ExportLiveTemplate"))
        }
        
        // 创建主工具栏
        val mainGroup = DefaultActionGroup().apply {
            add(fileTemplateGroup)
            addSeparator()
            add(liveTemplateGroup)
        }
        
        val toolbar = actionManager.createActionToolbar(
            ActionPlaces.TOOLWINDOW_CONTENT,
            mainGroup,
            true
        )
        toolbar.targetComponent = this
        setToolbar(toolbar.component)
    }

    override fun getComponent(): JComponent = super.getComponent()!!
}