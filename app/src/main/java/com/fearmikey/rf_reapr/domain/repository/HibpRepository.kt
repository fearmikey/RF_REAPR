package com.fearmikey.rf_reapr.domain.repository

import com.fearmikey.rf_reapr.domain.model.Breach
import com.fearmikey.rf_reapr.domain.model.Paste

interface HibpRepository {
    suspend fun getBreachedAccounts(account: String): Result<List<Breach>>

    /** Pastes are indexed by email address only; other account names return an empty list. */
    suspend fun getPastes(account: String): Result<List<Paste>>

    /**
     * How often [password] appears in Pwned Passwords (0 = not found). Uses the k-anonymity
     * range API: only the first five characters of the SHA-1 hash are sent, no API key needed.
     */
    suspend fun pwnedPasswordCount(password: String): Result<Int>
}
