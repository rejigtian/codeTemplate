package com.wepie.coder.wpcoder.action

import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.ide.fileTemplates.impl.FileTemplateManagerImpl
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.io.FileUtil
import java.io.File
import java.util.Properties
import java.util.zip.ZipFile

class ImportTemplateAction : DumbAwareAction() {
    companion object {
        fun importTemplateFromFile(project: Project, sourceFile: File) {
            if (!sourceFile.exists()) {
                Messages.showErrorDialog(
                    project,
                    "Source file does not exist",
                    "Import Templates"
                )
                return
            }

            try {
                val fileTemplateManager = FileTemplateManager.getInstance(project) as FileTemplateManagerImpl
                ApplicationManager.getApplication().runWriteAction {
                    when {
                        sourceFile.extension == "zip" -> {
                            // 如果是 zip 文件，解压所有文件
                            val tempDir = FileUtil.createTempDirectory("templates", "import")
                            try {
                                ZipFile(sourceFile).use { zip ->
                                    // 调试：列出所有文件
                                    println("Files in zip:")
                                    zip.entries().asSequence().forEach { entry ->
                                        println("  ${entry.name}")
                                    }
                                    
                                    // 第一步：解压所有文件
                                    zip.entries().asSequence().forEach { entry ->
                                        if (!entry.isDirectory) {
                                            val tempFile = File(tempDir, entry.name.substringAfter("templates/"))
                                            FileUtil.createParentDirs(tempFile)
                                            zip.getInputStream(entry).use { input ->
                                                tempFile.outputStream().use { output ->
                                                    input.copyTo(output)
                                                }
                                            }
                                        }
                                    }
                                    
                                    // 调试：列出临时目录中的所有文件
                                    println("\nFiles in temp directory:")
                                    tempDir.walk().forEach { file ->
                                        println("  ${file.relativeTo(tempDir)}")
                                    }
                                    
                                    // 第二步：处理所有属性文件
                                    tempDir.walk()
                                        .filter { it.extension == "properties" }
                                        .forEach { propFile ->
                                            val properties = Properties()
                                            propFile.inputStream().use { properties.load(it) }
                                            
                                            val name = properties.getProperty("NAME")
                                            val extension = properties.getProperty("EXTENSION")
                                            val fileName = properties.getProperty("FILENAME")
                                            val reformat = properties.getProperty("REFORMAT", "true").toBoolean()
                                            val liveTemplateEnabled = properties.getProperty("LIVE_TEMPLATE_ENABLED", "false").toBoolean()
                                            
                                            if (name != null && extension != null) {
                                                val contentFile = File(tempDir, "$name.content")
                                                println("Looking for content file: ${contentFile.absolutePath}")
                                                
                                                if (contentFile.exists()) {
                                                    val content = contentFile.readText()
                                                    
                                                    // 先删除同名模板
                                                    fileTemplateManager.allTemplates.find { 
                                                        it.name == name && it.extension == extension 
                                                    }?.let {
                                                        fileTemplateManager.removeTemplate(it)
                                                    }
                                                    
                                                    // 添加新模板
                                                    val template = fileTemplateManager.addTemplate(name, extension)
                                                    template.text = content
                                                    template.isReformatCode = reformat
                                                    template.isLiveTemplateEnabled = liveTemplateEnabled

                                                    // 设置文件名
                                                    if (!fileName.isNullOrEmpty()) {
                                                        template.fileName = fileName
                                                        // 强制保存模板配置
                                                        fileTemplateManager.saveAllTemplates()
                                                    }

                                                    // 调试输出
                                                    println("Imported template: name=$name, fileName=${template.fileName}, extension=$extension")
                                                } else {
                                                    println("  WARNING: Content file not found for template: $name")
                                                }
                                            }
                                        }
                                }
                            } finally {
                                FileUtil.delete(tempDir)
                            }
                        }
                        else -> {
                            // 如果是单个文件，直接导入
                            val name = sourceFile.nameWithoutExtension
                                .replace(" ", "_") // 替换空格为下划线
                                .replace("-", "_") // 替换横线为下划线
                            val extension = sourceFile.extension
                            val content = sourceFile.readText()
                            
                            // 先删除同名模板
                            fileTemplateManager.allTemplates.find { it.name == name }?.let {
                                fileTemplateManager.removeTemplate(it)
                            }
                            // 添加新模板
                            val template = fileTemplateManager.addTemplate(name, extension)
                            template.text = content
                        }
                    }
                    
                    // 强制刷新模板管理器
                    fileTemplateManager.setTemplates(FileTemplateManager.DEFAULT_TEMPLATES_CATEGORY, fileTemplateManager.getAllTemplates().toList())
                }

                Messages.showInfoMessage(
                    project,
                    "Templates imported successfully",
                    "Import Templates"
                )
            } catch (e: Exception) {
                throw e
            }
        }
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        val descriptor = FileChooserDescriptor(
            true,
            false,
            true,
            false,
            false,
            false
        ).withTitle("Import Templates")
            .withDescription("Choose a template file or zip file to import")
            .withFileFilter { it.extension == "zip" || it.extension == "ft" }

        val virtualFile = FileChooser.chooseFile(descriptor, project, null)
            ?: return

        val sourceFile = File(virtualFile.path)
        try {
            importTemplateFromFile(project, sourceFile)
        } catch (e: Exception) {
            Messages.showErrorDialog(
                project,
                "Failed to import templates: ${e.message}",
                "Import Templates"
            )
        }
    }
}