package com.close.hook.ads.hook.util

import java.lang.reflect.Method

object StringFinderKit {

    fun findMethodsWithString(
        key: String,
        searchString: String,
        methodName: String,
        classLoader: ClassLoader
    ): List<Method> {
        return DexKitUtil.withBridge { bridge ->
            DexKitUtil.getCachedOrFindMethods(key) {
                bridge.findMethod {
                    matcher {
                        usingStrings = listOf(searchString)
                        name = methodName
                    }
                }
            }.mapNotNull { methodData ->
                runCatching {
                    methodData.getMethodInstance(classLoader)
                }.getOrNull()
            }
        }.orEmpty()
    }
}
