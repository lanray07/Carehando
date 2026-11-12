package app.carehando.data

import org.json.JSONArray
import org.json.JSONObject

internal const val MAX_FOLLOW_UP_ITEMS = 20
internal const val MAX_FOLLOW_UP_LENGTH = 160

internal data class AppointmentFollowUp(val label: String, val complete: Boolean = false)

internal fun decodeAppointmentFollowUp(value: String): List<AppointmentFollowUp> {
    if (value.isEmpty()) return emptyList()
    val rows = JSONArray(value)
    require(rows.length() <= MAX_FOLLOW_UP_ITEMS)
    return List(rows.length()) { index ->
        val row = rows.getJSONObject(index)
        val label = row.getString("label")
        require(label.isNotBlank() && label.length <= MAX_FOLLOW_UP_LENGTH)
        AppointmentFollowUp(label, row.getBoolean("complete"))
    }
}

internal fun encodeAppointmentFollowUp(items: List<AppointmentFollowUp>): String {
    if (items.isEmpty()) return ""
    return JSONArray().apply {
        items.forEach { put(JSONObject().put("label", it.label).put("complete", it.complete)) }
    }.toString()
}
