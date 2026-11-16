package com.maxrave.simpmusic.utils

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Our release channel is intentionally separate from upstream SimpMusic.
 * GitHub currently marks enhanced-latest as a prerelease even though it is
 * OUR stable channel: validate the exact tag, owner, asset and Android build.
 *
 * The OS still checks package ID + signing certificate before allowing install.
 * We only offer the official signed APK through the browser; never install silently.
 */
internal const val ENHANCED_STABLE_PACKAGE = "com.maxrave.simpmusic.dev"
internal const val ENHANCED_STABLE_CHANNEL = "Enhanced Stable"
internal const val ENHANCED_STABLE_APK_URL =
    "https://github.com/aenorbiko/SimpMusic/releases/download/enhanced-latest/SimpMusic-Enhanced-arm64.apk"
private const val ENHANCED_STABLE_API_URL =
    "https://api.github.com/repos/aenorbiko/SimpMusic/releases/tags/enhanced-latest"

internal data class EnhancedForkRelease(
    val buildCode: Int,
    val releaseTime: String?,
    val notes: String,
)

internal fun isEnhancedStablePackage(
    channel: String,
    applicationId: String,
): Boolean = channel == ENHANCED_STABLE_CHANNEL && applicationId == ENHANCED_STABLE_PACKAGE

/** Only our exact stable APK can receive an update; never offer it to a Lab app. */
internal fun isNewerEnhancedBuild(
    installedCode: Int,
    tag: String,
): Boolean {
    val newest = Regex("^Enhanced build ([0-9]+)$").matchEntire(tag)?.groupValues?.get(1)?.toIntOrNull()
    return newest != null && newest > installedCode
}

internal fun parseEnhancedStableRelease(response: String): EnhancedForkRelease? =
    runCatching {
        val release = Json.parseToJsonElement(response).jsonObject
        if (release["tag_name"]?.jsonPrimitive?.content != "enhanced-latest") return null
        if (release["draft"]?.jsonPrimitive?.content == "true") return null

        val notes = release["body"]?.jsonPrimitive?.content ?: return null
        // A GitHub release with no known build number is NOT safe to compare:
        // never compare upstream 2.5.0 against our Android build 65.
        val code = Regex("^Android build: ([0-9]+)\\s*$", RegexOption.MULTILINE)
            .find(notes)?.groupValues?.get(1)?.toIntOrNull() ?: return null
        if (code <= 0) return null

        val matchingAssets = release["assets"]?.jsonArray?.filter { item ->
            val a = item.jsonObject
            a["name"]?.jsonPrimitive?.content == "SimpMusic-Enhanced-arm64.apk" &&
                a["browser_download_url"]?.jsonPrimitive?.content == ENHANCED_STABLE_APK_URL &&
                (a["size"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L) > 1_000_000L
        } ?: return null
        if (matchingAssets.size != 1) return null

        EnhancedForkRelease(
            buildCode = code,
            releaseTime = matchingAssets.single().jsonObject["updated_at"]?.jsonPrimitive?.content,
            notes = notes,
        )
    }.getOrNull()

/** Network failures are ordinary offline operation; playback is unaffected. */
internal object EnhancedForkUpdateClient {
    private val client = HttpClient(CIO) {
        install(HttpTimeout) {
            connectTimeoutMillis = 8_000
            requestTimeoutMillis = 12_000
            socketTimeoutMillis = 8_000
        }
    }

    suspend fun check(): EnhancedForkRelease? =
        try {
            val response = client.get(ENHANCED_STABLE_API_URL) {
                header(HttpHeaders.Accept, "application/vnd.github+json")
                header(HttpHeaders.UserAgent, "SimpMusic-Enhanced-Android-Updater")
            }
            if (response.status != HttpStatusCode.OK) null
            else parseEnhancedStableRelease(response.bodyAsText())
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
}
