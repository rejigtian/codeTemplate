package com.wepie.coder.wpcoder.window

import com.intellij.icons.AllIcons
import com.intellij.ide.fileTemplates.FileTemplate
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.CheckboxTree
import com.intellij.ui.CheckedTreeNode
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBScrollPane
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.*
import javax.swing.tree.DefaultTreeModel
import javax.swing.event.ChangeEvent
import javax.swing.tree.TreePath

class ExportTemplateDialog(
    project: Project,
    private val templates: List<FileTemplate>
) : DialogWrapper(project) {
    private val rootNode = CheckedTreeNode("Templates")
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
                        is FileTemplate -> {
                            // 如果是主文件，显示简单名称，否则显示子文件名称
                            val displayName = if (!userObject.name.contains(".child.")) {
                                userObject.name
                            } else {
                                userObject.fileName
                            }
                            textRenderer.append(displayName)
                            textRenderer.icon = AllIcons.FileTypes.Any_type
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
        title = "选择要导出的模板"
        init()

        // 按目录组织模板
        val templateGroups = mutableMapOf<String, MutableList<FileTemplate>>()

        // 首先找出所有主模板和它们的子模板
        templates.forEach { template ->
            val name = template.name
            val baseName = if (name.contains(".child.")) {
                // 对于子模板，找到父模板名称
                val parentExt = template.extension // 父模板的扩展名
                val pattern = ".$parentExt.child."
                if (name.contains(pattern)) {
                    name.substringBefore(pattern)
                } else {
                    name
                }
            } else {
                name
            }
            templateGroups.getOrPut(baseName) { mutableListOf() }.add(template)
        }

        // 为每个组创建目录结构
        templateGroups.forEach { (baseName, groupTemplates) ->
            // 创建目录节点
            val dirNode = CheckedTreeNode(baseName).apply {
                isChecked = true
            }
            rootNode.add(dirNode)

            // 添加主文件
            val mainFile = groupTemplates.find { !it.name.contains(".child.") }
            if (mainFile != null) {
                val mainNode = CheckedTreeNode(mainFile).apply {
                    isChecked = true
                }
                dirNode.add(mainNode)

                // 添加子文件，按文件名排序
                val parentExt = mainFile.extension
                val pattern = ".$parentExt.child."
                groupTemplates
                    .filter { it.name.contains(pattern) }
                    .sortedBy { it.fileName }
                    .forEach { template ->
                        val childNode = CheckedTreeNode(template).apply {
                            isChecked = true
                        }
                        dirNode.add(childNode)
                    }
            }
        }

        (tree.model as DefaultTreeModel).reload()
        
        // 展开所有节点
        expandAll()
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
        tree.preferredSize = Dimension(400, 300)
        
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

    fun getSelectedTemplates(): List<FileTemplate> {
        val selected = mutableListOf<FileTemplate>()
        fun collectSelectedNodes(node: CheckedTreeNode) {
            if (node.isChecked && node.userObject is FileTemplate) {
                selected.add(node.userObject as FileTemplate)
            }
            for (i in 0 until node.childCount) {
                collectSelectedNodes(node.getChildAt(i) as CheckedTreeNode)
            }
        }
        collectSelectedNodes(rootNode)
        return selected
    }
}