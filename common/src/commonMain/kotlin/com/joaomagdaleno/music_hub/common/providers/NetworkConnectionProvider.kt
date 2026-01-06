package com.joaomagdaleno.music_hub.common.providers

import com.joaomagdaleno.music_hub.common.models.NetworkConnection

/**
 * Interface to provide the current network connection of the app
 */
interface NetworkConnectionProvider {

    /**
     * Called when the network connection changes,
     * to provide the current [NetworkConnection] to the extension
     */
    fun setNetworkConnection(networkConnection: NetworkConnection)
}