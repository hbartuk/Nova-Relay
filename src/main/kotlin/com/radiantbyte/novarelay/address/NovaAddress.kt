package com.radiantbyte.novarelay.address

import java.net.InetSocketAddress

data class NovaAddress(val hostName: String, val port: Int)

inline val NovaAddress.inetSocketAddress
    get() = InetSocketAddress(hostName, port)

