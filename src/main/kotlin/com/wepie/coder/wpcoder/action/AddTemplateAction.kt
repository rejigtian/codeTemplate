package com.wepie.coder.wpcoder.action

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.ui.Messages
import com.wepie.coder.wpcoder.model.Template
import com.wepie.coder.wpcoder.model.TemplateType
import com.wepie.coder.wpcoder.service.TemplateManager

class AddTemplateAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val templateManager = project.service<TemplateManager>()

        val templateName = Messages.showInputDialog(
            project,
            "Enter template name:",
            "Add Template",
            Messages.getQuestionIcon()
        ) ?: return

        val templateTypes = TemplateType.values().map { it.name }
        val typeIndex = Messages.showChooseDialog(
            project,
            "Choose template type:",
            "Add Template",
            Messages.getQuestionIcon(),
            templateTypes.toTypedArray(),
            templateTypes[0]
        )
        if (typeIndex == -1) return

        val templateContent = Messages.showInputDialog(
            project,
            "Enter template content:",
            "Add Template",
            Messages.getQuestionIcon()
        ) ?: return

        val template = Template(
            name = templateName,
            type = TemplateType.valueOf(templateTypes[typeIndex]),
            content = templateContent,
            extension = "kt"
        )
        templateManager.addTemplate(template)
    }
}