package hu.nevermind.notecalc

import hu.nevermind.lib.*
import kotlin.js.Math

class TokenListEvaulator {

    fun processPostfixNotationStack(tokens: List<Token>, variables: Map<String, Operand>, functions: Map<String, TextEvaulator.FunctionDefinition>): Operand? {
        console.log("processPostfixNotationStack: $tokens, variables: $variables, functions: $functions")
        val quantitativeStack = processPostfixNotationStackRec(listOf<Operand>(),
                tokens,
                null,
                variables,
                functions,
                1)
        console.log("================= END =================")
        return quantitativeStack.lastOrNull()
    }

    fun processPostfixNotationStackRec(quantitativeStack: List<Operand>,
                                       tokens: List<Token>,
                                       lastUnit: String?,
                                       variables: Map<String, Operand>,
                                       functions: Map<String, TextEvaulator.FunctionDefinition>,
                                       deepness: Int): List<Operand> {
        console.log("""    quantitativeStack: $quantitativeStack,
            |    processPostfixNotationStack: $tokens
            |    lastUnit: $lastUnit
            |    variables: $variables
            |    functions: $functions
            |    deepness: $deepness""".trimMargin())
        if (tokens.isEmpty() || deepness > 100) {
            return quantitativeStack
        }
        var lastUnit = lastUnit
        val incomingToken = tokens.first()
        val modifiedQuantitativeStack: List<Operand> = when (incomingToken) {
            is Token.NumberLiteral -> quantitativeStack + (Operand.Number(incomingToken.num, incomingToken.type))
            is Token.Variable -> {
                val variable = variables[incomingToken.variableName]
                if (variable != null) {
                    quantitativeStack + variable
                } else quantitativeStack
            }
            is Token.UnitOfMeasure -> {
                val topOfStack = quantitativeStack.lastOrNull()
                if (topOfStack != null && topOfStack is Operand.Number) {
                    quantitativeStack.dropLast(1) + addUnitToTheTopOfStackEntry(topOfStack, incomingToken)
                } else {
                    lastUnit = incomingToken.unitName
                    quantitativeStack
                }
            }
            is Token.Operator -> {
                if (incomingToken.operator.startsWith("fun ")) {
                    val funcName = incomingToken.operator.drop("fun ".length)
                    val functionDef = functions[funcName]
                    if (functionDef != null && quantitativeStack.size >= functionDef.argumentNames.size) {
                        val arguments = quantitativeStack.takeLast(functionDef.argumentNames.size)
                        val methodScope = HashMap(variables + functionDef.argumentNames.zip(arguments).toMap())
                        console.log("FUNC CALL $funcName($arguments)")
                        val resultOperand = functionDef.tokenLines.map { funcLineAndItsTokens ->
                            val lastToken = funcLineAndItsTokens.postfixNotationStack.lastOrNull()
                            val resultOperand = processPostfixNotationStackRec(listOf<Operand>(),
                                    funcLineAndItsTokens.postfixNotationStack,
                                    null,
                                    methodScope,
                                    functions,
                                    deepness + 1).lastOrNull()
                            val currentVariableName = if (resultOperand != null && lastToken is Token.Operator && lastToken.operator == "=") {
                                funcLineAndItsTokens.line.takeWhile { it != '=' }.trim()
                            } else null
                            if (currentVariableName != null && resultOperand != null) {
                                methodScope.put(currentVariableName, resultOperand)
                            }
                            resultOperand
                        }.lastOrNull()
                        if (resultOperand != null) {
                            quantitativeStack.dropLast(functionDef.argumentNames.size + 1) + resultOperand
                        } else {
                            quantitativeStack.dropLast(functionDef.argumentNames.size + 1)
                        }
                    } else {
                        quantitativeStack.dropLast(1)
                    }
                } else if (quantitativeStack.isNotEmpty() && incomingToken.operator == "%") {
                    val topOfStack = quantitativeStack.last()
                    if (topOfStack is Operand.Number) {
                        val num = topOfStack.num
                        quantitativeStack.dropLast(1) + Operand.Percentage(num, topOfStack.type)
                    } else {
                        quantitativeStack.dropLast(1)
                    }
                } else if (quantitativeStack.isNotEmpty() && incomingToken.operator == "in") {
                    val theQuantityThatWillBeConverted = quantitativeStack.lastOrNull()
                    if (lastUnit != null && theQuantityThatWillBeConverted is Operand.Quantity) {
                        val convertedQuantity = try {
                            theQuantityThatWillBeConverted.quantity.convertTo(lastUnit)
                        } catch (e: Throwable) {
                            null
                        }
                        if (convertedQuantity != null) {
                            quantitativeStack.dropLast(1) + (Operand.Quantity(convertedQuantity, theQuantityThatWillBeConverted.type))
                        } else {
                            quantitativeStack
                        }
                    } else {
                        quantitativeStack
                    }
                } else if (quantitativeStack.isNotEmpty()) {
                    val lastTwo = quantitativeStack.takeLast(2)
                    val lhs = lastTwo[0]
                    val rhs = lastTwo.getOrNull(1)
                    val resultOperandAnddropCount = try {
                        applyOperation(incomingToken.operator, lhs, rhs)
                    } catch (e: Throwable) {
                        console.error(e)
                        null to 0
                    }
                    if (resultOperandAnddropCount.first != null) {
                        quantitativeStack.dropLast(resultOperandAnddropCount.second) + resultOperandAnddropCount.first!!
                    } else {
                        quantitativeStack
                    }
                } else {
                    quantitativeStack
                }
            }
            is Token.StringLiteral -> {
                quantitativeStack
            } // do nothing
        }
        return processPostfixNotationStackRec(modifiedQuantitativeStack, tokens.drop(1), lastUnit, variables, functions, deepness)
    }

