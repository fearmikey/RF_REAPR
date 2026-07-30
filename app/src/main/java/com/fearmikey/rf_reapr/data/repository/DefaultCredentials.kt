package com.fearmikey.rf_reapr.data.repository

import com.fearmikey.rf_reapr.domain.repository.CredentialPair

object DefaultCredentials {
    val common = listOf(
        // Generic
        CredentialPair("admin", "admin"),
        CredentialPair("admin", "password"),
        CredentialPair("admin", "1234"),
        CredentialPair("admin", "12345"),
        CredentialPair("admin", "admin123"),
        CredentialPair("root", "root"),
        CredentialPair("root", "password"),
        CredentialPair("root", "1234"),
        CredentialPair("root", "123456"),
        CredentialPair("user", "user"),
        CredentialPair("user", "password"),
        CredentialPair("guest", "guest"),
        CredentialPair("support", "support"),
        CredentialPair("administrator", "administrator"),
        CredentialPair("administrator", "password"),
        
        // Networking (Cisco, Ubiquiti, MikroTik)
        CredentialPair("cisco", "cisco"),
        CredentialPair("ubnt", "ubnt"),
        CredentialPair("admin", "mikrotik"),
        CredentialPair("manager", "friend"),
        
        // Databases
        CredentialPair("sa", "password"),
        CredentialPair("postgres", "postgres"),
        CredentialPair("oracle", "oracle"),
        CredentialPair("root", "mysql"),
        
        // IoT / Embedded
        CredentialPair("pi", "raspberry"),
        CredentialPair("telnet", "telnet"),
        CredentialPair("service", "service"),
        CredentialPair("tech", "tech"),
        CredentialPair("admin", "1111"),
        CredentialPair("admin", "888888")
    )
}
