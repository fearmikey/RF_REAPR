package com.fearmikey.rf_reapr.domain.model

enum class DorkCategory(val displayName: String) {
    FILES("Files & Configs"),
    LOGINS("Admin & Logins"),
    DIRECTORIES("Open Directories"),
    EXPOSURES("Exposures & Leaks"),
    DOCUMENTS("Documents & People"),
    THIRD_PARTY("Code & Paste Sites"),
    DEVICES("Cameras & IoT")
}

/** How a target domain is combined with a dork. */
enum class DorkScope {
    /** Results must be on the target itself: `site:target query`. */
    SITE,

    /**
     * The dork already searches a third-party site (GitHub, Pastebin, a cloud provider), so a
     * second `site:` would return nothing; the target is added as a quoted keyword instead.
     */
    KEYWORD
}

data class GoogleDork(
    val title: String,
    val description: String,
    val query: String,
    val category: DorkCategory,
    val scope: DorkScope = DorkScope.SITE
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
            category = DorkCategory.EXPOSURES,
            scope = DorkScope.KEYWORD
        ),
        GoogleDork(
            title = "Firebase Realtime Databases",
            description = "Locates open Firebase database endpoints.",
            query = "site:firebaseio.com",
            category = DorkCategory.EXPOSURES,
            scope = DorkScope.KEYWORD
        ),
        GoogleDork(
            title = "Azure Blob & Google Cloud Storage",
            description = "Finds indexed objects in Azure Blob containers and Google Cloud Storage buckets.",
            query = "site:blob.core.windows.net OR site:storage.googleapis.com",
            category = DorkCategory.EXPOSURES,
            scope = DorkScope.KEYWORD
        ),
        GoogleDork(
            title = "Verbose Error Messages",
            description = "Finds pages leaking stack traces, SQL errors and file-system paths.",
            query = "intext:\"Fatal error\" OR intext:\"Stack trace\" OR intext:\"SQL syntax\" OR intext:\"Warning: mysql\"",
            category = DorkCategory.EXPOSURES
        ),
        GoogleDork(
            title = "phpinfo() Pages",
            description = "Locates phpinfo output exposing server configuration, paths and environment variables.",
            query = "intitle:\"phpinfo()\" \"PHP Version\"",
            category = DorkCategory.EXPOSURES
        ),
        GoogleDork(
            title = "Subdomains (excluding www)",
            description = "Lists indexed hosts under the target domain. Needs a target domain.",
            query = "-inurl:www",
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

        // Documents & People
        GoogleDork(
            title = "Office Documents & PDFs",
            description = "Published documents often carry author names, usernames and software versions in their metadata.",
            query = "filetype:pdf OR filetype:docx OR filetype:xlsx OR filetype:pptx",
            category = DorkCategory.DOCUMENTS
        ),
        GoogleDork(
            title = "Confidential / Internal Documents",
            description = "Finds documents marked as not intended for the public.",
            query = "(filetype:pdf OR filetype:docx OR filetype:xlsx) (\"confidential\" OR \"internal use only\" OR \"not for distribution\")",
            category = DorkCategory.DOCUMENTS
        ),
        GoogleDork(
            title = "Spreadsheets & CSV Exports",
            description = "Data exports that may contain contact lists or account data.",
            query = "filetype:csv OR filetype:xls OR filetype:xlsx \"email\"",
            category = DorkCategory.DOCUMENTS
        ),
        GoogleDork(
            title = "Staff Email Addresses",
            description = "Surfaces published email addresses, which reveal the address format for phishing-risk assessments.",
            query = "intext:\"@\" (\"contact\" OR \"email\" OR \"staff\" OR \"team\")",
            category = DorkCategory.DOCUMENTS
        ),
        GoogleDork(
            title = "Employees on LinkedIn",
            description = "Lists public LinkedIn profiles that mention the target.",
            query = "site:linkedin.com/in",
            category = DorkCategory.DOCUMENTS,
            scope = DorkScope.KEYWORD
        ),

        // Code & Paste Sites
        GoogleDork(
            title = "GitHub Mentions of Secrets",
            description = "Finds repositories mentioning the target next to credential keywords.",
            query = "site:github.com (\"password\" OR \"api_key\" OR \"secret\" OR \"token\")",
            category = DorkCategory.THIRD_PARTY,
            scope = DorkScope.KEYWORD
        ),
        GoogleDork(
            title = "GitLab & Bitbucket Mentions",
            description = "Finds public repositories and snippets on other code hosts that mention the target.",
            query = "site:gitlab.com OR site:bitbucket.org",
            category = DorkCategory.THIRD_PARTY,
            scope = DorkScope.KEYWORD
        ),
        GoogleDork(
            title = "Paste Sites",
            description = "Finds pastes mentioning the target, a common place for leaked credentials and configs.",
            query = "site:pastebin.com OR site:paste.ee OR site:ghostbin.com OR site:rentry.co",
            category = DorkCategory.THIRD_PARTY,
            scope = DorkScope.KEYWORD
        ),
        GoogleDork(
            title = "Public Trello Boards",
            description = "Finds public Trello boards mentioning the target; teams often paste credentials into cards.",
            query = "site:trello.com",
            category = DorkCategory.THIRD_PARTY,
            scope = DorkScope.KEYWORD
        ),
        GoogleDork(
            title = "Stack Overflow Questions",
            description = "Developers asking for help sometimes paste internal hostnames, code and config.",
            query = "site:stackoverflow.com",
            category = DorkCategory.THIRD_PARTY,
            scope = DorkScope.KEYWORD
        ),
        GoogleDork(
            title = "Shared Google Docs & Drive Files",
            description = "Finds publicly shared documents that mention the target.",
            query = "site:docs.google.com OR site:drive.google.com",
            category = DorkCategory.THIRD_PARTY,
            scope = DorkScope.KEYWORD
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
