package com.revcore.android.data.map

/**
 * Small JSON reader for the synthetic MAP format.
 *
 * The public map template uses only objects, arrays, strings, numbers, booleans,
 * and null. This parser keeps the library independent of any JSON dependency and
 * avoids silently accepting malformed input. It is not a general-purpose JSON
 * implementation and does not preserve number formatting.
 */
internal object MiniJson {

    fun parse(text: String): Any? {
        val parser = Parser(text)
        val value = parser.readValue()
        parser.skipWhitespace()
        if (!parser.atEnd()) {
            throw IllegalArgumentException("Data tambahan setelah nilai JSON pada posisi ${parser.position()}")
        }
        return value
    }

    private class Parser(private val source: String) {
        private var index = 0

        fun position(): Int = index
        fun atEnd(): Boolean = index >= source.length
        fun skipWhitespace() {
            while (index < source.length && source[index].isWhitespace()) index++
        }

        fun readValue(): Any? {
            skipWhitespace()
            if (atEnd()) throw IllegalArgumentException("JSON berakhir sebelum nilai dimulai")
            return when (val c = source[index]) {
                '{' -> readObject()
                '[' -> readArray()
                '"' -> readString()
                't' -> readLiteral("true", true)
                'f' -> readLiteral("false", false)
                'n' -> readLiteral("null", null)
                else -> if (c == '-' || c.isDigit()) readNumber()
                else throw IllegalArgumentException("Karakter tak terduga '$c' pada posisi $index")
            }
        }

        private fun readObject(): Map<String, Any?> {
            expect('{')
            val result = LinkedHashMap<String, Any?>()
            skipWhitespace()
            if (peek() == '}') {
                index++
                return result
            }
            while (true) {
                skipWhitespace()
                val key = readString()
                skipWhitespace()
                expect(':')
                result[key] = readValue()
                skipWhitespace()
                when (peek()) {
                    ',' -> index++
                    '}' -> {
                        index++
                        return result
                    }
                    else -> throw IllegalArgumentException("Diharapkan ',' atau '}' pada posisi $index")
                }
            }
        }

        private fun readArray(): List<Any?> {
            expect('[')
            val result = mutableListOf<Any?>()
            skipWhitespace()
            if (peek() == ']') {
                index++
                return result
            }
            while (true) {
                result.add(readValue())
                skipWhitespace()
                when (peek()) {
                    ',' -> index++
                    ']' -> {
                        index++
                        return result
                    }
                    else -> throw IllegalArgumentException("Diharapkan ',' atau ']' pada posisi $index")
                }
            }
        }

        private fun readString(): String {
            expect('"')
            val builder = StringBuilder()
            while (index < source.length) {
                val c = source[index++]
                when (c) {
                    '"' -> return builder.toString()
                    '\\' -> {
                        if (atEnd()) throw IllegalArgumentException("Escape terpotong")
                        when (val escaped = source[index++]) {
                            '"' -> builder.append('"')
                            '\\' -> builder.append('\\')
                            '/' -> builder.append('/')
                            'b' -> builder.append('\b')
                            'f' -> builder.append('\u000C')
                            'n' -> builder.append('\n')
                            'r' -> builder.append('\r')
                            't' -> builder.append('\t')
                            'u' -> {
                                if (index + 4 > source.length) {
                                    throw IllegalArgumentException("Escape unicode terpotong")
                                }
                                val hex = source.substring(index, index + 4)
                                val code = hex.toIntOrNull(16)
                                    ?: throw IllegalArgumentException("Escape unicode tidak valid: $hex")
                                builder.append(code.toChar())
                                index += 4
                            }
                            else -> throw IllegalArgumentException("Escape tidak dikenal: \\$escaped")
                        }
                    }
                    else -> {
                        if (c < ' ') throw IllegalArgumentException("Karakter kontrol dalam string")
                        builder.append(c)
                    }
                }
            }
            throw IllegalArgumentException("String JSON tidak ditutup")
        }

        private fun readNumber(): Double {
            val start = index
            if (peek() == '-') index++
            if (atEnd()) throw IllegalArgumentException("Angka terpotong")
            if (peek() == '0') {
                index++
            } else {
                if (!peek().isDigit()) throw IllegalArgumentException("Angka tidak valid pada posisi $index")
                while (!atEnd() && peek().isDigit()) index++
            }
            if (!atEnd() && peek() == '.') {
                index++
                if (atEnd() || !peek().isDigit()) throw IllegalArgumentException("Pecahan tidak valid")
                while (!atEnd() && peek().isDigit()) index++
            }
            if (!atEnd() && (peek() == 'e' || peek() == 'E')) {
                index++
                if (!atEnd() && (peek() == '+' || peek() == '-')) index++
                if (atEnd() || !peek().isDigit()) throw IllegalArgumentException("Eksponen tidak valid")
                while (!atEnd() && peek().isDigit()) index++
            }
            val raw = source.substring(start, index)
            return raw.toDoubleOrNull()
                ?: throw IllegalArgumentException("Angka tidak valid: $raw")
        }

        private fun readLiteral(literal: String, value: Any?): Any? {
            if (index + literal.length > source.length ||
                source.substring(index, index + literal.length) != literal
            ) {
                throw IllegalArgumentException("Literal tidak valid pada posisi $index")
            }
            index += literal.length
            return value
        }

        private fun peek(): Char {
            if (atEnd()) throw IllegalArgumentException("JSON berakhir tak terduga")
            return source[index]
        }

        private fun expect(expected: Char) {
            skipWhitespace()
            if (atEnd() || source[index] != expected) {
                throw IllegalArgumentException("Diharapkan '$expected' pada posisi $index")
            }
            index++
        }
    }
}
