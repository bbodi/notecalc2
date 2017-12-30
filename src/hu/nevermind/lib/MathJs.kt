package hu.nevermind.lib

import kotlinext.js.invoke
import kotlinext.js.require

val MathJs: MathJsType = require("mathjs")

@JsName("Unit")
external class Quantity {
    fun equalBase(quantity: Quantity): Boolean
    fun toNumber(): Number
    @JsName("to")
    fun convertTo(unitName: String): Quantity

    fun toNumber(unitName: String): Number
    fun equals(other: Any): Boolean
}

external interface MathJsType {
    fun add(a: Any, b: Any): Quantity
    fun subtract(a: Any, b: Any): Quantity
    fun multiply(a: Any, b: Any): Quantity
    fun divide(a: Any, b: Any): Quantity
    fun pow(a: Any, b: Any): Quantity
    fun abs(a: Any): Quantity
    fun sqrt(a: Any): Quantity

    @JsName("unit")
    fun parseUnitName(expressionString: String): Quantity

    @JsName("eval")
    fun evaluateUnitExpression(expressionString: String): Quantity
}

fun Quantity.add(other: Any): Quantity = MathJs.add(this, other)
fun Quantity.subtract(other: Any): Quantity = MathJs.subtract(this, other)
fun Quantity.multiply(other: Any): Any = MathJs.multiply(this, other)
fun Quantity.divide(other: Any): Any = MathJs.divide(this, other)
fun Quantity.pow(other: Any): Quantity = MathJs.pow(this, other)
fun Quantity.abs(): Quantity = MathJs.abs(this)
fun Quantity.sqrt(): Quantity = MathJs.sqrt(this)
