package com.dhruv.volumetweak

import org.json.JSONArray
import org.json.JSONObject

data class CustomCombo(
    val id: String,
    val tokens: List<String>,
    var action: String,
    var isEnabled: Boolean = true
) {
    fun toJson(): JSONObject {
        val obj = JSONObject()
        obj.put("id", id)
        val arr = JSONArray()
        tokens.forEach { arr.put(it) }
        obj.put("tokens", arr)
        obj.put("action", action)
        obj.put("isEnabled", isEnabled)
        return obj
    }

    fun getDisplaySequence(): String {
        return tokens.joinToString(" → ") { formatToken(it) }
    }

    companion object {
        fun fromJson(obj: JSONObject): CustomCombo? {
            return try {
                val id = obj.optString("id", System.currentTimeMillis().toString())
                val tokensArr = obj.getJSONArray("tokens")
                val tokens = mutableListOf<String>()
                for (i in 0 until tokensArr.length()) {
                    tokens.add(tokensArr.getString(i))
                }
                val action = obj.optString("action", "SKIP_FWD_15")
                val isEnabled = obj.optBoolean("isEnabled", true)
                CustomCombo(id, tokens, action, isEnabled)
            } catch (e: Exception) {
                null
            }
        }

        fun parseList(jsonStr: String?): List<CustomCombo> {
            if (jsonStr.isNullOrBlank()) return getDefaultCombos()
            return try {
                val arr = JSONArray(jsonStr)
                val list = mutableListOf<CustomCombo>()
                for (i in 0 until arr.length()) {
                    fromJson(arr.getJSONObject(i))?.let { list.add(it) }
                }
                if (list.isEmpty()) getDefaultCombos() else list
            } catch (e: Exception) {
                getDefaultCombos()
            }
        }

        fun toJsonList(list: List<CustomCombo>): String {
            val arr = JSONArray()
            list.forEach { arr.put(it.toJson()) }
            return arr.toString()
        }

        fun getDefaultCombos(): List<CustomCombo> {
            return listOf(
                CustomCombo("default_fwd", listOf("DUAL", "UP"), "SKIP_FWD_15", true),
                CustomCombo("default_bwd", listOf("DUAL", "DOWN"), "SKIP_BWD_15", true)
            )
        }

        fun formatToken(token: String): String {
            return when (token) {
                "DUAL" -> "Dual Press"
                "DUAL_HOLD" -> "Hold Dual"
                "UP" -> "Vol UP"
                "UP_HOLD" -> "Hold UP"
                "DOWN" -> "Vol DOWN"
                "DOWN_HOLD" -> "Hold DOWN"
                else -> token
            }
        }
    }
}
