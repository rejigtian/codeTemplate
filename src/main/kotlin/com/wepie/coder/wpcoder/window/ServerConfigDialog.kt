package com.wepie.coder.wpcoder.window

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBTextField
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

class ServerConfigDialog(
    project: Project,
    private val currentUrl: String,
    private val currentKey: String
) : DialogWrapper(project) {
    private val serverUrlField = JBTextField(currentUrl)
    private val apiKeyField = JBTextField(currentKey)

    init {
        title = "Server Configuration"
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(GridBagLayout())
        val c = GridBagConstraints()

        // 服务器地址
        c.gridx = 0
        c.gridy = 0
        c.anchor = GridBagConstraints.LINE_START
        panel.add(JLabel("Server URL:"), c)

        c.gridx = 1
        c.gridy = 0
        c.fill = GridBagConstraints.HORIZONTAL
        c.weightx = 1.0
        panel.add(serverUrlField, c)

        // API Key
        c.gridx = 0
        c.gridy = 1
        c.weightx = 0.0
        panel.add(JLabel("API Key:"), c)

        c.gridx = 1
        c.gridy = 1
        c.fill = GridBagConstraints.HORIZONTAL
        c.weightx = 1.0
        panel.add(apiKeyField, c)

        return panel
    }

    override fun doValidate(): ValidationInfo? {
        if (serverUrlField.text.isBlank()) {
            return ValidationInfo("Please enter server URL", serverUrlField)
        }
        if (!serverUrlField.text.startsWith("http://") && !serverUrlField.text.startsWith("https://")) {
            return ValidationInfo("Server URL must start with http:// or https://", serverUrlField)
        }
        if (apiKeyField.text.isBlank()) {
            return ValidationInfo("Please enter API key", apiKeyField)
        }
        return null
    }

    fun getServerUrl(): String = serverUrlField.text.trim()
    fun getApiKey(): String = apiKeyField.text.trim()
}
