package com.wepie.coder.wpcoder.action

import com.intellij.codeInsight.template.impl.TemplateSettings
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.ui.Messages
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ExportLiveTemplateAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return



        val descriptor = FileSaverDescriptor(
            "导出实时模板",
            "选择保存位置",
            "zip"
        )
        val fileSaver = FileChooserFactory.getInstance()
            .createSaveFileDialog(descriptor, project)
        val virtualFileWrapper = fileSaver.save(Path.of(""), "templates.zip")
            ?: return

        try {
            // 获取模板目录
            val templatesDir = File(PathManager.getConfigPath(), "templates")
            if (!templatesDir.exists() || !templatesDir.isDirectory) {
                throw IllegalStateException("模板目录不存在")
            }

            // 创建 zip 文件
            val zipFile = virtualFileWrapper.file
            ZipOutputStream(FileOutputStream(zipFile)).use { zipOut ->
                templatesDir.listFiles()?.filter { 
                    it.isFile && it.extension == "xml" 
                }?.forEach { file ->
                    // 添加文件到 zip
                    val entry = ZipEntry(file.name)
                    zipOut.putNextEntry(entry)
                    file.inputStream().use { input ->
                        input.copyTo(zipOut)
                    }
                    zipOut.closeEntry()
                }
            }

            Messages.showInfoMessage(
                project,
                "实时模板导出成功",
                "导出实时模板"
            )
        } catch (e: Exception) {
            Messages.showErrorDialog(
                project,
                "导出实时模板失败: ${e.message}",
                "导出实时模板"
            )
        }
    }
}