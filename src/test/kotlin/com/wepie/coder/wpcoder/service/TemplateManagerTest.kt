package com.wepie.coder.wpcoder.service

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.wepie.coder.wpcoder.model.Template
import com.wepie.coder.wpcoder.model.TemplateType
import com.wepie.coder.wpcoder.service.impl.TemplateManagerImpl
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

class TemplateManagerTest : BasePlatformTestCase() {
    
    private lateinit var templateManager: TemplateManager
    
    @Before
    override fun setUp() {
        super.setUp()
        templateManager = TemplateManagerImpl(project)
    }
    
    @Test
    fun testImportTemplate() {
        // 创建测试文件
        val content = """
            package com.example
            
            class MyClass {
                // TODO: Add implementation
            }
        """.trimIndent()
        
        val tempFile = File.createTempFile("test", ".java")
        tempFile.writeText(content)
        
        // 导入模板
        val template = templateManager.importTemplate(tempFile)
        
        // 验证
        assertEquals("MyClass", template.name)
        assertEquals(TemplateType.FILE, template.type)
        assertEquals(content, template.content)
        assertTrue(template.variables.isEmpty())
        
        // 清理
        tempFile.delete()
    }
    
    fun testExportTemplate() {
        // 创建测试模板
        val template = Template(
            name = "TestTemplate",
            type = TemplateType.FILE,
            content = "Test content",
            variables = mapOf("VAR" to "value"),
            description = "Test description",
            category = "Test",
            extension = "java"
        )
        
        // 导出模板
        val tempFile = File.createTempFile("test", ".java")
        templateManager.exportTemplate(template, tempFile)
        
        // 验证
        assertEquals("Test content", tempFile.readText())
        
        // 清理
        tempFile.delete()
    }
    
    fun testSyncToIDE() {
        // 创建测试模板
        val template = Template(
            name = "TestTemplate",
            type = TemplateType.FILE,
            content = "Test content",
            variables = mapOf("VAR" to "value"),
            description = "Test description",
            category = "Test",
            extension = "java"
        )
        
        // 同步到IDE
        templateManager.syncToIDE(template)
        
        // 验证
        val templates = templateManager.getAllTemplates()
        assertTrue(templates.any { it.name == "TestTemplate" })
    }
}
