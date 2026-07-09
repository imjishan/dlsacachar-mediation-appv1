package com.example

import org.json.JSONObject

data class CaseRecord(
    val caseNumber: String,
    val year: String,
    val serialNumber: Int,
    val category: String,
    val courtReferredFrom: String,
    val petitioner: String,
    val petitionerPhone: String,
    val respondent: String,
    val respondentPhone: String,
    val intakeDate: String, // "YYYY-MM-DD" or similar format
    val firstMediationDate: String, // Date of first mediation
    val reportDate: String, // Date for report/fixing
    val mediator: String,
    val status: String, // "Registered", "Settled", "Not Settled", etc.
    val nextDate: String = "" // Added for next session date tracking
) {
    val searchIndex: String by lazy {
        "$caseNumber|$petitioner|$petitionerPhone|$respondent|$respondentPhone|$mediator".lowercase()
    }
}

object CaseRecordMapper {
    fun caseToJson(case: CaseRecord): String {
        return """{
  "caseNumber": "${escapeJson(case.caseNumber)}",
  "year": "${escapeJson(case.year)}",
  "serialNumber": ${case.serialNumber},
  "category": "${escapeJson(case.category)}",
  "courtReferredFrom": "${escapeJson(case.courtReferredFrom)}",
  "petitioner": "${escapeJson(case.petitioner)}",
  "petitionerPhone": "${escapeJson(case.petitionerPhone)}",
  "respondent": "${escapeJson(case.respondent)}",
  "respondentPhone": "${escapeJson(case.respondentPhone)}",
  "intakeDate": "${escapeJson(case.intakeDate)}",
  "firstMediationDate": "${escapeJson(case.firstMediationDate)}",
  "reportDate": "${escapeJson(case.reportDate)}",
  "mediator": "${escapeJson(case.mediator)}",
  "status": "${escapeJson(case.status)}",
  "nextDate": "${escapeJson(case.nextDate)}"
}"""
    }

    private fun escapeJson(s: String): String {
        return s.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    fun jsonToCase(jsonStr: String): CaseRecord {
        val obj = JSONObject(jsonStr)
        val intake = obj.optString("intakeDate", "")
        val extractedYear = getYearFromDate(intake)
        return CaseRecord(
            caseNumber = obj.optString("caseNumber", ""),
            year = obj.optString("year", extractedYear),
            serialNumber = obj.optInt("serialNumber", 1),
            category = obj.optString("category", ""),
            courtReferredFrom = obj.optString("courtReferredFrom", ""),
            petitioner = obj.optString("petitioner", ""),
            petitionerPhone = obj.optString("petitionerPhone", "N/A").ifEmpty { "N/A" },
            respondent = obj.optString("respondent", ""),
            respondentPhone = obj.optString("respondentPhone", "N/A").ifEmpty { "N/A" },
            intakeDate = intake,
            firstMediationDate = obj.optString("firstMediationDate", intake),
            reportDate = obj.optString("reportDate", ""),
            mediator = obj.optString("mediator", ""),
            status = obj.optString("status", ""),
            nextDate = obj.optString("nextDate", "")
        )
    }

    fun getYearFromDate(dateStr: String): String {
        val parts = dateStr.trim().split("-", "/", " ")
        if (parts.isNotEmpty()) {
            val yearPart = parts[0]
            if (yearPart.length == 4 && yearPart.all { it.isDigit() }) {
                return yearPart
            }
        }
        return "2026"
    }

    fun getMonthFromDate(dateStr: String): String {
        val parts = dateStr.trim().split("-", "/", " ")
        if (parts.size >= 2) {
            return when (val monthPart = parts[1]) {
                "01", "1" -> "January"
                "02", "2" -> "February"
                "03", "3" -> "March"
                "04", "4" -> "April"
                "05", "5" -> "May"
                "06", "6" -> "June"
                "07", "7" -> "July"
                "08", "8" -> "August"
                "09", "9" -> "September"
                "10" -> "October"
                "11" -> "November"
                "12" -> "December"
                else -> monthPart
            }
        }
        return "Unknown"
    }
}
