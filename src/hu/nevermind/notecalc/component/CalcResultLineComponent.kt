package hu.nevermind.notecalc.component

import hu.nevermind.notecalc.NumberType
import hu.nevermind.notecalc.Operand
import hu.nevermind.notecalc.Token
import kotlinx.html.Draggable
import kotlinx.html.draggable
import kotlinx.html.js.onDragStartFunction
import react.*
import react.dom.div
import react.dom.jsStyle
import kotlin.js.Math

interface CalcResultLineComponentProps : RProps {
    var classes: String
    var result: Operand?
    var renderingConfig: Any?
    var postFixNotationTokens: List<Token>
    var onLineClick: (Int) -> Unit
    var zeroBasedLineNumber: Int
}

class CalcResultLineComponent(props: CalcResultLineComponentProps) : RComponent<CalcResultLineComponentProps, RState>(props) {


    override fun RBuilder.render() {
        div(props.classes) {
            attrs.jsStyle {
                fontFamily = "monospace"
            }
            attrs.asDynamic().unselectable = "on"
            attrs.asDynamic().onselectstart = "return false;"
            attrs.draggable = Draggable.htmlTrue
            attrs.onDragStartFunction = { ev ->
                val lineId = getLineIdAt(codeMirrorInstance, props.zeroBasedLineNumber)
                val variableStringForLineIdRef = "\${$lineId}"
                ev.asDynamic().dataTransfer.setData("Text", variableStringForLineIdRef);
            }
            val result = props.result
            +(if (result != null) {
                createHumanizedResultString(result, 16, 3)
            } else {
                "\u00A0"
            })
        }
    }

}

fun createHumanizedResultString(quantity: Operand, padStart: Int, padAfterDecimalPoint: Int): String {
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
    val decimalPart = if (indexOf == -1) if (outputType == NumberType.Float) ",00" else "\u00A0".repeat(padAfterDecimalPoint) else localizedString.substring(indexOf).padEnd(3, '0')
    val resultNumberPart = "$wholePart$decimalPart".padStart(padStart, '\u00A0')
    return if (unitPart.isEmpty()) resultNumberPart else "$resultNumberPart $unitPart"
}

fun RBuilder.calcResultLineComponent(handler: RHandler<CalcResultLineComponentProps>) = child(CalcResultLineComponent::class) {
    handler()
}