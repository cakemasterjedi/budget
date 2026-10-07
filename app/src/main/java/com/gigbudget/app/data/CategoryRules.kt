package com.gigbudget.app.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Niome's changes to categories: renamed buckets, renamed / re-emojied / moved / hidden categories,
 * and categories she added. Expenses keep storing the original category key, so renaming never
 * touches saved data. Saved with the settings (and so included in backups).
 */
data class CategoryRules(
    val bucketNames: Map<Bucket, String> = emptyMap(),
    val bucketEmojis: Map<Bucket, String> = emptyMap(),
    /** Which bucket a category belongs to, when it isn't the built-in one (and for every custom category). */
    val categoryBuckets: Map<String, Bucket> = emptyMap(),
    val categoryLabels: Map<String, String> = emptyMap(),
    val categoryEmojis: Map<String, String> = emptyMap(),
    val customCategories: List<String> = emptyList(),
    /** Hidden from the pick lists; spending already in them still counts. */
    val hidden: Set<String> = emptySet(),
) {
    fun toJson(): String = JSONObject().apply {
        put("bucketNames", bucketNames.mapKeys { it.key.name }.toJson())
        put("bucketEmojis", bucketEmojis.mapKeys { it.key.name }.toJson())
        put("categoryBuckets", categoryBuckets.mapValues { it.value.name }.toJson())
        put("categoryLabels", categoryLabels.toJson())
        put("categoryEmojis", categoryEmojis.toJson())
        put("customCategories", JSONArray(customCategories))
        put("hidden", JSONArray(hidden.toList()))
    }.toString()

    companion object {
        /**
         * The rules in effect, read by [Bucket.of] and [Categories]. Set by the settings store whenever
         * settings load or change; screens recompose on those changes because they collect settings.
         */
        @Volatile
        var current = CategoryRules()

        fun fromJson(json: String?): CategoryRules {
            if (json.isNullOrBlank()) return CategoryRules()
            val o = JSONObject(json)
            fun strings(key: String) = o.optJSONObject(key)?.let { m -> m.keys().asSequence().associateWith { m.getString(it) } } ?: emptyMap()
            fun list(key: String) = o.optJSONArray(key)?.let { a -> List(a.length()) { a.getString(it) } } ?: emptyList()
            fun bucket(name: String) = Bucket.entries.firstOrNull { it.name == name }
            return CategoryRules(
                bucketNames = strings("bucketNames").mapNotNull { (k, v) -> bucket(k)?.let { it to v } }.toMap(),
                bucketEmojis = strings("bucketEmojis").mapNotNull { (k, v) -> bucket(k)?.let { it to v } }.toMap(),
                categoryBuckets = strings("categoryBuckets").mapNotNull { (k, v) -> bucket(v)?.let { k to it } }.toMap(),
                categoryLabels = strings("categoryLabels"),
                categoryEmojis = strings("categoryEmojis"),
                customCategories = list("customCategories"),
                hidden = list("hidden").toSet(),
            )
        }

        private fun Map<String, String>.toJson() = JSONObject().also { o -> forEach { (k, v) -> o.put(k, v) } }
    }
}
