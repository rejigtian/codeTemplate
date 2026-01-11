package com.wepie.coder.wpcoder.window

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class ServerConfigDialog(
    project: Project,
    private val currentUrl: String,
    private val currentKey: String,
    private val currentMcpEnabled: Boolean = false,
    private val currentMcpPort: Int = 12345
) : DialogWrapper(project) {
    private val serverUrlField = JBTextField(currentUrl, 30)
    private val apiKeyField = JBTextField(currentKey, 30)
    private val mcpEnabledCheckBox = com.intellij.ui.components.JBCheckBox("Enable Local MCP Server", currentMcpEnabled)
    private val mcpPortField = JBTextField(currentMcpPort.toString(), 10)
    private val configTextArea = JBTextArea().apply {
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
        background = JBUI.CurrentTheme.EditorTabs.background()
        border = JBUI.Borders.empty(5)
    }
    private val copyButton = JButton("Copy Config")
    private val tipLabel = JLabel("Tip: Use 'Search project templates' in AI chat to trigger tools.").apply {
        font = JBUI.Fonts.smallFont()
        foreground = JBUI.CurrentTheme.Label.disabledForeground()
    }

    init {
        title = "WP Coder Configuration"
        init()
        updateConfigText()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(GridBagLayout())
        val c = GridBagConstraints()

        // --- Server Settings Group ---
        c.gridx = 0
        c.gridy = 0
        c.gridwidth = 2
        c.anchor = GridBagConstraints.LINE_START
        c.fill = GridBagConstraints.HORIZONTAL
        c.insets = JBUI.insets(10, 5, 5, 5)
        panel.add(com.intellij.ui.TitledSeparator("Server Settings"), c)

        // 服务器地址
        c.gridy++
        c.gridwidth = 1
        c.weightx = 0.0
        c.fill = GridBagConstraints.NONE
        panel.add(JLabel("Server URL:"), c)

        c.gridx = 1
        c.weightx = 1.0
        c.fill = GridBagConstraints.HORIZONTAL
        panel.add(serverUrlField, c)

        // API Key
        c.gridx = 0
        c.gridy++
        c.weightx = 0.0
        c.fill = GridBagConstraints.NONE
        panel.add(JLabel("API Key:"), c)

        c.gridx = 1
        c.weightx = 1.0
        c.fill = GridBagConstraints.HORIZONTAL
        panel.add(apiKeyField, c)

        // --- MCP Settings Group ---
        c.gridx = 0
        c.gridy++
        c.gridwidth = 2
        c.insets = JBUI.insets(20, 5, 5, 5)
        panel.add(com.intellij.ui.TitledSeparator("Local MCP Settings"), c)

        c.gridy++
        c.insets = JBUI.insets(5, 5, 5, 5)
        panel.add(mcpEnabledCheckBox, c)

        c.gridy++
        c.gridwidth = 1
        c.weightx = 0.0
        c.fill = GridBagConstraints.NONE
        panel.add(JLabel("MCP Port:"), c)

        c.gridx = 1
        c.weightx = 1.0
        c.fill = GridBagConstraints.HORIZONTAL
        panel.add(mcpPortField, c)

        // --- Config Display ---
        c.gridx = 0
        c.gridy++
        c.gridwidth = 2
        c.insets = JBUI.insets(10, 5, 5, 5)
        panel.add(JLabel("MCP Configuration (for Cursor/Claude):"), c)

        c.gridy++
        c.fill = GridBagConstraints.BOTH
        c.weighty = 1.0
        val scrollPane = com.intellij.ui.components.JBScrollPane(configTextArea).apply {
            preferredSize = Dimension(450, 120)
        }
        panel.add(scrollPane, c)

        c.gridy++
        c.fill = GridBagConstraints.NONE
        c.anchor = GridBagConstraints.LINE_START
        panel.add(tipLabel, c)
        
        c.anchor = GridBagConstraints.LINE_END
        panel.add(copyButton, c)

        // 根据开关状态禁用/启用组件
        mcpEnabledCheckBox.addActionListener {
            updateMcpComponents()
        }
        
        mcpPortField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) = updateConfigText()
            override fun removeUpdate(e: DocumentEvent?) = updateConfigText()
            override fun changedUpdate(e: DocumentEvent?) = updateConfigText()
        })

        copyButton.addActionListener {
            val selection = StringSelection(configTextArea.text)
            Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)
            JOptionPane.showMessageDialog(panel, "Config copied to clipboard")
        }

        updateMcpComponents()
        return panel
    }

    private fun updateMcpComponents() {
        val enabled = mcpEnabledCheckBox.isSelected
        mcpPortField.isEnabled = enabled
        configTextArea.isEnabled = enabled
        copyButton.isEnabled = enabled
        tipLabel.isEnabled = enabled
        updateConfigText()
    }

    private fun updateConfigText() {
        val port = mcpPortField.text.toIntOrNull() ?: currentMcpPort
        val config = """
            {
              "mcpServers": {
                "WP-Coder": {
                  "command": "nc",
                  "args": ["127.0.0.1", "$port"]
                }
              }
            }
        """.trimIndent()
        configTextArea.text = config
    }

    override fun doValidate(): ValidationInfo? {
        val url = serverUrlField.text.trim()
        val key = apiKeyField.text.trim()

        if (url.isBlank()) {
            return ValidationInfo("Please enter server URL", serverUrlField)
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return ValidationInfo("Server URL must start with http:// or https://", serverUrlField)
        }
        if (key.isBlank()) {
            return ValidationInfo("Please enter API key", apiKeyField)
        }

        if (mcpEnabledCheckBox.isSelected) {
            val portStr = mcpPortField.text.trim()
            val port = portStr.toIntOrNull()
            if (port == null || port !in 1024..65535) {
                return ValidationInfo("Please enter a valid port number (1024-65535)", mcpPortField)
            }
        }

        return null
    }

    fun getServerUrl(): String = serverUrlField.text.trim()
    fun getApiKey(): String = apiKeyField.text.trim()
    fun isMcpEnabled(): Boolean = mcpEnabledCheckBox.isSelected
    fun getMcpPort(): Int = mcpPortField.text.trim().toIntOrNull() ?: 12345
}