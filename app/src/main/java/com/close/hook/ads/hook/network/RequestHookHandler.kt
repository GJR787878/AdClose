package com.close.hook.ads.hook.network

import android.content.Context
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.close.hook.ads.data.model.BlockedRequest
import com.close.hook.ads.hook.util.HookUtil
import com.close.hook.ads.util.TeeInputStream
import com.close.hook.ads.preference.HookPrefs
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.InetAddress
import java.net.ProtocolException
import java.net.UnknownHostException
import java.lang.reflect.Field
import java.nio.ByteBuffer
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.SSLEngineResult
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLParameters

internal object RequestHookHandler {

    private const val LOG_PREFIX = "[RequestHookHandler] "
    private lateinit var applicationContext: Context

    private val emptyWebResponse: WebResourceResponse? by lazy { createEmptyWebResourceResponse() }

    private val cronetHttpURLConnectionCls: Class<*>? by lazy {
        try {
            XposedHelpers.findClass("com.ttnet.org.chromium.net.urlconnection.CronetHttpURLConnection", applicationContext.classLoader)
        } catch (e: Throwable) { null }
    }
    private val urlResponseInfoCls: Class<*>? by lazy {
        try {
            XposedHelpers.findClass("com.ttnet.org.chromium.net.UrlResponseInfo", applicationContext.classLoader)
        } catch (e: Throwable) { null }
    }
    private val urlRequestCls: Class<*>? by lazy {
        try {
            XposedHelpers.findClass("com.ttnet.org.chromium.net.UrlRequest", applicationContext.classLoader)
        } catch (e: Throwable) { null }
    }
    private val cronetInputStreamCls: Class<*>? by lazy {
        try {
            XposedHelpers.findClass("com.ttnet.org.chromium.net.urlconnection.CronetInputStream", applicationContext.classLoader)
        } catch (e: Throwable) { null }
    }

    private val hookedWebViewClientClasses: MutableSet<String> = ConcurrentHashMap.newKeySet()

    private val switchNineKey by lazy { "switch_nine_${applicationContext.packageName}" }
    private val switchTwoKey by lazy { "switch_two_${applicationContext.packageName}" }

    fun init(context: Context) {
        applicationContext = context
        setupDNSRequestHook()
        setupConnectHook()
        setupSocketHook()
        setupConscryptEngineHook()
        setupWebViewRequestHook()
        setupCronetRequestHook() // ByteDance
    }

    /**
     * Universal socket-level domain block hook on libcore.io.BlockGuardOs.connect.
     *
     * Every user-app socket — TCP via Socket -> IoBridge.connect, UDP via
     * DatagramSocket -> PlainDatagramSocketImpl, including QUIC / HTTP/3
     * sockets, OkHttp's raw sockets, Chromium's NIO sockets — bottoms out
     * through Libcore.os.connect(), which in user processes is BlockGuardOs.
     * Intercepting here makes the existing rule engine apply regardless of
     * application-layer protocol.
     */
    private fun setupConnectHook() {
        HookUtil.findAndHookMethod(
            "libcore.io.BlockGuardOs",
            "connect",
            arrayOf(
                java.io.FileDescriptor::class.java,
                InetAddress::class.java,
                Int::class.javaPrimitiveType
            ),
            "before"
        ) { param ->
            val addr = param.args[1] as? InetAddress ?: return@findAndHookMethod
            val port = param.args[2] as? Int ?: return@findAndHookMethod
            val ip = addr.hostAddress ?: return@findAndHookMethod

            // Skip loopback / link-local / metadata destinations so we don't
            // misclassify in-app IPC, mDNS, NTP-on-router style traffic.
            if (ip == "127.0.0.1" || ip == "::1" ||
                ip.startsWith("169.254.") ||
                ip.startsWith("fe80:", ignoreCase = true)) {
                return@findAndHookMethod
            }

            val host = RequestHook.getUnambiguousDnsHost(ip)
            val displayHost = host ?: ip
            val info = BlockedRequest(
                requestType     = " CONNECT",
                requestValue    = displayHost,
                method          = null,
                urlString       = null,
                requestHeaders  = null,
                requestBody     = null,
                responseCode    = -1,
                responseMessage = null,
                responseHeaders = null,
                responseBody    = null,
                responseBodyContentType = null,
                stack           = HookUtil.getFormattedStackTrace(),
                dnsHost         = host,
                fullAddress     = "$ip:$port",
                requestId       = java.util.UUID.randomUUID().toString()
            )
            if (RequestHook.checkShouldBlockRequest(info)) {
                param.throwable = java.net.ConnectException("Blocked by AdClose: $displayHost")
            }
        }
    }

