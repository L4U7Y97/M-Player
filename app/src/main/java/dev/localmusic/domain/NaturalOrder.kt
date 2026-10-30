package dev.localmusic.domain

/**
 * Case-insensitive "natural" string order (pure Kotlin): runs of ASCII digits compare by numeric value, so
 * "Track 2" < "Track 10" and "01 Intro" == "1 intro" at this level. Everything else compares character by
 * character, case-insensitively (like [String.CASE_INSENSITIVE_ORDER]).
 *
 * Numeric runs of any length are supported (compared without converting to a number, so no overflow).
 * Equal numeric values with different zero padding ("01" vs "1") compare equal here; callers that need a
 * total order add their own tie-break (see [TrackSorter.FILE_NAME_TIE_BREAK]).
 */
object NaturalOrder : Comparator<String> {

    override fun compare(a: String, b: String): Int {
        var i = 0
        var j = 0
        while (i < a.length && j < b.length) {
            val ca = a[i]
            val cb = b[j]
            if (ca.isAsciiDigit() && cb.isAsciiDigit()) {
                val endA = digitRunEnd(a, i)
                val endB = digitRunEnd(b, j)
                val cmp = compareDigitRuns(a, i, endA, b, j, endB)
                if (cmp != 0) return cmp
                i = endA
                j = endB
            } else {
                val cmp = compareCharsIgnoreCase(ca, cb)
                if (cmp != 0) return cmp
                i++
                j++
            }
        }
        // One string is a prefix of the other (in natural terms): shorter remainder first.
        return (a.length - i).compareTo(b.length - j)
    }

    private fun Char.isAsciiDigit(): Boolean = this in '0'..'9'

    private fun digitRunEnd(s: String, start: Int): Int {
        var k = start
        while (k < s.length && s[k].isAsciiDigit()) k++
        return k
    }

    /** Compares two digit runs by numeric value (leading zeros ignored). */
    private fun compareDigitRuns(a: String, startA: Int, endA: Int, b: String, startB: Int, endB: Int): Int {
        var sa = startA
        var sb = startB
        while (sa < endA - 1 && a[sa] == '0') sa++
        while (sb < endB - 1 && b[sb] == '0') sb++
        val lenA = endA - sa
        val lenB = endB - sb
        if (lenA != lenB) return lenA.compareTo(lenB)
        for (k in 0 until lenA) {
            val d = a[sa + k].compareTo(b[sb + k])
            if (d != 0) return d
        }
        return 0
    }

    /** Same per-character rule as String.CASE_INSENSITIVE_ORDER. */
    private fun compareCharsIgnoreCase(c1: Char, c2: Char): Int {
        if (c1 == c2) return 0
        val u1 = c1.uppercaseChar()
        val u2 = c2.uppercaseChar()
        if (u1 == u2) return 0
        val l1 = u1.lowercaseChar()
        val l2 = u2.lowercaseChar()
        return if (l1 == l2) 0 else l1.compareTo(l2)
    }
}
