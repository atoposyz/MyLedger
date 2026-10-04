package com.example.myledger.network

import com.sun.net.httpserver.HttpsConfigurator
import com.sun.net.httpserver.HttpsServer
import java.net.InetSocketAddress
import java.security.KeyStore
import javax.net.ssl.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import android.app.Application

/** Actual TLS sockets; trust is scoped to this known test certificate, never disabled. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class HttpsTransportTest {
    private lateinit var server: HttpsServer
    private lateinit var trusted: SSLSocketFactory
    private lateinit var previous: SSLSocketFactory
    private var status = 200
    private var body = "response-not-a-key".toByteArray()
    private var seenAuthorization: String? = null
    @Before fun setup() {
        val store = KeyStore.getInstance("PKCS12").apply {
            requireNotNull(requireNotNull(HttpsTransportTest::class.java.classLoader).getResourceAsStream("network/localhost-test.p12")).use { load(it, "test-fixture-only".toCharArray()) }
        }
        val key = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(store, "test-fixture-only".toCharArray()) }
        val trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(store) }
        val ssl = SSLContext.getInstance("TLS").apply { init(key.keyManagers, trust.trustManagers, null) }
        trusted = ssl.socketFactory; previous = HttpsURLConnection.getDefaultSSLSocketFactory()
        server = HttpsServer.create(InetSocketAddress("localhost", 0), 0)
        server.httpsConfigurator = HttpsConfigurator(ssl)
        server.createContext("/") { exchange ->
            seenAuthorization = exchange.requestHeaders.getFirst("Authorization")
            exchange.requestBody.use { it.readBytes() }
            if (status == 302) exchange.responseHeaders.set("Location", "https://different.example.com/steal-key")
            exchange.sendResponseHeaders(status, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }; server.start()
    }
    @After fun close() { if (::previous.isInitialized) HttpsURLConnection.setDefaultSSLSocketFactory(previous); if (::server.isInitialized) server.stop(0) }
    private fun request(limit: Long = 4096) = runBlocking { HttpsTransport().request("POST", "https://localhost:${server.address.port}/test", "test-only-secret", "request".toByteArray(), "application/json", limit) }
    @Test fun validTlsRoundTripUsesBearerAuthentication() {
        HttpsURLConnection.setDefaultSSLSocketFactory(trusted)
        assertArrayEquals(body, request()); assertEquals("Bearer test-only-secret", seenAuthorization)
    }
    @Test fun unknownCertificateIsRejected() { assertThrows(SSLHandshakeException::class.java) { request() }; assertNull(seenAuthorization) }
    @Test fun redirectAndHttpErrorsDoNotExposeServerBody() {
        HttpsURLConnection.setDefaultSSLSocketFactory(trusted)
        status = 302; assertTrue(assertThrows(ServiceException::class.java) { request() }.message!!.contains("重定向"))
        status = 401; body = "private-key-in-server-body".toByteArray()
        val error = assertThrows(ServiceException::class.java) { request() }; assertFalse(error.message!!.contains("private-key"))
    }
    @Test fun oversizedResponseRejectedBeforeReadingIt() {
        HttpsURLConnection.setDefaultSSLSocketFactory(trusted); body = ByteArray(1024)
        assertThrows(IllegalArgumentException::class.java) { request(16) }
    }
}
