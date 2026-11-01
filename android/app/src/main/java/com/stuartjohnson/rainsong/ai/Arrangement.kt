package com.stuartjohnson.rainsong.ai

/**
 * Compact score from Muse Spark (text only — Meta Model API does not emit audio).
 * The phone turns this into a local backing pad until MusicGen exists.
 */
data class Arrangement(
    val title: String,
    val mood: String,
    val rootHz: Float,
    val brightness: Float,
    val density: Float,
    val prompt: String,
    val source: Source,
) {
    enum class Source { MuseSpark, LocalFallback }

    companion object {
        fun fromIntensity(summary: RainPatternSummary): Arrangement {
            val dps = summary.dropsPerSecond
            val (title, mood, root, bright) = when {
                dps < 0.4f -> Quad("Still air", "hushed, almost dry", 196f, 0.22f)
                dps < 2f -> Quad("Drizzle", "soft pentatonic, close mic rain", 220f, 0.38f)
                dps < 8f -> Quad("Steady rain", "warm dorian pad, patient pulse", 174f, 0.55f)
                else -> Quad("Downpour", "dark, dense, low drones", 130f, 0.72f)
            }
            return Arrangement(
                title = title,
                mood = mood,
                rootHz = root,
                brightness = bright,
                density = (dps / 12f).coerceIn(0.08f, 1f),
                prompt = "$mood. ${"%.1f".format(dps)} drops/s.",
                source = Source.LocalFallback,
            )
        }

        fun parseJson(raw: String, fallback: Arrangement): Arrangement {
            val json = extractObject(raw) ?: return fallback
            return Arrangement(
                title = stringField(json, "title") ?: fallback.title,
                mood = stringField(json, "mood") ?: fallback.mood,
                rootHz = floatField(json, "rootHz")?.coerceIn(80f, 880f) ?: fallback.rootHz,
                brightness = floatField(json, "brightness")?.coerceIn(0f, 1f) ?: fallback.brightness,
                density = floatField(json, "density")?.coerceIn(0f, 1f) ?: fallback.density,
                prompt = stringField(json, "prompt") ?: fallback.prompt,
                source = if (stringField(json, "source") == "local") {
                    Source.LocalFallback
                } else {
                    Source.MuseSpark
                },
            )
        }

        internal fun extractObject(raw: String): String? {
            val trimmed = raw.trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()
            val start = trimmed.indexOf('{')
            val end = trimmed.lastIndexOf('}')
            if (start < 0 || end <= start) return null
            return trimmed.substring(start, end + 1)
        }

        private fun stringField(json: String, key: String): String? {
            val quoted = Regex(""""$key"\s*:\s*"([^"]*)"""").find(json)?.groupValues?.get(1)
            return quoted?.takeIf { it.isNotBlank() }
        }

        private fun floatField(json: String, key: String): Float? {
            val num = Regex(""""$key"\s*:\s*(-?\d+(?:\.\d+)?)""").find(json)?.groupValues?.get(1)
            return num?.toFloatOrNull()
        }
    }
}

private data class Quad(
    val title: String,
    val mood: String,
    val root: Float,
    val bright: Float,
)
