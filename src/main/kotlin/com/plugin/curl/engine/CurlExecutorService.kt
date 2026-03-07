package com.plugin.curl.engine

import com.intellij.openapi.components.Service
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.system.measureTimeMillis

@Service(Service.Level.PROJECT)
class CurlExecutorService {

    private val curlExecutablePath: String
        get() = com.plugin.curl.settings.CurlSettingsState.getInstance().curlExecutablePath

    fun execute(request: CurlRequest): CurlResponse {
        val tempDir = File(System.getProperty("java.io.tmpdir"))
        val requestPrefix = "curl_orch_${System.currentTimeMillis()}"
        val headerFile = File.createTempFile("${requestPrefix}_headers", ".txt", tempDir)
        val bodyFile = File.createTempFile("${requestPrefix}_body", ".tmp", tempDir)

        val command = buildCommand(request, headerFile.absolutePath, bodyFile.absolutePath)
        val commandString = command.joinToString(" ")
        
        var stdErr = ""
        var exitCode = -1

        val timeTaken = measureTimeMillis {
            try {
                val processBuilder = ProcessBuilder(command)
                val process = processBuilder.start()

                // Read error output to capture verbose flags
                stdErr = process.errorStream.bufferedReader().use { it.readText() }

                val finished = process.waitFor(30, TimeUnit.SECONDS)
                if (!finished) {
                    process.destroyForcibly()
                    headerFile.delete()
                    bodyFile.delete()
                    return CurlResponse(error = "Request timed out after 30 seconds", command = commandString)
                }
                exitCode = process.exitValue()
            } catch (e: Exception) {
                headerFile.delete()
                bodyFile.delete()
                return CurlResponse(error = "Execution failed: ${e.message}\nCommand: $commandString", command = commandString)
            }
        }

        if (exitCode != 0) {
            headerFile.delete()
            bodyFile.delete()
            return CurlResponse(
                 error = "Curl returned exit code $exitCode\nError Output:\n$stdErr",
                 timeMillis = timeTaken,
                 command = commandString,
                 rawOutput = stdErr
            )
        }

        val rawHeaders = if (headerFile.exists()) headerFile.readText() else ""
        headerFile.delete() // cleanup headers file

        val response = parseResponse(rawHeaders, bodyFile, commandString, timeTaken)
        return response
    }

    private fun buildCommand(request: CurlRequest, headerFilePath: String, bodyFilePath: String): List<String> {
        val cmd = mutableListOf(curlExecutablePath)

        // -s: Silent mode, -v: Verbose (writes to stderr), -o: Output file, -D: Header dump file
        cmd.add("-v")
        cmd.add("-s")
        cmd.add("-o")
        cmd.add(bodyFilePath)
        cmd.add("-D")
        cmd.add(headerFilePath)
        
        // Method
        cmd.add("-X")
        cmd.add(request.method)

        // Headers
        request.headers.forEach { (key, value) ->
            cmd.add("-H")
            cmd.add("$key: $value")
        }

        // Body
        if (!request.body.isNullOrBlank()) {
            cmd.add("-d")
            cmd.add(request.body)
        }

        // Form Data
        request.formData.forEach { (key, value) ->
            cmd.add("-F")
            cmd.add("$key=$value")
        }

        // URL
        cmd.add(request.url)

        return cmd
    }

    private fun parseResponse(rawHeaders: String, bodyFile: File, commandString: String, timeTaken: Long): CurlResponse {
        if (rawHeaders.isBlank()) {
            bodyFile.delete()
            return CurlResponse(error = "Empty response headers from curl", timeMillis = timeTaken, command = commandString)
        }

        val headerLines = rawHeaders.split("\r\n").filter { it.isNotBlank() }
        if (headerLines.isEmpty()) {
            bodyFile.delete()
            return CurlResponse(error = "Failed to parse headers", timeMillis = timeTaken, command = commandString)
        }

        // Take the last HTTP/xx status line in case of redirects like 301 -> 200
        val statusLines = headerLines.filter { it.startsWith("HTTP/") }
        val finalStatusLine = statusLines.lastOrNull() ?: headerLines[0]
        val statusCodeMatch = Regex("""HTTP/[\d.]+ (\d+)""").find(finalStatusLine)
        val statusCode = statusCodeMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0

        val headersMap = mutableMapOf<String, String>()
        
        // Find the index of the final status line
        val finalStatusIndex = headerLines.lastIndexOf(finalStatusLine)
        
        // Parse headers only for the final response
        for (i in finalStatusIndex + 1 until headerLines.size) {
            val line = headerLines[i]
            val colonIndex = line.indexOf(':')
            if (colonIndex > 0) {
                val key = line.substring(0, colonIndex).trim()
                val value = line.substring(colonIndex + 1).trim()
                headersMap[key] = value
            }
        }

        val contentType = headersMap.entries.firstOrNull { it.key.equals("Content-Type", ignoreCase = true) }?.value ?: ""
        val isImage = contentType.startsWith("image/", ignoreCase = true)

        var parsedBody = ""
        var imageBytes: ByteArray? = null

        if (bodyFile.exists()) {
            if (isImage) {
                imageBytes = bodyFile.readBytes()
                parsedBody = "<Binary Image Data: ${imageBytes.size} bytes>"
            } else {
                parsedBody = bodyFile.readText()
            }
            bodyFile.delete() // Cleanup temporary body file
        }

        return CurlResponse(
            statusCode = statusCode,
            headers = headersMap,
            body = parsedBody,
            timeMillis = timeTaken,
            command = commandString,
            rawOutput = "${rawHeaders}\n${parsedBody}",
            isImage = isImage,
            imageBytes = imageBytes
        )
    }
}
