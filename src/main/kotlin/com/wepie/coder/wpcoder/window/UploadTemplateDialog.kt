package com.wepie.coder.wpcoder.window

import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.io.File
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel

class UploadTemplateDialog(
    private val project: Project
) : DialogWrapper(project) {
    private val displayNameField = JBTextField().apply {
        preferredSize = Dimension(400, 30)
        minimumSize = Dimension(300, 30)
    }
    private var selectedFile: File? = null
    private val filePathLabel = JBLabel("No file selected").apply {
        preferredSize = Dimension(300, 30)
    }

    init {
        title = "Upload Template"
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(GridBagLayout())
        panel.border = JBUI.Borders.empty(10)
        val c = GridBagConstraints()

        // 显示名称标签
        c.apply {
            gridx = 0
            gridy = 0
            anchor = GridBagConstraints.WEST
            insets = Insets(5, 5, 5, 5)
        }
        panel.add(JBLabel("Display Name:"), c)

        // 显示名称输入框
        c.apply {
            gridx = 1
            gridy = 0
            gridwidth = 2
            fill = GridBagConstraints.HORIZONTAL
            weightx = 1.0
            insets = Insets(5, 5, 0, 5)
        }
        panel.add(displayNameField, c)

        // 显示名称说明
        c.apply {
            gridx = 1
            gridy = 1
            gridwidth = 2
            fill = GridBagConstraints.NONE
            weightx = 0.0
            insets = Insets(0, 5, 10, 5)
        }
        panel.add(
            JBLabel("显示在模板列表中的名称").apply {
                foreground = JBUI.CurrentTheme.ContextHelp.FOREGROUND
                font = JBUI.Fonts.smallFont()
            },
            c
        )

        // 文件标签
        c.apply {
            gridx = 0
            gridy = 2
            gridwidth = 1
            anchor = GridBagConstraints.WEST
            insets = Insets(5, 5, 5, 5)
        }
        panel.add(JBLabel("File:"), c)

        // 文件路径
        c.apply {
            gridx = 1
            gridy = 2
            fill = GridBagConstraints.HORIZONTAL
            weightx = 1.0
            insets = Insets(5, 5, 5, 5)
        }
        panel.add(filePathLabel, c)

        // 浏览按钮
        c.apply {
            gridx = 2
            gridy = 2
            fill = GridBagConstraints.NONE
            weightx = 0.0
            insets = Insets(5, 5, 5, 5)
        }
        panel.add(JButton("Browse...").apply {
            preferredSize = Dimension(100, 30)
            addActionListener {
                val descriptor = FileChooserDescriptor(
                    true,
                    false,
                    false,
                    false,
                    false,
                    false
                ).apply {
                    title = "Select Template File"
                    description = "Choose a ZIP file containing templates"
                    withFileFilter { it.extension?.lowercase() == "zip" }
                }

                FileChooser.chooseFile(descriptor, project, null)?.let {
                    selectedFile = File(it.path)
                    filePathLabel.text = selectedFile?.name ?: "No file selected"
                }
            }
        }, c)

        panel.preferredSize = Dimension(500, 150)
        return panel
    }

    override fun doValidate(): ValidationInfo? {
        if (displayNameField.text.isBlank()) {
            return ValidationInfo("Please enter a display name", displayNameField)
        }
        if (selectedFile == null) {
            return ValidationInfo("Please select a file", filePathLabel)
        }
        return null
    }

    fun getDisplayName(): String = displayNameField.text.trim()
    fun getSelectedFile(): File = selectedFile!!
}
