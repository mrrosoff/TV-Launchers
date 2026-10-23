package dev.mrrosoff.braviatv.home

import android.app.Activity
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.widget.ImageView
import dev.mrrosoff.braviatv.R

/** The Wi-Fi bars (or Ethernet, or offline) icon, kept current by the system's network callback. */
class NetworkButton(
    private val activity: Activity,
    private val view: ImageView,
    private val handler: Handler,
) {
    private val connectivity = activity.getSystemService(ConnectivityManager::class.java)

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            handler.post { show(caps) }
        }

        override fun onLost(network: Network) {
            handler.post { show(current()) }
        }
    }

    fun start() {
        show(current())
        connectivity.registerDefaultNetworkCallback(callback)
    }

    fun stop() = connectivity.unregisterNetworkCallback(callback)

    private fun current() = connectivity.getNetworkCapabilities(connectivity.activeNetwork)

    private fun show(caps: NetworkCapabilities?) {
        when {
            caps == null -> {
                view.setImageResource(R.drawable.ic_wifi_off)
                view.contentDescription = activity.getString(R.string.offline)
            }
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> {
                view.setImageResource(R.drawable.ic_ethernet)
                view.contentDescription = activity.getString(R.string.ethernet)
            }
            else -> {
                val bars = bars(caps)
                view.setImageResource(WIFI_ICONS[bars])
                view.contentDescription = activity.getString(R.string.wifi_bars, bars)
            }
        }
    }

    /** 0 to 4 bars from the connection's signal strength. */
    @Suppress("DEPRECATION") // the static form works back to minSdk; the instance form needs Android 11
    private fun bars(caps: NetworkCapabilities): Int {
        val reported = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) caps.signalStrength else Int.MIN_VALUE
        val rssi = reported.takeIf { it != Int.MIN_VALUE }
            ?: activity.getSystemService(WifiManager::class.java)?.connectionInfo?.rssi
            ?: return 4
        return WifiManager.calculateSignalLevel(rssi, 5)
    }

    private companion object {
        val WIFI_ICONS = intArrayOf(
            R.drawable.ic_wifi_0, R.drawable.ic_wifi_1, R.drawable.ic_wifi_2, R.drawable.ic_wifi_3, R.drawable.ic_wifi_4,
        )
    }
}
