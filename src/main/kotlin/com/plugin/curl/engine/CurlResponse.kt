package com.plugin.curl.engine

data class CurlResponse(
    val statusCode: Int = 0,
    val timeMillis: Long = 0,
    val headers: Map<String, String> = emptyMap(),
    val body: String = "",
    val error: String? = null,
    val command: String = "",
    val rawOutput: String = "",
    val isImage: Boolean = false,
    val imageBytes: ByteArray? = null
)
