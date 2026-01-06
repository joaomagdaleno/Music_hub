package com.joaomagdaleno.music_hub.common.models

import com.joaomagdaleno.music_hub.common.models.NetworkConnection.Metered
import com.joaomagdaleno.music_hub.common.models.NetworkConnection.NotConnected
import com.joaomagdaleno.music_hub.common.models.NetworkConnection.Unmetered


/**
 * Enum class to define the type of network,
 * - [NotConnected]: No network connection
 * - [Metered]: Connected to a metered network (e.g., mobile data)
 * - [Unmetered]: Connected to an unmetered network (e.g., Wi-Fi)
 */
enum class NetworkConnection {
    NotConnected, Metered, Unmetered
}