package com.minikano.f50_sms.utils

object IPManager {
    /**
    * Get the current hotspot gateway IPv4 address
    * @param setPort Optional port to append
    * @return Gateway IP (e.g., 192.168.0.1); null if not found
     */
    fun getHotspotGatewayIp(setPort:String?): String? {
        try {
            val process = Runtime.getRuntime().exec("ip route")
            val output = process.inputStream.bufferedReader().use { it.readText() }
            process.destroy()

            val regex = Regex("""([0-9.]+/\d+)\s+dev\s+(\w+)\s+.*src\s+([0-9.]+)""")

            regex.findAll(output).forEach { match ->
                val iface = match.groupValues[2]
                val ip = match.groupValues[3]

                // Filter out interfaces unlikely to be the hotspot
                if (iface.startsWith("br") || iface.startsWith("ap")) {
                    KanoLog.d("kano_ZTE_LOG", "IPManager hotspot IP: $ip:$setPort")
                    if(setPort != null){
                        return "$ip:$setPort" // Hotspot gateway IP found
                    }
                    return ip // Hotspot gateway IP found
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }
}