package com.wepie.coder.wpcoder.action

import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.io.FileUtil
import java.io.File
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ExportTemplateAction : DumbAwareAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        // 获取所有用户自定义模板
        val fileTemplateManager = FileTemplateManager.getInstance(project)
        val templates = fileTemplateManager.allTemplates.filter { !it.isDefault }
        
        // 调试输出
        templates.forEach { template ->
            println("Template name: ${template.name}, fileName: ${template.fileName}, extension: ${template.extension}, description: ${template.description}")
        }
        
        if (templates.isEmpty()) {
            Messages.showInfoMessage(
                project,
                "No templates available to export",
                "Export Templates"
            )
            return
        }

        val descriptor = FileSaverDescriptor(
            "Export Templates",
            "Choose where to save the templates",
            "zip"
        )
        val fileSaver = FileChooserFactory.getInstance()
            .createSaveFileDialog(descriptor, project)
        val virtualFileWrapper = fileSaver.save(Path.of(""), "templates.zip")
            ?: return

        try {
            val targetFile = virtualFileWrapper.file
            if (targetFile.exists()) {
                targetFile.delete()
            }

            // 创建临时目录
            val tempDir = FileUtil.createTempDirectory("templates", "export")

            // 保存所有模板
            templates.forEach { template ->
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
                    val entryPath = file.relativeTo(tempDir).path.replace(File.separatorChar, '/')
                    zipOut.putNextEntry(ZipEntry(entryPath))
                    file.inputStream().use { input ->
                        input.copyTo(zipOut)
                    }
                    zipOut.closeEntry()
                }
            }

            FileUtil.delete(tempDir)

            Messages.showInfoMessage(
                project,
                "Templates exported successfully",
                "Export Templates"
            )
        } catch (e: Exception) {
            Messages.showErrorDialog(
                project,
                "Failed to export templates: ${e.message}",
                "Export Templates"
            )
        }
    }
}