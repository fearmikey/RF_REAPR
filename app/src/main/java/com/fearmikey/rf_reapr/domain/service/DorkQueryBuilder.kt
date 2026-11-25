package com.fearmikey.rf_reapr.domain.service

import com.fearmikey.rf_reapr.domain.model.DorkCategory
import com.fearmikey.rf_reapr.domain.model.DorkScope
import com.fearmikey.rf_reapr.domain.model.GoogleDork
import java.net.URLEncoder

enum class SearchEngine(val label: String, private val urlPrefix: String, val note: String? = null) {
    GOOGLE("Google", "https://www.google.com/search?q="),
    BING("Bing", "https://www.bing.com/search?q=", "Bing ignores inurl: and treats intext: loosely."),
    DUCKDUCKGO("DuckDuckGo", "https://duckduckgo.com/?q=", "DuckDuckGo supports site:, filetype:, intitle: and inurl: only."),
    STARTPAGE("Startpage", "https://www.startpage.com/do/search?q=", "Startpage proxies Google results without a Google session.");

    fun url(query: String): String = urlPrefix + URLEncoder.encode(query, "UTF-8")
}

/** Builds search-engine queries from dork templates and an optional target domain. */
object DorkQueryBuilder {

    /** Operators offered as chips when composing a custom dork. */
    val operators = listOf("site:", "filetype:", "intitle:", "inurl:", "intext:", "ext:", "-", "\"\"", "OR", "*")

    /**
     * Reduces whatever the user typed (URL, `*.example.com`, `EXAMPLE.com/path`) to a bare
     * hostname. Returns "" for blank input and null when it isn't a usable host.
     */
    fun normalizeTarget(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return ""
        val host = trimmed.lowercase().substringAfter("://").substringBefore('/').substringBefore('?')
            .substringBefore(':').removePrefix("*.").trim('.')
        if (host.isEmpty() || host.length > 253) return null
        val valid = host.split('.').all { label ->
            label.isNotEmpty() && label.length <= 63 && label.all { it.isLetterOrDigit() || it == '-' }
        }
        return host.takeIf { valid }
    }

    /**
     * Combines [query] with the target. An empty target returns the query unchanged.
     * Queries with a top-level OR are parenthesised so the scope applies to every alternative.
     */
    fun build(query: String, target: String, scope: DorkScope = DorkScope.SITE): String {
        val q = query.trim()
        if (target.isBlank()) return q
        if (q.isEmpty()) return "site:$target"
        return when (scope) {
            DorkScope.SITE -> "site:$target ${group(q)}"
            DorkScope.KEYWORD -> "${group(q)} \"$target\""
        }
    }

    fun build(dork: GoogleDork, target: String): String = build(dork.query, target, dork.scope)

    /** A custom query the user typed: scoped by `site:` unless it already names a site. */
    fun buildCustom(query: String, target: String): String =
        build(query, target, if (Regex("(^|\\s|\\()site:").containsMatchIn(query)) DorkScope.KEYWORD else DorkScope.SITE)

    private fun group(query: String): String =
        if (hasTopLevelOr(query) && !isWrapped(query)) "($query)" else query

    private fun hasTopLevelOr(query: String): Boolean {
        var depth = 0
        var inQuotes = false
        var i = 0
        while (i < query.length) {
            val c = query[i]
            when {
                c == '"' -> inQuotes = !inQuotes
                inQuotes -> Unit
                c == '(' -> depth++
                c == ')' -> depth--
                depth == 0 && query.startsWith(" OR ", i) -> return true
            }
            i++
        }
        return false
    }

    /** True when the whole query is already one parenthesised group. */
    private fun isWrapped(query: String): Boolean {
        if (!query.startsWith("(") || !query.endsWith(")")) return false
        var depth = 0
        var inQuotes = false
        query.forEachIndexed { index, c ->
            when {
                c == '"' -> inQuotes = !inQuotes
                inQuotes -> Unit
                c == '(' -> depth++
                c == ')' -> {
                    depth--
                    if (depth == 0 && index != query.lastIndex) return false
                }
            }
        }
        return true
    }

    fun filter(dorks: List<GoogleDork>, category: DorkCategory?, text: String): List<GoogleDork> {
        val needle = text.trim()
        return dorks.filter { dork ->
            (category == null || dork.category == category) &&
                (needle.isEmpty() || dork.title.contains(needle, true) || dork.description.contains(needle, true) ||
                    dork.query.contains(needle, true))
        }
    }
}
