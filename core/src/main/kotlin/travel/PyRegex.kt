package travel

import java.util.regex.Matcher
import java.util.regex.Pattern

/** Python re operations with Unicode classes and code-point offsets. */
class PyRegex(pattern: String, flags: Int = 0) {
    data class Group(val value: String?, val span: List<Int>)
    data class Match(val groups: List<Group>, val named: Map<String, Group>)
    private val names = Regex("\\(\\?P<([A-Za-z][A-Za-z0-9]*)>").findAll(pattern).map { it.groupValues[1] }.toList()
    private val translated = pattern.replace(Regex("\\(\\?P<([A-Za-z][A-Za-z0-9]*)>"), "(?<$1>")
        .replace(Regex("\\(\\?P=([A-Za-z][A-Za-z0-9]*)\\)"), "\\\\k<$1>")
    private val compiled = run {
        val caseFlag = if (flags and 2 != 0) Pattern.CASE_INSENSITIVE else 0
        try {
            Pattern.compile(translated, Pattern.UNICODE_CHARACTER_CLASS or Pattern.UNICODE_CASE or caseFlag)
        } catch (error: IllegalArgumentException) {
            if (error.message?.contains("UNICODE_CHARACTER_CLASS flag not supported") != true) throw error
            // Android's ICU regex engine uses Unicode character classes without this JVM-only flag.
            Pattern.compile(translated, caseFlag)
        }
    }
    private fun offset(input: String, index: Int): Int = if (index < 0) -1 else input.codePointCount(0, index)
    private fun group(input: String, value: String?, start: Int, end: Int) = Group(value, listOf(offset(input, start), offset(input, end)))
    private fun capture(input: String, matcher: Matcher): Match {
        val groups = (0..matcher.groupCount()).map { group(input, matcher.group(it), matcher.start(it), matcher.end(it)) }
        val named = names.associateWith { group(input, matcher.group(it), matcher.start(it), matcher.end(it)) }
        return Match(groups, named)
    }
    fun search(input: String): Match? = compiled.matcher(input).let { if (it.find()) capture(input, it) else null }
    fun match(input: String): Match? = compiled.matcher(input).let { if (it.lookingAt()) capture(input, it) else null }
    fun fullmatch(input: String): Match? = compiled.matcher(input).let { if (it.matches()) capture(input, it) else null }
    fun finditer(input: String): List<Match> {
        val matcher = compiled.matcher(input); val matches = mutableListOf<Match>()
        while (matcher.find()) matches.add(capture(input, matcher))
        return matches
    }
    fun split(input: String): List<String?> {
        val matcher = compiled.matcher(input); val pieces = mutableListOf<String?>(); var start = 0
        while (matcher.find()) { pieces.add(input.substring(start, matcher.start())); for (i in 1..matcher.groupCount()) pieces.add(matcher.group(i)); start = matcher.end() }
        pieces.add(input.substring(start)); return pieces
    }
    // Python replacement templates use \1 and \g<name>, not Java replacement syntax.
    fun sub(input: String, replacement: String = "<X>"): String {
        val matcher = compiled.matcher(input); val result = StringBuilder(); var start = 0
        while (matcher.find()) {
            result.append(input, start, matcher.start())
            var i = 0
            while (i < replacement.length) {
                val c = replacement[i++]
                if (c != '\\') { result.append(c); continue }
                require(i < replacement.length) { "Trailing replacement escape" }
                val next = replacement[i++]
                when {
                    next in '1'..'9' -> {
                        val index = if (i < replacement.length && replacement[i].isDigit()) "$next${replacement[i++]}".toInt() else next.digitToInt()
                        result.append(matcher.group(index) ?: "")
                    }
                    next == 'g' && i < replacement.length && replacement[i] == '<' -> {
                        val end = replacement.indexOf('>', i + 1); require(end >= 0)
                        val name = replacement.substring(i + 1, end)
                        val index = name.toIntOrNull()
                        result.append((if (index != null) matcher.group(index) else matcher.group(name)) ?: "")
                        i = end + 1
                    }
                    next == '\\' -> result.append('\\')
                    next == 'n' -> result.append('\n')
                    next == 't' -> result.append('\t')
                    else -> error("Unsupported replacement escape: $next")
                }
            }
            start = matcher.end()
        }
        result.append(input, start, input.length)
        return result.toString()
    }
}
