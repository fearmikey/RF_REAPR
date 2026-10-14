package com.fearmikey.rf_reapr.domain.model

enum class DorkCategory(val displayName: String) {
    FILES("Files & Configs"),
    LOGINS("Admin & Logins"),
    DIRECTORIES("Open Directories"),
    EXPOSURES("Exposures & Leaks"),
    DEVICES("Cameras & IoT")
}

data class GoogleDork(
    val title: String,
    val description: String,
    val query: String,
    val category: DorkCategory
)

object GoogleDorkDatabase {
    val dorks = listOf(
        // Files & Configs
        GoogleDork(
            title = "Exposed Environment Files (.env)",
            description = "Finds exposed .env files containing API keys, database credentials, and secrets.",
            query = "filetype:env \"DB_PASSWORD\" OR \"API_KEY\"",
            category = DorkCategory.FILES
        ),
        GoogleDork(
            title = "Exposed Git Repositories",
            description = "Locates publicly accessible .git folder configurations and commit logs.",
            query = "intitle:\"index of\" \"/.git/\" config",
            category = DorkCategory.FILES
        ),
        GoogleDork(
            title = "Database Backup & SQL Dumps",
            description = "Searches for downloaded or exposed database SQL backups.",
            query = "filetype:sql \"CREATE TABLE\" OR \"INSERT INTO\" OR \"dump\"",
            category = DorkCategory.FILES
        ),
        GoogleDork(
            title = "SSH Private Keys & Configs",
            description = "Finds accidentally exposed private keys (id_rsa) and SSH config files.",
            query = "intitle:\"index of\" \"id_rsa\" OR \"id_dsa\" OR \"known_hosts\"",
            category = DorkCategory.FILES
        ),
        GoogleDork(
            title = "Server Log Files",
            description = "Locates access and error logs containing user IPs, URLs, and errors.",
            query = "filetype:log \"access.log\" OR \"error.log\"",
            category = DorkCategory.FILES
        ),
        GoogleDork(
            title = "Configuration & Secret Files (YAML/JSON)",
            description = "Finds configuration files often holding tokens and database strings.",
            query = "filetype:yaml OR filetype:yml \"password:\" OR \"secret:\"",
            category = DorkCategory.FILES
        ),

        // Admin & Logins
        GoogleDork(
            title = "Generic Admin Portals",
            description = "Finds common web portal login and administration endpoints.",
            query = "inurl:admin OR inurl:login OR inurl:cpanel \"username\" \"password\"",
            category = DorkCategory.LOGINS
        ),
        GoogleDork(
            title = "WordPress Admin Pages",
            description = "Identifies WordPress login panels and installation setups.",
            query = "inurl:wp-login.php OR inurl:wp-admin",
            category = DorkCategory.LOGINS
        ),
        GoogleDork(
            title = "phpMyAdmin Dashboard",
            description = "Locates web-based MySQL administration interfaces.",
            query = "intitle:\"phpMyAdmin\" \"Welcome to phpMyAdmin\"",
            category = DorkCategory.LOGINS
        ),
        GoogleDork(
            title = "Grafana Dashboards",
            description = "Identifies exposed Grafana telemetry and monitoring login pages.",
            query = "intitle:\"Grafana\" inurl:login",
            category = DorkCategory.LOGINS
        ),
        GoogleDork(
            title = "Kibana & Elastic Dashboards",
            description = "Finds exposed Kibana log visualization interfaces.",
            query = "inurl:app/kibana intext:\"Loading Kibana\"",
            category = DorkCategory.LOGINS
        ),

        // Open Directories
        GoogleDork(
            title = "Open Directory Indexes",
            description = "Locates web servers with directory indexing enabled.",
            query = "intitle:\"index of /\" \"Parent Directory\"",
            category = DorkCategory.DIRECTORIES
        ),
        GoogleDork(
            title = "Open Uploads Directory",
            description = "Finds unindexed user-uploaded file folders.",
            query = "intitle:\"index of /wp-content/uploads/\" OR intitle:\"index of /uploads/\"",
            category = DorkCategory.DIRECTORIES
        ),
        GoogleDork(
            title = "Open Backup Archives",
            description = "Searches for tar, zip, and gz archives in open directory listings.",
            query = "intitle:\"index of /\" (\".tar.gz\" OR \".zip\" OR \".bak\" OR \".7z\")",
            category = DorkCategory.DIRECTORIES
        ),

        // Exposures & Leaks
        GoogleDork(
            title = "AWS S3 Bucket URLs",
            description = "Finds exposed Amazon S3 cloud storage buckets.",
            query = "site:s3.amazonaws.com OR inurl:\".s3.amazonaws.com\"",
            category = DorkCategory.EXPOSURES
        ),
        GoogleDork(
            title = "Firebase Realtime Databases",
            description = "Locates open Firebase database endpoints.",
            query = "site:firebaseio.com",
            category = DorkCategory.EXPOSURES
        ),
        GoogleDork(
            title = "Exposed Swagger / OpenAPI Docs",
            description = "Finds interactive API documentation revealing hidden endpoints.",
            query = "inurl:swagger-ui.html OR inurl:\"/api-docs\"",
            category = DorkCategory.EXPOSURES
        ),
        GoogleDork(
            title = "Spring Boot Actuator Endpoints",
            description = "Discovers exposed Actuator endpoints (heapdump, env, beans).",
            query = "inurl:actuator/env OR inurl:actuator/health",
            category = DorkCategory.EXPOSURES
        ),

        // Cameras & IoT
        GoogleDork(
            title = "Live Webcams & IP Cameras",
            description = "Discovers live public webcam streams and IP camera web interfaces.",
            query = "inurl:\"view/index.shtml\" OR inurl:\"view/view.shtml\" OR intitle:\"Live View / - AXIS\"",
            category = DorkCategory.DEVICES
        ),
        GoogleDork(
            title = "Hikvision / Dahua Camera Portals",
            description = "Finds surveillance system remote access login pages.",
            query = "intitle:\"Network Camera\" OR intitle:\"WEB SERVICE\" inurl:doc/page/login.asp",
            category = DorkCategory.DEVICES
        ),
        GoogleDork(
            title = "Network Printer Interfaces",
            description = "Locates network printers with web administration consoles.",
            query = "intitle:\"HP Color LaserJet\" OR intitle:\"Xerox CentreWare\"",
            category = DorkCategory.DEVICES
        )
    )
}
