package com.wepie.coder.wpcoder.action

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.template.impl.TemplateSettings
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.JDOMUtil
import org.jdom.Element
import java.io.File
import java.util.zip.ZipInputStream

class ImportLiveTemplateAction : DumbAwareAction() {
    private fun ensureTemplateDirectories(): File {
        // 获取模板目录
        val templatesDir = File(PathManager.getConfigPath(), "templates")
        if (!templatesDir.exists()) {
            templatesDir.mkdirs()
        }

        // 创建备份目录
        val backupDir = File(templatesDir.parentFile, "templates.backup")
        if (!backupDir.exists()) {
            backupDir.mkdirs()
        }

        return templatesDir
    }

    fun importTemplateFromFile(project: Project, sourceFile: File) {
        if (!sourceFile.exists()) {
            Messages.showErrorDialog(
                project,
                "源文件不存在",
                "导入实时模板"
            )
            return
        }

        try {
            // 确保目录存在
            val templatesDir = ensureTemplateDirectories()

            // 备份原有文件
            val files = templatesDir.listFiles() ?: emptyArray()
            val backupDir = File(templatesDir.parentFile, "templates.backup")
            files.forEach { file ->
                if (file.isFile && file.extension == "xml") {
                    file.copyTo(File(backupDir, file.name), overwrite = true)
                }
            }
            
            // 在写操作中解压文件并重新加载模板
            ApplicationManager.getApplication().runWriteAction {
                // 清空模板目录
                files.forEach { it.delete() }
                
                // 解压新文件到模板目录
                ZipInputStream(sourceFile.inputStream()).use { zipIn ->
                    var entry = zipIn.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory && entry.name.endsWith(".xml")) {
                            val targetFile = File(templatesDir, entry.name)
                            targetFile.outputStream().use { output ->
                                zipIn.copyTo(output)
                            }
                            
                            // 立即加载这个文件中的模板
                            val element = JDOMUtil.load(targetFile)
                            if (element.name == "templateSet") {
                                val groupName = element.getAttributeValue("group") ?: "user"
                                val templateSettings = TemplateSettings.getInstance()
                                
                                element.getChildren("template").forEach { templateElement ->
                                    val template = TemplateSettings.readTemplateFromElement(
                                        groupName,
                                        templateElement,
                                        javaClass.classLoader,
                                    )

                                    // 如果已存在同名模板，先删除
                                    val existingTemplate = templateSettings.getTemplate(template.key, groupName)
                                    if (existingTemplate != null) {
                                        templateSettings.removeTemplate(existingTemplate)
                                    }
                                    
                                    // 添加新模板
                                    templateSettings.addTemplate(template)
                                }
                            }
                        }
                        zipIn.closeEntry()
                        entry = zipIn.nextEntry
                    }
                }
                
                // 刷新编辑器
                DaemonCodeAnalyzer.getInstance(project).restart()
            }

            Messages.showInfoMessage(
                project,
                "模板导入成功",
                "导入实时模板"
            )
        } catch (e: Exception) {
            // 确保目录存在，即使发生错误
            ensureTemplateDirectories()
            throw e
        }
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        // 在选择文件前就确保目录存在
        ensureTemplateDirectories()

        val descriptor = FileChooserDescriptor(
            true,
            false,
            true,
            false,
            false,
            false
        ).withTitle("导入实时模板")
            .withDescription("选择模板压缩包文件")
            .withFileFilter { it.extension == "zip" }

        val virtualFile = FileChooser.chooseFile(descriptor, project, null)
            ?: return

        val sourceFile = File(virtualFile.path)
        try {
            importTemplateFromFile(project, sourceFile)
        } catch (e: Exception) {
            Messages.showErrorDialog(
                project,
                "导入实时模板失败: ${e.message}",
                "导入实时模板"
            )
        }
    }
}