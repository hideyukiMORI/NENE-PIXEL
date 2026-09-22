package io.github.hideyukimori.nenepixel

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import io.github.hideyukimori.nenepixel.presentation.compose.editor.AppVersionDisplay

/**
 * ARC-004 keeps app identity on the host platform boundary. The package lookup is injected so the
 * normalization of a missing package, an absent version name, and a failed lookup is decided on the
 * host JVM; presentation only receives the resulting display value.
 */
internal class AppVersionSource(
    private val lookup: () -> AppVersionMetadata,
) {
    fun read(): AppVersionDisplay =
        try {
            lookup().display()
        } catch (_: PackageManager.NameNotFoundException) {
            AppVersionDisplay.Unavailable
        } catch (_: RuntimeException) {
            AppVersionDisplay.Unavailable
        }

    companion object {
        /** The package lookup is a cheap synchronous in-process call, so startup reads it once. */
        fun create(context: Context): AppVersionSource = AppVersionSource { context.readPackageMetadata() }
    }
}

/**
 * The platform values before normalization. `versionName` is a platform null, and `versionCode` is
 * the long version code Android reports from API 28 only.
 */
internal data class AppVersionMetadata(
    val versionName: String?,
    val versionCode: Long?,
)

private fun AppVersionMetadata.display(): AppVersionDisplay =
    if (versionName.isNullOrBlank()) {
        AppVersionDisplay.Unavailable
    } else {
        AppVersionDisplay.Available(versionName, versionCode)
    }

private fun Context.readPackageMetadata(): AppVersionMetadata {
    val info = packageManager.getPackageInfo(packageName, 0)
    return AppVersionMetadata(info.versionName, info.longVersionCodeOrNull())
}

/**
 * `PackageInfo.versionCode` is deprecated and cannot be read under `allWarningsAsErrors`, so the
 * older supported releases report no numeric code rather than a suppressed or substituted one.
 */
private fun PackageInfo.longVersionCodeOrNull(): Long? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) longVersionCode else null
