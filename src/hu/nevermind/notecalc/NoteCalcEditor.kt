package hu.nevermind.notecalc

import kotlin.browser.window
import kotlin.js.Math


class TextEvaulator(private val lineIndexToLineIdConverter: (zeroBasedLineIndex: Int) -> String) {
    data class HighlightedText(val text: String, val cssClassName: String)

    data class LineAndTokens(val line: String, val postfixNotationStack: List<Token>)

    data class FunctionDefinition(val name: String, val argumentNames: List<String>,
                                  val tokenLines: List<LineAndTokens>)

    private val lineParser = LineParser()
    private val tokenListEvaulator = TokenListEvaulator()

    data class FinalEvaulationResult(val result: Operand?, val debugInfo: LineParser.EvaulationResult?)

    val highlightedTexts = arrayListOf<HighlightedText>()
    val variables = hashMapOf<String, Operand>()
    val resultString = StringBuilder()
    var sum: Double = 0.0
    var currentFunctionDefinition: FunctionDefinition? = null
    val functionDefsByName = hashMapOf<String, FunctionDefinition>()
    var resultsByLineNumber = listOf<Pair<Int, Operand>>()
    val methodScopeVariableNames = arrayListOf<String>()

    fun textAreaChanged(str: String): List<FinalEvaulationResult?> {
        return str.lines().mapIndexed { nullBasedLineIndex, line ->
            // createVariablesForPreviousLineResults(resultsByLineNumber, variables)
            // TODO: support UTF8 characters in method names
            val functionDefInCurrentLine = tryParseFunctionDef(line)
            if (functionDefinitionStart(currentFunctionDefinition, functionDefInCurrentLine)) {
                methodScopeVariableNames.addAll(functionDefInCurrentLine!!.argumentNames)
                currentFunctionDefinition = functionDefInCurrentLine
                functionDefsByName[currentFunctionDefinition!!.name] = currentFunctionDefinition!!
                resultString.append('\n')
                null
            } else if (stillInTheFunctionBody(currentFunctionDefinition, line)) {
                val oldCurrentFunctionDefinition = currentFunctionDefinition!!
                val trimmedLine = line.trim()
                val evaluationResult = lineParser.parseProcessAndEvaulate(functionDefsByName.keys, trimmedLine, variables.keys + methodScopeVariableNames)
                if (evaluationResult != null) {
                    resultString.append(createDebugString(
                            evaluationResult.parsedTokens,
                            evaluationResult.tokensWithMergedCompoundUnits,
                            evaluationResult.postFixNotationTokens))
                    val lineAndTokens = LineAndTokens(trimmedLine, evaluationResult.postFixNotationTokens)
                    currentFunctionDefinition = oldCurrentFunctionDefinition.copy(tokenLines = oldCurrentFunctionDefinition.tokenLines + lineAndTokens)
                    functionDefsByName[currentFunctionDefinition!!.name] = currentFunctionDefinition!!
                    highlightedTexts.addAll(evaluationResult.highlightedTexts)
                    val currentVariableName = tryParseVariableName(evaluationResult.lastToken, trimmedLine)
                    if (currentVariableName != null) {
                        methodScopeVariableNames.add(currentVariableName)
                    }
                }
                resultString.append('\n')
                FinalEvaulationResult(null, evaluationResult)
            } else {
                if (currentFunctionDefinition != null) {
                    currentFunctionDefinition = null
                    methodScopeVariableNames.clear()
                }
                val evaluationResult = lineParser.parseProcessAndEvaulate(functionDefsByName.keys, line, variables.keys)

                val resultOperand = if (evaluationResult == null) {
                    resultString.append('\n')
                    highlightedTexts.add(HighlightedText(line, "error"))
                    null
                } else {
                    resultString.append(createDebugString(
                            evaluationResult.parsedTokens,
                            evaluationResult.tokensWithMergedCompoundUnits,
                            evaluationResult.postFixNotationTokens
                    ))
                    val currentVariableName = tryParseVariableName(evaluationResult.lastToken, line)
                    val resultOperand = tokenListEvaulator.processPostfixNotationStack(
                            evaluationResult.postFixNotationTokens,
                            variables,
                            functionDefsByName
                    )
                    saveResultIntoVariable(currentVariableName, resultOperand, variables)
                    if (resultOperand != null) {
                        sum += resultOperand.toRawNumber()
                        resultsByLineNumber += nullBasedLineIndex + 1 to resultOperand
                        variables["\$prev"] = resultOperand
                        if (line.startsWith("--") || line.startsWith("==")) {
                            resultString.append("$line\n")
                        } else {
                            resultString.append("${createResultString(resultOperand, currentVariableName)}\n")
                        }
                    } else {
                        if (line.startsWith("--") || line.startsWith("==")) {
                            resultString.append("$line\n")
                        } else {
                            resultString.append('\n')
                        }
                    }
                    if (line.startsWith("--") || line.startsWith("==")) {
                        sum = 0.0
                    }
                    highlightedTexts.addAll(evaluationResult.highlightedTexts)
                    resultOperand
                }
                variables["\$sum"] = Operand.Number(sum, NumberType.Float)
                FinalEvaulationResult(resultOperand, evaluationResult)
            }
        }
//        return resultString.toString()
    }

