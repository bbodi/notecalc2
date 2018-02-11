package hu.nevermind.notecalc

import hu.nevermind.lib.*

class TokenListEvaulator {

    fun processPostfixNotationStack(tokens: List<Token>, variables: Map<String, Operand>, functions: Map<String, TextEvaulator.FunctionDefinition>): Operand? {
        val quantitativeStack = processPostfixNotationStackRec(listOf<Operand>(),
                tokens,
                null,
                variables,
                functions,
                1)
        return quantitativeStack.lastOrNull()
    }

    fun processPostfixNotationStackRec(quantitativeStack: List<Operand>,
                                       tokens: List<Token>,
                                       lastUnit: String?,
                                       variables: Map<String, Operand>,
                                       functions: Map<String, TextEvaulator.FunctionDefinition>,
                                       deepness: Int): List<Operand> {
        if (tokens.isEmpty() || deepness > 100) {
            return quantitativeStack
        }
        val incomingToken = tokens.first()
        val modifiedQuantitativeStack: List<Operand> = when (incomingToken) {
            is Token.NumberLiteral -> quantitativeStack + (Operand.Number(incomingToken.num))
            is Token.Variable -> {
                val variable = variables[incomingToken.variableName]
                if (variable != null) {
                    quantitativeStack + variable
                } else quantitativeStack
            }
            is Token.UnitOfMeasure -> {
                val topOfStack = quantitativeStack.lastOrNull()
                if (topOfStack != null && topOfStack is Operand.Number) { // defining unit to number, e.g. 3 m
                    quantitativeStack.dropLast(1) + addUnitToTheTopOfStackEntry(topOfStack, incomingToken)
                } else { // conversion 3m in cm, put the unit name into the stack, the next operator is probably an 'in'
                    quantitativeStack + (Operand.Quantity(MathJs.unit(null, incomingToken.unitName)))
                }
            }
            is Token.Operator -> {
                if (incomingToken.operator.startsWith("fun ")) {
                    handleFunction(incomingToken, functions, quantitativeStack, variables, deepness)
                } else if (quantitativeStack.isNotEmpty() && incomingToken.operator == "%") {
                    val topOfStack = quantitativeStack.last()
                    if (topOfStack is Operand.Number) {
                        val num = topOfStack.num
                        quantitativeStack.dropLast(1) + Operand.Percentage(num)
                    } else {
                        quantitativeStack.dropLast(1)
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

    private fun handleFunction(incomingToken: Token.Operator, functions: Map<String, TextEvaulator.FunctionDefinition>, quantitativeStack: List<Operand>, variables: Map<String, Operand>, deepness: Int): List<Operand> {
        val funcName = incomingToken.operator.drop("fun ".length)
        val functionDef = functions[funcName]
        return if (functionDef != null && quantitativeStack.size >= functionDef.argumentNames.size) {
            val arguments = quantitativeStack.takeLast(functionDef.argumentNames.size)
            val methodScope = HashMap(variables + functionDef.argumentNames.zip(arguments).toMap())
            val resultOperand = functionDef.tokenLines.map { funcLineAndItsTokens ->
                val resultOperand = processPostfixNotationStackRec(listOf<Operand>(),
                        funcLineAndItsTokens.postfixNotationStack,
                        null,
                        methodScope,
                        functions,
                        deepness + 1).lastOrNull()
                val currentVariableName = tryParseVariableAssignment(funcLineAndItsTokens.line)
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
    }

    fun tryParseVariableAssignment(line: String): String? {
        val variableName = line.takeWhile { it != '=' }
        val rhs = line.drop(variableName.length+1)
        return if (variableName == line || rhs.contains('=')) {
            null
        } else variableName.trim()
    }

    private fun addUnitToTheTopOfStackEntry(targetNumber: Operand.Number, token: Token.UnitOfMeasure): Operand.Quantity {
        val number: BigNumber = targetNumber.num
        val newQuantityWithUnit = MathJs.unit(number, token.unitName)
        return Operand.Quantity(newQuantityWithUnit)
    }

    private fun applyOperation(operator: String, lhs: Operand, rhs: Operand?): Pair<Operand?, Int> {
        return try {
            if (rhs != null) {
                when (operator) {
                    "as a % of" -> asAPercentOfOperator(lhs, rhs) to 2
                    "on what is" -> onWhatIsOperator(lhs, rhs) to 2
                    "of what is" -> ofWhatIsOperator(lhs, rhs) to 2
                    "off what is" -> offWhatIsOperator(lhs, rhs) to 2
                    "*" -> multiplyOperator(lhs, rhs) to 2
                    "/" -> divideOperator(lhs, rhs) to 2
                    "+" -> plusOperator(lhs, rhs) to 2
                    "-" -> minusOperator(lhs, rhs) to 2
                    ShuntingYard.UNARY_MINUS_TOKEN_SYMBOL -> unaryMinusOperator(rhs) to 1
                    ShuntingYard.UNARY_PLUS_TOKEN_SYMBOL -> unaryPlusOperator(rhs) to 1
                    "^" -> powerOperator(lhs, rhs) to 2
                    "in" -> convertOperator(lhs, rhs) to 2
                    else -> null to 0
                }
            } else {
                when (operator) {
                    ShuntingYard.UNARY_MINUS_TOKEN_SYMBOL -> unaryMinusOperator(lhs) to 1
                    ShuntingYard.UNARY_PLUS_TOKEN_SYMBOL -> unaryPlusOperator(lhs) to 1
                    else -> null to 0
                }
            }
        } catch (e: Throwable) {
            console.error("${lhs.asString()}$operator${rhs?.asString()}")
            console.error(e)
            null to 0
        }
    }

    private fun convertOperator(theQuantityThatWillBeConverted: Operand, targetUnit: Operand): Operand? {
        return if (theQuantityThatWillBeConverted is Operand.Quantity && targetUnit is Operand.Quantity) {
            return try {
                Operand.Quantity(theQuantityThatWillBeConverted.quantity.convertTo(targetUnit.quantity.formatUnits()))
            } catch (e: Throwable) {
                null
            }
        } else {
            null
        }
    }

    private fun powerOperator(lhs: Operand, rhs: Operand): Operand? {
        return when (lhs) {
            is Operand.Number -> when (rhs) {
                is Operand.Number -> Operand.Number(MathJs.pow(lhs.num, rhs.num))
                is Operand.Quantity -> null
                is Operand.Percentage -> null
            }
            is Operand.Quantity -> when (rhs) {
                is Operand.Quantity -> null
                is Operand.Number -> Operand.Quantity(MathJs.pow(lhs.quantity, rhs.num))
                is Operand.Percentage -> null
            }
            is Operand.Percentage -> when (rhs) {
                is Operand.Number -> Operand.Number(MathJs.pow(MathJs.bignumber(lhs.num) / 100 + 1, rhs.num))
                is Operand.Quantity -> null
                is Operand.Percentage -> null
            }
        }
    }

    private fun unaryMinusOperator(operand: Operand): Operand? {
        return when (operand) {
            is Operand.Number -> operand.copy(num = MathJs.unaryMinus(operand.num))
            is Operand.Quantity -> operand.copy(quantity = MathJs.unaryMinus(operand.quantity)) // TODO negate
            is Operand.Percentage -> operand.copy(num = -operand.num)
        }
    }

    private fun unaryPlusOperator(operand: Operand): Operand? {
        return when (operand) {
            is Operand.Number -> operand.copy(num = operand.num)
            is Operand.Quantity -> operand.copy(quantity = operand.quantity)
            is Operand.Percentage -> operand.copy(num = +operand.num)
        }
    }

    private fun minusOperator(lhs: Operand, rhs: Operand): Operand? {
        return when (lhs) {
            is Operand.Number -> when (rhs) {
                is Operand.Number -> subtractNumbers(lhs, rhs)
                is Operand.Quantity -> null
                is Operand.Percentage -> {
                    val xPercentOfLeftHandSide = lhs.num / 100 * rhs.num
                    Operand.Number(lhs.num - xPercentOfLeftHandSide)
                }
            }
            is Operand.Quantity -> when (rhs) {
                is Operand.Quantity -> subtractQuantities(lhs, rhs)
                is Operand.Number -> null
                is Operand.Percentage -> {
                    val xPercentOfLeftHandSide = lhs.quantity.multiply(rhs.toRawNumber() / 100.0)
                    Operand.Quantity(lhs.quantity.subtract(xPercentOfLeftHandSide))
                }
            }
            is Operand.Percentage -> when (rhs) {
                is Operand.Quantity -> null
                is Operand.Number -> null
                is Operand.Percentage -> Operand.Percentage(lhs.num - rhs.num)
            }
        }
    }

    private fun plusOperator(lhs: Operand, rhs: Operand?): Operand? {
        return when (lhs) {
            is Operand.Number -> when (rhs) {
                is Operand.Number -> addNumbers(lhs, rhs)
                is Operand.Quantity -> null
                is Operand.Percentage -> {
                    val xPercentOfLeftHandSide = lhs.num / 100 * rhs.num
                    Operand.Number(lhs.num + xPercentOfLeftHandSide)
                }
                null -> null
            }
            is Operand.Quantity -> when (rhs) {
                is Operand.Quantity -> addQuantities(lhs, rhs)
                is Operand.Number -> null
                is Operand.Percentage -> {
                    val xPercentOfLeftHandSide = lhs.quantity.multiply(rhs.toRawNumber() / 100.0)
                    Operand.Quantity(lhs.quantity.add(xPercentOfLeftHandSide))
                }
                null -> null
            }
            is Operand.Percentage -> when (rhs) {
                is Operand.Quantity -> null
                is Operand.Number -> null
                is Operand.Percentage -> Operand.Percentage(lhs.num + rhs.num)
                null -> null
            }
        }
    }

    private fun divideOperator(lhs: Operand, rhs: Operand): Operand? {
        return when (lhs) {
            is Operand.Number -> when (rhs) {
                is Operand.Number -> divideNumbers(lhs, rhs)
                is Operand.Quantity -> {
                    Operand.Quantity(MathJs.divide(lhs.num, rhs.quantity).asDynamic())
                }
                is Operand.Percentage -> {
                    val x = lhs.num / rhs.num * 100
                    Operand.Number(x)
                }
            }
            is Operand.Quantity -> when (rhs) {
                is Operand.Quantity -> divideQuantities(lhs, rhs)
                is Operand.Number -> {
                    Operand.Quantity(MathJs.divide(lhs.quantity, rhs.num))
                }
                is Operand.Percentage -> null
            }
            is Operand.Percentage -> null
        }
    }

    private fun BigNumber.percentageOf(base: BigNumber) = base / 100 * this

    private fun multiplyOperator(lhs: Operand, rhs: Operand): Operand? {
        return when (lhs) {
            is Operand.Number -> when (rhs) {
                is Operand.Number -> multiplyNumbers(lhs, rhs)
                is Operand.Quantity -> {
                    Operand.Quantity(rhs.quantity.multiply(lhs.num))
                }
                is Operand.Percentage -> Operand.Number(rhs.num.percentageOf(lhs.num))

            }
            is Operand.Quantity -> when (rhs) {
                is Operand.Quantity -> multiplyQuantities(lhs, rhs)
                is Operand.Number -> {
                    Operand.Quantity(lhs.quantity.multiply(rhs.num))
                }
                is Operand.Percentage -> {
                    Operand.Quantity(lhs.quantity.multiply(rhs.toRawNumber() / 100.0))
                }
            }
            is Operand.Percentage -> when (rhs) {
                is Operand.Number -> Operand.Number(lhs.num.percentageOf(rhs.num))
                is Operand.Quantity -> null
                is Operand.Percentage -> {
                    Operand.Number(MathJs.bignumber(lhs.num / 100.0) * (rhs.num / 100.0))
                }
            }
        }
    }

    private fun asAPercentOfOperator(lhs: Operand, rhs: Operand): Operand.Percentage? {
        return when (lhs) {
            is Operand.Number -> when (rhs) {
                is Operand.Number -> {
                    Operand.Percentage(lhs.num / rhs.num * 100)
                }
                is Operand.Quantity -> null
                is Operand.Percentage -> null
            }

            is Operand.Quantity -> when (rhs) {
                is Operand.Number -> null
                is Operand.Quantity -> {
                    Operand.Percentage(lhs.toRawNumber() / rhs.toRawNumber() * 100)
                }
                is Operand.Percentage -> null
            }
            is Operand.Percentage -> null
        }
    }

    private fun onWhatIsOperator(lhs: Operand, rhs: Operand): Operand? {
        return when (lhs) {
            is Operand.Number -> null
            is Operand.Quantity -> null
            is Operand.Percentage -> when (rhs) {
                is Operand.Number -> {
                    // lhs% on what is rhs
                    Operand.Number(rhs.num / MathJs.add(1, lhs.num / 100))
                }
                is Operand.Quantity -> {
                    Operand.Quantity(rhs.quantity.divide(MathJs.add(1, lhs.num / 100)))
                }
                is Operand.Percentage -> null
            }
        }
    }

    private fun ofWhatIsOperator(lhs: Operand, rhs: Operand): Operand? {
        return when (lhs) {
            is Operand.Number -> null
            is Operand.Quantity -> null
            is Operand.Percentage -> when (rhs) {
                is Operand.Number -> {
                    // lhs% of what is rhs
                    Operand.Number(rhs.num / (lhs.num / 100))
                }
                is Operand.Quantity -> {
                    Operand.Quantity(rhs.quantity.divide((lhs.num / 100)))
                }
                is Operand.Percentage -> null
            }
        }
    }

    private fun offWhatIsOperator(lhs: Operand, rhs: Operand): Operand? {
        return when (lhs) {
            is Operand.Number -> null
            is Operand.Quantity -> null
            is Operand.Percentage -> when (rhs) {
                is Operand.Number -> {
                    // lhs% off what is rhs
                    Operand.Number(rhs.num / MathJs.subtract(1, (lhs.num / 100)))
                }
                is Operand.Quantity -> {
                    Operand.Quantity(rhs.quantity.divide(MathJs.subtract(1, (lhs.num / 100))))
                }
                is Operand.Percentage -> null
            }
        }
    }


    private fun multiplyQuantities(lhs: Operand.Quantity, rhs: Operand.Quantity): Operand {
        val result = lhs.quantity.multiply(rhs.quantity)
        return when (MathJs.typeOf(result)) {
            "Unit" -> Operand.Quantity(result.asDynamic())
            else -> Operand.Number(MathJs.bignumber(result))
        }
    }

    private fun multiplyNumbers(lhs: Operand.Number, rhs: Operand.Number): Operand.Number {
        return Operand.Number(MathJs.multiply(lhs.num, rhs.num))
    }

    private fun addNumbers(lhs: Operand.Number, rhs: Operand.Number): Operand.Number {
        return Operand.Number(MathJs.add(lhs.num, rhs.num))
    }

    private fun subtractNumbers(lhs: Operand.Number, rhs: Operand.Number): Operand.Number {
        return Operand.Number(MathJs.subtract(lhs.num, rhs.num))

    }

    private fun divideQuantities(lhs: Operand.Quantity, rhs: Operand.Quantity): Operand {
        val result = lhs.quantity.divide(rhs.quantity)
        return when (MathJs.typeOf(result)) {
            "Unit" -> Operand.Quantity(result.asDynamic())
            else -> Operand.Number(MathJs.bignumber(result))
        }
    }

    private fun addQuantities(lhs: Operand.Quantity, rhs: Operand.Quantity): Operand.Quantity {
        return Operand.Quantity(lhs.quantity.add(rhs.quantity))
    }

    private fun subtractQuantities(lhs: Operand.Quantity, rhs: Operand.Quantity): Operand.Quantity {
        return Operand.Quantity(lhs.quantity.subtract(rhs.quantity))
    }

    private fun divideNumbers(lhs: Operand.Number, rhs: Operand.Number): Operand.Number {
        return Operand.Number(MathJs.divide(lhs.num, rhs.num))
    }

}
