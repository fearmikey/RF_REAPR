package com.fearmikey.rf_reapr.domain.service

import com.fearmikey.rf_reapr.domain.model.FindingDraft
import com.fearmikey.rf_reapr.domain.model.FindingTools
import com.fearmikey.rf_reapr.domain.model.RiskLevel

enum class CloudProvider(val label: String, val enabledByDefault: Boolean) {
    AWS_S3("AWS S3", true),
    GCS("Google Cloud Storage", true),
    AZURE_BLOB("Azure Blob Storage", true),
    FIREBASE("Firebase Realtime Database", true),
    DO_SPACES("DigitalOcean Spaces", false),
    LINODE("Linode Object Storage", false)
}

enum class BucketState(val label: String) {
    /** Anyone can list (or read) the contents without credentials. */
    PUBLIC_LISTING("Publicly listable"),
    /** Answers anonymous requests with content, but does not list its objects. */
    PUBLIC_READ("Public content"),
    EXISTS_PRIVATE("Exists (access denied)"),
    NOT_FOUND("Not found"),
    UNKNOWN("Inconclusive")
}

/** One HTTP request the scanner will make. */
data class BucketProbe(
    val provider: CloudProvider,
    /** Bucket / account / database name being guessed. */
    val name: String,
    val url: String,
    /** Host that identifies the asset, used as the finding target. */
    val host: String,
    /** Extra label such as the region or the Azure container. */
    val qualifier: String? = null
)

/** What came back for a [BucketProbe]; [code] is null when the request never got an answer. */
data class ProbeResponse(
    val code: Int?,
    val body: String = "",
    val region: String? = null,
    /** The hostname did not resolve, which for per-bucket hostnames means the asset does not exist. */
    val dnsFailed: Boolean = false
)

data class BucketVerdict(
    val state: BucketState,
    val note: String,
    /** First object keys from a public listing. */
    val sampleKeys: List<String> = emptyList(),
    /** Set when S3 says the bucket lives in another region and should be re-probed there. */
    val redirectRegion: String? = null
)

/** Builds the list of candidate names and probe URLs for a target domain. */
object CloudBucketPlanner {

    val defaultKeywords = listOf(
        "dev", "prod", "staging", "test", "assets", "backup", "backups", "data", "logs",
        "static", "public", "private", "internal", "files", "media", "uploads", "web", "storage"
    )

    /** Container names tried on an Azure storage account that turned out to exist. */
    val azureContainers = listOf("\$web", "public", "files", "backup", "backups", "images", "assets", "data", "uploads", "media", "static")

    private val doRegions = listOf("nyc3", "sfo3", "ams3", "sgp1", "fra1")
    private val linodeRegions = listOf("us-east-1", "eu-central-1", "ap-south-1")
    private val secondLevelLabels = setOf("co", "com", "org", "net", "gov", "ac", "edu")

    /** Hostname of the target, or null when the input isn't a plausible domain. */
    fun normalizeDomain(input: String): String? {
        val host = input.trim().lowercase()
            .substringAfter("://").substringBefore('/').substringBefore('?').substringBefore(':')
            .removePrefix("*.").removePrefix("www.").trim('.')
        if (host.isEmpty() || host.length > 253 || '.' !in host) return null
        if (!host.split('.').all { label -> label.isNotEmpty() && label.length <= 63 && label.all { it.isLetterOrDigit() || it == '-' } }) return null
        return host
    }

    /** The organisation label: `example` for both `shop.example.com` and `example.co.uk`. */
    fun baseLabel(domain: String): String {
        val labels = domain.split('.')
        val tldIndex = labels.lastIndex
        val hasSecondLevel = labels.size >= 3 && labels[tldIndex].length == 2 && labels[tldIndex - 1] in secondLevelLabels
        return labels[(if (hasSecondLevel) tldIndex - 2 else tldIndex - 1).coerceAtLeast(0)]
    }

    fun parseKeywords(input: String): List<String> = input.split(',', ' ', '\n', ';')
        .map { it.trim().lowercase().filter { c -> c.isLetterOrDigit() || c == '-' }.trim('-') }
        .filter { it.isNotEmpty() && it.length <= 30 }
        .distinct()

