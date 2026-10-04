package com.example

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class TaskItem(
    val id: String = UUID.randomUUID().toString(),
    val caseNumber: String = "",
    val caseYear: String = "",
    val caseTitle: String = "",
    val mediator: String = "",
    val note: String,
    val date: String,
    val isCompleted: Boolean = false
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("caseNumber", caseNumber)
            put("caseYear", caseYear)
            put("caseTitle", caseTitle)
            put("mediator", mediator)
            put("note", note)
            put("date", date)
            put("isCompleted", isCompleted)
        }
    }

    companion object {
        fun fromJson(obj: JSONObject): TaskItem {
            return TaskItem(
                id = obj.optString("id", UUID.randomUUID().toString()),
                caseNumber = obj.optString("caseNumber", ""),
                caseYear = obj.optString("caseYear", ""),
                caseTitle = obj.optString("caseTitle", ""),
                mediator = obj.optString("mediator", ""),
                note = obj.optString("note", ""),
                date = obj.optString("date", ""),
                isCompleted = obj.optBoolean("isCompleted", false)
            )
        }

        fun listToJson(list: List<TaskItem>): String {
            val array = JSONArray()
            for (item in list) {
                array.put(item.toJson())
            }
            return array.toString()
        }

        fun listFromJson(jsonStr: String): List<TaskItem> {
            val list = mutableListOf<TaskItem>()
            if (jsonStr.isBlank()) return list
            try {
                val array = JSONArray(jsonStr)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(fromJson(obj))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return list
        }
    }
}
