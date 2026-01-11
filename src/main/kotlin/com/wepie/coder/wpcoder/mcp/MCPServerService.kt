package com.wepie.coder.wpcoder.mcp

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.intellij.codeInsight.template.impl.TemplateSettings
import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.ProjectManager
import com.wepie.coder.wpcoder.service.TemplateServerService
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors

@Service(Service.Level.APP)
class MCPServerService {
    private val executor = Executors.newCachedThreadPool()
    private var serverSocket: ServerSocket? = null
    private var running = false
    private val gson = Gson()

    init {
        // 服务初始化
    }

    fun updateServerState() {
        val state = service<TemplateServerService>().state
        println("[DEBUG_LOG] updateServerState triggered. mcpEnabled=${state.mcpEnabled}, mcpPort=${state.mcpPort}, currentRunning=$running")
        if (state.mcpEnabled) {
            startServer(state.mcpPort)
        } else {
            stopServer()
        }
    }

    @Synchronized
    private fun startServer(port: Int) {
        println("[DEBUG_LOG] startServer called for port $port. running=$running, currentPort=${serverSocket?.localPort}")
        if (running && serverSocket?.localPort == port) {
            println("[DEBUG_LOG] Server already running on port $port")
            return
        }
        
        stopServer()
        
        try {
            serverSocket = ServerSocket(port)
            running = true
            println("MCP Server started on port $port")
            println("[DEBUG_LOG] MCP Server successfully started on port $port")
            
            executor.execute {
                while (running) {
                    try {
                        val socket = serverSocket?.accept() ?: break
                        println("[DEBUG_LOG] New client connected to MCP Server")
                        handleClient(socket)
                    } catch (e: Exception) {
                        if (running) {
                            println("[DEBUG_LOG] Error accepting connection: ${e.message}")
                            e.printStackTrace()
                        }
                    }
                }
            }
        } catch (e: java.net.BindException) {
            println("MCP Server failed to start on port $port: Port already in use (possibly by another IDE window)")
            println("[DEBUG_LOG] BindException for port $port")
        } catch (e: Exception) {
            println("[DEBUG_LOG] Unexpected error starting MCP server: ${e.message}")
            e.printStackTrace()
        }
    }

    @Synchronized
    private fun stopServer() {
        running = false
        serverSocket?.close()
        serverSocket = null
        println("MCP Server stopped")
    }

    private fun handleClient(socket: Socket) {
        executor.execute {
            socket.use { s ->
                val reader = BufferedReader(InputStreamReader(s.getInputStream()))
                val writer = PrintWriter(s.getOutputStream(), true)
                
                while (running) {
                    val line = reader.readLine() ?: break
                    try {
                        val request = gson.fromJson(line, JsonObject::class.java)
                        val response = processRequest(request)
                        // 通知请求不返回 ID，也不需要响应
                        if (request.has("id")) {
                            writer.println(gson.toJson(response))
                        }
                    } catch (e: Exception) {
                        val errorResponse = JsonObject().apply {
                            addProperty("jsonrpc", "2.0")
                            add("id", null)
                            val error = JsonObject().apply {
                                addProperty("code", -32603)
                                addProperty("message", e.message)
                            }
                            add("error", error)
                        }
                        writer.println(gson.toJson(errorResponse))
                    }
                }
            }
        }
    }