    private fun setupDNSRequestHook() {
        HookUtil.findAndHookMethod(
            InetAddress::class.java,
            "getByName",
            arrayOf(String::class.java),
            "after"
        ) { param ->
            if (RequestHook.processDnsRequest(param.args[0], param.result)) {
                param.throwable = UnknownHostException("Blocked by AdClose: ${param.args[0]}")
            }
        }
        HookUtil.findAndHookMethod(
            InetAddress::class.java,
            "getAllByName",
            arrayOf(String::class.java),
            "after"
        ) { param ->
            if (RequestHook.processDnsRequest(param.args[0], param.result)) {
                param.throwable = UnknownHostException("Blocked by AdClose: ${param.args[0]}")
            }
        }
    }

    private fun setupSocketHook() {
        try {
            HookUtil.hookAllMethods(
                "java.net.SocketOutputStream",
                "socketWrite0",
                "before"
            ) { param ->
                if (HookPrefs.getBoolean(switchNineKey, false)) {
                    return@hookAllMethods
                }

                val socket = XposedHelpers.getObjectField(param.thisObject, "socket")
                    ?: return@hookAllMethods
                if (socket is SSLSocket) return@hookAllMethods

                val bytes = param.args[1] as ByteArray
                val offset = param.args[2] as Int
                val len = param.args[3] as Int
                if (len <= 0) return@hookAllMethods

                val key = socket
                val buffer = RequestHook.requestBuffers.computeIfAbsent(key) { ByteArrayOutputStream() }
                buffer.write(bytes, offset, len)
                if (RequestHook.processRequestBuffer(key, isHttps = false)) {
                    param.throwable = IOException("Request blocked by AdClose")
                    return@hookAllMethods
                }
            }

            HookUtil.hookAllMethods(
                "java.net.SocketInputStream",
                "socketRead0",
                "after"
            ) { param ->
                if (HookPrefs.getBoolean(switchNineKey, false)) {
                    return@hookAllMethods
                }

                val socket = XposedHelpers.getObjectField(param.thisObject, "socket")
                    ?: return@hookAllMethods
                if (socket is SSLSocket) return@hookAllMethods

                val bytes = param.args[1] as ByteArray
                val len = param.result as? Int ?: -1
                if (len <= 0) return@hookAllMethods

                val key = socket
                val buffer = RequestHook.responseBuffers.computeIfAbsent(key) { ByteArrayOutputStream() }
                buffer.write(bytes, 0, len)
                RequestHook.processResponseBuffer(key, param)
            }

            // Remove buffer entries as soon as the socket closes.
            HookUtil.hookAllMethods(
                "java.net.Socket",
                "close",
                "after"
            ) { param ->
                val key = param.thisObject
                RequestHook.requestBuffers.remove(key)
                RequestHook.responseBuffers.remove(key)
                RequestHook.pendingRequests.remove(key)
            }
        } catch (e: Throwable) {
            XposedBridge.log("$LOG_PREFIX Error setting up plain socket hook: ${e.message}")
        }
    }

