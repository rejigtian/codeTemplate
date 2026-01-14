@file:Suppress("DialogTitleCapitalization")

package com.wepie.coder.wpcoder.action

import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import com.wepie.coder.wpcoder.service.TemplateServerService
import com.wepie.coder.wpcoder.window.ExportTemplateDialog
import com.wepie.coder.wpcoder.window.TemplatePanel
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.io.File
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

class ExportTemplateAction : DumbAwareAction() {
    private class ShareDialog(
        project: Project
    ) : DialogWrapper(project) {
        private val displayNameField = JBTextField().apply {
            preferredSize = Dimension(300, 30)
            minimumSize = Dimension(300, 30)
        }

        init {
            title = "分享到云端"
            init()
        }

        override fun createCenterPanel(): JComponent {
            val panel = JPanel(GridBagLayout())
            val c = GridBagConstraints()
            c.insets = JBUI.insets(5)

            // Label for display name
            c.gridx = 0
            c.gridy = 0
            c.anchor = GridBagConstraints.WEST
            panel.add(JLabel("模板名称:"), c)

            // Text field for display name
            c.gridx = 1
            c.gridy = 0
            c.fill = GridBagConstraints.HORIZONTAL
            c.weightx = 1.0
            panel.add(displayNameField, c)

            // Comment for display name
            c.gridx = 0
            c.gridy = 1
            c.gridwidth = 2
            c.anchor = GridBagConstraints.WEST
            panel.add(JBLabel("显示在模板列表中的名称").apply {
                foreground = JBUI.CurrentTheme.ContextHelp.FOREGROUND
            }, c)

            panel.preferredSize = Dimension(400, 100)
            return panel
        }

        override fun doValidate(): ValidationInfo? {
            if (displayNameField.text.isBlank()) {
                return ValidationInfo("请输入模板名称", displayNameField)
            }
            return null
        }

        fun getDisplayName(): String = displayNameField.text
    }