    private fun processRequest(request: JsonObject): JsonObject {
        val id = request.get("id")
        val method = request.get("method")?.asString
        
        val response = JsonObject().apply {
            addProperty("jsonrpc", "2.0")
            add("id", id)
        }

        when (method) {
            "initialize" -> {
                val result = JsonObject().apply {
                    addProperty("protocolVersion", "2024-11-05")
                    val capabilities = JsonObject().apply {
                        val tools = JsonObject()
                        add("tools", tools)
                    }
                    add("capabilities", capabilities)
                    val serverInfo = JsonObject().apply {
                        addProperty("name", "WP Coder MCP Server")
                        addProperty("version", "1.0.0")
                    }
                    add("serverInfo", serverInfo)
                }
                response.add("result", result)
            }
            "notifications/initialized" -> {
                return JsonObject() // 简单的通知不需要回复，但我们返回空对象以示处理
            }
            "tools/list", "listTools" -> {
                val result = JsonObject().apply {
                    val tools = com.google.gson.JsonArray()
                    
                    tools.add(JsonObject().apply {
                        addProperty("name", "list_file_templates")
                        addProperty("description", "List all user-defined file templates in IntelliJ. Call this when you need to know what file templates are available for creating new files with consistent project style.")
                        add("inputSchema", JsonObject().apply {
                            addProperty("type", "object")
                            add("properties", JsonObject())
                        })
                    })
                    
                    tools.add(JsonObject().apply {
                        addProperty("name", "get_file_template")
                        addProperty("description", "Get the content of a specific file template. Use this to see the template structure and variables before applying it.")
                        add("inputSchema", JsonObject().apply {
                            addProperty("type", "object")
                            add("properties", JsonObject().apply {
                                add("templateName", JsonObject().apply {
                                    addProperty("type", "string")
                                })
                            })
                            val required = com.google.gson.JsonArray()
                            required.add("templateName")
                            add("required", required)
                        })
                    })

                    tools.add(JsonObject().apply {
                        addProperty("name", "list_live_templates")
                        addProperty("description", "List all user-defined live templates (snippets) in IntelliJ. Call this when you want to use existing code snippets or shortcuts defined in the project.")
                        add("inputSchema", JsonObject().apply {
                            addProperty("type", "object")
                            add("properties", JsonObject())
                        })
                    })

                    tools.add(JsonObject().apply {
                        addProperty("name", "get_live_template")
                        addProperty("description", "Get the content of a specific live template (XML). Use this to understand the code snippet structure and its variables.")
                        add("inputSchema", JsonObject().apply {
                            addProperty("type", "object")
                            add("properties", JsonObject().apply {
                                add("templateName", JsonObject().apply {
                                    addProperty("type", "string")
                                })
                            })
                            val required = com.google.gson.JsonArray()
                            required.add("templateName")
                            add("required", required)
                        })
                    })

                    add("tools", tools)
                }
                response.add("result", result)
            }
            "tools/call", "callTool" -> {
                val params = request.getAsJsonObject("params")
                val name = params.get("name")?.asString
                val toolParams = params.getAsJsonObject("arguments")
                
                val result = when (name) {
                    "list_file_templates" -> handleListFileTemplates()
                    "get_file_template" -> handleGetFileTemplate(toolParams.get("templateName")?.asString ?: "")
                    "list_live_templates" -> handleListLiveTemplates()
                    "get_live_template" -> handleGetLiveTemplate(toolParams.get("templateName")?.asString ?: "")
                    else -> throw Exception("Tool not found: $name")
                }
                response.add("result", result)
            }
            else -> {
                val error = JsonObject().apply {
                    addProperty("code", -32601)
                    addProperty("message", "Method not found")
                }
                response.add("error", error)
            }
        }
        
        return response
    }

    private fun handleListFileTemplates(): JsonObject {
        val templatesInfo = mutableListOf<String>()
        ApplicationManager.getApplication().runReadAction {
            ProjectManager.getInstance().openProjects.forEach { project ->
                val manager = FileTemplateManager.getInstance(project)
                manager.allTemplates.filter { !it.isDefault }.forEach {
                    val info = "Name: ${it.name}, Extension: ${it.extension}, Description: ${it.description ?: "N/A"}"
                    templatesInfo.add(info)
                }
            }
        }
        
        return JsonObject().apply {
            val content = com.google.gson.JsonArray()
            content.add(JsonObject().apply {
                addProperty("type", "text")
                addProperty("text", if (templatesInfo.isEmpty()) "No user-defined file templates found." else "Available File Templates:\n" + templatesInfo.distinct().joinToString("\n"))
            })
            add("content", content)
        }
    }