    private fun setupConscryptEngineHook() {
        try {
            val conscryptEngineClass = Class.forName("com.android.org.conscrypt.ConscryptEngine")

            HookUtil.findAndHookMethod(
                conscryptEngineClass,
                "wrap",
                arrayOf(ByteBuffer::class.java, ByteBuffer::class.java),
                "before"
            ) { param ->
                if (HookPrefs.getBoolean(switchNineKey, false) ||
                    !HookPrefs.getBoolean(switchTwoKey, false)) {
                    return@findAndHookMethod
                }

                try {
                    val srcBuffer = param.args[0] as ByteBuffer
                    if (srcBuffer.hasRemaining()) {
                        val connId = System.identityHashCode(param.thisObject).toLong() or (1L shl 48)

                        val position = srcBuffer.position()
                        val bytes = ByteArray(srcBuffer.remaining())
                        srcBuffer.get(bytes)
                        srcBuffer.position(position)

                        val collectRespBody = NativeRequestHook.getCollectResponseBody()
                        val status = NativeRequestHook.feedH2Data(connId, true, bytes, 0, bytes.size, collectRespBody)
                        
                        if (status == 2) {
                            param.throwable = ProtocolException("Request blocked by AdClose (HTTP/2)")
                            return@findAndHookMethod
                        } else if (status == 1) {
                            return@findAndHookMethod
                        }

                        val key = param.thisObject
                        val buffer = RequestHook.requestBuffers.computeIfAbsent(key) { ByteArrayOutputStream() }
                        buffer.write(bytes)
                        if (RequestHook.processRequestBuffer(key, isHttps = true)) {
                            param.throwable = ProtocolException("Request blocked by AdClose")
                            return@findAndHookMethod
                        }
                    }
                } catch (e: Throwable) {
                    XposedBridge.log("$LOG_PREFIX ConscryptEngine.wrap hook error: ${e.message}")
                }
            }

            HookUtil.findAndHookMethod(
                conscryptEngineClass,
                "unwrap",
                arrayOf(ByteBuffer::class.java, ByteBuffer::class.java),
                "after"
            ) { param ->
                if (HookPrefs.getBoolean(switchNineKey, false) ||
                    !HookPrefs.getBoolean(switchTwoKey, false)) {
                    return@findAndHookMethod
                }

                try {
                    val result = param.result as? SSLEngineResult ?: return@findAndHookMethod
                    val dstBuffer = param.args[1] as ByteBuffer
                    val bytesProduced = result.bytesProduced()

                    if (bytesProduced > 0) {
                        val connId = System.identityHashCode(param.thisObject).toLong() or (1L shl 48)

                        val position = dstBuffer.position()
                        val start = position - bytesProduced
                        val bytes = ByteArray(bytesProduced)
                        val dup = dstBuffer.duplicate()
                        dup.position(start)
                        dup.get(bytes, 0, bytesProduced)

                        val collectRespBody = NativeRequestHook.getCollectResponseBody()
                        val status = NativeRequestHook.feedH2Data(connId, false, bytes, 0, bytes.size, collectRespBody)
                        
                        if (status == 2) {
                            param.throwable = ProtocolException("Request blocked by AdClose (HTTP/2)")
                            return@findAndHookMethod
                        } else if (status == 1) {
                            return@findAndHookMethod
                        }

                        val key = param.thisObject
                        val buffer = RequestHook.responseBuffers.computeIfAbsent(key) { ByteArrayOutputStream() }
                        buffer.write(bytes)
                        RequestHook.processResponseBuffer(key, param)
                    }
                } catch (e: Throwable) {
                    XposedBridge.log("$LOG_PREFIX ConscryptEngine.unwrap hook error: ${e.message}")
                }
            }

            HookUtil.hookAllMethods(
                conscryptEngineClass,
                "closeInbound",
                "after"
            ) { param ->
                if (HookPrefs.getBoolean(switchNineKey, false)) {
                    return@hookAllMethods
                }
                try {
                    val connId = System.identityHashCode(param.thisObject).toLong() or (1L shl 48)
                    NativeRequestHook.freeH2Conn(connId)
                    RequestHook.requestBuffers.remove(param.thisObject)
                    RequestHook.responseBuffers.remove(param.thisObject)
                    RequestHook.pendingRequests.remove(param.thisObject)
                } catch (e: Throwable) {
                    XposedBridge.log("$LOG_PREFIX ConscryptEngine.closeInbound hook error: ${e.message}")
                }
            }
        } catch (e: Throwable) {
            XposedBridge.log("$LOG_PREFIX Error setting up ConscryptEngine hook: ${e.message}")
        }
    }

    private fun setupWebViewRequestHook() {
        HookUtil.findAndHookMethod(
            WebView::class.java,
            "setWebViewClient",
            arrayOf(WebViewClient::class.java),
            "before"
        ) { param ->
            param.args[0]?.let {
                hookClientMethods(it.javaClass.name, applicationContext.classLoader)
            }
        }
    }

    private fun hookClientMethods(clientClassName: String, classLoader: ClassLoader) {
        if (!hookedWebViewClientClasses.add(clientClassName)) return

        XposedBridge.log("$LOG_PREFIX WebViewClient set: $clientClassName")

        HookUtil.hookAllMethods(
            clientClassName,
            "shouldInterceptRequest",
            "before",
            { param ->
                if (param.args.size != 2) return@hookAllMethods
                val request = param.args[1] as? WebResourceRequest ?: return@hookAllMethods

                if (processWebRequest(request)) {
                    param.result = emptyWebResponse
                    return@hookAllMethods
                }
            },
            classLoader
        )

        HookUtil.hookAllMethods(
            clientClassName,
            "shouldOverrideUrlLoading",
            "before",
            { param ->
                if (param.args.size != 2) return@hookAllMethods
                if (processWebRequest(param.args[1])) {
                    param.result = true
                }
            },
            classLoader
        )
    }

    private fun processWebRequest(request: Any?): Boolean {
        try {
            val webResourceRequest = request as? WebResourceRequest ?: return false

            val urlString = webResourceRequest.url?.toString() ?: return false
            val formattedUrl = RequestHook.formatUrlWithoutQuery(Uri.parse(urlString))

            val info = BlockedRequest(
                requestType = " Web",
                requestValue = formattedUrl,
                method = webResourceRequest.method,
                urlString = urlString,
                requestHeaders = webResourceRequest.requestHeaders.toString(),
                requestBody = null,
                responseCode = -1,
                responseMessage = null,
                responseHeaders = null,
                responseBody = null,
                responseBodyContentType = null,
                stack = HookUtil.getFormattedStackTrace(),
                dnsHost = null,
                fullAddress = null,
                requestId = UUID.randomUUID().toString()
            )
            return RequestHook.checkShouldBlockRequest(info)
        } catch (e: Exception) {
            XposedBridge.log("$LOG_PREFIX Web request error: ${e.message}")
        }
        return false
    }

