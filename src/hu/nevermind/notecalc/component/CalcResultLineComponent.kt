package hu.nevermind.notecalc.component

import hu.nevermind.notecalc.Operand
import hu.nevermind.notecalc.Token
import kotlinx.html.js.onDragStartFunction
import react.*
import react.dom.div
import react.dom.jsStyle
import kotlin.math.max

interface CalcResultLineComponentProps : RProps {
    var classes: String
    var result: Operand?
    var renderingConfig: Any?
    var postFixNotationTokens: List<Token>
    var zeroBasedLineNumber: Int
    var padStart: Int
}

class CalcResultLineComponent(props: CalcResultLineComponentProps) : RComponent<CalcResultLineComponentProps, RState>(props) {


    override fun RBuilder.render() {
        div(props.classes) {
            attrs.jsStyle {
                fontFamily = "monospace"
            }
            attrs.onDragStartFunction = { ev ->
                val lineId = getLineIdAt(codeMirrorInstance, props.zeroBasedLineNumber)
                val variableStringForLineIdRef = "\${$lineId}"
                ev.asDynamic().dataTransfer.setData("Text", variableStringForLineIdRef);
            }
            val result = props.result
            +operandToString(result, props.padStart)
        }
    }

}

fun operandToString(operand: Operand?, padStart: Int): String = (if (operand != null) {
    val (resultString, lengthOfWholePart) = createHumanizedResultString(operand)
    val padding = "\u00A0".repeat(max(padStart - lengthOfWholePart, 0))
    padding + resultString
} else {
    "\u00A0"
})

fun createHumanizedResultString(operand: Operand): Pair<String, Int> {
    // TODO: Operand osztályba?
    val resultStr = operand.asString()
    val numberPart = when (operand) {
        is Operand.Number -> operand.num
        is Operand.Quantity -> operand.toRawNumber()
        is Operand.Percentage -> operand.num
    }

    val unitPart = resultStr.indexOf(" ").let { if (it != -1) resultStr.substring(it + 1) else "" }
    if (numberPart greaterThan js("Number.MAX_SAFE_INTEGER")) {
        val strRepr = "${numberPart.toExponential(16)} $unitPart"
        return strRepr to strRepr.length
    }
    val DECIMAL_COUNT = 10
    val roundedNumber = numberPart.toFixed(DECIMAL_COUNT)
    val wholePart = numberPart.truncated()
    val wholePartString = wholePart.toNumber().asDynamic().toLocaleString("hu").toString()
    val indexOf = roundedNumber.indexOf('.')
    val decimalPart = if (indexOf == -1) {
        ""
    } else {
        roundedNumber.substring(indexOf).padEnd(DECIMAL_COUNT + 1, '0')
    }
    val resultNumberPart = "$wholePartString$decimalPart"//
    return (if (unitPart.isEmpty()) resultNumberPart else "$resultNumberPart $unitPart") to wholePartString.length
}

fun RBuilder.calcResultLineComponent(handler: RHandler<CalcResultLineComponentProps>) = child(CalcResultLineComponent::class) {
    handler()
}