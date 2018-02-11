package hu.nevermind.notecalc

import hu.nevermind.lib.*


object ShuntingYard {

    const val UNARY_MINUS_TOKEN_SYMBOL: String = "unary-"
    const val UNARY_PLUS_TOKEN_SYMBOL: String = "unary+"

    fun shuntingYard(inputTokens: List<Token>, functionNames: Iterable<String>): List<Token> {
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
                    } else if (inputToken.operator == "=") {
                        // the left side of the '=' might be a variable name like 'km' or 'm'
                        // don't put '=' on the stack and  remove the lhs of '=' from it
                        ShuntingYardStacks(operatorStack, output.dropLast(1))
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
        return if (incomingOpPrecedence < topOfStackPrecedence || incomingPrecLeftAssocAndEqual) {
            val last = operatorStack.last()
            shuntingYardOperatorRule(operatorStack.dropLast(1), applyOrPutOperatorOnTheStack(last, output), incomingOperatorName)
        } else if (topOfOpStack.operator == "in") {
            // 'in' has a lowest precedence to avoid writing a lot of parenthesis,
            // but because of that it would be put at the very end of the output stack.
            // This code puts it into the output
            val last = operatorStack.last()
            ShuntingYardStacks(operatorStack.dropLast(1), applyOrPutOperatorOnTheStack(last, output))
        } else {
            ShuntingYardStacks(operatorStack, output)
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
            "+" to OperatorInfo(2, "left") { operator, outputStack -> outputStack + operator },
            "-" to OperatorInfo(2, "left") { operator, outputStack -> outputStack + operator },
            "in" to OperatorInfo(1, "left") { operator, outputStack -> outputStack + operator },
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
}