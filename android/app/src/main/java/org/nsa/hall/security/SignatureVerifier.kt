package org.nsa.hall.security

import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer

/** A bundled implementation keeps identical signature rules on Android 12 and later. */
fun verifyEd25519(publicKeySpki: ByteArray, signature: ByteArray, message: ByteArray): Boolean {
    val prefix = byteArrayOf(0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00)
    if (publicKeySpki.size != prefix.size + 32 || signature.size != 64 || !publicKeySpki.copyOfRange(0, prefix.size).contentEquals(prefix)) return false
    val verifier = Ed25519Signer()
    verifier.init(false, Ed25519PublicKeyParameters(publicKeySpki, prefix.size))
    verifier.update(message, 0, message.size)
    return verifier.verifySignature(signature)
}
