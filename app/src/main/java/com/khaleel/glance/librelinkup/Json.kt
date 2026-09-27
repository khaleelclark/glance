package com.khaleel.glance.librelinkup

import java.math.BigDecimal

/** Small, strict JSON reader kept private to the unofficial protocol adapter. */
internal sealed interface JsonValue {
    data class Object(val values: Map<String, JsonValue>) : JsonValue
    data class Array(val values: List<JsonValue>) : JsonValue
    data class StringValue(val value: String) : JsonValue
    data class NumberValue(val value: BigDecimal) : JsonValue
    data class BooleanValue(val value: Boolean) : JsonValue
    data object NullValue : JsonValue
}

internal class JsonParseException : Exception()

internal object Json {
    fun parse(source: String): JsonValue {
        val parser = Parser(source)
        val value = parser.readValue()
        parser.skipWhitespace()
        if (!parser.atEnd()) throw JsonParseException()
        return value
    }

    fun quote(value: String): String = buildString(value.length + 2) {
        append('"')
        value.forEach { character ->
            when (character) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000c' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (character < ' ') {
                    append("\\u")
                    append(character.code.toString(16).padStart(4, '0'))
                } else {
                    append(character)
                }
            }
        }
        append('"')
    }

    private class Parser(private val source: String) {
        private var position = 0

        fun atEnd(): Boolean = position == source.length

        fun skipWhitespace() {
            while (position < source.length && source[position] in " \t\r\n") position++
        }

        fun readValue(): JsonValue {
            skipWhitespace()
            if (atEnd()) throw JsonParseException()
            return when (source[position]) {
                '{' -> readObject()
                '[' -> readArray()
                '"' -> JsonValue.StringValue(readString())
                't' -> readLiteral("true", JsonValue.BooleanValue(true))
                'f' -> readLiteral("false", JsonValue.BooleanValue(false))
                'n' -> readLiteral("null", JsonValue.NullValue)
                '-', in '0'..'9' -> readNumber()
                else -> throw JsonParseException()
            }
        }

        private fun readObject(): JsonValue.Object {
            position++
            skipWhitespace()
            val result = linkedMapOf<String, JsonValue>()
            if (consume('}')) return JsonValue.Object(result)
            while (true) {
                skipWhitespace()
                if (atEnd() || source[position] != '"') throw JsonParseException()
                val name = readString()
                skipWhitespace()
                requireCharacter(':')
                if (result.put(name, readValue()) != null) throw JsonParseException()
                skipWhitespace()
                if (consume('}')) return JsonValue.Object(result)
                requireCharacter(',')
            }
        }

        private fun readArray(): JsonValue.Array {
            position++
            skipWhitespace()
            val result = mutableListOf<JsonValue>()
            if (consume(']')) return JsonValue.Array(result)
            while (true) {
                result += readValue()
                skipWhitespace()
                if (consume(']')) return JsonValue.Array(result)
                requireCharacter(',')
            }
        }

        private fun readString(): String {
            requireCharacter('"')
            val result = StringBuilder()
            while (!atEnd()) {
                val character = source[position++]
                when {
                    character == '"' -> return result.toString()
                    character == '\\' -> result.append(readEscape())
                    character < ' ' -> throw JsonParseException()
                    else -> result.append(character)
                }
            }
            throw JsonParseException()
        }

        private fun readEscape(): Char {
            if (atEnd()) throw JsonParseException()
            return when (val escaped = source[position++]) {
                '"', '\\', '/' -> escaped
                'b' -> '\b'
                'f' -> '\u000c'
                'n' -> '\n'
                'r' -> '\r'
                't' -> '\t'
                'u' -> readUnicodeEscape()
                else -> throw JsonParseException()
            }
        }

        private fun readUnicodeEscape(): Char {
            if (position + 4 > source.length) throw JsonParseException()
            val value = source.substring(position, position + 4).toIntOrNull(16)
                ?: throw JsonParseException()
            position += 4
            return value.toChar()
        }

        private fun readNumber(): JsonValue.NumberValue {
            val start = position
            if (consume('-') && atEnd()) throw JsonParseException()
            if (consume('0')) {
                if (!atEnd() && source[position].isDigit()) throw JsonParseException()
            } else {
                readDigits(requireAtLeastOne = true)
            }
            if (consume('.')) readDigits(requireAtLeastOne = true)
            if (!atEnd() && source[position] in "eE") {
                position++
                if (!atEnd() && source[position] in "+-") position++
                readDigits(requireAtLeastOne = true)
            }
            return try {
                JsonValue.NumberValue(BigDecimal(source.substring(start, position)))
            } catch (_: NumberFormatException) {
                throw JsonParseException()
            }
        }

        private fun readDigits(requireAtLeastOne: Boolean) {
            val start = position
            while (!atEnd() && source[position].isDigit()) position++
            if (requireAtLeastOne && start == position) throw JsonParseException()
        }

        private fun <T : JsonValue> readLiteral(literal: String, value: T): T {
            if (!source.startsWith(literal, position)) throw JsonParseException()
            position += literal.length
            return value
        }

        private fun consume(character: Char): Boolean {
            if (!atEnd() && source[position] == character) {
                position++
                return true
            }
            return false
        }

        private fun requireCharacter(character: Char) {
            if (!consume(character)) throw JsonParseException()
        }
    }
}

internal fun JsonValue.asObject(): JsonValue.Object? = this as? JsonValue.Object
internal fun JsonValue.asArray(): JsonValue.Array? = this as? JsonValue.Array
internal fun JsonValue.asString(): String? = (this as? JsonValue.StringValue)?.value
internal fun JsonValue.asBoolean(): Boolean? = (this as? JsonValue.BooleanValue)?.value
internal fun JsonValue.asNumber(): BigDecimal? = (this as? JsonValue.NumberValue)?.value
