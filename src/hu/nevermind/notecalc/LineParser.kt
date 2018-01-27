package hu.nevermind.notecalc

import hu.nevermind.lib.*

const val UNARY_MINUS_TOKEN_SYMBOL: String = "unary-"
const val UNARY_PLUS_TOKEN_SYMBOL: String = "unary+"

class LineParser {

    private val tokenParser: TokenParser = TokenParser()
    private val tokenListSimplifier: TokenListSimplifier = TokenListSimplifier()

    internal fun parse(functionNames: Iterable<String>, line: String, variableNames: Iterable<String>): ParsingResult {
        return try {
            val parsedTokens = tokenParser.parse(line, variableNames, functionNames)
            val tokensWithMergedCompoundUnits = tokenListSimplifier.mergeCompoundUnits(parsedTokens)
            val postFixNotationTokens = shuntingYard(tokensWithMergedCompoundUnits, functionNames)
            val highlightingInfos = createHighlightingNamesForTokens(parsedTokens)
            ParsingResult(parsedTokens, tokensWithMergedCompoundUnits, postFixNotationTokens, highlightingInfos)
        } catch (e: Throwable) {
            console.error(e)
            ParsingResult(emptyList(), emptyList(), emptyList(), emptyList())
        }
    }

    internal fun shuntingYard(inputTokens: List<Token>, functionNames: Iterable<String>): List<Token> {
        val output = listOf<Token>()
        val operatorStack = listOf<Token.Operator>()

        val (newOperatorStack, newOutput) = shuntingYardRec(inputTokens, operatorStack, output, functionNames, null)
        return newOperatorStack.asReversed().fold(newOutput) { output, operator ->
            applyOrPutOperatorOnTheStack(operator, output)
        }
    }

    private fun shuntingYardRec(inputTokens: List<Token>,
                                operatorStack: List<Token.Operator>,
                                output: List<Token>,
                                functionNames: Iterable<String>,
                                lastToken: Token?): ShuntingYardStacks {
        if (inputTokens.isEmpty()) {
            return ShuntingYardStacks(operatorStack, output)
        } else {
            val inputToken = inputTokens.first()
            val (newOperatorStack, newOutput) = when (inputToken) {
                is Token.Operator -> {
                    if (inputToken.operator == "(") {
                        ShuntingYardStacks((operatorStack + inputToken), output)
                    } else if (inputToken.operator == ")") {
                        val modifiedStacksAfterBracketRule = popAnythingUntilOpeningBracket(operatorStack, output)
                        modifiedStacksAfterBracketRule
                    } else if (inputToken.operator == "-") {
                        handleUnaryOperator(inputToken, lastToken, operatorStack, output, UNARY_MINUS_TOKEN_SYMBOL)
                    } else if (inputToken.operator == "+") {
                        handleUnaryOperator(inputToken, lastToken, operatorStack, output, UNARY_PLUS_TOKEN_SYMBOL)
                    } else {
                        val (newOperatorStack, newOutput) = shuntingYardOperatorRule(operatorStack, output, inputToken.operator)
                        ShuntingYardStacks(newOperatorStack + inputToken, newOutput)
                    }
                }
                is Token.NumberLiteral -> ShuntingYardStacks(operatorStack, output + inputToken)
                is Token.StringLiteral -> {
                    if (inputToken.str in functionNames) {
                        ShuntingYardStacks((operatorStack + Token.Operator("fun " + inputToken.str)), output + inputToken)
                    } else if (inputToken.str == ",") {
                        shuntingYardOperatorRule(operatorStack, output, ",")
                    } else {
                        ShuntingYardStacks(operatorStack, output + inputToken)
                    }
                }
                is Token.UnitOfMeasure -> {
                    shuntingYardOperatorRule(operatorStack, output, "unit")
                    ShuntingYardStacks(operatorStack, output + inputToken)
                }
                is Token.Variable -> ShuntingYardStacks(operatorStack, output + inputToken)
            }
            return shuntingYardRec(inputTokens.drop(1), newOperatorStack, newOutput, functionNames, inputToken)
        }
    }

