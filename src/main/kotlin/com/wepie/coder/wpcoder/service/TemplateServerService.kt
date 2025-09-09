package com.wepie.coder.wpcoder.service

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.ui.Messages
import com.intellij.util.io.HttpRequests
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import com.google.gson.JsonParser
import com.google.gson.JsonObject
import java.io.File

@Service(Service.Level.APP)
@State(
    name = "TemplateServerService",
    storages = [Storage("template-server.xml")]
)
class TemplateServerService : PersistentStateComponent<TemplateServerService.State> {
    companion object {
        const val DEFAULT_SERVER_URL = "http://124.222.104.87:8080"
        const val DEFAULT_API_KEY = "4EE488BE0C1F4C2FA58A1040F1D8251D"
        const val GITHUB_REPO_URL = "https://github.com/rejigtian/codeTemplateServer"
    }

    data class State(
        var serverUrl: String = DEFAULT_SERVER_URL,
        var apiKey: String = DEFAULT_API_KEY,
        var hasShownDefaultWarning: Boolean = false  // 添加标记，记录是否显示过提示
    ) {
        override fun toString(): String {
            return "State(serverUrl='$serverUrl', apiKey='$apiKey', hasShownDefaultWarning=$hasShownDefaultWarning)"
        }
    }

    data class TemplateInfo(
        val fileName: String,
        val displayName: String,
        val type: String,
        val createTime: Long = 0
    )

    private var myState = State()
    private val client = OkHttpClient()

    override fun getState(): State {
        println("Getting state: $myState")
        return myState
    }

    override fun loadState(state: State) {
        println("Loading state: $state")
        myState = state
    }

    private fun checkAndShowDefaultWarning() {
        if (!myState.hasShownDefaultWarning && 
            myState.serverUrl == DEFAULT_SERVER_URL && 
            myState.apiKey == DEFAULT_API_KEY) {
            Messages.showWarningDialog(
                """当前使用的是默认开放服务器地址和只读密钥。
                |
                |如果需要自定义地址和完整权限，请访问：
                |$GITHUB_REPO_URL
                |按照说明部署自己的服务器。
                """.trimMargin(),
                "使用默认服务器配置"
            )
            myState.hasShownDefaultWarning = true
        }
    }

    fun getTemplates(type: String? = null): List<TemplateInfo> {
        checkAndShowDefaultWarning()
        
        val url = "${myState.serverUrl}/api/templates/list" + (type?.let { "?type=$it" } ?: "")
        return HttpRequests.request(url)
            .tuner {
                it.setRequestProperty("X-API-Key", myState.apiKey)
            }
            .accept("application/json")
            .connect { request ->
                val responseText = request.readString()
                val jsonArray = JsonParser.parseString(responseText).asJsonArray
                val result = mutableListOf<TemplateInfo>()
                for (element in jsonArray) {
                    val obj = element.asJsonObject
                    result.add(TemplateInfo(
                        fileName = obj.get("fileName").asString,
                        displayName = obj.get("displayName").asString,
                        type = obj.get("type").asString,
                        createTime = obj.get("createTime")?.asLong ?: 0
                    ))
                }
                result
            }
    }

    fun downloadTemplate(type: String, fileName: String): File {
        checkAndShowDefaultWarning()
        
        val tempDir = com.intellij.openapi.util.io.FileUtil.createTempDirectory("templates", "", true)
        val tempFile = File(tempDir, fileName)
        val url = "${myState.serverUrl}/api/templates/$type/$fileName"
        
        println("=== 开始下载模板 ===")
        println("下载地址: $url")
        println("保存路径: ${tempFile.absolutePath}")
        
        HttpRequests.request(url)
            .tuner {
                it.setRequestProperty("X-API-Key", myState.apiKey)
            }
            .connect { request ->
                java.nio.file.Files.copy(request.inputStream, tempFile.toPath())
            }
        
        println("下载完成: 文件存在=${tempFile.exists()}, 大小=${tempFile.length()}")
        return tempFile
    }

    fun uploadTemplate(type: String, displayName: String, file: File) {
        checkAndShowDefaultWarning()
        
        if (myState.serverUrl == DEFAULT_SERVER_URL && myState.apiKey == DEFAULT_API_KEY) {
            throw Exception("""
                |当前使用默认服务器配置，仅具有只读权限。
                |如需上传模板，请访问：$GITHUB_REPO_URL
                |按照说明部署自己的服务器。
            """.trimMargin())
        }
        
        val url = "${myState.serverUrl}/api/templates/upload/$type"
        println("=== 开始上传模板 ===")
        println("上传地址: $url")
        println("模板类型: $type")
        println("显示名称: $displayName")
        println("文件名称: ${file.name}")
        
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("displayName", displayName)
            .addFormDataPart(
                "file",
                file.name,
                file.asRequestBody("application/zip".toMediaTypeOrNull())
            )
            .build()

        val request = Request.Builder()
            .url(url)
            .header("X-API-Key", myState.apiKey)
            .post(requestBody)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Unknown error"
                println("上传失败: ${response.code} - ${response.message} - $errorBody")
                throw Exception(when (response.code) {
                    401 -> "Invalid API key"
                    403 -> "Permission denied: Your API key doesn't have sufficient permissions"
                    else -> "Failed to upload template: ${response.message} - $errorBody"
                })
            }
            println("上传成功: ${response.code} - ${response.message}")
        }
    }

    fun deleteTemplate(type: String, fileName: String) {
        checkAndShowDefaultWarning()
        
        if (myState.serverUrl == DEFAULT_SERVER_URL && myState.apiKey == DEFAULT_API_KEY) {
            throw Exception("""
                |当前使用默认服务器配置，仅具有只读权限。
                |如需删除模板，请访问：$GITHUB_REPO_URL
                |按照说明部署自己的服务器。
            """.trimMargin())
        }
        
        val url = "${myState.serverUrl}/api/templates/$type/$fileName"
        println("=== 开始删除模板 ===")
        println("删除地址: $url")
        println("模板类型: $type")
        println("文件名称: $fileName")

        val request = Request.Builder()
            .url(url)
            .header("X-API-Key", myState.apiKey)
            .delete()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Unknown error"
                println("删除失败: ${response.code} - ${response.message} - $errorBody")
                throw Exception(when (response.code) {
                    401 -> "Invalid API key"
                    403 -> "Permission denied: Only administrators can delete templates"
                    404 -> "Template not found"
                    else -> "Failed to delete template: ${response.message} - $errorBody"
                })
            }
            println("删除成功: ${response.code} - ${response.message}")
        }
    }

    fun updateServerConfig(serverUrl: String, apiKey: String) {
        println("Updating server config - Current state: url=${myState.serverUrl}, key=${myState.apiKey}")
        println("New values: url=$serverUrl, key=$apiKey")
        
        // 创建新的状态对象
        val newState = State(
            serverUrl = serverUrl,
            apiKey = apiKey,
            hasShownDefaultWarning = !(serverUrl != DEFAULT_SERVER_URL || apiKey != DEFAULT_API_KEY)
        )
        
        // 使用 loadState 更新状态
        loadState(newState)
        
        // 强制保存状态到磁盘
        com.intellij.openapi.application.ApplicationManager.getApplication().saveSettings()
        
        println("Updated state: url=${myState.serverUrl}, key=${myState.apiKey}")
    }
}