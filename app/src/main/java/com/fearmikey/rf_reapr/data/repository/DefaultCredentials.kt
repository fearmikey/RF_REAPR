package com.fearmikey.rf_reapr.data.repository

import com.fearmikey.rf_reapr.domain.repository.CredentialPair

object DefaultCredentials {

    val generic = listOf(
        CredentialPair("admin", "admin"),
        CredentialPair("admin", "password"),
        CredentialPair("admin", "1234"),
        CredentialPair("admin", "12345"),
        CredentialPair("admin", "admin123"),
        CredentialPair("admin", "setup"),
        CredentialPair("root", "root"),
        CredentialPair("root", "password"),
        CredentialPair("root", "1234"),
        CredentialPair("root", "123456"),
        CredentialPair("root", "toor"),
        CredentialPair("user", "user"),
        CredentialPair("user", "password"),
        CredentialPair("user", "1234"),
        CredentialPair("guest", "guest"),
        CredentialPair("guest", "12345"),
        CredentialPair("support", "support"),
        CredentialPair("administrator", "administrator"),
        CredentialPair("administrator", "password"),
        CredentialPair("admin", ""),
        CredentialPair("root", "")
    )

    val networking = listOf(
        // Cisco
        CredentialPair("cisco", "cisco"),
        CredentialPair("admin", "cisco"),
        
        // Ubiquiti
        CredentialPair("ubnt", "ubnt"),
        
        // MikroTik
        CredentialPair("admin", "mikrotik"),
        
        // TP-Link
        CredentialPair("admin", "tplink"),
        
        // D-Link
        CredentialPair("admin", "dlink"),
        
        // Netgear
        CredentialPair("admin", "password"), // common for Netgear
        
        // Linksys
        CredentialPair("admin", "admin"),
        
        // ZyXEL
        CredentialPair("admin", "1234"),
        
        // Allied Telesis
        CredentialPair("manager", "friend"),
        
        // Motorola
        CredentialPair("admin", "motorola"),
        
        // Sky
        CredentialPair("admin", "sky")
    )

    val databases = listOf(
        // MSSQL
        CredentialPair("sa", "password"),
        CredentialPair("sa", "123456"),
        
        // PostgreSQL
        CredentialPair("postgres", "postgres"),
        CredentialPair("postgres", "password"),
        
        // Oracle
        CredentialPair("system", "manager"),
        CredentialPair("sys", "password"),
        CredentialPair("scott", "tiger"),
        
        // MySQL / MariaDB
        CredentialPair("root", "mysql"),
        CredentialPair("root", "root"),
        
        // Redis
        CredentialPair("redis", "redis"),
        
        // MongoDB
        CredentialPair("admin", "admin"),
        
        // SAP HANA
        CredentialPair("SYSTEM", "Manager1")
    )

    val serverManagement = listOf(
        // Dell iDRAC
        CredentialPair("root", "calvin"),
        
        // HP iLO
        CredentialPair("Administrator", "password"),
        
        // Supermicro IPMI
        CredentialPair("ADMIN", "ADMIN"),
        
        // IBM IMM
        CredentialPair("admin", "admin"),
        
        // APC UPS
        CredentialPair("apc", "apc")
    )

    val webApps = listOf(
        // Jenkins
        CredentialPair("admin", "admin"),
        
        // Tomcat / JBoss
        CredentialPair("tomcat", "tomcat"),
        CredentialPair("admin", "admin"),
        CredentialPair("manager", "manager"),
        
        // WebLogic
        CredentialPair("weblogic", "weblogic1"),
        CredentialPair("system", "password"),
        
        // ColdFusion
        CredentialPair("admin", "admin")
    )

    val cameras = listOf(
        // Hikvision
        CredentialPair("admin", "12345"),
        
        // Dahua
        CredentialPair("admin", "admin"),
        
        // Axis
        CredentialPair("root", "pass"),
        CredentialPair("root", "axis"),
        
        // Mobotix
        CredentialPair("admin", "meinsm")
    )

    val industrial = listOf(
        CredentialPair("USER", "USER"),
        CredentialPair("MINI", "MINI"),
        CredentialPair("modicon", "modicon"),
        CredentialPair("ladder", "ladder"),
        CredentialPair("proworx", "proworx")
    )

    val iot = listOf(
        CredentialPair("pi", "raspberry"),
        CredentialPair("telnet", "telnet"),
        CredentialPair("service", "service"),
        CredentialPair("tech", "tech"),
        CredentialPair("admin", "1111"),
        CredentialPair("admin", "888888"),
        CredentialPair("admin", "9999")
    )

    val common = generic + networking + databases + serverManagement + webApps + cameras + industrial + iot
}
