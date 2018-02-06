package hu.nevermind.notecalc

import hu.nevermind.lib.MathJs

class TokenListSimplifier {
    internal fun mergeCompoundUnits(tokens: List<Token>): List<Token> {
        var restTokens = tokens
        val output = arrayListOf<Token>()
        var prevToken: Token = Token.StringLiteral("")
        var codeSmell = false
        while (restTokens.isNotEmpty()) {
            val token = restTokens.first()
            restTokens = when (token) {
                is Token.NumberLiteral -> {
                    output.add(token)
                    restTokens.drop(1)
                }
                is Token.Variable -> {
                    output.add(token)
                    restTokens.drop(1)
                }
                is Token.StringLiteral -> {
                    output.add(token)
                    restTokens.drop(1)
                }
                is Token.Operator -> {
                    output.add(token)
                    restTokens.drop(1)
                }
                is Token.UnitOfMeasure -> {
                    if (prevToken is Token.Operator || prevToken is Token.StringLiteral || prevToken is Token.NumberLiteral || prevToken is Token.Variable) {
                        val compundUnitResult = parseCompundUnit(restTokens)
                        if (compundUnitResult != null) {
                            val tokenCountInThisUnit = compundUnitResult.tokens.size
                            restTokens = restTokens.drop(tokenCountInThisUnit)
                            output.add(compundUnitResult)
                            codeSmell = true
                        }
                    }
                    if (codeSmell) {
                        restTokens
                    } else {
                        output.add(token)
                        restTokens.drop(1)
                    }
                }
            }
            prevToken = token
            codeSmell = false
        }
        return output
    }

    private fun parseCompundUnit(tokens: List<Token>): Token.UnitOfMeasure? {
        if (tokens.size <= 1) {
            return null
        }
        var prevToken: Token = Token.StringLiteral("")
        val tokensThatTogetherMayFormACompundUnit = tokens.takeWhile {
            val result = when (it) {
                is Token.Operator -> it.operator in arrayOf("*", "/", "^", "(", ")")
                is Token.NumberLiteral -> {
                    prevToken is Token.Operator && (prevToken as Token.Operator).operator == "^"
                }
                is Token.UnitOfMeasure -> true
                else -> false
            }
            prevToken = it
            result
        }
        if (tokensThatTogetherMayFormACompundUnit.isNotEmpty()) {
            val maybeCompoundUnit = tryFindCorrectCompoundUnit(tokensThatTogetherMayFormACompundUnit)
            return maybeCompoundUnit
        }
        return null
    }

    private fun tryFindCorrectCompoundUnit(tokenGroup: List<Token>): Token.UnitOfMeasure? {
        val expressionString = tokenGroup.joinToString(transform = Token::asString, separator = "")
        try {
            val compundUnit = MathJs.unit(null, expressionString)
            val compundUnitname = compundUnit.formatUnits()
//            if (compundUnitname != expressionString) {
//                return null
//            }
            return Token.UnitOfMeasure(compundUnitname, tokenGroup)
        } catch (e: Throwable) {
            return tryFindCorrectCompoundUnit(tokenGroup.dropLast(1))
        }
    }


}