    private fun addUnitToTheTopOfStackEntry(targetNumber: Operand.Number, token: Token.UnitOfMeasure): Operand.Quantity {
        val number: Number = targetNumber.num
        val newQuantityWithUnit = MathJs.parseUnitName("$number ${token.unitName}")
        return Operand.Quantity(newQuantityWithUnit, targetNumber.type)
    }

    private fun applyOperation(operator: String, lhs: Operand, rhs: Operand?): Pair<Operand?, Int> {
        return try {
            when (operator) {
                "as a % of" -> asAPercentOfOperator(lhs, rhs!!) to 2
                "on what is" -> onWhatIsOperator(lhs, rhs!!) to 2
                "of what is" -> ofWhatIsOperator(lhs, rhs!!) to 2
                "off what is" -> offWhatIsOperator(lhs, rhs!!) to 2
                "*" -> multiplyOperator(lhs, rhs!!) to 2
                "/" -> divideOperator(lhs, rhs!!) to 2
                "+" -> plusOperator(lhs, rhs) to 2
                "-" -> minusOperator(lhs, rhs!!) to 2
                UNARY_MINUS_TOKEN_SYMBOL -> unaryMinusOperator(rhs ?: lhs) to 1
                UNARY_PLUS_TOKEN_SYMBOL -> unaryPlusOperator(rhs ?: lhs) to 1
                "^" -> powerOperator(lhs, rhs!!) to 2
                else -> null to 0
            }
        } catch (e: Throwable) {
            console.error("${lhs.asString()}$operator${rhs?.asString()}")
            console.error(e)
            null to 0
        }
    }

    private fun powerOperator(lhs: Operand, rhs: Operand): Operand? {
        return when (lhs) {
            is Operand.Number -> when (rhs) {
                is Operand.Number -> Operand.Number(Math.pow(lhs.num.toDouble(), rhs.num.toDouble()), lhs.type)
                is Operand.Quantity -> null
                is Operand.Percentage -> null
            }
            is Operand.Quantity -> when (rhs) {
                is Operand.Quantity -> null
                is Operand.Number -> Operand.Quantity(lhs.quantity.pow(rhs.num.toDouble()), lhs.type)
                is Operand.Percentage -> null
            }
            is Operand.Percentage -> when (rhs) {
                is Operand.Number -> Operand.Number(Math.pow(lhs.num.toDouble()/100+1, rhs.num.toDouble()), lhs.type)
                is Operand.Quantity -> null
                is Operand.Percentage -> null
            }
        }
    }

    private fun unaryMinusOperator(operand: Operand): Operand? {
        return when (operand) {
            is Operand.Number -> operand.copy(num = -operand.num.toDouble())
            is Operand.Quantity -> operand.copy(quantity = operand.quantity) // TODO negate
            is Operand.Percentage -> operand.copy(num = -operand.num.toDouble())
        }
    }

