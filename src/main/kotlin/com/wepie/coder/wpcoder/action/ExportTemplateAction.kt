package com.wepie.coder.wpcoder.action

import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.io.FileUtil
import java.io.File
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ExportTemplateAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        // 获取所有用户自定义模板
        val fileTemplateManager = FileTemplateManager.getInstance(project)
        val templates = fileTemplateManager.allTemplates.filter { !it.isDefault }
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
                val file = File(tempDir, "${template.name}.${template.extension}")
                file.writeText(template.text)
            }

            // 打包成 zip
            ZipOutputStream(targetFile.outputStream()).use { zipOut ->
                tempDir.listFiles()?.forEach { file ->
                    zipOut.putNextEntry(ZipEntry(file.name))
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