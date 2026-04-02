package com.wepie.coder.wpcoder.mcp

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonSyntaxException
import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.ProjectManager
import com.wepie.coder.wpcoder.service.TemplateServerService
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import org.w3c.dom.Element
import java.io.StringWriter

@Service(Service.Level.APP)
class MCPServerService {
    private data class RequestFrame(
        val text: String,
        val useContentLength: Boolean
    )

    private data class LiveTemplateInfo(
        val id: String,
        val groupName: String,
        val templateName: String,
        val description: String,
        val file: File
    )

    private val executor = Executors.newCachedThreadPool()
    private val clientSockets = ConcurrentHashMap.newKeySet<Socket>()
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
        clientSockets.forEach {
            try {
                it.close()
            } catch (_: Exception) {
            }
        }
        clientSockets.clear()
        serverSocket?.close()
        serverSocket = null
        println("MCP Server stopped")
    }

    private fun handleClient(socket: Socket) {
        executor.execute {
            clientSockets.add(socket)
            socket.use { s ->
                try {
                    val input = BufferedInputStream(s.getInputStream())
                    
                    while (running && !s.isClosed) {
                        var useContentLength = false
                        var requestId: JsonElement = JsonNull.INSTANCE
                        try {
                            val frame = readRequestText(input) ?: break
                            useContentLength = frame.useContentLength

                            val payload = frame.text.trim()
                            if (payload.isEmpty()) continue

                            val request = try {
                                gson.fromJson(payload, JsonObject::class.java)
                            } catch (e: JsonSyntaxException) {
                                throw IllegalArgumentException("Invalid JSON payload", e)
                            }
                            requestId = request.get("id") ?: JsonNull.INSTANCE
                            val response = processRequest(request)
                            // 通知请求不返回 ID，也不需要响应
                            if (request.has("id")) {
                                writeResponse(
                                    output = s.getOutputStream(),
                                    useContentLength = useContentLength,
                                    responseText = gson.toJson(response)
                                )
                            }
                        } catch (e: SocketException) {
                            println("[DEBUG_LOG] MCP client disconnected: ${e.message}")
                            break
                        } catch (e: Exception) {
                            println("[DEBUG_LOG] Error handling MCP request: ${e.message}")
                            val errorResponse = JsonObject().apply {
                                addProperty("jsonrpc", "2.0")
                                add("id", requestId)
                                val error = JsonObject().apply {
                                    addProperty("code", errorCodeFor(e))
                                    addProperty("message", e.message)
                                }
                                add("error", error)
                            }
                            try {
                                writeResponse(
                                    output = s.getOutputStream(),
                                    useContentLength = useContentLength,
                                    responseText = gson.toJson(errorResponse)
                                )
                            } catch (writeError: Exception) {
                                println("[DEBUG_LOG] Failed writing MCP error response: ${writeError.message}")
                                break
                            }
                        }
                    }
                } finally {
                    clientSockets.remove(s)
                }
            }
        }
    }

    private fun readRequestText(input: BufferedInputStream): RequestFrame? {
        val firstLineBytes = readAsciiLine(input) ?: return null
        val firstLine = String(firstLineBytes, StandardCharsets.UTF_8)

        return if (looksLikeHeaderLine(firstLine)) {
            val headers = linkedMapOf<String, String>()
            parseHeaderLine(firstLine)?.let { (key, value) ->
                headers[key] = value
            }

            while (true) {
                val headerLineBytes = readAsciiLine(input) ?: return null
                val headerLine = String(headerLineBytes, StandardCharsets.UTF_8)
                if (headerLine.isBlank()) break

                parseHeaderLine(headerLine)?.let { (key, value) ->
                    headers[key] = value
                }
            }

            val contentLength = headers["content-length"]?.toIntOrNull()
                ?: throw IllegalArgumentException("Missing Content-Length header")
            RequestFrame(
                text = readUtf8Body(input, contentLength),
                useContentLength = true
            )
        } else {
            RequestFrame(text = firstLine, useContentLength = false)
        }
    }

    private fun writeResponse(
        output: OutputStream,
        useContentLength: Boolean,
        responseText: String
    ) {
        if (useContentLength) {
            writeContentLengthResponse(output, responseText)
        } else {
            output.write((responseText + "\n").toByteArray(StandardCharsets.UTF_8))
            output.flush()
        }
    }

    private fun writeContentLengthResponse(output: OutputStream, responseText: String) {
        val payload = responseText.toByteArray(StandardCharsets.UTF_8)
        val header = "Content-Length: ${payload.size}\r\n\r\n".toByteArray(StandardCharsets.UTF_8)
        output.write(header)
        output.write(payload)
        output.flush()
    }

    private fun readUtf8Body(input: InputStream, contentLength: Int): String {
        val body = ByteArray(contentLength)
        var offset = 0
        while (offset < contentLength) {
            val read = input.read(body, offset, contentLength - offset)
            if (read == -1) {
                throw IllegalStateException("Unexpected EOF while reading request body")
            }
            offset += read
        }
        return String(body, StandardCharsets.UTF_8)
    }

    private fun readAsciiLine(input: InputStream): ByteArray? {
        val buffer = ArrayList<Byte>()
        while (true) {
            val next = input.read()
            if (next == -1) {
                return if (buffer.isEmpty()) null else buffer.toByteArray()
            }
            if (next == '\n'.code) {
                if (buffer.isNotEmpty() && buffer.last() == '\r'.code.toByte()) {
                    buffer.removeAt(buffer.lastIndex)
                }
                return buffer.toByteArray()
            }
            buffer.add(next.toByte())
        }
    }

    private fun looksLikeHeaderLine(line: String): Boolean {
        val trimmed = line.trimStart()
        return trimmed.contains(':') && !trimmed.startsWith("{") && !trimmed.startsWith("[")
    }

    private fun parseHeaderLine(line: String): Pair<String, String>? {
        val colonIndex = line.indexOf(':')
        if (colonIndex <= 0) return null
        val key = line.substring(0, colonIndex).trim().lowercase()
        val value = line.substring(colonIndex + 1).trim()
        return key to value
    }

    private fun errorCodeFor(error: Exception): Int {
        val message = error.message.orEmpty()
        return when {
            message.contains("Invalid JSON payload") -> -32700
            message.contains("Missing Content-Length") -> -32600
            message.contains("Unexpected EOF while reading request body") -> -32600
            message.contains("Missing params") -> -32602
            message.contains("Missing tool name") -> -32602
            message.contains("Tool not found") -> -32601
            else -> -32603
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
                    ?: throw IllegalArgumentException("Missing params for tools/call")
                val name = params.get("name")?.asString
                    ?: throw IllegalArgumentException("Missing tool name for tools/call")
                val toolParams = params.getAsJsonObject("arguments") ?: JsonObject()
                
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
            val content = JsonArray()
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
            val content = JsonArray()
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
        val templatesInfo = loadLiveTemplateInfos()
        
        return JsonObject().apply {
            val content = JsonArray()
            content.add(JsonObject().apply {
                addProperty("type", "text")
                addProperty(
                    "text",
                    if (templatesInfo.isEmpty()) {
                        "No user-defined live templates found."
                    } else {
                        "Available Live Templates:\n" + templatesInfo.joinToString("\n") {
                            "Id: ${it.id}, Group: ${it.groupName}, Abbreviation: ${it.templateName}, Description: ${it.description}"
                        }
                    }
                )
            })
            add("content", content)
        }
    }

    fun handleGetLiveTemplate(name: String): JsonObject {
        val templates = loadLiveTemplateInfos()
        val matchedTemplates = when {
            name.contains(':') -> templates.filter { it.id == name }
            else -> templates.filter { it.templateName == name || it.groupName == name }
        }

        val responseText = when {
            matchedTemplates.isEmpty() -> "Live Template $name not found"
            matchedTemplates.size > 1 && !name.contains(':') -> {
                "Multiple live templates match '$name'. Use one of these ids:\n" +
                    matchedTemplates.joinToString("\n") { it.id }
            }
            else -> {
                val selected = matchedTemplates.first()
                val templateXml = extractTemplateXml(selected.file, selected.groupName, selected.templateName)
                    ?: return JsonObject().apply {
                        val content = JsonArray()
                        content.add(JsonObject().apply {
                            addProperty("type", "text")
                            addProperty("text", "Live Template ${selected.id} not found in ${selected.file.name}")
                        })
                        add("content", content)
                    }

                "Live Template Content for ${selected.id}:\n$templateXml"
            }
        }
        
        return JsonObject().apply {
            val content = JsonArray()
            content.add(JsonObject().apply {
                addProperty("type", "text")
                addProperty("text", responseText)
            })
            add("content", content)
        }
    }

    private fun loadLiveTemplateInfos(): List<LiveTemplateInfo> {
        val templatesDir = File(PathManager.getConfigPath(), "templates")
        if (!templatesDir.exists() || !templatesDir.isDirectory) return emptyList()

        return templatesDir.listFiles()
            ?.asSequence()
            ?.filter { it.isFile && it.extension == "xml" }
            ?.flatMap { file -> parseLiveTemplates(file).asSequence() }
            ?.sortedWith(compareBy({ it.groupName }, { it.templateName }))
            ?.toList()
            ?: emptyList()
    }

    private fun parseLiveTemplates(file: File): List<LiveTemplateInfo> {
        return try {
            val document = newXmlDocumentBuilder().parse(file)
            val root = document.documentElement ?: return emptyList()
            val groupName = root.getAttribute("group").ifBlank { file.nameWithoutExtension }
            val templates = root.getElementsByTagName("template")
            buildList {
                for (i in 0 until templates.length) {
                    val element = templates.item(i) as? Element ?: continue
                    val templateName = element.getAttribute("name").trim()
                    if (templateName.isEmpty()) continue
                    add(
                        LiveTemplateInfo(
                            id = "$groupName:$templateName",
                            groupName = groupName,
                            templateName = templateName,
                            description = element.getAttribute("description").ifBlank { "N/A" },
                            file = file
                        )
                    )
                }
            }
        } catch (e: Exception) {
            println("[DEBUG_LOG] Failed to parse live template file ${file.name}: ${e.message}")
            emptyList()
        }
    }

    private fun extractTemplateXml(file: File, groupName: String, templateName: String): String? {
        return try {
            val document = newXmlDocumentBuilder().parse(file)
            val templates = document.documentElement?.getElementsByTagName("template") ?: return null
            for (i in 0 until templates.length) {
                val element = templates.item(i) as? Element ?: continue
                if (element.getAttribute("name") == templateName) {
                    val templateXml = nodeToXml(element)
                    return "<templateSet group=\"$groupName\">\n$templateXml\n</templateSet>"
                }
            }
            null
        } catch (e: Exception) {
            println("[DEBUG_LOG] Failed to extract live template $groupName:$templateName from ${file.name}: ${e.message}")
            null
        }
    }

    private fun newXmlDocumentBuilder() =
        DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            isIgnoringComments = true
            isCoalescing = true
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        }.newDocumentBuilder()

    private fun nodeToXml(element: Element): String {
        val writer = StringWriter()
        val transformer = TransformerFactory.newInstance().newTransformer().apply {
            setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes")
            setOutputProperty(OutputKeys.INDENT, "yes")
        }
        transformer.transform(DOMSource(element), StreamResult(writer))
        return writer.toString().trim()
    }
}