    private fun unaryPlusOperator(operand: Operand): Operand? {
        return when (operand) {
            is Operand.Number -> operand.copy(num = +operand.num.toDouble())
            is Operand.Quantity -> operand.copy(quantity = operand.quantity)
            is Operand.Percentage -> operand.copy(num = +operand.num.toDouble())
        }
    }

    private fun minusOperator(lhs: Operand, rhs: Operand): Operand? {
        return when (lhs) {
            is Operand.Number -> when (rhs) {
                is Operand.Number -> subtractNumbers(lhs, rhs)
                is Operand.Quantity -> null
                is Operand.Percentage -> {
                    val xPercentOfLeftHandSide = lhs.num.toDouble() / 100 * rhs.num.toDouble()
                    Operand.Number(lhs.num.toDouble() - xPercentOfLeftHandSide, lhs.type)
                }
            }
            is Operand.Quantity -> when (rhs) {
                is Operand.Quantity -> subtractQuantities(lhs, rhs)
                is Operand.Number -> null
                is Operand.Percentage -> null
            }
            is Operand.Percentage -> when (rhs) {
                is Operand.Quantity -> null
                is Operand.Number -> null
                is Operand.Percentage -> Operand.Percentage(lhs.num.toDouble() - rhs.num.toDouble(), lhs.type)
            }
        }
    }

    private fun plusOperator(lhs: Operand, rhs: Operand?): Operand? {
        return when (lhs) {
            is Operand.Number -> when (rhs) {
                is Operand.Number -> addNumbers(lhs, rhs)
                is Operand.Quantity -> null
                is Operand.Percentage -> {
                    val xPercentOfLeftHandSide = lhs.num.toDouble() / 100 * rhs.num.toDouble()
                    Operand.Number(lhs.num.toDouble() + xPercentOfLeftHandSide, lhs.type)
                }
                null -> null
            }
            is Operand.Quantity -> when (rhs) {
                is Operand.Quantity -> addQuantities(lhs, rhs)
                is Operand.Number -> null
                is Operand.Percentage -> null
                null -> null
            }
            is Operand.Percentage -> when (rhs) {
                is Operand.Quantity -> null
                is Operand.Number -> null
                is Operand.Percentage -> Operand.Percentage(lhs.num.toDouble() + rhs.num.toDouble(), lhs.type)
                null -> null
            }
        }
    }

    private fun divideOperator(lhs: Operand, rhs: Operand): Operand? {
        return when (lhs) {
            is Operand.Number -> when (rhs) {
                is Operand.Number -> divideNumbers(lhs, rhs)
                is Operand.Quantity -> Operand.Quantity(MathJs.evaluateUnitExpression("${lhs.num} / ${rhs.quantity}"), NumberType.Float)
                is Operand.Percentage -> {
                    val x = lhs.num.toDouble() / rhs.num.toDouble() * 100
                    Operand.Number(x, lhs.type)
                }
            }
            is Operand.Quantity -> when (rhs) {
                is Operand.Quantity -> divideQuantities(lhs, rhs)
                is Operand.Number -> null
                is Operand.Percentage -> null
            }
            is Operand.Percentage -> null
        }
    }

    private fun Number.percentageOf(base: Number) = base.toDouble() / 100 * this.toDouble()

    private fun multiplyOperator(lhs: Operand, rhs: Operand): Operand? {
        return when (lhs) {
            is Operand.Number -> when (rhs) {
                is Operand.Number -> multiplyNumbers(lhs, rhs)
                is Operand.Quantity -> Operand.Quantity(MathJs.evaluateUnitExpression("${lhs.num} * ${rhs.quantity}"), NumberType.Float)
                is Operand.Percentage -> Operand.Number(rhs.num.percentageOf(lhs.num), lhs.type)

            }
            is Operand.Quantity -> when (rhs) {
                is Operand.Quantity -> multiplyQuantities(lhs, rhs)
                is Operand.Number -> null
                is Operand.Percentage -> null
            }
            is Operand.Percentage -> when (rhs) {
                is Operand.Number -> Operand.Number(lhs.num.percentageOf(rhs.num), lhs.type)
                is Operand.Quantity -> null
                is Operand.Percentage -> {
                    Operand.Number((lhs.num.toDouble() / 100.0) * (rhs.num.toDouble() / 100.0), NumberType.Float)
                }
            }
        }
    }