    private class SaveFileDialog(
        project: Project,
        defaultName: String
    ) : DialogWrapper(project) {
        private val fileNameField = JBTextField(defaultName).apply {
            preferredSize = Dimension(300, 30)
        }

        init {
            title = "保存导出文件"
            init()
        }

        override fun createCenterPanel(): JComponent {
            val panel = JPanel(GridBagLayout())
            panel.border = JBUI.Borders.empty(10)
            val gbc = GridBagConstraints().apply {
                gridx = 0; gridy = 0; anchor = GridBagConstraints.WEST; insets = JBUI.insets(5)
            }
            panel.add(JLabel("文件名:"), gbc)
            gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0
            panel.add(fileNameField, gbc)
            
            gbc.gridx = 1; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0.0
            panel.add(JBLabel(".zip").apply {
                foreground = JBUI.CurrentTheme.ContextHelp.FOREGROUND
            }, gbc)
            
            return panel
        }

        override fun doValidate(): ValidationInfo? {
            val text = fileNameField.text
            if (text.isBlank()) return ValidationInfo("请输入文件名", fileNameField)
            if (text.contains("/") || text.contains("\\")) return ValidationInfo("文件名包含非法字符", fileNameField)
            return null
        }

        fun getFullFileName(): String {
            val name = fileNameField.text.trim()
            return if (name.lowercase().endsWith(".zip")) name else "$name.zip"
        }
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        // 获取所有用户自定义模板
        val fileTemplateManager = FileTemplateManager.getInstance(project)
        val templates = fileTemplateManager.allTemplates.filter { 
            !it.isDefault 
        }

        if (templates.isEmpty()) {
            Messages.showInfoMessage(
                project,
                "没有可导出的模板",
                "导出模板"
            )
            return
        }

        // 显示选择对话框
        val dialog = ExportTemplateDialog(project, templates)
        if (!dialog.showAndGet()) {
            return
        }

        // 获取选中的模板
        val selectedTemplates = dialog.getSelectedTemplates()
        if (selectedTemplates.isEmpty()) {
            Messages.showWarningDialog(
                project,
                "请至少选择一个模板",
                "导出模板"
            )
            return
        }

        // 选择保存位置
        val descriptor = FileChooserDescriptor(false, true, false, false, false, false)
            .withTitle("选择保存目录")
            .withDescription("请选择一个目录来保存导出的模板压缩包 (templates.zip)")
        
        val baseDir = project.basePath?.let { path ->
            com.intellij.openapi.vfs.LocalFileSystem.getInstance().findFileByPath(path)
        }
        
        val selectedFolder = FileChooser.chooseFile(descriptor, project, baseDir)
            ?: return

        // 弹出文件名输入对话框
        val saveDialog = SaveFileDialog(project, "templates")
        if (!saveDialog.showAndGet()) return

        val fullFileName = saveDialog.getFullFileName()
        val targetFile = File(selectedFolder.path, fullFileName)
        
        try {
            if (targetFile.exists()) {
                val overwrite = Messages.showYesNoDialog(
                    project,
                    "文件 $fullFileName 已存在，是否覆盖？",
                    "文件已存在",
                    Messages.getQuestionIcon()
                )
                if (overwrite != Messages.YES) return
                targetFile.delete()
            }

            // 创建临时目录
            val tempDir = FileUtil.createTempDirectory("templates", "export")

            // 保存所有模板
            selectedTemplates.forEach { template ->
                // 保存模板属性
                val propertiesFile = File(tempDir, "${template.name}.properties")
                propertiesFile.writeText("""
                    NAME=${template.name}
                    EXTENSION=${template.extension}
                    FILENAME=${template.fileName}
                    DESCRIPTION=${template.description}
                    REFORMAT=${template.isReformatCode}
                    LIVE_TEMPLATE_ENABLED=${template.isLiveTemplateEnabled}
                """.trimIndent())
                
                // 保存模板内容
                val contentFile = File(tempDir, "${template.name}.content")
                contentFile.writeText(template.text)
            }

            // 打包成 zip，保持目录结构
            ZipOutputStream(targetFile.outputStream()).use { zipOut ->
                tempDir.walk().filter { it.isFile }.forEach { file ->
                    val entryPath = "templates/" + file.relativeTo(tempDir).path.replace(File.separatorChar, '/')
                    zipOut.putNextEntry(ZipEntry(entryPath))
                    file.inputStream().use { input ->
                        input.copyTo(zipOut)
                    }
                    zipOut.closeEntry()
                }
            }

            FileUtil.delete(tempDir)

            // 显示成功消息，并询问是否分享到云端
            val choice = Messages.showYesNoDialog(
                project,
                "文件模板导出成功到 $fullFileName。是否要分享到云端？",
                "导出成功",
                "分享到云端",
                "关闭",
                Messages.getQuestionIcon()
            )

            if (choice == Messages.YES) { // 用户点击了"分享到云端"
                val templateService = service<TemplateServerService>()

                // 检查服务器配置
                if (templateService.state.serverUrl.isBlank() || templateService.state.apiKey.isBlank()) {
                    Messages.showErrorDialog(
                        project,
                        "请先配置服务器地址和API Key",
                        "分享到云端"
                    )
                    return
                }

                // 显示分享对话框
                val shareDialog = ShareDialog(project)
                if (!shareDialog.showAndGet()) {
                    return
                }

                try {
                    // 上传到服务器
                    templateService.uploadTemplate(
                        "file",
                        shareDialog.getDisplayName(),
                        targetFile
                    )

                    Messages.showInfoMessage(
                        project,
                        "模板已成功分享到云端",
                        "分享成功"
                    )
                    // 刷新工具窗口
                    ToolWindowManager.getInstance(project).getToolWindow("WPCoder")?.contentManager?.let { contentManager ->
                        contentManager.selectedContent?.let { content ->
                            (content.component as? TemplatePanel)?.refreshTemplates()
                        }
                    }
                } catch (e: Exception) {
                    Messages.showErrorDialog(
                        project,
                        "分享失败: ${e.message}",
                        "分享到云端"
                    )
                }
            }
        } catch (e: Exception) {
            Messages.showErrorDialog(
                project,
                "导出模板失败: ${e.message}",
                "导出模板"
            )
        }
    }
}