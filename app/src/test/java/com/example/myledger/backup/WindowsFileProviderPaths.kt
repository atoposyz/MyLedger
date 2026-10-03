package com.example.myledger.backup

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.lang.reflect.Proxy

// AndroidX's Android-only implementation tests canonical paths with '/' separators.
// Adapt that path strategy on the Windows JVM; keep the real provider, streams and URI grants.
object WindowsFileProviderPaths {
    fun install(context: Context) {
        if (File.separatorChar != '\\') return
        val authority = "${context.packageName}.backupfiles"
        val root = File(context.cacheDir, "backups/exports").canonicalFile
        val strategyType = Class.forName("androidx.core.content.FileProvider\$PathStrategy")
        val strategy = Proxy.newProxyInstance(strategyType.classLoader, arrayOf(strategyType)) { _, method, arguments ->
            when (method.name) {
                "getUriForFile" -> {
                    val file = (arguments!![0] as File).canonicalFile
                    require(file.toPath().startsWith(root.toPath()))
                    val relative = root.toPath().relativize(file.toPath()).toString().replace('\\', '/')
                    Uri.Builder().scheme("content").authority(authority).encodedPath("/exports/${Uri.encode(relative, "/")}").build()
                }
                "getFileForUri" -> {
                    val uri = arguments!![0] as Uri
                    require(uri.authority == authority && uri.encodedPath!!.startsWith("/exports/"))
                    File(root, Uri.decode(uri.encodedPath!!.removePrefix("/exports/"))).canonicalFile.also {
                        require(it.toPath().startsWith(root.toPath()))
                    }
                }
                "toString" -> "Windows test exports path strategy"
                else -> error("Unexpected path strategy method: ${method.name}")
            }
        }
        val cache = FileProvider::class.java.getDeclaredField("sCache").apply { isAccessible = true }
        context.contentResolver.acquireContentProviderClient(Uri.parse("content://$authority/exports/test.zip"))!!.use { client ->
            val provider = requireNotNull(client.localContentProvider)
            FileProvider::class.java.getDeclaredField("mLocalPathStrategy").apply { isAccessible = true }.set(provider, strategy)
        }
        @Suppress("UNCHECKED_CAST")
        val strategies = cache.get(null) as MutableMap<String, Any>
        strategies[authority] = strategy
    }
}
