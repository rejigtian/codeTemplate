package com.wepie.coder.wpcoder.action

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.Messages
import com.wepie.coder.wpcoder.window.ExportLiveTemplateDialog
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ExportLiveTemplateAction : DumbAwareAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        // 获取模板目录
        val templatesDir = File(PathManager.getConfigPath(), "templates")
        if (!templatesDir.exists() || !templatesDir.isDirectory) {
            Messages.showErrorDialog(
                project,
                "模板目录不存在",
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
                "没有可导出的模板文件",
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