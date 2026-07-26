package com.close.hook.ads.hook.util

import android.content.Context
import android.util.Log
import com.close.hook.ads.data.model.CustomHookInfo
import org.luckypray.dexkit.DexKitBridge

/**
 * Manager-process fallback for auto-detect: reads the target package's
 * APK paths via PackageManager and runs the same DexKit query directly
 * inside the AdClose UI process.
 *
 * Why this exists: on Xiaomi / OPPO / vivo ROMs the hook-side scan is
 * routinely killed before it can push results back, because the target
 * app is backgrounded as soon as the user returns to AdClose. That kill
 * doesn't happen here — we scan in our own process, so the result is
 * always delivered.
 *
 * Pixel-class devices keep using the hook-side path because it returns
 * faster (already-loaded DEX vs. parsed-from-APK); this scanner is only
 * triggered by [com.close.hook.ads.ui.viewmodel.CustomHookViewModel] as
 * a delayed fallback when the hook-side push doesn't arrive in time.
 */
object AppApkScanner {

    private const val TAG = "AppApkScanner"

    @Volatile
    private var nativeLoaded: Boolean = false

    fun scan(context: Context, packageName: String): List<CustomHookInfo> {
        if (!loadNativeIfNeeded()) {
            Log.w(TAG, "DexKit native unavailable")
            return emptyList()
        }
        val apkPaths = collectApkPaths(context, packageName)
        if (apkPaths.isEmpty()) {
            Log.w(TAG, "No APK paths found for $packageName")
            return emptyList()
        }
        val bridge: DexKitBridge = runCatching {
            DexKitBridge.create(apkPaths.joinToString(separator = ":"))
        }.onFailure {
            Log.e(TAG, "DexKitBridge.create failed: ${it.message}", it)
        }.getOrNull() ?: return emptyList()

        return try {
            AdSdkScanRules.findSdkHooks(bridge)
        } catch (t: Throwable) {
            Log.e(TAG, "findSdkHooks failed", t)
            emptyList()
        } finally {
            runCatching { bridge.close() }
        }
    }

    private fun collectApkPaths(context: Context, packageName: String): List<String> {
        return runCatching {
            val info = context.packageManager.getApplicationInfo(packageName, 0)
            buildList {
                add(info.sourceDir)
                info.splitSourceDirs?.forEach { add(it) }
            }
        }.getOrElse {
            Log.w(TAG, "ApplicationInfo for $packageName not available: ${it.message}")
            emptyList()
        }
    }

    @Synchronized
    private fun loadNativeIfNeeded(): Boolean {
        if (nativeLoaded) return true
        nativeLoaded = runCatching { System.loadLibrary("dexkit") }
            .onFailure { Log.e(TAG, "loadLibrary(dexkit) failed: ${it.message}", it) }
            .isSuccess
        return nativeLoaded
    }
}
