package com.fearmikey.rf_reapr.domain.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HostValidatorTest {

    @Test
    fun `accepts plain hosts and ip literals`() {
        listOf("8.8.8.8", "192.168.1.1", "google.com", "my-nas.local", "a.b.c.example.org", "::1", "2001:4860:4860::8888", "fe80::1%wlan0")
            .forEach { assertTrue(it, HostValidator.isValidHost(it)) }
    }

    @Test
    fun `rejects option injection and malformed input`() {
        listOf("-f", "-c 1000 8.8.8.8", "8.8.8.8 -f", "999.1.1.1", "1.2.3", "host;rm -rf /", "a..b", "", "-host.com", "exa mple.com")
            .forEach { assertFalse(it, HostValidator.isValidHost(it)) }
    }

    @Test
    fun `normalize strips urls ports and brackets`() {
        assertEquals("example.com", HostValidator.normalize(" https://example.com/path?q=1 "))
        assertEquals("example.com", HostValidator.normalize("example.com:8080"))
        assertEquals("10.0.0.1", HostValidator.normalize("user@10.0.0.1"))
        assertEquals("2001:db8::1", HostValidator.normalize("[2001:db8::1]:443"))
        assertEquals("2001:db8::1", HostValidator.normalize("2001:db8::1"))
        assertNull(HostValidator.normalize("-f 8.8.8.8"))
    }

    @Test
    fun `classifies private addresses`() {
        listOf("10.1.2.3", "172.16.0.1", "192.168.0.1", "100.64.0.1", "169.254.1.1", "127.0.0.1", "fe80::1", "fd00::1")
            .forEach { assertTrue(it, HostValidator.isPrivateAddress(it)) }
        listOf("8.8.8.8", "172.32.0.1", "2001:4860::1").forEach { assertFalse(it, HostValidator.isPrivateAddress(it)) }
    }
}
