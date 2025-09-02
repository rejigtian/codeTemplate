package com.wepie.coder.wpcoder.action

import com.intellij.ide.fileTemplates.FileTemplate
import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.ide.fileTemplates.impl.FileTemplateManagerImpl
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.io.FileUtil
import java.io.File
import java.util.zip.ZipFile

class ImportTemplateAction : AnAction() {
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
        if (!sourceFile.exists()) {
            Messages.showErrorDialog(
                project,
                "Source file does not exist",
                "Import Templates"
            )
            return
        }

        try {
            val fileTemplateManager = FileTemplateManager.getInstance(project)
            when {
                sourceFile.extension == "zip" -> {
                    // 如果是 zip 文件，解压所有文件
                    val tempDir = FileUtil.createTempDirectory("templates", "import")
                    ZipFile(sourceFile).use { zip ->
                        zip.entries().asSequence().forEach { entry ->
                            if (!entry.isDirectory) {
                                val tempFile = File(tempDir, entry.name)
                                zip.getInputStream(entry).use { input ->
                                    tempFile.outputStream().use { output ->
                                        input.copyTo(output)
                                    }
                                }
                                
                                // 导入模板
                                val name = tempFile.nameWithoutExtension
                                val extension = tempFile.extension
                                val content = tempFile.readText()
                                val template = fileTemplateManager.addTemplate(name, extension)
                                template.text = content
                            }
                        }
                    }
                    FileUtil.delete(tempDir)
                }
                else -> {
                    // 如果是单个文件，直接导入
                    val name = sourceFile.nameWithoutExtension
                    val extension = sourceFile.extension
                    val content = sourceFile.readText()
                    val template = fileTemplateManager.addTemplate(name, extension)
                    template.text = content
                }
            }

            Messages.showInfoMessage(
                project,
                "Templates imported successfully",
                "Import Templates"
            )
        } catch (e: Exception) {
            Messages.showErrorDialog(
                project,
                "Failed to import templates: ${e.message}",
                "Import Templates"
            )
        }
    }
}