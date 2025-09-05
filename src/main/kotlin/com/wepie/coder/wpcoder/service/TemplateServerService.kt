package com.wepie.coder.wpcoder.service

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.io.HttpRequests
import com.intellij.util.xmlb.XmlSerializerUtil
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.util.concurrent.TimeUnit

@Service(Service.Level.APP)
@State(
    name = "TemplateServerService",
    storages = [Storage("template-server.xml")]
)
class TemplateServerService : PersistentStateComponent<TemplateServerService.State> {
    private val LOG = Logger.getInstance(TemplateServerService::class.java)

    data class State(
        var serverUrl: String = "http://localhost:8080"
    )

    data class TemplateInfo(
        val fileName: String,
        val displayName: String,
        val type: String
    )

    private var myState = State()
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        XmlSerializerUtil.copyBean(state, myState)
    }

    fun getTemplates(type: String? = null): List<TemplateInfo> {
        val url = "${myState.serverUrl}/api/templates/list" + (type?.let { "?type=$it" } ?: "")
        return HttpRequests.request(url)
            .accept("application/json")
            .connect { request ->
                val responseText = request.readString()
                val response = JSONObject(responseText)
                val result = mutableListOf<TemplateInfo>()
                for (typeKey in response.keys()) {
                    val templates = response.getJSONArray(typeKey)
                    for (i in 0 until templates.length()) {
                        val template = templates.getJSONObject(i)
                        result.add(TemplateInfo(
                            fileName = template.getString("fileName"),
                            displayName = template.getString("displayName"),
                            type = typeKey
                        ))
                    }
                }
                result
            }
    }

    fun downloadTemplate(type: String, fileName: String): File {
        val tempDir = FileUtil.createTempDirectory("templates", "", true)
        val tempFile = File(tempDir, fileName)
        val url = "${myState.serverUrl}/api/templates/$type/$fileName"
        
        println("=== 开始下载模板 ===")
        println("下载地址: $url")
        println("保存路径: ${tempFile.absolutePath}")
        
        HttpRequests.request(url)
            .connect { request ->
                Files.copy(request.inputStream, tempFile.toPath())
            }
        
        println("下载完成: 文件存在=${tempFile.exists()}, 大小=${tempFile.length()}")
        
        if (!tempFile.exists() || tempFile.length() == 0L) {
            throw Exception("Failed to download template: File is empty or does not exist")
        }

        return tempFile
    }

    fun uploadTemplate(type: String, displayName: String, file: File) {
        val url = "${myState.serverUrl}/api/templates/upload/$type"
        
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
            .post(requestBody)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Unknown error"
                throw Exception("Failed to upload template: ${response.message} - $errorBody")
            }
        }
    }
}