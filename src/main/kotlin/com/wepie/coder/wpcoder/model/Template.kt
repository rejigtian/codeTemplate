package com.wepie.coder.wpcoder.model

enum class TemplateType {
    FILE,
    CODE_SNIPPET,
    LIVE_TEMPLATE
}

data class Template(
    val name: String,
    val type: TemplateType,
    val content: String,
    val extension: String
) {
    override fun toString(): String {
        return "$name|$type|$content|$extension"
    }

    companion object {
        fun fromString(str: String): Template {
            val parts = str.split("|")
            return Template(
                name = parts[0],
                type = TemplateType.valueOf(parts[1]),
                content = parts[2],
                extension = parts[3]
            )
        }
    }
}