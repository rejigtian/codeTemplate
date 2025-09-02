package com.wepie.coder.wpcoder.service.impl

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.wepie.coder.wpcoder.model.Template
import com.wepie.coder.wpcoder.service.TemplateStorage

@State(
    name = "WPCoderTemplates",
    storages = [Storage("wp-coder-templates.xml")]
)
class TemplateStorageImpl : TemplateStorage, PersistentStateComponent<TemplateStorageImpl.TemplateState> {
    private var myState = TemplateState()

    override fun getState(): TemplateState = myState

    override fun loadState(state: TemplateState) {
        myState = state
    }

    override fun getAllTemplates(): List<Template> = myState.templates

    override fun saveTemplates(templates: List<Template>) {
        myState.templates = templates.toMutableList()
    }

    override fun addTemplate(template: Template) {
        myState.templates.add(template)
    }

    override fun updateTemplate(template: Template) {
        val index = myState.templates.indexOfFirst { it.name == template.name }
        if (index != -1) {
            myState.templates[index] = template
        }
    }

    override fun deleteTemplate(templateName: String) {
        myState.templates.removeIf { it.name == templateName }
    }

    data class TemplateState(
        var templates: MutableList<Template> = mutableListOf()
    )
}