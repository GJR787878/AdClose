package com.close.hook.ads.hook.util

import com.close.hook.ads.data.model.CustomHookInfo
import com.close.hook.ads.data.model.HookMethodType
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.enums.StringMatchType
import org.luckypray.dexkit.query.matchers.base.StringMatcher
import org.luckypray.dexkit.result.MethodData
import java.lang.reflect.Modifier

/**
 * Pure logic for "auto-detect ad SDK methods" — the SDK package list,
 * the DexKit queries, the validity filter and the MethodData -> CustomHookInfo
 * mapping. Shared between the hook-side scan (AutoHookAds, runs inside the
 * target app's process) and the manager-side fallback scan
 * (AppApkScanner, runs in the AdClose UI process).
 *
 * Has no Android / Xposed dependencies so it can run anywhere a
 * DexKitBridge is available.
 */
object AdSdkScanRules {

    val adSdkPackages: List<String> = listOf(
        "com.sjm.sjmsdk",
        "com.ap.android",
        "com.youxiao.ssp",
        "com.cat.sdk",
        "com.superad.ad",
        "com.bytedance.pangle",
        "com.bytedance.sdk.openadsdk",
        "com.bytedance.android.openliveplugin",
        "com.ss.android.ad",
        "com.ss.android.downloadlib",
        "com.kwad",
        "com.qq.e",
        "com.baidu.mobads",
        "com.sigmob",
        "com.sigmob.sdk",
        "com.czhj",
        "cn.admobiletop",
        "com.inmobi.sdk",
        "com.tradplus.ads",
        "com.jd.ad.sdk",
        "com.beizi.fusion",
        "com.meishu.sdk",
        "com.link.sdk",
        "com.xwuad.sdk",
        "com.qumeng",
        "com.huawei.hms.ads",
        "com.huawei.openalliance.ad",
        "com.mbridge.msdk",
        "com.windmill.sdk",
        "com.alimm.tanx",
        "com.umeng",
        "com.anythink",
        "com.miui.zeus.mimo.sdk",
        "cn.xiaochuankeji",
        "com.tencent.bugly",
        "com.tencent.klevin.ads",
        "com.tencent.qqmini.ad",
        "com.baichuan",
        "com.vungle.warren",
        "com.applovin.sdk",
        "com.unity3d.ads",
        "com.unity3d.services",
        "com.google.ads",
        "com.google.unity.ads",
        "com.google.android.ads",
        "com.google.android.gms.ads",
        "com.google.android.gms.admob",
        "com.facebook.ads",
        "com.appsflyer"
    )

    private val excludedClasses = setOf(
        "com.baidu.mobads.sdk.api.BDAdConfig\$Builder"
    )

    /**
     * Run the standard "init / start / getContext" SDK method query, filter
     * and convert into a list of [CustomHookInfo] entries ready to display
     * in the auto-detect dialog. Caller owns the [bridge] lifecycle.
     */
    fun findSdkHooks(bridge: DexKitBridge): List<CustomHookInfo> {
        val packageSet = adSdkPackages.toSet()
        val initMethods = bridge.findMethod {
            searchPackages(packageSet)
            matcher { name(StringMatcher("init", StringMatchType.Contains, true)) }
        }
        val startMethods = bridge.findMethod {
            searchPackages(packageSet)
            matcher { name(StringMatcher("start", StringMatchType.Equals)) }
        }
        val getContextMethods = bridge.findMethod {
            searchPackages(packageSet)
            matcher { name(StringMatcher("getContext", StringMatchType.Equals)) }
        }
        return (initMethods + startMethods + getContextMethods)
            .asSequence()
            .filter { isValidSdkMethod(it) }
            .mapNotNull { createCustomHookInfo(it) }
            .toList()
    }

    fun isValidSdkMethod(methodData: MethodData): Boolean {
        if (methodData.className in excludedClasses) return false

        val isAbstractClass = runCatching {
            methodData.declaredClass?.let { Modifier.isAbstract(it.modifiers) }
        }.getOrNull() ?: false

        val isAbstractMethod = Modifier.isAbstract(methodData.modifiers)
        val isConstructor = methodData.name in setOf("<init>", "<clinit>")

        return !isAbstractClass && !isAbstractMethod && !isConstructor
    }

    fun createCustomHookInfo(methodData: MethodData): CustomHookInfo? {
        val className = methodData.className
        val methodName = methodData.name
        val paramTypeNames = methodData.paramTypeNames
        val returnTypeName = methodData.returnTypeName

        val returnsContext = returnTypeName == "android.content.Context"
        val hasContextParam = paramTypeNames?.contains("android.content.Context") == true
        val returnsBoolean = returnTypeName == "boolean"
        val isContextMethod = returnsContext || (hasContextParam && !returnsBoolean)

        return when {
            isContextMethod -> CustomHookInfo(
                hookMethodType = HookMethodType.REPLACE_CONTEXT_WITH_FAKE,
                hookPoint = "before",
                className = className,
                methodNames = listOf(methodName),
                parameterTypes = paramTypeNames,
                isEnabled = true,
            )
            else -> {
                val returnValue = when (returnTypeName) {
                    "boolean" -> "false"
                    "int" -> "0"
                    "long" -> "0L"
                    "float" -> "0.0f"
                    "double" -> "0.0"
                    "void" -> "null"
                    else -> null
                }
                when {
                    paramTypeNames.isNullOrEmpty() -> CustomHookInfo(
                        hookMethodType = HookMethodType.HOOK_ALL_METHODS,
                        hookPoint = "before",
                        className = className,
                        methodNames = listOf(methodName),
                        returnValue = returnValue,
                        isEnabled = true,
                    )
                    else -> CustomHookInfo(
                        hookMethodType = HookMethodType.FIND_AND_HOOK_METHOD,
                        hookPoint = "before",
                        className = className,
                        methodNames = listOf(methodName),
                        parameterTypes = paramTypeNames,
                        returnValue = returnValue,
                        isEnabled = true,
                    )
                }
            }
        }
    }
}
