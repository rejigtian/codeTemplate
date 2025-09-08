package com.wepie.coder.wpcoder.service

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
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
    data class State(
        var serverUrl: String = "http://localhost:8080",
        var apiKey: String = ""
    )

    data class TemplateInfo(
        val fileName: String,
        val displayName: String,
        val type: String
    )

    private var myState = State()
    private val client = OkHttpClient()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    fun getTemplates(type: String? = null): List<TemplateInfo> {
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
                               type = obj.get("type").asString
                           ))
                       }
                result
            }
    }

    fun downloadTemplate(type: String, fileName: String): File {
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
        myState.serverUrl = serverUrl
        myState.apiKey = apiKey
    }
}