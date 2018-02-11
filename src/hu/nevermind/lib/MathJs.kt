package hu.nevermind.lib

import kotlinext.js.invoke
import kotlinext.js.require
import kotlinx.html.Q

val MathJs: MathJsType = require("mathjs")

@JsName("Unit")
external class Quantity {
    fun equalBase(quantity: Quantity): Boolean
    fun toNumber(): Number
    @JsName("to")
    fun convertTo(unitName: String): Quantity

    fun toNumber(unitName: String): Number
    fun equals(other: Any): Boolean
    fun formatUnits(): String
}

external interface BigNumber {
    @JsName("negated")
    operator fun unaryMinus(): BigNumber

    operator fun unaryPlus(): BigNumber

    operator fun plus(other: Any): BigNumber

    operator fun minus(other: Any): BigNumber

    operator fun times(other: Any): BigNumber

    operator fun div(other: Any): BigNumber


    fun isFinite(): Boolean
    fun floor(): BigNumber
    fun truncated(): BigNumber
    fun toFixed(decimalCount: Int): String
    fun toExponential(i: Int): BigNumber
    fun toNumber(): Any

    @JsName("gt")
    infix fun greaterThan(l: Any): Boolean
}

external interface MathJsType {
    fun <T>add(a: T, b: Any): T
    fun <T>subtract(a: T, b: Any): T
    fun <T>multiply(a: T, b: Any): T
    fun <T>divide(a: T, b: Any): T
    fun <T>pow(a: T, b: Any): T
    fun <T>abs(a: T): T
    fun <T>sqrt(a: T): T

    @JsName("unit")
    fun parseUnitName(expressionString: String): Quantity

    @JsName("eval")
    fun evaluateUnitExpression(expressionString: String): Quantity

    fun <T>unaryMinus(quantity: T): T
    fun <T>unaryPlus(quantity: T): T
    fun unit(nothing: Any?, unitName: String): Quantity

    fun bignumber(any: Any): BigNumber
    fun number(num: Any): Double
    fun equal(a: BigNumber, b: BigNumber): Boolean
    fun round(bigNumber: BigNumber): BigNumber

    @JsName("typeof")
    fun typeOf(result: Any): String

    fun larger(numberPart: Any, l: Any): Boolean

    val Infinity: BigNumber
    fun format(quantity: Any, options: FormatOptions): String
}

interface FormatOptionsNotation
val fixed: FormatOptionsNotation = js("'fixed'")
val exponential: FormatOptionsNotation = js("'fixed'")
val engineering: FormatOptionsNotation = js("'fixed'")
val auto: FormatOptionsNotation = js("'fixed'")

class FormatOptions(val notation: FormatOptionsNotation,
                    val precision: Int = js("null"))

fun Quantity.add(other: Any): Quantity = MathJs.add(this, other)
fun Quantity.subtract(other: Any): Quantity = MathJs.subtract(this, other)
fun Quantity.multiply(other: Any): Quantity = MathJs.multiply(this, other)
fun Quantity.divide(other: Any): Quantity = MathJs.divide(this, other)
fun Quantity.pow(other: Any): Quantity = MathJs.pow(this, other)
fun Quantity.abs(): Quantity = MathJs.abs(this)
fun Quantity.sqrt(): Quantity = MathJs.sqrt(this)

fun MathJsType.isValuelessUnit(expression: String) = MathJs.asDynamic().type.Unit.isValuelessUnit(expression)
