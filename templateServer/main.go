package main

import (
    "encoding/json"
    "github.com/gin-gonic/gin"
    "io/ioutil"
    "net/http"
    "os"
    "path/filepath"
    "strings"
)

// 支持的模板类型
var validTypes = map[string]bool{
    "live": true,
    "file": true,
}

type TemplateInfo struct {
    FileName    string `json:"fileName"`
    DisplayName string `json:"displayName"`
}

type TemplateMetadata struct {
    Templates map[string]map[string]string `json:"templates"` // type -> filename -> displayName
}

func loadMetadata() TemplateMetadata {
    data, err := ioutil.ReadFile("templates/metadata.json")
    if err != nil {
        return TemplateMetadata{Templates: make(map[string]map[string]string)}
    }

    var metadata TemplateMetadata
    if err := json.Unmarshal(data, &metadata); err != nil {
        return TemplateMetadata{Templates: make(map[string]map[string]string)}
    }

    return metadata
}

func saveMetadata(metadata TemplateMetadata) error {
    data, err := json.MarshalIndent(metadata, "", "  ")
    if err != nil {
        return err
    }
    return ioutil.WriteFile("templates/metadata.json", data, 0644)
}

func main() {
    // 创建模板目录
    os.MkdirAll("templates/live", os.ModePerm)
    os.MkdirAll("templates/file", os.ModePerm)
    
    r := gin.Default()
    
    // 允许跨域
    r.Use(func(c *gin.Context) {
        c.Header("Access-Control-Allow-Origin", "*")
        c.Header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        c.Header("Access-Control-Allow-Headers", "Content-Type")
        if c.Request.Method == "OPTIONS" {
            c.AbortWithStatus(204)
            return
        }
        c.Next()
    })

    // 列出模板文件
    r.GET("/api/templates/list", func(c *gin.Context) {
        templateType := c.Query("type") // 可选参数，筛选类型
        metadata := loadMetadata()

        result := make(map[string][]TemplateInfo)
        
        // 如果指定了类型，只列出该类型的模板
        if templateType != "" {
            if !validTypes[templateType] {
                c.JSON(http.StatusBadRequest, gin.H{"error": "Invalid template type"})
                return
            }
            if typeMetadata, ok := metadata.Templates[templateType]; ok {
                for fileName, displayName := range typeMetadata {
                    result[templateType] = append(result[templateType], TemplateInfo{
                        FileName: fileName,
                        DisplayName: displayName,
                    })
                }
            }
        } else {
            // 列出所有类型的模板
            for tType := range validTypes {
                if typeMetadata, ok := metadata.Templates[tType]; ok {
                    for fileName, displayName := range typeMetadata {
                        result[tType] = append(result[tType], TemplateInfo{
                            FileName: fileName,
                            DisplayName: displayName,
                        })
                    }
                }
            }
        }
        
        c.JSON(http.StatusOK, result)
    })

    // 下载模板
    r.GET("/api/templates/:type/:name", func(c *gin.Context) {
        templateType := c.Param("type")
        fileName := c.Param("name")

        if !validTypes[templateType] {
            c.JSON(http.StatusBadRequest, gin.H{"error": "Invalid template type"})
            return
        }

        filePath := filepath.Join("templates", templateType, fileName)
        if _, err := os.Stat(filePath); os.IsNotExist(err) {
            c.JSON(http.StatusNotFound, gin.H{"error": "Template not found"})
            return
        }

        c.File(filePath)
    })

    // 上传模板
    r.POST("/api/templates/upload/:type", func(c *gin.Context) {
        templateType := c.Param("type")
        displayName := c.PostForm("displayName")

        if !validTypes[templateType] {
            c.JSON(http.StatusBadRequest, gin.H{"error": "Invalid template type"})
            return
        }

        if displayName == "" {
            c.JSON(http.StatusBadRequest, gin.H{"error": "Display name is required"})
            return
        }

        file, err := c.FormFile("file")
        if err != nil {
            c.JSON(http.StatusBadRequest, gin.H{"error": "No file uploaded"})
            return
        }

        // 检查文件扩展名
        ext := strings.ToLower(filepath.Ext(file.Filename))
        if ext != ".zip" {
            c.JSON(http.StatusBadRequest, gin.H{"error": "Only .zip files are allowed"})
            return
        }

        // 生成唯一文件名
        fileName := strings.ReplaceAll(displayName, " ", "_") + "_" + 
                   strings.ReplaceAll(filepath.Base(file.Filename), " ", "_")
        targetPath := filepath.Join("templates", templateType, fileName)

        // 保存文件
        if err := c.SaveUploadedFile(file, targetPath); err != nil {
            c.JSON(http.StatusInternalServerError, gin.H{"error": "Failed to save file"})
            return
        }

        // 更新元数据
        metadata := loadMetadata()
        if metadata.Templates[templateType] == nil {
            metadata.Templates[templateType] = make(map[string]string)
        }
        metadata.Templates[templateType][fileName] = displayName
        if err := saveMetadata(metadata); err != nil {
            os.Remove(targetPath) // 如果元数据保存失败，删除已上传的文件
            c.JSON(http.StatusInternalServerError, gin.H{"error": "Failed to save metadata"})
            return
        }

        c.JSON(http.StatusOK, gin.H{
            "message": "Template uploaded successfully",
            "fileName": fileName,
            "displayName": displayName,
        })
    })

    r.Run(":8080")
}