    /**
     * Candidate bucket names for [domain]. Dots are never used: a dotted bucket name does not
     * match the provider's wildcard TLS certificate, so the probe would fail for the wrong reason.
     */
    fun candidateNames(domain: String, keywords: List<String> = defaultKeywords): List<String> {
        val base = baseLabel(domain)
        val full = domain.replace('.', '-')
        val names = linkedSetOf(base, full)
        keywords.forEach { kw ->
            names += "$base-$kw"
            names += "$kw-$base"
            names += "$base$kw"
        }
        return names.filter(::isValidBucketName)
    }

    /** S3-style naming rules, which are the strictest common denominator. */
    fun isValidBucketName(name: String): Boolean =
        name.length in 3..63 && name.all { it in 'a'..'z' || it.isDigit() || it == '-' } &&
            !name.startsWith('-') && !name.endsWith('-')

    /** Azure storage account names are 3-24 lowercase letters and digits, no hyphens. */
    fun azureAccountName(name: String): String? =
        name.replace("-", "").takeIf { it.length in 3..24 && it.all { c -> c in 'a'..'z' || c.isDigit() } }

    fun probes(names: List<String>, providers: Set<CloudProvider>): List<BucketProbe> = buildList {
        names.forEach { name ->
            if (CloudProvider.AWS_S3 in providers) add(s3Probe(name, null))
            if (CloudProvider.GCS in providers) {
                add(BucketProbe(CloudProvider.GCS, name, "https://storage.googleapis.com/$name?max-keys=5", "storage.googleapis.com/$name"))
            }
            if (CloudProvider.FIREBASE in providers) {
                listOf(name, "$name-default-rtdb").forEach { db ->
                    add(BucketProbe(CloudProvider.FIREBASE, db, "https://$db.firebaseio.com/.json?shallow=true", "$db.firebaseio.com"))
                }
            }
            if (CloudProvider.DO_SPACES in providers) doRegions.forEach { region ->
                val host = "$name.$region.digitaloceanspaces.com"
                add(BucketProbe(CloudProvider.DO_SPACES, name, "https://$host/?max-keys=5", host, region))
            }
            if (CloudProvider.LINODE in providers) linodeRegions.forEach { region ->
                val host = "$name.$region.linodeobjects.com"
                add(BucketProbe(CloudProvider.LINODE, name, "https://$host/?max-keys=5", host, region))
            }
        }
        if (CloudProvider.AZURE_BLOB in providers) {
            names.mapNotNull(::azureAccountName).distinct().forEach { account ->
                val host = "$account.blob.core.windows.net"
                add(BucketProbe(CloudProvider.AZURE_BLOB, account, "https://$host/?comp=list", host))
            }
        }
    }.distinctBy { it.url }

    fun s3Probe(name: String, region: String?): BucketProbe {
        val host = if (region == null) "$name.s3.amazonaws.com" else "$name.s3.$region.amazonaws.com"
        return BucketProbe(CloudProvider.AWS_S3, name, "https://$host/?max-keys=5", host, region)
    }

    /** Follow-up probes for an Azure storage account that exists: guess public containers. */
    fun azureContainerProbes(account: String): List<BucketProbe> = azureContainers.map { container ->
        val host = "$account.blob.core.windows.net"
        BucketProbe(
            CloudProvider.AZURE_BLOB, account,
            "https://$host/$container?restype=container&comp=list&maxresults=5", "$host/$container", container
        )
    }
}

/** Interprets a provider's answer to an anonymous probe. */
object CloudBucketClassifier {

    private val s3Like = setOf(CloudProvider.AWS_S3, CloudProvider.GCS, CloudProvider.DO_SPACES, CloudProvider.LINODE)

    fun classify(probe: BucketProbe, response: ProbeResponse): BucketVerdict {
        if (response.dnsFailed) return BucketVerdict(BucketState.NOT_FOUND, "Hostname does not resolve")
        val code = response.code ?: return BucketVerdict(BucketState.UNKNOWN, "No response")
        return when (probe.provider) {
            in s3Like -> classifyS3(probe, code, response)
            CloudProvider.AZURE_BLOB -> classifyAzure(probe, code, response)
            CloudProvider.FIREBASE -> classifyFirebase(code, response)
            else -> BucketVerdict(BucketState.UNKNOWN, "HTTP $code")
        }
    }

