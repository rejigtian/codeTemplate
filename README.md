# Template Manager

Template Manager 是一个 Android Studio 插件，用于管理和同步代码模板。它提供了以下功能：

## 功能特点

1. 模板管理
   - 文件模板管理
   - 代码片段模板管理
   - 模板编辑
   - 模板导入/导出

2. IDE 集成
   - 与 Android Studio 模板系统集成
   - 自动同步到 IDE 模板库

## 安装

1. 在 Android Studio 中打开 Settings/Preferences
2. 选择 Plugins > Install Plugin from Disk
3. 选择下载的插件文件 (.zip)
4. 重启 Android Studio

## 使用方法

### 模板管理

1. 打开模板设置
   - 从菜单栏选择 Tools > Template Settings
   - 或使用快捷键 (待定)

2. 导入模板
   - 从菜单栏选择 Tools > Import Templates
   - 选择要导入的模板文件
   - 支持的格式：
     - 单个模板文件 (.java, .kt, .xml)
     - 模板包 (.zip)
     - 模板配置文件 (.json)

3. 导出模板
   - 从菜单栏选择 Tools > Export Templates
   - 选择要导出的模板
   - 选择导出位置和格式

### 模板类型

1. 文件模板
   - Activity
   - Fragment
   - Layout XML
   - 自定义 View
   - 数据类
   - 接口类
   - 工具类

2. 代码片段模板
   - 常用方法
   - 初始化代码
   - 网络请求
   - 数据库操作
   - 权限处理

## 开发

### 环境要求

- Android Studio 2025.1.1 或更高版本
- JDK 21 或更高版本
- Kotlin 2.1.0 或更高版本

### 构建

```bash
./gradlew clean build
```

### 运行

```bash
./gradlew runIde
```

## 贡献

欢迎提交 Pull Request 或提出 Issue。

## 许可证

本项目采用 MIT 许可证。详见 [LICENSE](LICENSE) 文件。
