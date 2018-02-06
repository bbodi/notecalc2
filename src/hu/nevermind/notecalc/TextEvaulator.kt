package hu.nevermind.notecalc

import hu.nevermind.lib.BigNumber
import hu.nevermind.lib.MathJs


class TextEvaulator {
    data class HighlightedText(val text: String, val cssClassName: String)

    data class LineAndTokens(val line: String, val postfixNotationStack: List<Token>)

    data class FunctionDefinition(val name: String, val argumentNames: List<String>,
                                  val tokenLines: List<LineAndTokens>)

    private val lineParser = LineParser()
    private val tokenListEvaulator = TokenListEvaulator()

    data class EvaulationResult(val result: Operand?, val debugInfo: LineParser.ParsingResult)

    private val highlightedTextsByLine = mutableMapOf<Int, List<HighlightedText>>()
    private val variables = hashMapOf<String, Operand>()
    private var sum: BigNumber = MathJs.bignumber(0)
    private var currentFunctionDefinition: FunctionDefinition? = null
    private val functionDefsByName = hashMapOf<String, FunctionDefinition>()
    private val methodScopeVariableNames = arrayListOf<String>()
    private val sumValuesByLines = arrayListOf<BigNumber>()

    fun getVariable(name: String): Operand? = variables[name]

    fun parseLine(zeroBasedLineIndex: Int, line: String): LineParser.ParsingResult {
        // TODO: support UTF8 characters in method names
        val functionDefInCurrentLine = tryParseFunctionDef(line)
        return when {
            functionDefinitionStart(currentFunctionDefinition, functionDefInCurrentLine) -> {
                methodScopeVariableNames.addAll(functionDefInCurrentLine!!.argumentNames)
                currentFunctionDefinition = functionDefInCurrentLine
                functionDefsByName[currentFunctionDefinition!!.name] = currentFunctionDefinition!!
                LineParser.ParsingResult(emptyList(), emptyList(), emptyList(), emptyList())
            }
            stillInTheFunctionBody(currentFunctionDefinition, line) -> {
                val oldCurrentFunctionDefinition = currentFunctionDefinition!!
                val trimmedLine = line.trim()
                val parsingResult = lineParser.parse(functionDefsByName.keys, trimmedLine, variables.keys + methodScopeVariableNames)
                val lineAndTokens = LineAndTokens(trimmedLine, parsingResult.postfixNotationTokens)
                currentFunctionDefinition = oldCurrentFunctionDefinition.copy(tokenLines = oldCurrentFunctionDefinition.tokenLines + lineAndTokens)
                functionDefsByName[currentFunctionDefinition!!.name] = currentFunctionDefinition!!
                highlightedTextsByLine[zeroBasedLineIndex] = parsingResult.highlightedTexts
                val lastToken = parsingResult.postfixNotationTokens.lastOrNull()
                val currentVariableName = tryParseVariableName(lastToken, trimmedLine)
                if (currentVariableName != null) {
                    methodScopeVariableNames.add(currentVariableName)
                }
                parsingResult
            }
            else -> {
                if (currentFunctionDefinition != null) {
                    currentFunctionDefinition = null
                    methodScopeVariableNames.clear()
                }
                val parsingResult = lineParser.parse(functionDefsByName.keys, line, variables.keys)

                highlightedTextsByLine[zeroBasedLineIndex] = parsingResult.highlightedTexts
                parsingResult
            }
        }
    }


    fun evaulate(line: String, zeroBasedLineIndex: Int, postfixNotationTokens: List<Token>, lineId: String): Operand? {
        val lastToken = postfixNotationTokens.lastOrNull()
        val currentVariableName = tryParseVariableName(lastToken, line)
        val resultOperand = tokenListEvaulator.processPostfixNotationStack(
                postfixNotationTokens,
                variables,
                functionDefsByName
        )
        saveResultIntoVariable(currentVariableName, resultOperand, variables)
        sum = if (zeroBasedLineIndex == 0) {
            MathJs.bignumber(0.0)
        } else {
            sumValuesByLines[zeroBasedLineIndex-1]
        }
        if (resultOperand != null) {
            sum += resultOperand.toRawNumber() // TODO: adding percentages and numbers does not make sense
            variables.put("\${$lineId}", resultOperand)
        } else if (line.startsWith("--") || line.startsWith("==")) {
            sum = MathJs.bignumber(0.0)
        }
        // ensure sumValuesByLines size
        while (sumValuesByLines.size <= zeroBasedLineIndex) {
            sumValuesByLines.add(sum)
        }
        sumValuesByLines[zeroBasedLineIndex] = sum
        variables["\$sum"] = Operand.Number(sum, NumberType.Float)
        return resultOperand
    }

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
}

enum class NumberType {
    Float, Int
}
