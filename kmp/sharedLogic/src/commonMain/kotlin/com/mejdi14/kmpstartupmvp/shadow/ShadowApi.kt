package com.mejdi14.kmpstartupmvp.shadow

import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * Client for the Shadow Studio engine running on the Mac (`app.py`). The engine only answers on
 * `127.0.0.1` / `localhost`, so this reaches it from the iOS Simulator directly and from Android
 * through `adb reverse tcp:8765 tcp:8765`.
 */
class ShadowApi(private val baseUrl: String = DEFAULT_URL) {
    private val client = HttpClient { expectSuccess = false }
    private val json = Json { ignoreUnknownKeys = true }
    private var token = ""

    @Throws(Throwable::class)
    suspend fun connect() {
        val html = call { client.get("$baseUrl/") }.bodyAsText()
        token = Regex("name=\"shadow-token\" content=\"([^\"]+)\"").find(html)?.groupValues?.get(1)
            ?: throw ShadowError(OFFLINE)
    }

    internal suspend fun library(): List<LibraryItem> = get(ListSerializer(LibraryItem.serializer()), "/api/library")
    internal suspend fun lesson(id: String): Lesson = get(Lesson.serializer(), "/api/lesson/$id")
    internal suspend fun job(id: String): Job = get(Job.serializer(), "/api/jobs/$id")
    internal suspend fun voice(id: String, index: Int): VoiceData = get(VoiceData.serializer(), "/api/voice/$id/$index")
    internal suspend fun bytes(path: String): ByteArray = send(HttpMethod.Get, path).bodyAsBytes()

    internal suspend fun importLink(url: String): String = decode(JobStart.serializer(), send(HttpMethod.Post, "/api/import") {
        contentType(ContentType.Application.Json)
        setBody(buildJsonObject { put("url", JsonPrimitive(url)) }.toString())
    }).jobId

    internal suspend fun upload(name: String, bytes: ByteArray): String = decode(JobStart.serializer(), authorized {
        call {
            client.submitFormWithBinaryData("$baseUrl/api/upload", formData {
                append("file", bytes, Headers.build {
                    append(HttpHeaders.ContentType, "application/octet-stream")
                    append(HttpHeaders.ContentDisposition, "filename=\"${name.replace("\"", "")}\"")
                })
            }) { header(TOKEN_HEADER, token) }
        }
    }).jobId

    internal suspend fun record(id: String, index: Int, speed: Double, bytes: ByteArray, type: String): String =
        decode(JobStart.serializer(), send(HttpMethod.Post, "/api/record/$id/$index?speed=$speed") {
            contentType(ContentType.parse(type))
            setBody(bytes)
        }).jobId

    internal suspend fun editPhrase(id: String, index: Int, text: String, start: Double, end: Double): Lesson =
        decode(Lesson.serializer(), send(HttpMethod.Patch, "/api/phrase/$id/$index") {
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject {
                put("text", JsonPrimitive(text)); put("start", JsonPrimitive(start)); put("end", JsonPrimitive(end))
            }.toString())
        })

    internal suspend fun delete(id: String) { send(HttpMethod.Delete, "/api/lesson/$id") }

    private suspend fun <T> get(serializer: KSerializer<T>, path: String): T = decode(serializer, send(HttpMethod.Get, path))

    private suspend fun <T> decode(serializer: KSerializer<T>, response: HttpResponse): T =
        try { json.decodeFromString(serializer, response.bodyAsText()) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { throw ShadowError("Shadow Studio couldn't read the reply from your Mac. Restart it and try again.") }

    private suspend fun send(method: HttpMethod, path: String, body: HttpRequestBuilder.() -> Unit = {}): HttpResponse =
        authorized {
            call {
                client.request("$baseUrl$path") {
                    this.method = method
                    header(TOKEN_HEADER, token)
                    body()
                }
            }
        }

    /** The engine makes a new token each time it starts; fetch it again once if it was restarted. */
    private suspend fun authorized(request: suspend () -> HttpResponse): HttpResponse =
        try { request() } catch (e: StaleToken) { connect(); request() }

    private suspend fun call(request: suspend () -> HttpResponse): HttpResponse {
        val response = try { request() }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { throw ShadowError(OFFLINE) }
        if (response.status.value in 200..299) return response
        val detail = try { json.decodeFromString(Detail.serializer(), response.bodyAsText()).detail } catch (e: Exception) { null }
        if (response.status.value == 403 && detail == "Refresh the app and try again." && token.isNotEmpty()) throw StaleToken()
        throw ShadowError(detail ?: "Something went wrong. Please try again.")
    }

    private class StaleToken : Exception()

    companion object {
        const val DEFAULT_URL = "http://127.0.0.1:8765"
        private const val TOKEN_HEADER = "X-Shadow-Token"
        const val OFFLINE = "Can't reach Shadow Studio on your Mac. Start it there, then try again."
    }
}