    private fun handleUnaryOperator(inputToken: Token.Operator, lastToken: Token?, operatorStack: List<Token.Operator>, output: List<Token>, unaryOperatorSymbol: String): ShuntingYardStacks {
        return if (lastToken == null || (lastToken is Token.Operator && lastToken.operator !in ")%")) {
            ShuntingYardStacks(operatorStack + Token.Operator(unaryOperatorSymbol), output)
        } else {
            val (newOperatorStack, newOutput) = shuntingYardOperatorRule(operatorStack, output, inputToken.operator)
            ShuntingYardStacks(newOperatorStack + inputToken, newOutput)
        }
    }

    data class ShuntingYardStacks(val operatorStack: List<Token.Operator>, val output: List<Token>)

    private fun popAnythingUntilOpeningBracket(operatorStack: List<Token.Operator>, output: List<Token>): ShuntingYardStacks {
        if (operatorStack.isEmpty()) {
            return ShuntingYardStacks(operatorStack, output)
        } else {
            val topOfOpStack = operatorStack.last()
            val newOperatorStack = operatorStack.dropLast(1)
            if (topOfOpStack.operator == "(") {
                return ShuntingYardStacks(newOperatorStack, output)
            }
            val newOutput = applyOrPutOperatorOnTheStack(topOfOpStack, output)
            return popAnythingUntilOpeningBracket(newOperatorStack, newOutput)
        }
    }

    private fun shuntingYardOperatorRule(operatorStack: List<Token.Operator>, output: List<Token>, incomingOperatorName: String): ShuntingYardStacks {
        if (operatorStack.isEmpty()) {
            return ShuntingYardStacks(operatorStack, output)
        }
        val topOfOpStack = operatorStack.last()
        if (topOfOpStack.operator in "()") {
            return ShuntingYardStacks(operatorStack, output)
        }
        val incomingOpPrecedence = operatorInfosForUnits[incomingOperatorName]?.precedence ?: 0
        val topOfStackPrecedence = operatorInfosForUnits[topOfOpStack.operator]?.precedence ?: 0
        val assoc = operatorInfosForUnits[incomingOperatorName]?.associativity ?: "left"
        val incomingPrecLeftAssocAndEqual = assoc == "left" && incomingOpPrecedence == topOfStackPrecedence
        if (incomingOpPrecedence < topOfStackPrecedence || incomingPrecLeftAssocAndEqual) {
            val last = operatorStack.last()
            return shuntingYardOperatorRule(operatorStack.dropLast(1), applyOrPutOperatorOnTheStack(last, output), incomingOperatorName)
        } else {
            return ShuntingYardStacks(operatorStack, output)
        }
    }

    private fun applyOrPutOperatorOnTheStack(operator: Token.Operator, outputStack: List<Token>): List<Token> {
        val newOutputStackFromApplying = operatorInfosForUnits[operator.operator]?.func?.invoke(operator, outputStack)
        return newOutputStackFromApplying ?: outputStack + operator
    }

