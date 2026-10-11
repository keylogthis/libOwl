package org.thebottle.libOwl.data

object Isbn {
    /** Returns a valid ISBN-13 (digits only) for an ISBN-10 or ISBN-13 input (hyphens/spaces allowed), else null. */
    fun normalize(raw: String?): String? {
        val s = raw?.filter { it.isDigit() || it == 'X' || it == 'x' }?.uppercase() ?: return null
        return when (s.length) {
            13 -> if ((s.startsWith("978") || s.startsWith("979")) && s.all { it.isDigit() } && valid13(s)) s else null
            10 -> if (valid10(s)) to13(s) else null
            else -> null
        }
    }

    private fun check13(first12: String): Int {
        val sum = first12.mapIndexed { i, c -> (c - '0') * if (i % 2 == 0) 1 else 3 }.sum()
        return (10 - sum % 10) % 10
    }

    private fun valid13(s: String) = check13(s.substring(0, 12)) == s[12] - '0'

    private fun valid10(s: String): Boolean {
        var sum = 0
        for (i in 0 until 10) {
            val c = s[i]
            val v = when {
                c.isDigit() -> c - '0'
                c == 'X' && i == 9 -> 10
                else -> return false
            }
            sum += (10 - i) * v
        }
        return sum % 11 == 0
    }

    private fun to13(isbn10: String): String {
        val first12 = "978" + isbn10.substring(0, 9)
        return first12 + check13(first12)
    }
}
