package com.stuartjohnson.rainsong.ai

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Finds the RainSong AI container on Wi-Fi (mDNS, then /24 probe).
 * Loopback is only a last resort (USB adb reverse / emulator).
 */
class LanAiFinder(
    context: Context,
    private val onUpdate: (foundUrl: String?, note: String) -> Unit,
) {
    private val app = context.applicationContext
    private val nsd = app.getSystemService(NsdManager::class.java)
    private val wifi = app.getSystemService(WifiManager::class.java)
    private val connectivity = app.getSystemService(ConnectivityManager::class.java)
    private val client = AiServiceClient()
    private val executor = Executors.newCachedThreadPool()
    private val running = AtomicBoolean(false)
    private val hasLan = AtomicBoolean(false)
    private val currentUrl = AtomicReference<String?>(null)
    private var multicast: WifiManager.MulticastLock? = null
    private var discovery: NsdManager.DiscoveryListener? = null
    private val networks = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            executor.execute {
                try {
                    Thread.sleep(800)
                } catch (_: InterruptedException) {
                }
                if (running.get()) searchLan("Wi-Fi changed, searching…")
            }
        }
        override fun onLost(network: Network) {
            val url = currentUrl.get() ?: return
            if (isLoopback(url) || !client.health(url)) {
                searchLan("Network lost, searching LAN…")
            }
        }
    }

    fun start() {
        if (!running.compareAndSet(false, true)) {
            searchLan("Searching LAN for RainSong AI…")
            return
        }
        onUpdate(null, "Searching LAN for RainSong AI…")
        holdMulticast()
        startNsd()
        try {
            connectivity.registerDefaultNetworkCallback(networks)
        } catch (_: Exception) {
        }
        searchLan("Searching LAN for RainSong AI…")
    }

    fun rediscover() {
        searchLan("Searching LAN for RainSong AI…")
    }

    /** Keep a working loopback URL, but keep looking for a Wi-Fi address. */
    fun seekLanUpgrade() {
        if (!running.get() || hasLan.get()) return
        executor.execute { scanSubnet() }
    }

    fun stop() {
        running.set(false)
        try {
            connectivity.unregisterNetworkCallback(networks)
        } catch (_: Exception) {
        }
        discovery?.let {
            try {
                nsd.stopServiceDiscovery(it)
            } catch (_: Exception) {
            }
        }
        discovery = null
        try {
            multicast?.release()
        } catch (_: Exception) {
        }
        multicast = null
    }

    private fun searchLan(note: String) {
        if (!running.get()) return
        hasLan.set(false)
        val prev = currentUrl.get()
        if (prev == null || isLoopback(prev)) {
            currentUrl.set(null)
            onUpdate(null, note)
        } else {
            onUpdate(prev, note)
        }
        executor.execute { scanSubnet() }
        executor.execute {
            try {
                Thread.sleep(3_000)
            } catch (_: InterruptedException) {
            }
            if (!running.get() || hasLan.get()) return@execute
            for (url in listOf("http://127.0.0.1:8765", "http://10.0.2.2:8765")) {
                if (hasLan.get()) return@execute
                if (client.health(url)) {
                    confirm(url, "USB tunnel", lan = false)
                    return@execute
                }
            }
            if (!hasLan.get() && currentUrl.get() == null) {
                onUpdate(null, "No AI server on LAN. Start Docker on this Wi-Fi, or paste a URL.")
            }
        }
    }

    private fun holdMulticast() {
        try {
            multicast = wifi?.createMulticastLock("rainsong-ai")?.apply {
                setReferenceCounted(false)
                acquire()
            }
        } catch (_: Exception) {
        }
    }

    private fun startNsd() {
        val listener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onServiceLost(serviceInfo: NsdServiceInfo) {}
            @Suppress("DEPRECATION")
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (!serviceInfo.serviceType.contains("rainsong-ai")) return
                nsd.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {}
                    override fun onServiceResolved(info: NsdServiceInfo) {
                        val host = hostString(info) ?: return
                        if (isLoopbackHost(host)) return
                        val url = "http://$host:${info.port}"
                        executor.execute { confirm(url, "Found on LAN", lan = true) }
                    }
                })
            }
        }
        discovery = listener
        try {
            nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (_: Exception) {
        }
    }

    private fun hostString(info: NsdServiceInfo): String? {
        if (Build.VERSION.SDK_INT >= 34) {
            val addrs = info.hostAddresses
            val v4 = addrs.firstOrNull { it is Inet4Address } ?: addrs.firstOrNull()
            if (v4 != null) return v4.hostAddress
        }
        @Suppress("DEPRECATION")
        return info.host?.hostAddress
    }

    private fun scanSubnet() {
        val ip = localIpv4() ?: return
        val parts = ip.split('.')
        if (parts.size != 4) return
        val prefix = "${parts[0]}.${parts[1]}.${parts[2]}."
        val self = parts[3].toIntOrNull() ?: return
        for (n in 1..254) {
            if (!running.get() || hasLan.get()) return
            if (n == self) continue
            val host = "$prefix$n"
            executor.execute {
                if (!running.get() || hasLan.get()) return@execute
                if (portOpen(host, 8765) && client.health("http://$host:8765")) {
                    confirm("http://$host:8765", "Found on Wi-Fi", lan = true)
                }
            }
        }
    }

    private fun confirm(url: String, how: String, lan: Boolean) {
        if (!client.health(url)) return
        if (lan) {
            hasLan.set(true)
            currentUrl.set(url)
            onUpdate(url, "$how · $url")
            return
        }
        if (hasLan.get()) return
        currentUrl.set(url)
        onUpdate(url, "$how · $url")
    }

    private fun localIpv4(): String? {
        val net = connectivity.activeNetwork ?: return null
        val lp = connectivity.getLinkProperties(net) ?: return null
        val v4 = lp.linkAddresses.map { it.address }.filterIsInstance<Inet4Address>()
            .firstOrNull { !it.isLoopbackAddress } ?: return null
        return v4.hostAddress
    }

    private fun portOpen(host: String, port: Int): Boolean {
        return try {
            Socket().use { sock ->
                sock.connect(InetSocketAddress(host, port), 250)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        const val SERVICE_TYPE = "_rainsong-ai._tcp."

        fun isLoopback(url: String): Boolean {
            val u = url.lowercase()
            return u.contains("127.0.0.1") || u.contains("localhost") || u.contains("10.0.2.2")
        }

        private fun isLoopbackHost(host: String): Boolean {
            return host == "127.0.0.1" || host == "::1" || host.equals("localhost", true)
        }
    }
}
