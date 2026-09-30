package com.example.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SupabaseProfileSyncTest {
    @Test
    fun `self provisioning merchant DB errors should not block app sync`() {
        assertTrue(MerchantDatabaseAuthPolicy.shouldSkipMerchantDbLogin("User not found"))
        assertTrue(MerchantDatabaseAuthPolicy.shouldSkipMerchantDbLogin("invalid login credentials"))
        assertTrue(MerchantDatabaseAuthPolicy.shouldSkipMerchantDbLogin("Auth user not found"))
        assertFalse(MerchantDatabaseAuthPolicy.shouldSkipMerchantDbLogin("Session expired. Please sign in again."))
    }

    @Test
    fun `real auth failures still surface to the user`() {
        assertFalse(MerchantDatabaseAuthPolicy.shouldSkipMerchantDbLogin("Network timeout while contacting Supabase"))
        assertFalse(MerchantDatabaseAuthPolicy.shouldSkipMerchantDbLogin("403 permission denied"))
    }
}
