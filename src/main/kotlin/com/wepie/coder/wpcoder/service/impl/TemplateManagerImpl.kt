package com.wepie.coder.wpcoder.service.impl

import com.intellij.ide.fileTemplates.FileTemplate
import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.wepie.coder.wpcoder.model.Template
import com.wepie.coder.wpcoder.model.TemplateType
import com.wepie.coder.wpcoder.service.TemplateManager
import com.wepie.coder.wpcoder.service.TemplateStorage

class TemplateManagerImpl(private val project: Project) : TemplateManager {
    private val templateStorage = ApplicationManager.getApplication().getService(TemplateStorage::class.java)
    private val fileTemplateManager = FileTemplateManager.getInstance(project)

    override fun getAllTemplates(): List<Template> {
        return templateStorage.getAllTemplates()
    }

    override fun addTemplate(template: Template) {
        templateStorage.addTemplate(template)
        syncToIDE(template)
    }

    override fun updateTemplate(template: Template) {
        templateStorage.updateTemplate(template)
        syncToIDE(template)
    }

    override fun deleteTemplate(templateName: String) {
        templateStorage.deleteTemplate(templateName)
        val template = fileTemplateManager.getAllTemplates().find { it.name == templateName }
        if (template != null) {
            fileTemplateManager.removeTemplate(template)
        }
    }

    override fun importTemplates(templates: List<Template>) {
        templates.forEach { template ->
            templateStorage.addTemplate(template)
            syncToIDE(template)
        }
    }

    override fun exportTemplates(): List<Template> {
        return templateStorage.getAllTemplates()
    }

    override fun syncToIDE(template: Template) {
        when (template.type) {
            TemplateType.FILE -> {
                val fileTemplate = fileTemplateManager.getTemplate(template.name)
                    ?: fileTemplateManager.addTemplate(template.name, "")
                fileTemplate.text = template.content
                fileTemplate.extension = template.extension
                fileTemplate.isReformatCode = true
            }
            TemplateType.CODE_SNIPPET,
            TemplateType.LIVE_TEMPLATE -> {
                // TODO: 同步到 Live Templates
            }
        }
    }
}