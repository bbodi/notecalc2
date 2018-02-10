package hu.nevermind.notecalc

class LineParser {

    private val tokenParser: TokenParser = TokenParser()
    private val tokenListSimplifier: TokenListSimplifier = TokenListSimplifier()

    internal fun parse(functionNames: Iterable<String>, line: String, variableNames: Iterable<String>): ParsingResult {
        return try {
            val parsedTokens = tokenParser.parse(line, variableNames, functionNames)
            val tokensWithMergedCompoundUnits = tokenListSimplifier.mergeCompoundUnits(parsedTokens)
            val postFixNotationTokens = ShuntingYard.shuntingYard(tokensWithMergedCompoundUnits, functionNames)
            val highlightingInfos = createHighlightingNamesForTokens(parsedTokens)
            ParsingResult(parsedTokens, tokensWithMergedCompoundUnits, postFixNotationTokens, highlightingInfos)
        } catch (e: Throwable) {
            console.error(e)
            ParsingResult(emptyList(), emptyList(), emptyList(), emptyList())
        }
    }

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