    private val operatorInfosForUnits = hashMapOf(
            "%" to OperatorInfo(6, "left") { operator, outputStack -> outputStack + operator },
            "^" to OperatorInfo(5, "right") { operator, outputStack ->
                val (lhs, rhs) = getTopTwoElements(outputStack)
                if (lhs is Token.UnitOfMeasure && rhs is Token.NumberLiteral) {
                    val poweredUnitname = MathJs.unit(null, lhs.unitName).pow(rhs.num).formatUnits()
                    outputStack.dropLast(2) + Token.UnitOfMeasure(poweredUnitname)
                } else {
                    outputStack + operator
                }
            },
            "unit" to OperatorInfo(4, "left") { operator, outputStack -> outputStack + operator },
            "=" to OperatorInfo(0, "left") { operator, outputStack -> outputStack + operator },
            "+" to OperatorInfo(2, "left") { operator, outputStack -> outputStack + operator },
            "-" to OperatorInfo(2, "left") { operator, outputStack -> outputStack + operator },
            UNARY_MINUS_TOKEN_SYMBOL to OperatorInfo(4, "left") { operator, outputStack -> outputStack + operator },
            UNARY_PLUS_TOKEN_SYMBOL to OperatorInfo(4, "left") { operator, outputStack -> outputStack + operator },
            "*" to OperatorInfo(3, "left") { operator, outputStack ->
                val (lhs, rhs) = getTopTwoElements(outputStack)
                if (lhs is Token.UnitOfMeasure && rhs is Token.UnitOfMeasure) {
                    val unitnameAfterOperation = MathJs.unit(null, lhs.unitName).multiply(MathJs.unit(null, rhs.unitName)).formatUnits()
                    outputStack.dropLast(2) + Token.UnitOfMeasure(unitnameAfterOperation)
                } else {
                    outputStack + operator
                }
            },
            "/" to OperatorInfo(3, "left") { operator, outputStack ->
                val (lhs, rhs) = getTopTwoElements(outputStack)
                if (lhs is Token.UnitOfMeasure && rhs is Token.UnitOfMeasure) {
                    val unitnameAfterOperation = MathJs.unit(null, lhs.unitName).divide(MathJs.unit(null, rhs.unitName)).formatUnits()
                    outputStack.dropLast(2) + Token.UnitOfMeasure(unitnameAfterOperation)
                } else {
                    outputStack + operator
                }
            }
    )

    private fun getTopTwoElements(outputStack: List<Token>): Pair<Token?, Token?> {
        val lastTwo = outputStack.takeLast(2)
        val lhs = lastTwo.getOrNull(0)
        val rhs = lastTwo.getOrNull(1)
        return Pair(lhs, rhs)
    }

    data class OperatorInfo(val precedence: Int, val associativity: String,
                            val func: (Token.Operator, List<Token>) -> List<Token>)

    private fun createHighlightingNamesForTokens(tokens: List<Token>): List<TextEvaulator.HighlightedText> {
        val highlightInfosForTokens = arrayListOf<TextEvaulator.HighlightedText>()
        tokens.forEach { token ->
            when (token) {
                is Token.NumberLiteral -> {
                    val strRepr = if (token.originalStringRepresentation.isEmpty()) token.num.toString() else token.originalStringRepresentation
                    highlightInfosForTokens.add(TextEvaulator.HighlightedText(strRepr, "number"))
                }
                is Token.Variable -> {
                    // TODO jelenleg ez a syntaxhilighterben van szinezve, nem lenne jobb itt? iugy h eltárolom a változó nevét is
                    highlightInfosForTokens.add(TextEvaulator.HighlightedText(token.variableName, "variable"))
                }
                is Token.StringLiteral -> {
                    highlightInfosForTokens.add(TextEvaulator.HighlightedText(token.str, "comment"))
                }
                is Token.Operator -> {
                    highlightInfosForTokens.add(TextEvaulator.HighlightedText(token.operator, "operator"))
                }
                is Token.UnitOfMeasure -> {
                    if (token.tokens.isEmpty()) {
                        highlightInfosForTokens.add(TextEvaulator.HighlightedText(token.unitName, "qualifier"))
                    } else {
                        token.tokens.forEach { subTokenInCompoundUnit ->
                            highlightInfosForTokens.add(TextEvaulator.HighlightedText(getStringRepresentation(subTokenInCompoundUnit), "qualifier"))
                        }
                    }
                }
            }
        }
        return highlightInfosForTokens
    }

    private fun getStringRepresentation(token: Token): String {
        val text = when (token) {
            is Token.UnitOfMeasure -> token.unitName
            is Token.NumberLiteral -> if (token.originalStringRepresentation.isEmpty()) token.num.toString() else token.originalStringRepresentation
            is Token.Operator -> token.operator
            is Token.StringLiteral -> token.str
            is Token.Variable -> token.variableName
        }
        return text
    }

    data class ParsingResult(
            val parsedTokens: List<Token>,
            val tokensWithMergedCompoundUnits: List<Token>,
            val postfixNotationTokens: List<Token>,
            val highlightedTexts: List<TextEvaulator.HighlightedText>
    )

}