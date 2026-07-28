package com.close.hook.ads.hook.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.close.hook.ads.preference.HookPrefs
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

object PackageVisibilityHandler {

    private const val TAG = "PkgVisHandler"
    private const val MODULE_PACKAGE = "com.close.hook.ads"
    private const val PERM_QUERY_ALL = "android.permission.QUERY_ALL_PACKAGES"
    private const val ACTION_UPDATE = "com.close.hook.ads.ACTION_UPDATE_PKG_VISIBILITY"

    private val ENABLE_PREFIXES = arrayOf(
        "switch_one_", "switch_two_", "switch_three_", "switch_four_",
        "switch_five_", "switch_six_", "switch_seven_", "switch_eight_",
        "switch_nine_",
        "overall_hook_enabled_"
    )

    private val threadWakeLock = Object()

    @Volatile
    private var cachedSystemContext: Context? = null

    @Volatile
    private var enabledCallerPackages: Set<String> = emptySet()

    @Volatile
    private var visibilityHookInstalled = false

    private val packageNameMethods = ConcurrentHashMap<Class<*>, Method>()
    private val loggedAllowedCallers = ConcurrentHashMap.newKeySet<String>()

    @android.annotation.SuppressLint("UnspecifiedRegisterReceiverFlag")
    fun init(xposed: XposedInterface) {
        xposed.log(Log.INFO, TAG, "Initializing Package Visibility Handler...")

        Thread {
            var appsFilterInstance: Any? = null

            var lastTargetPackages: Set<String> = emptySet()
            var lastFeatureEnabled: Boolean? = null
            var isReceiverRegistered = false

            val updateReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent?.action == ACTION_UPDATE) {
                        xposed.log(Log.DEBUG, TAG, "Update signal received, waking up...")
                        synchronized(threadWakeLock) {
                            threadWakeLock.notifyAll()
                        }
                    }
                }
            }

            while (true) {
                try {
                    if (!isReceiverRegistered) {
                        getSystemContext()?.let { sysContext ->
                            try {
                                val filter = IntentFilter(ACTION_UPDATE)
                                if (Build.VERSION.SDK_INT >= 33) {
                                    sysContext.javaClass.getMethod(
                                        "registerReceiver",
                                        BroadcastReceiver::class.java,
                                        IntentFilter::class.java,
                                        Int::class.javaPrimitiveType
                                    ).invoke(sysContext, updateReceiver, filter, 2)
                                } else {
                                    sysContext.registerReceiver(updateReceiver, filter)
                                }
                                isReceiverRegistered = true
                                xposed.log(Log.INFO, TAG, "BroadcastReceiver registered successfully. System is ready.")
                            } catch (e: Throwable) {
                                xposed.log(Log.WARN, TAG, "Failed to register BroadcastReceiver: ${e.message}")
                            }
                        }
                    }

                    if (appsFilterInstance == null) {
                        val binder = getService("package")
                        val pms = if (binder != null) extractPMS(binder) else null

                        if (pms != null) {
                            val appsFilter = getFieldValue(pms, "mAppsFilter")
                            if (appsFilter != null && installVisibilityHook(appsFilter, xposed)) {
                                xposed.log(Log.INFO, TAG, "--- Targeted Package Visibility Initialized ---")
                                xposed.log(Log.INFO, TAG, "[Binder] ServiceManager 'package': ${binder?.javaClass?.name}")
                                xposed.log(Log.INFO, TAG, "[PMS] Extracted instance: ${pms.javaClass.name}")
                                xposed.log(Log.INFO, TAG, "[Filter] Located mAppsFilter instance: ${appsFilter.javaClass.name}")
                                appsFilterInstance = appsFilter
                            }
                        }

                        if (appsFilterInstance == null) {
                            Thread.sleep(10000)
                            continue
                        }
                    }

                    HookPrefs.invalidateCaches()
                    val isFeatureEnabled = HookPrefs.getBoolean(HookPrefs.KEY_ENABLE_PACKAGE_VISIBILITY_BYPASS, false)

                    if (isFeatureEnabled) {
                        val targetPackages = buildTargetPackages()

                        if (lastFeatureEnabled != true || targetPackages != lastTargetPackages) {
                            enabledCallerPackages = targetPackages
                            loggedAllowedCallers.retainAll(targetPackages)
                            lastTargetPackages = targetPackages
                            xposed.log(
                                Log.INFO,
                                TAG,
                                "Allowed callers updated: [${targetPackages.joinToString()}], target=$MODULE_PACKAGE"
                            )
                        }
                    } else {
                        if (lastFeatureEnabled != false) {
                            enabledCallerPackages = emptySet()
                            loggedAllowedCallers.clear()
                            lastTargetPackages = emptySet()
                            xposed.log(Log.INFO, TAG, "Targeted visibility disabled.")
                        }
                    }
                    lastFeatureEnabled = isFeatureEnabled

                    if (isReceiverRegistered) {
                        synchronized(threadWakeLock) {
                            threadWakeLock.wait(60000L)
                        }
                    } else {
                        Thread.sleep(5000)
                    }

                } catch (e: InterruptedException) {
                    xposed.log(Log.INFO, TAG, "Daemon thread interrupted, exiting.")
                    Thread.currentThread().interrupt()
                    break
                } catch (e: Throwable) {
                    xposed.log(Log.ERROR, TAG, "Daemon error: ${Log.getStackTraceString(e)}")
                    if (e is ReflectiveOperationException) appsFilterInstance = null
                    Thread.sleep(10000)
                }
            }
        }.apply {
            isDaemon = true
            name = "AdClose-PkgVisDaemon"
            start()
        }
    }

    @Synchronized
    private fun installVisibilityHook(appsFilter: Any, xposed: XposedInterface): Boolean {
        if (visibilityHookInstalled) return true

        val filterMethod = findShouldFilterMethod(appsFilter.javaClass)
        if (filterMethod == null) {
            xposed.log(Log.WARN, TAG, "Exact shouldFilterApplication method not found")
            return false
        }

        return try {
            filterMethod.isAccessible = true
            XposedBridge.hookMethod(filterMethod, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val args = param.args
                    if (args.size != 5 || getPackageName(args[3]) != MODULE_PACKAGE) return

                    val callerPackage = findEnabledCaller(args[2]) ?: return
                    param.result = false
                    if (loggedAllowedCallers.add(callerPackage)) {
                        xposed.log(
                            Log.INFO,
                            TAG,
                            "Allowed targeted query: caller=$callerPackage, target=$MODULE_PACKAGE"
                        )
                    }
                }
            })
            visibilityHookInstalled = true
            xposed.log(
                Log.INFO,
                TAG,
                "Hooked ${filterMethod.declaringClass.name}#${filterMethod.name}(5 args)"
            )
            true
        } catch (e: Throwable) {
            xposed.log(Log.WARN, TAG, "Failed to install targeted visibility hook: ${e.message}")
            false
        }
    }

    private fun findShouldFilterMethod(startClass: Class<*>): Method? {
        var current: Class<*>? = startClass
        while (current != null && current != Any::class.java) {
            val method = current.declaredMethods.firstOrNull {
                it.name == "shouldFilterApplication" &&
                    it.parameterCount == 5 &&
                    it.returnType == Boolean::class.javaPrimitiveType &&
                    it.parameterTypes[1] == Int::class.javaPrimitiveType &&
                    it.parameterTypes[3].name.endsWith(".PackageStateInternal")
            }
            if (method != null) return method
            current = current.superclass
        }
        return null
    }

    private fun findEnabledCaller(callingSetting: Any?): String? {
        if (callingSetting == null) return null
        val enabledPackages = enabledCallerPackages
        if (enabledPackages.isEmpty()) return null

        getPackageName(callingSetting)?.let { packageName ->
            if (packageName in enabledPackages) return packageName
        }

        val packageStates = runCatching {
            callingSetting.javaClass.methods.firstOrNull {
                it.name == "getPackageStates" && it.parameterCount == 0
            }?.invoke(callingSetting)
        }.getOrNull()

        val states = when (packageStates) {
            is Map<*, *> -> packageStates.values
            is Iterable<*> -> packageStates
            is Array<*> -> packageStates.asIterable()
            else -> emptyList()
        }
        return states.firstNotNullOfOrNull { state ->
            getPackageName(state)?.takeIf(enabledPackages::contains)
        }
    }

    private fun getPackageName(value: Any?): String? {
        if (value == null) return null
        val getter = packageNameMethods[value.javaClass] ?: value.javaClass.methods
            .firstOrNull {
                it.name == "getPackageName" &&
                    it.parameterCount == 0 &&
                    it.returnType == String::class.java
            }
            ?.also {
                it.isAccessible = true
                packageNameMethods.putIfAbsent(value.javaClass, it)
            }
            ?: return null
        return runCatching { getter.invoke(value) as? String }.getOrNull()
    }

    private fun buildTargetPackages(): Set<String> {
        val allPrefs = HookPrefs.getAll()
        val systemPm = getSystemContext()?.packageManager
        val rawPackages = mutableSetOf<String>()

        allPrefs.forEach { (key, value) ->
            if (value == true) {
                for (prefix in ENABLE_PREFIXES) {
                    if (key.startsWith(prefix)) {
                        rawPackages.add(key.substring(prefix.length))
                        break
                    }
                }
            }
        }

        return if (systemPm != null) {
            rawPackages.filterTo(mutableSetOf()) { pkg ->
                systemPm.checkPermission(PERM_QUERY_ALL, pkg) != PackageManager.PERMISSION_GRANTED
            }
        } else {
            rawPackages
        }
    }

    private fun getSystemContext(): Context? {
        cachedSystemContext?.let { return it }
        return try {
            val atClass = Class.forName("android.app.ActivityThread")
            val at = atClass.getMethod("currentActivityThread").invoke(null)
            val ctx = atClass.getMethod("getSystemContext").invoke(at) as? Context
            cachedSystemContext = ctx
            ctx
        } catch (e: Exception) {
            null
        }
    }

    private fun getService(name: String): IBinder? {
        return try {
            val smClass = Class.forName("android.os.ServiceManager")
            val method = smClass.getDeclaredMethod("getService", String::class.java)
            method.invoke(null, name) as? IBinder
        } catch (e: Exception) { null }
    }

    private fun extractPMS(binder: IBinder): Any? {
        val className = binder.javaClass.name
        if (className.contains("PackageManagerService") && !className.contains("IPackageManagerImpl")) {
            return binder
        }
        getFieldValue(binder, "mService")?.let { return it }
        getFieldValue(binder, "this\$0")?.let { return it }
        return null
    }

    private fun getFieldValue(obj: Any, fieldName: String): Any? {
        val field = findField(obj.javaClass, fieldName) ?: return null
        return try { field.get(obj) } catch (e: Exception) { null }
    }

    private fun findField(clazz: Class<*>, fieldName: String): Field? {
        var current: Class<*>? = clazz
        while (current != null && current != Any::class.java) {
            try {
                return current.getDeclaredField(fieldName).also { it.isAccessible = true }
            } catch (e: NoSuchFieldException) {
                current = current.superclass
            }
        }
        return null
    }
}
