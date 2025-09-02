package com.wepie.coder.wpcoder.service

import com.wepie.coder.wpcoder.model.Template

interface TemplateStorage {
    fun getAllTemplates(): List<Template>
    fun saveTemplates(templates: List<Template>)
    fun addTemplate(template: Template)
    fun updateTemplate(template: Template)
    fun deleteTemplate(templateName: String)
}