    fun evaulateLine(zeroBasedLineIndex: Int, line: String): FinalEvaulationResult? {
        // TODO: support UTF8 characters in method names
        val functionDefInCurrentLine = tryParseFunctionDef(line)
        return if (functionDefinitionStart(currentFunctionDefinition, functionDefInCurrentLine)) {
            methodScopeVariableNames.addAll(functionDefInCurrentLine!!.argumentNames)
            currentFunctionDefinition = functionDefInCurrentLine
            functionDefsByName[currentFunctionDefinition!!.name] = currentFunctionDefinition!!
            resultString.append('\n')
            null
        } else if (stillInTheFunctionBody(currentFunctionDefinition, line)) {
            val oldCurrentFunctionDefinition = currentFunctionDefinition!!
            val trimmedLine = line.trim()
            val evaluationResult = lineParser.parseProcessAndEvaulate(functionDefsByName.keys, trimmedLine, variables.keys + methodScopeVariableNames)
            if (evaluationResult != null) {
                resultString.append(createDebugString(
                        evaluationResult.parsedTokens,
                        evaluationResult.tokensWithMergedCompoundUnits,
                        evaluationResult.postFixNotationTokens))
                val lineAndTokens = LineAndTokens(trimmedLine, evaluationResult.postFixNotationTokens)
                currentFunctionDefinition = oldCurrentFunctionDefinition.copy(tokenLines = oldCurrentFunctionDefinition.tokenLines + lineAndTokens)
                functionDefsByName[currentFunctionDefinition!!.name] = currentFunctionDefinition!!
                highlightedTexts.addAll(evaluationResult.highlightedTexts)
                val currentVariableName = tryParseVariableName(evaluationResult.lastToken, trimmedLine)
                if (currentVariableName != null) {
                    methodScopeVariableNames.add(currentVariableName)
                }
            }
            resultString.append('\n')
            FinalEvaulationResult(null, evaluationResult)
        } else {
            if (currentFunctionDefinition != null) {
                currentFunctionDefinition = null
                methodScopeVariableNames.clear()
            }
            val evaluationResult = lineParser.parseProcessAndEvaulate(functionDefsByName.keys, line, variables.keys)

            val resultOperand = if (evaluationResult == null) {
                resultString.append('\n')
                highlightedTexts.add(HighlightedText(line, "error"))
                null
            } else {
                resultString.append(createDebugString(
                        evaluationResult.parsedTokens,
                        evaluationResult.tokensWithMergedCompoundUnits,
                        evaluationResult.postFixNotationTokens
                ))
                val currentVariableName = tryParseVariableName(evaluationResult.lastToken, line)
                val resultOperand = tokenListEvaulator.processPostfixNotationStack(
                        evaluationResult.postFixNotationTokens,
                        variables,
                        functionDefsByName
                )
                saveResultIntoVariable(currentVariableName, resultOperand, variables)
                if (resultOperand != null) {
                    sum += resultOperand.toRawNumber()
                    resultsByLineNumber += zeroBasedLineIndex + 1 to resultOperand
                    variables["\$prev"] = resultOperand
                    if (line.startsWith("--") || line.startsWith("==")) {
                        resultString.append("$line\n")
                    } else {
                        resultString.append("${createResultString(resultOperand, currentVariableName)}\n")
                    }
                    variables.put("$${zeroBasedLineIndex + 1}", resultOperand)
                    val lineId = lineIndexToLineIdConverter(zeroBasedLineIndex) // it contains one-based zeroBasedLineIndex
                    variables.put("\${$lineId}", resultOperand)
                } else {
                    if (line.startsWith("--") || line.startsWith("==")) {
                        resultString.append("$line\n")
                    } else {
                        resultString.append('\n')
                    }
                }
                if (line.startsWith("--") || line.startsWith("==")) {
                    sum = 0.0
                }
                highlightedTexts.addAll(evaluationResult.highlightedTexts)
                resultOperand
            }
            variables["\$sum"] = Operand.Number(sum, NumberType.Float)
            FinalEvaulationResult(resultOperand, evaluationResult)
        }
    }
//        return resultString.toString()

