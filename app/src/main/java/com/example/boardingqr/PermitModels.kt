package com.example.boardingqr

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import java.time.Instant
import java.time.ZonedDateTime

@Serializable
data class PermitInfo(
    val permit_no: String,
    val name: String,
    val zones: List<String>,
    val status: String,
    val valid_to: String
)

object PermitParser {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    fun fromJson(raw: String): Result<PermitInfo> = runCatching {
        json.decodeFromString<PermitInfo>(raw)
    }
}

data class ValidationResult(
    val ok: Boolean,
    val reasons: List<String>
)

object PermitValidator {
    fun validate(info: PermitInfo, requiredZone: String = "B"): ValidationResult {
        val reasons = mutableListOf<String>()
        if (!info.zones.contains(requiredZone)) {
            reasons += "Invalid zone: required '$requiredZone' but got ${info.zones}"
        }
        if (info.status.lowercase() != "active") {
            reasons += "Status is not active: ${info.status}"
        }
        val now = Instant.now()
        val validTo = runCatching { ZonedDateTime.parse(info.valid_to).toInstant() }.getOrElse {
            reasons += "Invalid valid_to format (expected ISO-8601): ${info.valid_to}"
            Instant.EPOCH
        }
        if (validTo.isBefore(now)) {
            reasons += "Permit expired at ${info.valid_to}"
        }
        return ValidationResult(reasons.isEmpty(), reasons)
    }
}