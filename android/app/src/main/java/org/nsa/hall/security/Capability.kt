package org.nsa.hall.security

import org.json.JSONObject

/** Missing device measurements never satisfy a declared release requirement. */
fun meetsMinimum(device: JSONObject, minimum: JSONObject): Boolean {
    if (device.optInt("api_level", 0) < minimum.getInt("api_level") || device.optString("abi") != minimum.getString("abi")) return false
    for (key in listOf("page_size_bytes", "gpu_family", "gpu_driver_digest")) {
        if (minimum.has(key) && (!device.has(key) || device.get(key) != minimum.get(key))) return false
    }
    if (minimum.has("ram_bytes") && (!device.has("ram_bytes") || checkedBytes(device.getString("ram_bytes")) < checkedBytes(minimum.getString("ram_bytes")))) return false
    if (minimum.has("vulkan_api")) {
        fun version(text: String): List<Int>? {
            if (!text.matches(Regex("[0-9]+(?:\\.[0-9]+){1,2}"))) return null
            val parts = text.split('.').map { it.toIntOrNull() ?: return null }
            return parts + List(3 - parts.size) { 0 }
        }
        val actual = version(device.optString("vulkan_api")) ?: return false
        val required = version(minimum.getString("vulkan_api")) ?: return false
        val difference = actual.zip(required).firstOrNull { it.first != it.second }
        if (difference != null && difference.first < difference.second) return false
    }
    val expected = minimum.optJSONArray("features")
    val actual = device.optJSONArray("features")
    if (expected != null && expected.strings().any { actual == null || it !in actual.strings() }) return false
    return true
}
