package com.wepie.coder.wpcoder.service

import com.wepie.coder.wpcoder.model.Template

interface TemplateManager {
    fun getAllTemplates(): List<Template>
    fun addTemplate(template: Template)
    fun updateTemplate(template: Template)
    fun deleteTemplate(templateName: String)
    fun importTemplates(templates: List<Template>)
    fun exportTemplates(): List<Template>
    fun syncToIDE(template: Template)
}