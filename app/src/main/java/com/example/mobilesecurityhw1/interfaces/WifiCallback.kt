package com.example.mobilesecurityhw1.interfaces

interface WifiCallback {
    fun onSpecificNetworkFound(networkName: String)
    fun onMinimumNetworksFound(networkCount: Int)
}