package org.nsa.hall

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.nsa.hall.data.DeviceCapabilities

/** Exercise platform JSON and the packaged device probe, rather than JVM stubs. */
@RunWith(AndroidJUnit4::class)
class DeviceContractTest {
    @Test fun measuredCapabilitiesKeepTheirWireTypes() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val wire = JSONObject(DeviceCapabilities.read(context).toString())
        assertTrue(wire.get("api_level") is Number)
        assertTrue(wire.get("page_size_bytes") is Number)
        assertTrue(wire.getInt("page_size_bytes") in listOf(4096, 16384))
        assertTrue(wire.get("ram_bytes") is String)
        assertTrue(wire.getString("ram_bytes").matches(Regex("[1-9][0-9]*")))
        assertTrue(wire.getString("abi").isNotBlank())
        if (wire.has("gpu_family")) {
            assertTrue(wire.getString("gpu_driver_digest").matches(Regex("[0-9a-f]{64}")))
            assertTrue(wire.getString("vulkan_api").matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+")))
        }
    }
}
