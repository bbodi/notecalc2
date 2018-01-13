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
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.round

interface CalcResultLineComponentProps : RProps {
    var classes: String
    var result: Operand?
    var renderingConfig: Any?
    var postFixNotationTokens: List<Token>
    var onLineClick: (Int) -> Unit
    var zeroBasedLineNumber: Int
    var padStart: Int
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
                val (resultString, lengthOfWholePart) = createHumanizedResultString(result)
                val padding = "\u00A0".repeat(max(props.padStart - lengthOfWholePart, 0))
                padding + resultString
            } else {
                "\u00A0"
            })
        }
    }

}

fun createHumanizedResultString(quantity: Operand): Pair<String, Int> {
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
    val DECIMAL_COUNT = 10
    val roundedNumber = round(numberPart.toDouble() * 10.0.pow(DECIMAL_COUNT)) / 10.0.pow(DECIMAL_COUNT)
    val localizedString = roundedNumber.asDynamic().toLocaleString("hu").toString()
    val indexOf = localizedString.indexOf(',')
    val wholePart = if (indexOf == -1) localizedString else localizedString.substring(0, indexOf)
    val decimalPart = if (indexOf == -1) {
        if (outputType == NumberType.Float) {
            ',' + "0".repeat(DECIMAL_COUNT)
        } else {
            ""
        }
    } else {
        localizedString.substring(indexOf).padEnd(DECIMAL_COUNT + 1, '0')
    }
    val resultNumberPart = "$wholePart$decimalPart"//
    return (if (unitPart.isEmpty()) resultNumberPart else "$resultNumberPart $unitPart") to wholePart.length
}

fun RBuilder.calcResultLineComponent(handler: RHandler<CalcResultLineComponentProps>) = child(CalcResultLineComponent::class) {
    handler()
}