    private fun handleGetFileTemplate(name: String): JsonObject {
        var templateText: String? = null
        ApplicationManager.getApplication().runReadAction {
            ProjectManager.getInstance().openProjects.forEach { project ->
                if (templateText != null) return@forEach
                val manager = FileTemplateManager.getInstance(project)
                templateText = manager.allTemplates.find { it.name == name }?.text
            }
        }
        
        return JsonObject().apply {
            val content = com.google.gson.JsonArray()
            content.add(JsonObject().apply {
                addProperty("type", "text")
                if (templateText != null) {
                    addProperty("text", "Template Content for $name:\n$templateText")
                } else {
                    addProperty("text", "Template $name not found")
                }
            })
            add("content", content)
        }
    }

    fun handleListLiveTemplates(): JsonObject {
        val templatesInfo = mutableListOf<String>()
        ApplicationManager.getApplication().runReadAction {
            val settings = TemplateSettings.getInstance()
            // 获取所有模板并过滤掉默认的
            settings.templates.forEach { template ->
                // TemplateImpl 可能没有直接的 isDefault，但通常用户自定义的模板会有不同的存储方式
                // 或者我们可以通过 groupName 来判断，或者查看 TemplateSettings 是如何加载的
                // 另一种方式是维持之前的逻辑，从 xml 文件名获取，但获取更多信息
                val contexts = mutableListOf<String>()
                // 暂时不通过 settings.templates 过滤，因为不知道哪个是自定义的
            }

            // 维持从文件读取自定义模板列表的逻辑，但增加详细信息
            val templatesDir = File(PathManager.getConfigPath(), "templates")
            if (templatesDir.exists() && templatesDir.isDirectory) {
                templatesDir.listFiles()?.filter { it.isFile && it.extension == "xml" }?.forEach { file ->
                    val groupName = file.nameWithoutExtension
                    // 从 TemplateSettings 中找匹配这个组的模板
                    val templatesInGroup = TemplateSettings.getInstance().templates.filter { it.groupName == groupName }
                    templatesInGroup.forEach { template ->
                        // 获取上下文信息
                        val contextNames = mutableListOf<String>()
                        try {
                            val context = template.templateContext
                            // 这是一个简化的获取方式，可能需要根据具体平台版本调整
                            // 在某些版本中可以使用 context.getOwnContextTypes()
                        } catch (e: Exception) {
                            // 忽略获取上下文时的错误
                        }
                        
                        val info = "Group: ${template.groupName}, Abbreviation: ${template.key}, Description: ${template.description ?: "N/A"}"
                        templatesInfo.add(info)
                    }
                }
            }
        }
        
        return JsonObject().apply {
            val content = com.google.gson.JsonArray()
            content.add(JsonObject().apply {
                addProperty("type", "text")
                addProperty("text", if (templatesInfo.isEmpty()) "No user-defined live templates found." else "Available Live Templates:\n" + templatesInfo.joinToString("\n"))
            })
            add("content", content)
        }
    }

    fun handleGetLiveTemplate(name: String): JsonObject {
        var templateContent: String? = null
        ApplicationManager.getApplication().runReadAction {
            val templatesDir = java.io.File(com.intellij.openapi.application.PathManager.getConfigPath(), "templates")
            val templateFile = java.io.File(templatesDir, "$name.xml")
            if (templateFile.exists() && templateFile.isFile) {
                templateContent = templateFile.readText()
            }
        }
        
        return JsonObject().apply {
            val content = com.google.gson.JsonArray()
            content.add(JsonObject().apply {
                addProperty("type", "text")
                if (templateContent != null) {
                    addProperty("text", "Live Template Content for $name:\n$templateContent")
                } else {
                    addProperty("text", "Live Template $name not found")
                }
            })
            add("content", content)
        }
    }
}