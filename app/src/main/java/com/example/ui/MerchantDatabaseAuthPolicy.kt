package com.example.ui

object MerchantDatabaseAuthPolicy {
    private val selfProvisioningFailures = listOf(
        "user not found",
        "auth user not found",
        "no user found",
        "invalid login credentials",
        "invalid_grant",
        "email not found",
        "this user does not exist",
        "user does not exist",
        "sign up required",
        "signup required"
    )

    fun shouldSkipMerchantDbLogin(error: String?): Boolean {
        if (error.isNullOrBlank()) return false
        val normalized = error.trim().lowercase()
        if (normalized.contains("session expired") || normalized.contains("session timeout")) {
            return false
        }
        return selfProvisioningFailures.any { normalized.contains(it) }
    }
}
