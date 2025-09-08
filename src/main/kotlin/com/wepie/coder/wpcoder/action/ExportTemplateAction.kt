package com.wepie.coder.wpcoder.action

import com.intellij.ide.fileTemplates.FileTemplate
import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.io.FileUtil
import com.wepie.coder.wpcoder.window.ExportTemplateDialog
import java.io.File
import java.io.FileOutputStream
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
        println("=== 模板信息 ===")
        templates.forEach { template ->
            println("""
                |名称: ${template.name}
                |文件名: ${template.fileName}
                |扩展名: ${template.extension}
                |描述: ${template.description}
                |路径: ${template.fileName}
                |是否默认: ${template.isDefault}
                |------------------------
            """.trimMargin())
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
        val descriptor = FileSaverDescriptor(
            "Export File Templates",
            "Choose where to save the exported templates",
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

            Messages.showInfoMessage(
                project,
                "模板导出成功",
                "导出模板"
            )
        } catch (e: Exception) {
            Messages.showErrorDialog(
                project,
                "导出模板失败: ${e.message}",
                "导出模板"
            )
        }
    }
}