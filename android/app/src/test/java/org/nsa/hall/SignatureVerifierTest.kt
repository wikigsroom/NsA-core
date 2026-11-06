package org.nsa.hall

import org.junit.Assert.*
import org.junit.Test
import org.nsa.hall.security.verifyEd25519

class SignatureVerifierTest {
    private fun hex(value: String): ByteArray {
        require(value.length % 2 == 0)
        return value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
    @Test fun rfc8032KnownAnswerAndTampering() {
        val key = hex("302a300506032b6570032100d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a")
        // RFC 8032 section 7.1, TEST 1 (empty message).
        val signature = hex("e5564300c360ac729086e2cc806e828a84877f1eb8e5d974d873e065224901555fb8821590a33bacc61e39701cf9b46bd25bf5f0595bbe24655141438e7a100b")
        assertTrue(verifyEd25519(key, signature, byteArrayOf()))
        assertFalse(verifyEd25519(key, signature, "NSA/policy/v1\u0000".toByteArray()))
        signature[0] = (signature[0].toInt() xor 1).toByte()
        assertFalse(verifyEd25519(key, signature, byteArrayOf()))
        assertFalse(verifyEd25519(key.drop(1).toByteArray(), signature, byteArrayOf()))
    }
}
