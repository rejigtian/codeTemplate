package com.wepie.coder.wpcoder.action

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.ui.Messages
import com.wepie.coder.wpcoder.service.TemplateManager

class EditTemplateAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val templateManager = project.service<TemplateManager>()

        val templates = templateManager.getAllTemplates()
        if (templates.isEmpty()) {
            Messages.showInfoMessage(
                project,
                "No templates available to edit.",
                "Edit Template"
            )
            return
        }

        val templateNames = templates.map { it.name }.toTypedArray()
        val selectedIndex = Messages.showChooseDialog(
            project,
            "Choose template to edit:",
            "Edit Template",
            Messages.getQuestionIcon(),
            templateNames,
            templateNames[0]
        )
        if (selectedIndex == -1) return

        val selectedTemplate = templates[selectedIndex]
        val newContent = Messages.showInputDialog(
            project,
            "Edit template content:",
            "Edit Template",
            Messages.getQuestionIcon(),
            selectedTemplate.content,
            null
        ) ?: return

        val updatedTemplate = selectedTemplate.copy(content = newContent)
        templateManager.updateTemplate(updatedTemplate)
    }
}