    private fun createEmptyWebResourceResponse(): WebResourceResponse? {
        return try {
            WebResourceResponse(
                "text/plain", "UTF-8", 204, "No Content",
                emptyMap(), ByteArrayInputStream(ByteArray(0))
            )
        } catch (e: Exception) {
            XposedBridge.log("$LOG_PREFIX Empty response error: ${e.message}")
            null
        }
    }

    private fun setupCronetRequestHook() {
        try {
            HookUtil.hookAllMethods(
                cronetHttpURLConnectionCls,
                "getResponse",
                "after"
            ) { param ->
                try {
                    val thisObject = param.thisObject

                    val requestObject = XposedHelpers.getObjectField(thisObject, "mRequest")
                    val responseInfoObject = XposedHelpers.getObjectField(thisObject, "mResponseInfo")
                    val inputStreamObject = XposedHelpers.getObjectField(thisObject, "mInputStream")

                    if (requestObject == null || responseInfoObject == null) return@hookAllMethods

                    val initialUrl = XposedHelpers.getObjectField(requestObject, "mInitialUrl") as? String
                    val method = XposedHelpers.getObjectField(requestObject, "mInitialMethod") as? String
                    val requestHeaders = XposedHelpers.getObjectField(requestObject, "mRequestHeaders")?.toString()

                    val finalUrl = XposedHelpers.callMethod(responseInfoObject, "getUrl") as? String
                    val httpStatusCode = XposedHelpers.callMethod(responseInfoObject, "getHttpStatusCode") as? Int ?: -1
                    val httpStatusText = XposedHelpers.callMethod(responseInfoObject, "getHttpStatusText") as? String
                    val responseHeadersMap = XposedHelpers.callMethod(responseInfoObject, "getAllHeaders") as? Map<String, List<String>>
                    val responseHeaders = responseHeadersMap?.toString() ?: ""
                    val negotiatedProtocol = XposedHelpers.callMethod(responseInfoObject, "getNegotiatedProtocol") as? String

                    var responseBody: ByteArray? = null
                    var responseContentType: String? = null

                    if (HookPrefs.getBoolean(HookPrefs.KEY_COLLECT_RESPONSE_BODY, false) && httpStatusCode in 200..399) {
                        inputStreamObject?.let { streamObj ->
                            try {
                                val buffer = XposedHelpers.getObjectField(streamObj, "mBuffer") as? ByteBuffer
                                if (buffer != null && buffer.hasArray()) {
                                    responseBody = buffer.array()
                                }
                            } catch (e: Throwable) {
                                XposedBridge.log("$LOG_PREFIX Cronet: failed to read mBuffer, falling back to InputStream: ${e.message}")
                            }

                            if (responseBody == null) {
                                (streamObj as? InputStream)?.use { inputStream ->
                                    val out = ByteArrayOutputStream()
                                    TeeInputStream(inputStream, out).use { it.readBytes() }
                                    responseBody = out.toByteArray()

                                    try {
                                        XposedHelpers.setObjectField(streamObj, "mBuffer", ByteBuffer.wrap(responseBody))
                                    } catch (ignored: Throwable) {}
                                }
                            }
                            responseContentType = responseHeadersMap?.get("Content-Type")?.firstOrNull()
                        }
                    }

                    val formattedUrl = finalUrl?.let { RequestHook.formatUrlWithoutQuery(Uri.parse(it)) }

                    val info = BlockedRequest(
                        requestType = " CRONET/$negotiatedProtocol",
                        requestValue = formattedUrl ?: "",
                        method = method,
                        urlString = finalUrl,
                        requestHeaders = requestHeaders,
                        requestBody = null,
                        responseCode = httpStatusCode,
                        responseMessage = httpStatusText,
                        responseHeaders = responseHeaders,
                        responseBody = responseBody,
                        responseBodyContentType = responseContentType,
                        stack = HookUtil.getFormattedStackTrace(),
                        dnsHost = null,
                        fullAddress = null,
                        requestId = UUID.randomUUID().toString()
                    )

                    if (RequestHook.checkShouldBlockRequest(info)) {
                        param.throwable = IOException("Request blocked by AdClose (Cronet)")
                    }
                } catch (e: Throwable) {
                    XposedBridge.log("$LOG_PREFIX Error in Cronet hook: ${e.message}")
                }
            }
        } catch (e: Throwable) {
            XposedBridge.log("$LOG_PREFIX Error setting up Cronet hook: ${e.message}")
        }
    }
}