    private fun createResultString(resultOperand: Operand, currentVariableName: String?) = createHumanizedResultString(resultOperand) + ("  " + (currentVariableName ?: ""))

    private fun saveResultIntoVariable(currentVariableName: String?, resultOperand: Operand?, variables: HashMap<String, Operand>) {
        if (currentVariableName != null && resultOperand != null) {
            variables.put(currentVariableName, resultOperand)
        }
    }

    private fun tryParseVariableName(lastToken: Token?, line: String): String? {
        return if (lastToken is Token.Operator && lastToken.operator == "=") {
            line.takeWhile { it != '=' }.trim()
        } else null
    }

    private fun createDebugString(parsedTokens: List<Token>, tokensWithMergedCompoundUnits: List<Token>, postFixNotationTokens: List<Token>): String {
        val debugEnabled = window.asDynamic().debugEnabled
        return if (debugEnabled) {
            var debugString = ""
            debugString += "| ${parsedTokens.joinToString()} | ${tokensWithMergedCompoundUnits.joinToString()}"
            debugString += "| ${postFixNotationTokens.joinToString()}"
            debugString
        } else ""
    }


    private fun functionDefinitionStart(currentFunctionDefinition: FunctionDefinition?, functionDefInCurrentLine: FunctionDefinition?) = functionDefInCurrentLine != null && currentFunctionDefinition == null

    private fun stillInTheFunctionBody(currentFunctionDefinition: FunctionDefinition?, line: String) = currentFunctionDefinition != null && line.firstOrNull()?.isWhitespace() ?: false

    private fun tryParseFunctionDef(line: String): FunctionDefinition? {
        val matches = line.match("""fun ([^\d\s\$\-\+\*\^\:\%][^\(]*)\(([^\)]*(,[^\)]*)*)\)""")
        return if (matches != null) {
            val funName = matches[1]
            val arguments = matches[2].split(',').map(String::trim).filterNot(String::isEmpty)
            FunctionDefinition(funName, arguments, emptyList())
        } else null
    }

    private fun createHumanizedResultString(quantity: Operand): String {
        // TODO: Operand osztályba?
        val resultStr = quantity.asString()
        val numberPart = when (quantity) {
            is Operand.Number -> quantity.num
            is Operand.Quantity -> quantity.quantity.toNumber()
            is Operand.Percentage -> quantity.num
        }
        val outputType = when (quantity) {
            is Operand.Number -> quantity.type
            is Operand.Quantity -> quantity.type
            is Operand.Percentage -> quantity.type
        }

        val unitPart = resultStr.indexOf(" ").let { if (it != -1) resultStr.substring(it + 1) else "" }
        val roundedNumber = Math.round(numberPart.toDouble() * 100.0) / 100.0
        val localizedString = roundedNumber.asDynamic().toLocaleString("hu").toString()
        val indexOf = localizedString.indexOf(',')
        val wholePart = if (indexOf == -1) localizedString else localizedString.substring(0, indexOf)
        val decimalPart = if (indexOf == -1) if (outputType == NumberType.Float) ",00" else "\u00A0\u00A0\u00A0" else localizedString.substring(indexOf).padEnd(3, '0')
        val resultNumberPart = "$wholePart$decimalPart".padStart(16, '\u00A0')
        val fullResult = "$resultNumberPart $unitPart"
        return fullResult
    }
}

enum class NumberType {
    Float, Int
}