    private fun asAPercentOfOperator(lhs: Operand, rhs: Operand): Operand.Percentage? {
        return when (lhs) {
            is Operand.Number -> when (rhs) {
                is Operand.Number -> {
                    Operand.Percentage(lhs.num.toDouble() / rhs.num.toDouble() * 100, NumberType.Float)
                }
                is Operand.Quantity -> null
                is Operand.Percentage -> null
            }

            is Operand.Quantity -> when (rhs) {
                is Operand.Number -> null
                is Operand.Quantity -> {
                    Operand.Percentage(lhs.toRawNumber() / rhs.toRawNumber() * 100, NumberType.Float)
                }
                is Operand.Percentage -> null
            }
            is Operand.Percentage -> null
        }
    }

    private fun onWhatIsOperator(lhs: Operand, rhs: Operand): Operand.Number? {
        return when (lhs) {
            is Operand.Number -> null
            is Operand.Quantity -> null
            is Operand.Percentage -> when (rhs) {
                is Operand.Number -> {
                    // lhs% on what is rhs
                    Operand.Number(rhs.num.toDouble() / (1 + (lhs.num.toDouble() / 100)), NumberType.Float)
                }
                is Operand.Quantity -> null
                is Operand.Percentage -> null
            }
        }
    }

    private fun ofWhatIsOperator(lhs: Operand, rhs: Operand): Operand.Number? {
        return when (lhs) {
            is Operand.Number -> null
            is Operand.Quantity -> null
            is Operand.Percentage -> when (rhs) {
                is Operand.Number -> {
                    // lhs% of what is rhs
                    Operand.Number(rhs.num.toDouble() / (lhs.num.toDouble() / 100), NumberType.Float)
                }
                is Operand.Quantity -> null
                is Operand.Percentage -> null
            }
        }
    }

    private fun offWhatIsOperator(lhs: Operand, rhs: Operand): Operand.Number? {
        return when (lhs) {
            is Operand.Number -> null
            is Operand.Quantity -> null
            is Operand.Percentage -> when (rhs) {
                is Operand.Number -> {
                    // lhs% off what is rhs
                    Operand.Number(rhs.num.toDouble() / (1 - (lhs.num.toDouble() / 100)), NumberType.Float)
                }
                is Operand.Quantity -> null
                is Operand.Percentage -> null
            }
        }
    }


    private fun multiplyQuantities(lhs: Operand.Quantity, rhs: Operand.Quantity): Operand {
        val result = lhs.quantity.multiply(rhs.quantity)
        return when (jsTypeOf(result)) {
            "number" -> Operand.Number(result as Number, lhs.type)
            else -> Operand.Quantity(result.asDynamic(), lhs.type)
        }
    }

    private fun multiplyNumbers(lhs: Operand.Number, rhs: Operand.Number): Operand.Number {
        return Operand.Number(lhs.num.toDouble() * rhs.num.toDouble(), lhs.type)
    }

    private fun addNumbers(lhs: Operand.Number, rhs: Operand.Number): Operand.Number {
        return Operand.Number(lhs.num.toDouble() + rhs.num.toDouble(), lhs.type)
    }

    private fun subtractNumbers(lhs: Operand.Number, rhs: Operand.Number): Operand.Number {
        return Operand.Number(lhs.num.toDouble() - rhs.num.toDouble(), lhs.type)

    }

    private fun divideQuantities(lhs: Operand.Quantity, rhs: Operand.Quantity): Operand {
        val result = lhs.quantity.divide(rhs.quantity)
        return when (jsTypeOf(result)) {
            "number" -> Operand.Number(result as Number, lhs.type)
            else -> Operand.Quantity(result.asDynamic(), lhs.type)
        }
    }

    private fun addQuantities(lhs: Operand.Quantity, rhs: Operand.Quantity): Operand.Quantity {
        return Operand.Quantity(lhs.quantity.add(rhs.quantity), lhs.type)
    }

    private fun subtractQuantities(lhs: Operand.Quantity, rhs: Operand.Quantity): Operand.Quantity {
        return Operand.Quantity(lhs.quantity.subtract(rhs.quantity), lhs.type)
    }

    private fun divideNumbers(lhs: Operand.Number, rhs: Operand.Number): Operand.Number {
        return Operand.Number(lhs.num.toDouble() / rhs.num.toDouble(), lhs.type)
    }

}
