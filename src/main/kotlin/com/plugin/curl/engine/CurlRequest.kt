package com.plugin.curl.engine

data class CurlRequest(
    val url: String,
    val method: String = "GET",
    val headers: Map<String, String> = emptyMap(),
    val formData: Map<String, String> = emptyMap(),
    val body: String? = null
)