    private fun classifyS3(probe: BucketProbe, code: Int, response: ProbeResponse): BucketVerdict = when {
        code == 200 && "<ListBucketResult" in response.body ->
            BucketVerdict(BucketState.PUBLIC_LISTING, "Anonymous listing allowed", keys(response.body, "Key"))
        code == 200 -> BucketVerdict(BucketState.PUBLIC_READ, "Serves content anonymously (e.g. static website hosting)")
        // S3 answers 301/400 with the real region for buckets that live elsewhere.
        (code == 301 || code == 307 || code == 400) && response.region != null && probe.provider == CloudProvider.AWS_S3 &&
            probe.qualifier != response.region ->
            BucketVerdict(BucketState.EXISTS_PRIVATE, "Bucket is in ${response.region}", redirectRegion = response.region)
        code == 301 || code == 307 -> BucketVerdict(BucketState.EXISTS_PRIVATE, "Exists in another region")
        code == 403 -> BucketVerdict(
            BucketState.EXISTS_PRIVATE,
            if ("AllAccessDisabled" in response.body) "Exists, all access disabled" else "Exists, anonymous access denied"
        )
        code == 404 || code == 400 || code == 410 -> BucketVerdict(BucketState.NOT_FOUND, "No such bucket")
        else -> BucketVerdict(BucketState.UNKNOWN, "HTTP $code")
    }

    private fun classifyAzure(probe: BucketProbe, code: Int, response: ProbeResponse): BucketVerdict {
        if (probe.qualifier != null) {
            // Container probe.
            return if (code == 200 && "<EnumerationResults" in response.body) {
                BucketVerdict(BucketState.PUBLIC_LISTING, "Container \"${probe.qualifier}\" allows anonymous listing", keys(response.body, "Name"))
            } else {
                BucketVerdict(BucketState.NOT_FOUND, "Container not public")
            }
        }
        // Account probe: the hostname resolved, so the storage account exists. Listing the
        // account's containers is never anonymous, so any HTTP answer means "exists".
        return BucketVerdict(BucketState.EXISTS_PRIVATE, "Storage account exists")
    }

    private fun classifyFirebase(code: Int, response: ProbeResponse): BucketVerdict = when (code) {
        200 -> BucketVerdict(
            BucketState.PUBLIC_LISTING,
            if (response.body.trim() == "null") "Readable without authentication (currently empty)"
            else "Readable without authentication",
            Regex("\"([^\"]{1,60})\"\\s*:").findAll(response.body).map { it.groupValues[1] }.take(5).toList()
        )
        401, 403 -> BucketVerdict(BucketState.EXISTS_PRIVATE, "Exists, rules deny anonymous reads")
        423 -> BucketVerdict(BucketState.EXISTS_PRIVATE, "Exists, database deactivated")
        404 -> BucketVerdict(BucketState.NOT_FOUND, "No such database")
        else -> BucketVerdict(BucketState.UNKNOWN, "HTTP $code")
    }

    private fun keys(xml: String, tag: String): List<String> =
        Regex("<$tag>([^<]{1,200})</$tag>").findAll(xml).map { it.groupValues[1] }.take(5).toList()

    /** Only anonymously readable assets are findings; a private bucket merely existing is not. */
    fun toDraft(probe: BucketProbe, verdict: BucketVerdict): FindingDraft? {
        if (verdict.state != BucketState.PUBLIC_LISTING) return null
        val firebase = probe.provider == CloudProvider.FIREBASE
        val sample = if (verdict.sampleKeys.isEmpty()) "" else
            "\n${if (firebase) "Top-level keys" else "Sample objects"}: ${verdict.sampleKeys.joinToString(", ")}"
        return FindingDraft(
            FindingTools.CLOUD, probe.host, RiskLevel.HIGH,
            if (firebase) "Firebase database readable without authentication"
            else "Publicly listable ${probe.provider.label} ${if (probe.provider == CloudProvider.AZURE_BLOB) "container" else "bucket"}",
            "${verdict.note}.$sample\nThe name was guessed from the target domain: confirm the asset belongs to the " +
                "organisation before reporting it, and do not download its contents without authorisation.",
            if (firebase) "Restrict the database rules so reads require authentication."
            else "Remove anonymous list/read access (block public access) and review the objects for sensitive data."
        )
    }
}
