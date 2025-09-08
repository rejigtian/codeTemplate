package com.wepie.coder.wpcoder.window

import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.CheckboxTree
import com.intellij.ui.CheckedTreeNode
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBScrollPane
import org.jdom.input.SAXBuilder
import java.awt.BorderLayout
import java.awt.Dimension
import java.io.File
import javax.swing.*
import javax.swing.event.ChangeEvent
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath

class ExportLiveTemplateDialog(
    project: Project,
    private val templateFiles: List<File>
) : DialogWrapper(project) {

    data class TemplateInfo(
        val name: String,
        val description: String
    )

    data class FileInfo(
        val file: File,
        val group: String,
        val templates: List<TemplateInfo>
    )

    private val rootNode = CheckedTreeNode("Live Templates")
    private val tree = CheckboxTree(
        object : CheckboxTree.CheckboxTreeCellRenderer() {
            override fun customizeRenderer(
                tree: JTree,
                value: Any?,
                selected: Boolean,
                expanded: Boolean,
                leaf: Boolean,
                row: Int,
                hasFocus: Boolean
            ) {
                if (value is CheckedTreeNode) {
                    when (val userObject = value.userObject) {
                        is FileInfo -> {
                            textRenderer.append(
                                "${userObject.file.nameWithoutExtension} (${userObject.group})",
                                SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES
                            )
                            textRenderer.icon = AllIcons.FileTypes.Xml
                        }
                        is TemplateInfo -> {
                            textRenderer.append(
                                "${userObject.name} - ${userObject.description}",
                                SimpleTextAttributes.REGULAR_ATTRIBUTES
                            )
                            textRenderer.icon = AllIcons.Nodes.Template
                        }
                        is String -> {
                            textRenderer.append(
                                userObject,
                                SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES
                            )
                            textRenderer.icon = AllIcons.Nodes.Folder
                        }
                    }
                }
            }
        },
        rootNode
    )

    private val selectAllCheckBox = JCheckBox("全选").apply {
        isSelected = true
        addChangeListener { e: ChangeEvent ->
            val source = e.source as? JCheckBox ?: return@addChangeListener
            setAllNodesSelected(source.isSelected)
        }
    }

    init {
        title = "选择要导出的Live Templates"
        init()

        // 解析所有模板文件
        templateFiles.forEach { file ->
            try {
                val saxBuilder = SAXBuilder()
                val document = saxBuilder.build(file)
                val rootElement = document.rootElement
                val group = rootElement.getAttribute("group")?.value ?: "unknown"
                
                val templates = rootElement.getChildren("template").map { templateElement ->
                    TemplateInfo(
                        name = templateElement.getAttribute("name")?.value ?: "",
                        description = templateElement.getAttribute("description")?.value ?: ""
                    )
                }

                val fileInfo = FileInfo(file, group, templates)
                val fileNode = CheckedTreeNode(fileInfo).apply {
                    isChecked = true
                }
                rootNode.add(fileNode)

                // 添加模板节点（不可选）
                templates.forEach { template ->
                    val templateNode = CheckedTreeNode(template).apply {
                        isEnabled = false  // 禁用模板节点的选择
                        isChecked = false
                    }
                    fileNode.add(templateNode)
                }
            } catch (e: Exception) {
                println("解析文件失败: ${file.name}, 错误: ${e.message}")
            }
        }

        (tree.model as DefaultTreeModel).reload()
        expandAll()
        setAllNodesSelected(true)
    }

    private fun expandAll() {
        fun expandNode(node: CheckedTreeNode) {
            val path = TreePath(node.path)
            tree.expandPath(path)
            for (i in 0 until node.childCount) {
                expandNode(node.getChildAt(i) as CheckedTreeNode)
            }
        }
        expandNode(rootNode)
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        
        // 设置树的大小
        tree.preferredSize = Dimension(500, 400)
        
        // 添加全选复选框
        panel.add(selectAllCheckBox, BorderLayout.NORTH)
        
        // 添加带滚动条的树
        panel.add(JBScrollPane(tree), BorderLayout.CENTER)
        
        return panel
    }

    private fun setAllNodesSelected(selected: Boolean) {
        fun setNodeSelected(node: CheckedTreeNode) {
            node.isChecked = selected
            for (i in 0 until node.childCount) {
                setNodeSelected(node.getChildAt(i) as CheckedTreeNode)
            }
        }
        setNodeSelected(rootNode)
        tree.repaint()
    }

    fun getSelectedFiles(): Set<File> {
        val selectedFiles = mutableSetOf<File>()
        fun collectSelectedNodes(node: CheckedTreeNode) {
            if (node.isChecked && node.userObject is FileInfo) {
                selectedFiles.add((node.userObject as FileInfo).file)
            }
            for (i in 0 until node.childCount) {
                collectSelectedNodes(node.getChildAt(i) as CheckedTreeNode)
            }
        }
        collectSelectedNodes(rootNode)
        return selectedFiles
    }
}