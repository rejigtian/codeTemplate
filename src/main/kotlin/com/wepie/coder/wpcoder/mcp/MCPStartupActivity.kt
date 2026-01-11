package com.wepie.coder.wpcoder.mcp

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

class MCPStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        service<MCPServerService>().updateServerState()
    }
}
