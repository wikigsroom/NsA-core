package org.nsa.hall
import org.junit.Assert.*
import org.junit.Test
import org.nsa.hall.security.checkedBytes
import org.nsa.hall.security.safeChild
import org.nsa.hall.security.strictObject
import java.io.File

class StorageBoundaryTest {
    @Test fun byteCountsDoNotOverflowOrAcceptSigns() {
        assertEquals(Long.MAX_VALUE, checkedBytes(Long.MAX_VALUE.toString()))
        listOf("-1", "+1", "01", "9223372036854775808").forEach { value -> assertTrue(runCatching { checkedBytes(value) }.isFailure) }
    }
    @Test fun archivePathsRemainInsideRoot() {
        val root = File(System.getProperty("java.io.tmpdir"), "nsa-path-test")
        listOf("../save", "a/../../b", "/absolute", "a\\b", "a//b", "a:stream", "trailing.").forEach { name -> assertTrue(name, runCatching { safeChild(root, name) }.isFailure) }
        assertTrue(safeChild(root, "RomFS/data.bin").canonicalPath.startsWith(root.canonicalPath + File.separator))
    }
    @Test fun duplicateSignedJsonIsRejected() {
        assertTrue(runCatching { strictObject("{\"epoch\":1,\"epoch\":2}".toByteArray()) }.isFailure)
        assertTrue(runCatching { strictObject("{\"a\":{\"b\":1,\"b\":2}}".toByteArray()) }.isFailure)
    }
}
