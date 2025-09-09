package com.wepie.coder.wpcoder.window

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.wepie.coder.wpcoder.action.*
import com.wepie.coder.wpcoder.service.TemplateServerService
import java.awt.BorderLayout
import javax.swing.JPanel
import javax.swing.DefaultListCellRenderer
import java.awt.Component
import javax.swing.JList
import com.intellij.ui.components.JBLabel
import java.awt.Color
import javax.swing.SwingConstants
import javax.swing.SwingUtilities

class TemplatePanel(
    private val project: Project,
    private val templateType: String
) : JPanel(BorderLayout()) {
    private val templateService = service<TemplateServerService>()
    private val templateList = JBList<TemplateServerService.TemplateInfo>()
    private val tipLabel = JBLabel("", SwingConstants.LEFT).apply {
        foreground = Color(255, 102, 102) // 使用红色
        isVisible = false
        border = javax.swing.BorderFactory.createEmptyBorder(5, 10, 5, 10) // 添加上下左右边距
    }
    private val contentPanel = JPanel(BorderLayout())

    init {
        // 创建列表面板
        val listPanel = JPanel(BorderLayout())
        templateList.cellRenderer = TemplateCellRenderer()
        listPanel.add(JBScrollPane(templateList), BorderLayout.CENTER)

        // 创建工具栏
        val toolbar = createToolbar()
        toolbar.targetComponent = listPanel
        listPanel.add(toolbar.component, BorderLayout.NORTH)

        // 添加提示标签和列表到内容面板
        contentPanel.add(tipLabel, BorderLayout.NORTH)
        contentPanel.add(listPanel, BorderLayout.CENTER)

        // 添加到主面板
        add(contentPanel, BorderLayout.CENTER)

        // 初始检查配置并加载数据
        checkConfigAndLoadData()
    }

    private fun checkConfigAndLoadData() {
        val serverUrl = templateService.state.serverUrl
        val apiKey = templateService.state.apiKey

        if (serverUrl.isBlank() || apiKey.isBlank()) {
            showTip("请先配置服务器地址和API Key")
            return
        }

        // 如果使用默认配置，显示提示信息
        if (templateService.state.serverUrl == TemplateServerService.DEFAULT_SERVER_URL &&
            templateService.state.apiKey == TemplateServerService.DEFAULT_API_KEY) {
            tipLabel.text = "<html>当前使用默认开放服务器，仅拥有可读权限。如需完整权限请<a href='${TemplateServerService.GITHUB_REPO_URL}'>部署自己的服务器</a></html>"
            tipLabel.isVisible = true
            // 添加点击事件处理
            tipLabel.cursor = java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR)
            tipLabel.addMouseListener(object : java.awt.event.MouseAdapter() {
                override fun mouseClicked(e: java.awt.event.MouseEvent) {
                    com.intellij.ide.BrowserUtil.browse(TemplateServerService.GITHUB_REPO_URL)
                }
            })
        } else {
            tipLabel.isVisible = false
        }

        refreshTemplates()
    }

    private fun showTip(message: String) {
        tipLabel.text = message
        tipLabel.isVisible = true
        templateList.clearSelection()
        templateList.setListData(emptyArray())
    }

    private fun hideTip() {
        tipLabel.isVisible = false
    }

    private fun createToolbar(): ActionToolbar {
        val actionGroup = DefaultActionGroup().apply {
            // 快捷设置入口
            add(object : AnAction(
                if (templateType == "live") "Live Templates Settings" else "File Templates Settings",
                "",
                AllIcons.General.Settings
            ) {
                override fun actionPerformed(e: AnActionEvent) {
                    val actionId = if (templateType == "live") {
                        "WPCoder.LiveTemplateSettings"
                    } else {
                        "WPCoder.FileTemplateSettings"
                    }
                    ActionManager.getInstance().getAction(actionId)?.actionPerformed(e)
                }
            })

            addSeparator()

            // 本地导出
            add(object : AnAction("Export to File", "", AllIcons.Actions.MenuSaveall) {
                override fun actionPerformed(e: AnActionEvent) {
                    val actionId = if (templateType == "live") {
                        "WPCoder.ExportLiveTemplate"
                    } else {
                        "WPCoder.ExportTemplate"
                    }
                    ActionManager.getInstance().getAction(actionId)?.actionPerformed(e)
                }
            })

            // 本地导入
            add(object : AnAction("Import from File", "", AllIcons.Actions.MenuOpen) {
                override fun actionPerformed(e: AnActionEvent) {
                    val actionId = if (templateType == "live") {
                        "WPCoder.ImportLiveTemplate"
                    } else {
                        "WPCoder.ImportTemplate"
                    }
                    ActionManager.getInstance().getAction(actionId)?.actionPerformed(e)
                }
            })

            addSeparator()

            // 分享到服务器
            add(object : AnAction("Share to Server", "", AllIcons.Actions.Upload) {
                override fun actionPerformed(e: AnActionEvent) {
                    if (checkServerConfig()) {
                        uploadTemplate()
                    }
                }
            })

            // 从服务器获取
            add(object : AnAction("Get from Server", "", AllIcons.Actions.Download) {
                override fun actionPerformed(e: AnActionEvent) {
                    if (checkServerConfig()) {
                        downloadTemplate()
                    }
                }

                override fun update(e: AnActionEvent) {
                    e.presentation.isEnabled = templateList.selectedValue != null
                }
            })

            // 删除模板
            add(object : AnAction("Delete Template", "", AllIcons.Actions.GC) {
                override fun actionPerformed(e: AnActionEvent) {
                    if (checkServerConfig()) {
                        deleteTemplate()
                    }
                }

                override fun update(e: AnActionEvent) {
                    e.presentation.isEnabled = templateList.selectedValue != null
                }
            })

            // 刷新
            add(object : AnAction("Refresh", "", AllIcons.Actions.Refresh) {
                override fun actionPerformed(e: AnActionEvent) {
                    checkConfigAndLoadData()
                }
            })

            addSeparator()

            // 服务器配置
            add(object : AnAction("Server Settings", "", AllIcons.General.Web) {
                override fun actionPerformed(e: AnActionEvent) {
                    val dialog = ServerConfigDialog(
                        project,
                        templateService.state.serverUrl,
                        templateService.state.apiKey
                    )
                    if (dialog.showAndGet()) {
                        val newUrl = dialog.getServerUrl()
                        val newKey = dialog.getApiKey()
                        println("Dialog result: url=$newUrl, key=$newKey") // 添加日志
                        templateService.updateServerConfig(newUrl, newKey)
                        checkConfigAndLoadData()
                    }
                }
            })
        }

        return ActionManager.getInstance()
            .createActionToolbar("TemplatePanel", actionGroup, true)
    }

    private fun checkServerConfig(): Boolean {
        val serverUrl = templateService.state.serverUrl
        val apiKey = templateService.state.apiKey

        if (serverUrl.isBlank() || apiKey.isBlank()) {
            Messages.showWarningDialog(
                project,
                "请先配置服务器地址和API Key",
                "配置缺失"
            )
            return false
        }
        return true
    }

    fun onTabSelected() {
        checkConfigAndLoadData()
    }

    fun refreshTemplates() {
        // 确保在 EDT 线程中执行 UI 更新
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater { refreshTemplates() }
            return
        }

        try {
            val templates = templateService.getTemplates(templateType)
            templateList.setListData(templates.toTypedArray())
            // 如果不是默认配置，隐藏提示信息
            if (!(templateService.state.serverUrl == TemplateServerService.DEFAULT_SERVER_URL &&
                templateService.state.apiKey == TemplateServerService.DEFAULT_API_KEY)) {
                hideTip()
            }
        } catch (e: Exception) {
            showTip("加载失败: ${e.message}")
        }
    }

    private fun uploadTemplate() {
        val dialog = UploadTemplateDialog(project)
        if (dialog.showAndGet()) {
            val displayName = dialog.getDisplayName()
            val file = dialog.getSelectedFile()
            
            try {
                templateService.uploadTemplate(templateType, displayName, file)
                refreshTemplates()
                Messages.showInfoMessage(
                    project,
                    "Template uploaded successfully",
                    "Success"
                )
            } catch (e: Exception) {
                Messages.showErrorDialog(
                    project,
                    "Failed to upload template: ${e.message}",
                    "Error"
                )
            }
        }
    }

    private fun deleteTemplate() {
        val template = templateList.selectedValue ?: run {
            Messages.showWarningDialog(
                project,
                "Please select a template to delete",
                "Warning"
            )
            return
        }

        val result = Messages.showYesNoDialog(
            project,
            "Are you sure you want to delete this template?\nThis action cannot be undone.",
            "Delete Template",
            "Delete",
            "Cancel",
            Messages.getWarningIcon()
        )

        if (result == 0) {  // 0 表示点击了"Delete"按钮
            try {
                templateService.deleteTemplate(template.type, template.fileName)
                refreshTemplates()
                Messages.showInfoMessage(
                    project,
                    "Template deleted successfully",
                    "Success"
                )
            } catch (e: Exception) {
                Messages.showErrorDialog(
                    project,
                    "Failed to delete template: ${e.message}",
                    "Error"
                )
            }
        }
    }

    private fun downloadTemplate() {
        val template = templateList.selectedValue ?: run {
            Messages.showWarningDialog(
                project,
                "Please select a template to download",
                "Warning"
            )
            return
        }

        try {
            println("=== 准备下载模板 ===")
            println("模板名称: ${template.fileName}")
            println("模板类型: ${template.type}")
            val file = templateService.downloadTemplate(template.type, template.fileName)
            
            println("显示应用确认对话框")
            val result = Messages.showYesNoDialog(
                project,
                "是否立即应用下载的模板？",
                "下载完成",
                "应用",
                "取消",
                Messages.getQuestionIcon()
            )
            println("对话框结果: $result")

            if (result == 0) {  // 0 表示点击了第一个按钮（应用）
                println("开始处理类型: ${template.type}")
                val success = when (template.type) {
                    "live" -> try {
                        println("处理实时模板")
                        ImportLiveTemplateAction().importTemplateFromFile(project, file)
                        true
                    } catch (e: Exception) {
                        println("导入实时模板失败: ${e.message}")
                        e.printStackTrace()
                        false
                    }
                    "file" -> try {
                        println("处理文件模板")
                        ImportTemplateAction.importTemplateFromFile(project, file)
                        true
                    } catch (e: Exception) {
                        println("导入文件模板失败: ${e.message}")
                        e.printStackTrace()
                        false
                    }
                    else -> false
                }
                if (!success) {
                    println("模板导入失败")
                }
            }
        } catch (e: Exception) {
            Messages.showErrorDialog(
                project,
                "Failed to download template: ${e.message}",
                "Error"
            )
        }
    }
}

private class TemplateCellRenderer : DefaultListCellRenderer() {
    override fun getListCellRendererComponent(
        list: JList<*>?,
        value: Any?,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean
    ): Component {
        super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
        
        if (value is TemplateServerService.TemplateInfo) {
            text = value.displayName
            icon = when (value.type) {
                "live" -> AllIcons.Actions.ListFiles
                "file" -> AllIcons.FileTypes.Any_type
                else -> null
            }
        }
        
        return this
    }
}