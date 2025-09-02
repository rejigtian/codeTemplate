package com.wepie.coder.wpcoder.action

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.ui.Messages
import com.wepie.coder.wpcoder.service.TemplateManager

class DeleteTemplateAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val templateManager = project.service<TemplateManager>()

        val templates = templateManager.getAllTemplates()
        if (templates.isEmpty()) {
            Messages.showInfoMessage(
                project,
                "No templates available to delete.",
                "Delete Template"
            )
            return
        }

        val templateNames = templates.map { it.name }.toTypedArray()
        val selectedIndex = Messages.showChooseDialog(
            project,
            "Choose template to delete:",
            "Delete Template",
            Messages.getQuestionIcon(),
            templateNames,
            templateNames[0]
        )
        if (selectedIndex == -1) return

        val result = Messages.showYesNoDialog(
            project,
            "Are you sure you want to delete template '${templateNames[selectedIndex]}'?",
            "Delete Template",
            Messages.getQuestionIcon()
        )
        if (result == 0) {
            templateManager.deleteTemplate(templateNames[selectedIndex])
        }
    }
}