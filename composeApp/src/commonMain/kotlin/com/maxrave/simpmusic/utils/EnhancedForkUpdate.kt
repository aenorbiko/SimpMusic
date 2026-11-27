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
internal const val ENHANCED_LAB_PACKAGE = "com.maxrave.simpmusic.dev.startradio"
private const val ENHANCED_LAB_TAG = "enhanced-lab-latest"
private const val RELEASE_API = "https://api.github.com/repos/aenorbiko/SimpMusic/releases/tags/"
private const val RELEASE_DOWNLOAD = "https://github.com/aenorbiko/SimpMusic/releases/download/"
internal fun isSharedEnhancedLab(channel: String, applicationId: String, revision: Long): Boolean =
    channel.isNotBlank() && channel != ENHANCED_STABLE_CHANNEL &&
        applicationId == ENHANCED_LAB_PACKAGE && revision > 0

// Keep release metadata in GitHub, not in the user-facing update dialog.
internal fun enhancedUserNotes(notes: String): String {
    val section = notes.substringAfter("What's new\n", "")
    return section.lineSequence().dropWhile { it.isBlank() }
        .takeWhile { it.startsWith("- ") }.joinToString("\n")
        .ifBlank { "See full version history." }
}

private const val ENHANCED_STABLE_API_URL =
    "https://api.github.com/repos/aenorbiko/SimpMusic/releases/tags/enhanced-latest"

internal data class EnhancedForkRelease(
    val buildCode: Int,
    val releaseTime: String?,
    val notes: String,
    val labRevision: Long = 0,
    val downloadUrl: String = ENHANCED_STABLE_APK_URL,
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
        if (release["tag_name"]?.jsonPrimitive?.content != "enhanced-latest") return@runCatching null
        if (release["draft"]?.jsonPrimitive?.content == "true") return@runCatching null

        val notes = release["body"]?.jsonPrimitive?.content ?: return@runCatching null
        // A GitHub release with no known build number is NOT safe to compare:
        // never compare upstream 2.5.0 against our Android build 65.
        val code = Regex("^Android build: ([0-9]+)\\s*$", RegexOption.MULTILINE)
            .find(notes)?.groupValues?.get(1)?.toIntOrNull() ?: return@runCatching null
        if (code <= 0) return@runCatching null

        val matchingAssets = release["assets"]?.jsonArray?.filter { item ->
            val a = item.jsonObject
            a["name"]?.jsonPrimitive?.content == "SimpMusic-Enhanced-arm64.apk" &&
                a["browser_download_url"]?.jsonPrimitive?.content == ENHANCED_STABLE_APK_URL &&
                (a["size"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L) > 1_000_000L
        } ?: return@runCatching null
        if (matchingAssets.size != 1) return@runCatching null

        EnhancedForkRelease(
            buildCode = code,
            releaseTime = matchingAssets.single().jsonObject["updated_at"]?.jsonPrimitive?.content,
            notes = notes,
        )
    }.getOrNull()

internal data class LabReleasePointer(
    val revision: Long,
    val buildCode: Int,
    val tag: String,
    val asset: String,
    val commit: String,
)

private fun releaseField(body: String, field: String): String? =
    body.lineSequence().filter { it.startsWith("$field: ") }.singleOrNull()
        ?.substringAfter(": ")?.trim()

internal fun parseLabPointer(response: String): LabReleasePointer? = runCatching {
    val release = Json.parseToJsonElement(response).jsonObject
    if (release["tag_name"]?.jsonPrimitive?.content != ENHANCED_LAB_TAG ||
        release["draft"]?.jsonPrimitive?.content != "false") return@runCatching null
    val body = release["body"]?.jsonPrimitive?.content ?: return@runCatching null
    if (releaseField(body, "Package") != ENHANCED_LAB_PACKAGE) return@runCatching null
    val revision = releaseField(body, "Lab revision")?.toLongOrNull() ?: return@runCatching null
    val code = releaseField(body, "Android build")?.toIntOrNull() ?: return@runCatching null
    val tag = releaseField(body, "Lab release") ?: return@runCatching null
    val asset = releaseField(body, "Lab asset") ?: return@runCatching null
    val commit = releaseField(body, "Source commit") ?: return@runCatching null
    if (revision <= 0 || code <= 0 || tag == ENHANCED_LAB_TAG ||
        !Regex("enhanced-[a-z0-9-]+-latest").matches(tag) ||
        !Regex("SimpMusic-[A-Za-z0-9-]+-r$revision-arm64\\.apk").matches(asset) ||
        !Regex("[0-9a-f]{40}").matches(commit)) return@runCatching null
    LabReleasePointer(revision, code, tag, asset, commit)
}.getOrNull()

internal fun parseLabAsset(response: String, pointer: LabReleasePointer): EnhancedForkRelease? = runCatching {
    val release = Json.parseToJsonElement(response).jsonObject
    if (release["tag_name"]?.jsonPrimitive?.content != pointer.tag ||
        release["draft"]?.jsonPrimitive?.content != "false") return@runCatching null
    val body = release["body"]?.jsonPrimitive?.content ?: return@runCatching null
    if (releaseField(body, "Package") != ENHANCED_LAB_PACKAGE ||
        releaseField(body, "Lab revision") != pointer.revision.toString() ||
        releaseField(body, "Android build") != pointer.buildCode.toString() ||
        releaseField(body, "Source commit") != pointer.commit) return@runCatching null
    val url = "$RELEASE_DOWNLOAD${pointer.tag}/${pointer.asset}"
    val asset = release["assets"]?.jsonArray?.singleOrNull { item ->
        val a = item.jsonObject
        a["name"]?.jsonPrimitive?.content == pointer.asset &&
            a["browser_download_url"]?.jsonPrimitive?.content == url &&
            (a["size"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L) > 1_000_000L
    }?.jsonObject ?: return@runCatching null
    EnhancedForkRelease(pointer.buildCode, asset["updated_at"]?.jsonPrimitive?.content,
        body, pointer.revision, url)
}.getOrNull()

internal fun isNewerLabBuild(installedCode: Int, installedRevision: Long, pointer: LabReleasePointer): Boolean =
    installedRevision > 0 && pointer.revision > installedRevision && pointer.buildCode >= installedCode

/** Network failures are ordinary offline operation; playback is unaffected. */
internal object EnhancedForkUpdateClient {
    private val client = HttpClient(CIO) {
        install(HttpTimeout) {
            connectTimeoutMillis = 8_000
            requestTimeoutMillis = 12_000
            socketTimeoutMillis = 8_000
        }
    }

    suspend fun check(): EnhancedForkRelease? = getRelease(ENHANCED_STABLE_API_URL)?.let(::parseEnhancedStableRelease)

    suspend fun checkLab(installedCode: Int, installedRevision: Long): EnhancedForkRelease? {
        val pointer = getRelease(RELEASE_API + ENHANCED_LAB_TAG)?.let(::parseLabPointer) ?: return null
        if (!isNewerLabBuild(installedCode, installedRevision, pointer)) return null
        return getRelease(RELEASE_API + pointer.tag)?.let { parseLabAsset(it, pointer) }
    }

    private suspend fun getRelease(url: String): String? =
        try {
            val response = client.get(url) {
                header(HttpHeaders.Accept, "application/vnd.github+json")
                header(HttpHeaders.UserAgent, "SimpMusic-Enhanced-Android-Updater")
            }
            if (response.status != HttpStatusCode.OK) null
            else response.bodyAsText()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
}
