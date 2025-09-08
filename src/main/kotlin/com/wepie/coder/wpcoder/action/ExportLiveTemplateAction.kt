package com.wepie.coder.wpcoder.action

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.service
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import com.wepie.coder.wpcoder.service.TemplateServerService
import com.wepie.coder.wpcoder.window.ExportLiveTemplateDialog
import com.intellij.openapi.wm.ToolWindowManager
import java.awt.Dimension
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.swing.JComponent
import javax.swing.JPanel
import java.awt.GridBagLayout
import java.awt.GridBagConstraints
import java.awt.Insets

class ExportLiveTemplateAction : DumbAwareAction() {

    private class ShareDialog(
        private val project: com.intellij.openapi.project.Project
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
            panel.border = JBUI.Borders.empty(10)
            
            val gbc = GridBagConstraints().apply {
                gridx = 0
                gridy = 0
                anchor = GridBagConstraints.WEST
                insets = Insets(5, 5, 5, 5)
            }
            
            // 添加标签
            panel.add(com.intellij.ui.components.JBLabel("模板名称:"), gbc)
            
            // 添加输入框
            gbc.gridx = 1
            gbc.fill = GridBagConstraints.HORIZONTAL
            gbc.weightx = 1.0
            panel.add(displayNameField, gbc)
            
            // 添加说明文字
            gbc.gridx = 1
            gbc.gridy = 1
            gbc.fill = GridBagConstraints.NONE
            gbc.weightx = 0.0
            panel.add(
                com.intellij.ui.components.JBLabel("显示在模板列表中的名称").apply {
                    foreground = JBUI.CurrentTheme.ContextHelp.FOREGROUND
                    font = JBUI.Fonts.smallFont()
                },
                gbc
            )
            
            panel.preferredSize = Dimension(400, 100)
            return panel
        }

        override fun doValidate(): ValidationInfo? {
            if (displayNameField.text.isBlank()) {
                return ValidationInfo("请输入模板名称", displayNameField)
            }
            return null
        }

        fun getDisplayName() = displayNameField.text
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        // 获取模板目录
        val templatesDir = File(PathManager.getConfigPath(), "templates")
        if (!templatesDir.exists() || !templatesDir.isDirectory) {
            Messages.showErrorDialog(
                project,
                "无用户自定义代码模版",
                "导出实时模板"
            )
            return
        }

        // 获取所有XML文件
        val xmlFiles = templatesDir.listFiles()?.filter { 
            it.isFile && it.extension == "xml" 
        } ?: emptyList()

        if (xmlFiles.isEmpty()) {
            Messages.showInfoMessage(
                project,
                "无用户自定义代码模版",
                "导出实时模板"
            )
            return
        }

        // 显示选择对话框
        val dialog = ExportLiveTemplateDialog(project, xmlFiles)
        if (!dialog.showAndGet()) {
            return
        }

        // 获取选中的文件
        val selectedFiles = dialog.getSelectedFiles()
        if (selectedFiles.isEmpty()) {
            Messages.showWarningDialog(
                project,
                "请至少选择一个模板",
                "导出实时模板"
            )
            return
        }

        // 选择保存位置
        val descriptor = FileSaverDescriptor(
            "导出实时模板",
            "选择保存位置",
            "zip"
        )
        val fileSaver = FileChooserFactory.getInstance()
            .createSaveFileDialog(descriptor, project)
        val virtualFileWrapper = fileSaver.save("templates.zip")
            ?: return

        try {
            // 创建 zip 文件
            val zipFile = virtualFileWrapper.file
            ZipOutputStream(FileOutputStream(zipFile)).use { zipOut ->
                selectedFiles.forEach { file ->
                    // 添加文件到 zip
                    val entry = ZipEntry(file.name)
                    zipOut.putNextEntry(entry)
                    file.inputStream().use { input ->
                        input.copyTo(zipOut)
                    }
                    zipOut.closeEntry()
                }
            }

            // 显示成功消息，并提供分享按钮
            val choice = Messages.showDialog(
                project,
                "实时模板导出成功",
                "导出实时模板",
                arrayOf("分享到云端", "关闭"),
                0,
                Messages.getInformationIcon()
            )

            if (choice == 0) { // 用户点击了"分享到云端"
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
                        "live",
                        shareDialog.getDisplayName(),
                        zipFile
                    )

                    Messages.showInfoMessage(
                        project,
                        "模板已成功分享到云端",
                        "分享成功"
                    )

                    // 刷新工具窗口
                    ToolWindowManager.getInstance(project).getToolWindow("WPCoder")?.let { toolWindow ->
                        toolWindow.contentManager.selectedContent?.let { content ->
                            (content.component as? com.wepie.coder.wpcoder.window.TemplatePanel)?.refreshTemplates()
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
                "导出实时模板失败: ${e.message}",
                "导出实时模板"
            )
        }
    }
}