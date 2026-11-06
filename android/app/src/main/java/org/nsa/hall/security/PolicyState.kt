package org.nsa.hall.security

import org.json.JSONArray
import org.json.JSONObject

/** Caller serializes the read/compare/write transaction, including replay. */
fun acceptPolicy(previous: JSONObject?, incoming: JSONObject, signedDigest: String, scope: String): JSONObject {
    require(signedDigest.matches(Regex("[0-9a-f]{64}")))
    require(incoming.getInt("policy_schema") == 1 && incoming.getLong("epoch") >= 0)
    if (previous != null) {
        val oldEpoch = previous.getLong("epoch")
        require(incoming.getLong("epoch") >= oldEpoch) { "拒绝过期的目录策略" }
        if (incoming.getLong("epoch") == oldEpoch) {
            val original = JSONObject(previous.toString()).apply { remove("_signed_digest"); remove("_scope") }
            require(if (previous.has("_signed_digest")) previous.getString("_signed_digest") == signedDigest else jsonEquivalent(original, incoming)) { "相同策略序列的内容发生变化，已保留原有安全记录" }
        }
    }
    return JSONObject(incoming.toString()).put("_signed_digest", signedDigest).put("_scope", scope)
}

/** Missing entries in a narrower visibility scope never erase known blocking. */
fun mergeReleasePolicies(previous: JSONArray, incoming: JSONObject): JSONArray {
    val records = (0 until previous.length()).associate { val value = previous.getJSONObject(it); value.getString("release_id") to value }.toMutableMap()
    val entries = incoming.getJSONArray("releases")
    for (index in 0 until entries.length()) {
        val item = entries.getJSONObject(index)
        val id = safeId(item.getString("release_id")); val epoch = incoming.getLong("epoch")
        val old = records[id]
        if (old != null && epoch < old.getLong("epoch")) continue
        require(old == null || epoch != old.getLong("epoch") || old.getString("state") == item.getString("state")) { "相同策略序列的版本状态不一致" }
        records[id] = JSONObject(item.toString()).put("epoch", epoch)
    }
    return JSONArray(records.toSortedMap().values.toList())
}
