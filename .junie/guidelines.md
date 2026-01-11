# WP Template Coder 开发指南

本指南旨在帮助开发者快速了解 WP Template Coder 项目的结构、功能以及开发规范。

## 1. 项目概述

WP Template Coder 是一个 IntelliJ 平台插件，用于高效管理和跨团队共享 IDE 模板（包括文件模板 File Templates 和活动模板 Live Templates）。它支持将本地模板导出、导入，以及与远程模板服务器进行同步。

### 核心功能
- **本地管理**：快速访问和管理 IDE 模板。
- **导入/导出**：支持将模板导出为文件或从文件导入。
- **模板共享**：通过远程服务器上传、下载和共享模板。
- **权限控制**：基于 API Key 的访问控制（管理员、写作者、阅读者）。
- **版本控制**：服务器端支持 Git 记录模板变更。

## 2. 项目结构说明

### 2.1 插件端 (Kotlin/IntelliJ SDK)
`src/main/kotlin/com/wepie/coder/wpcoder/` 目录下：

- **`action/`**: 包含所有用户交互动作，如导入/导出模板、打开设置等。
- **`service/`**:
    - `TemplateServerService.kt`: 核心服务，负责与远程服务器通信（REST API）。
- **`mcp/`**:
    - `MCPServerService.kt`: 本地 MCP (Model Context Protocol) 服务，允许 AI 查询 IDE 模板。
- **`window/`**: UI 相关组件。
    - `TemplateToolWindowFactory.kt`: 插件右侧工具栏入口。
    - `TemplatePanel.kt`: 模板列表展示与操作面板。
    - `*Dialog.kt`: 各类交互弹窗（配置、上传、导出等）。
- **`src/main/resources/META-INF/plugin.xml`**: 插件配置文件，定义了 Action、Service 和扩展点。

### 2.2 服务端 (Go)
`templateServer/` 目录下包含了一个轻量级的 Go 后端服务：

- **`main.go`**: 程序入口和路由定义。
- **`auth.go`**: API Key 验证逻辑。
- **`git.go`**: 处理模板存储的 Git 版本控制。
- **`templates/`**: 存储实际的模板文件及元数据 (`metadata.json`)。
- **`Dockerfile` & `docker-compose.yml`**: 支持快速容器化部署。

## 3. 核心功能详情

### 3.1 本地 MCP 服务
插件集成了一个本地 MCP 服务器，默认端口为 12345（可在设置中更改）。AI（如 Cursor、Claude 等）可以通过该服务查询 IDE 中的 File Templates 和 Live Templates。
- **暴露的工具 (Tools)**:
  - `list_file_templates`: 列出所有用户定义的文件模板。
  - `get_file_template`: 获取指定文件模板的内容。
  - `list_live_templates`: 列出所有用户定义的实时代码模板。
  - `get_live_template`: 获取指定实时模板的内容。
- **配置**: 在插件工具窗口的 "Settings" 按钮中开启/关闭 MCP 服务。开启后，可以修改端口并复制专为 AI 工具（如 Cursor、Claude）生成的配置代码块。
- **工作原理**: MCP 客户端（如 Cursor）在与 AI 对话时，会扫描可用工具的 `description`。
  - AI 通过描述判断该工具是否能帮助回答用户问题。
  - 开发者通过在 `MCPServerService` 中提供详尽的工具描述（例如：“当需要保持项目代码风格一致时调用此工具”），可以引导 AI 更准确地使用模板功能。
  - 用户也可以在对话中通过明确指令触发，如：“使用项目模板创建一个新的 Service”。

## 4. 构建与运行

### 环境要求
- JDK 21
- Gradle
- Go (如需开发/部署服务端)

### 插件开发
1. 使用 IntelliJ IDEA 打开项目根目录。
2. 运行 Gradle 任务 `runIde` 以启动带有插件的 IDE 实例。
3. 构建插件包：运行 `./gradlew buildPlugin`。

### 服务端运行
```bash
cd templateServer
# 方式 1: 直接运行
go run main.go
# 方式 2: 使用 Docker
docker-compose up -d
```

## 5. 开发规范

- **代码风格**：遵循 Kotlin 官方代码风格指南。
- **资源国际化**：所有 UI 文本应通过 `WPCoderBundle.properties` 管理。
- **安全性**：不要在代码中硬编码敏感的 API Key。默认 Key 仅供公共只读访问。
- **插件兼容性**：在修改 `build.gradle.kts` 中的 `intellijPlatform` 配置时需谨慎，以确保对不同版本 IDE 的支持。
