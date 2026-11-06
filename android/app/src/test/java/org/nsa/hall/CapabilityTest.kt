package org.nsa.hall

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.nsa.hall.security.meetsMinimum

class CapabilityTest {
    private val minimum = JSONObject("""{"api_level":31,"abi":"arm64-v8a","ram_bytes":"8192","page_size_bytes":4096,"gpu_family":"measured GPU","gpu_driver_digest":"${"a".repeat(64)}","vulkan_api":"1.2.0","features":["shaderInt64"]}""")
    @Test fun requirementsRejectMissingMeasurements() {
        assertTrue(meetsMinimum(JSONObject(minimum.toString()), minimum))
        listOf("ram_bytes", "page_size_bytes", "gpu_family", "gpu_driver_digest", "vulkan_api", "features").forEach { key ->
            assertFalse(key, meetsMinimum(JSONObject(minimum.toString()).apply { remove(key) }, minimum))
        }
    }
    @Test fun versionMemoryAndFeatureLimitsUseMeasuredValues() {
        listOf("ram_bytes" to "4096", "page_size_bytes" to 16384, "gpu_family" to "other", "vulkan_api" to "1.1.99", "vulkan_api" to "unknown").forEach { (key, value) ->
            assertFalse(meetsMinimum(JSONObject(minimum.toString()).put(key, value), minimum))
        }
        assertTrue(meetsMinimum(JSONObject(minimum.toString()).put("vulkan_api", "1.2"), minimum))
        assertTrue(meetsMinimum(JSONObject(minimum.toString()).put("vulkan_api", "1.3.0"), minimum))
        assertFalse(meetsMinimum(JSONObject(minimum.toString()).put("features", org.json.JSONArray()), minimum))
    }
}
