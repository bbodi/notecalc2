package hu.nevermind.notecalc

import hu.nevermind.lib.MathJs
import hu.nevermind.lib.isValuelessUnit

class TokenParser {

    internal fun parse(text: String, variableNames: Iterable<String> = emptyList(), functionNames: Iterable<String> = emptyList()): List<Token> {
        val tokens = arrayListOf<Token>()
        var str = text.trim()
        val sortedVariableNames = variableNames.sortedByDescending { it.length }
        val sortedFunctionNames = functionNames.sortedByDescending { it.length }
        while (str.isNotEmpty()) {
            val originalLength = str.length
            val tokenAndRest = tryExtractToken(str,
                    { str -> tryParseFunctionInvocation(str, sortedFunctionNames) },
                    { str -> tryParseVariableName(str, sortedVariableNames) },
                    ::tryExtractOperator,
                    ::tryExtractNumberLiteral,
                    ::tryExtractUnit,
                    ::tryExtractStringLiteral
            )
            if (tokenAndRest != null) {
                str = tokenAndRest.second.trim()
            } else {
                break
            }
            val token = tokenAndRest.first
            val prevToken = tokens.lastOrNull()
            // TODO: I don't like it here
            if (prevToken is Token.NumberLiteral && token is Token.StringLiteral && token.str.length == 1 && token.str.first() in "kM") {
                val newNumber = when (token.str) {
                    "k" -> prevToken.num * 1_000
                    "M" -> prevToken.num * 1_000_000
                    else -> error("can't happen")
                }
                val newStringRepresentation = prevToken.originalStringRepresentation + token.str
                tokens.removeAt(tokens.lastIndex)
                tokens.add(Token.NumberLiteral(newNumber, newStringRepresentation, prevToken.type))
            } else {
                tokens.add(token)
            }
            require(str.length < originalLength) { "$str: The length of the processing string must be shorter at the end of the block! $originalLength" }
        }
        return tokens
    }

}


private fun tryExtractToken(str: String, vararg tokenRecognizers: (String) -> Pair<Token, String>?): Pair<Token, String>? {
    tokenRecognizers.forEach {
        val tokenAndRest = it(str)
        if (tokenAndRest != null) {
            return tokenAndRest
        }
    }
    return null
}

private fun tryExtractNumberLiteral(str: String): Pair<Token, String>? {
    return if (str.startsWith("0b")) {
        val numStr = str.drop(2).takeWhile {
            it in "01 "
        }
        if (numStr.isEmpty()) {
            null
        } else {
            val num = MathJs.bignumber("0b"+numStr.replace(" ", ""))
            val rest = str.drop(2 + numStr.length)
            Token.NumberLiteral(num, "0b" + numStr, NumberType.Int) to rest
        }
    } else if (str.startsWith("0x")) {
        val numStr = str.drop(2).takeWhile {
            it in " 0123456789abcdefABCDEF"
        }
        if (numStr.isEmpty()) {
            null
        } else {
            val num = MathJs.bignumber("0x"+numStr.replace(" ", ""))
            val rest = str.drop(2 + numStr.length)
            Token.NumberLiteral(num, "0x" + numStr, NumberType.Int) to rest
        }
    } else if (str.first().let { c -> c in "0123456789" || c == '.' }) {
        val numStr = str.takeWhile {
            it in " 0123456789."
        }
        val decimalPointCount = numStr.count { it == '.' }
        if (decimalPointCount <= 1 && decimalPointCount != numStr.trimEnd().length) {
            val num = MathJs.bignumber(numStr.replace(" ", ""))
            val rest = str.drop(numStr.length)
            Token.NumberLiteral(num, numStr, if (decimalPointCount == 0) NumberType.Int else NumberType.Float) to rest
        } else null
    } else {
        null
    }
}

private fun tryExtractOperator(str: String): Pair<Token, String>? {
    return if (str.startsWith("on what is")) {
        Token.Operator("on what is") to str.drop("on what is".length)
    } else if (str.startsWith("of what is")) {
        Token.Operator("of what is") to str.drop("of what is".length)
    } else if (str.startsWith("off what is")) {
        Token.Operator("off what is") to str.drop("off what is".length)
    } else if (str.startsWith("as a % of")) {
        Token.Operator("as a % of") to str.drop("as a % of".length)
    } else if (str.first() in "=+-/%*^()&|!") {
        Token.Operator(str.first().toString()) to str.drop(1)
    } else if (str.startsWith("in ")) {
        Token.Operator("in") to str.drop(2)
    } else if (str.length > 1) {
        val twoChars = str.substring(0, 2)
        if (twoChars == "<<" || twoChars == ">>") {
            Token.Operator(twoChars) to str.drop(2)
        } else {
            null
        }
    } else {
        null
    }
}

private fun Char.isLetter(): Boolean = this.toLowerCase() != this.toUpperCase()
private fun Char.isDigit(): Boolean = this in "0123456789"

private fun tryExtractUnit(str: String): Pair<Token, String>? {
    val piece = str.takeWhile(Char::isLetter)
    return if (MathJs.isValuelessUnit(piece)) {
        Token.UnitOfMeasure(piece) to str.drop(piece.length)
    } else null
}

private fun tryExtractStringLiteral(str: String): Pair<Token, String>? {
    require(!str.first().isWhitespace()) { "At this point, str must already be trimmed!" }
    val extractedStr = str.first() + str.drop(1).takeWhile {
        !it.isDigit() && it !in "=%/+-*^() "
    }
    return Token.StringLiteral(extractedStr) to str.drop(extractedStr.length)
}

private fun tryParseVariableName(str: String, variableNames: Iterable<String>): Pair<Token, String>? {
    require(!str.first().isWhitespace()) { "At this point, str must already be trimmed!" }
    val variableName = variableNames.firstOrNull { str.startsWith(it) }
    return if (variableName != null) {
        Token.Variable(variableName) to str.drop(variableName.length)
    } else {
        null
    }
}

private fun tryParseFunctionInvocation(str: String, functionNames: Iterable<String>): Pair<Token, String>? {
    // TODO: remove these 'requries'
    require(!str.first().isWhitespace()) { "At this point, str must already be trimmed!" }
    val functionName = functionNames.firstOrNull {
        val nextChar = str.getOrNull(it.length)
        str.startsWith(it) && nextChar != null && (nextChar.isDigit() || nextChar == '(')
    }
    return if (functionName != null) {
        Token.StringLiteral(functionName) to str.drop(functionName.length)
    } else {
        null
    }
}