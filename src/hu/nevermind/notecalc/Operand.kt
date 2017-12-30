package hu.nevermind.notecalc

sealed class Operand {

    abstract fun asString(): String
    abstract fun toRawNumber(): Double

    data class Percentage(val num: kotlin.Number, val type: NumberType = if (num is Int) NumberType.Int else NumberType.Float) : Operand() {
        override fun asString(): String = this.num.toString()
        override fun toRawNumber(): Double = num.toDouble()
    }

    data class Number(val num: kotlin.Number, val type: NumberType = if (num is Int) NumberType.Int else NumberType.Float) : Operand() {
        override fun asString(): String = this.num.toString()
        override fun toRawNumber(): Double = num.toDouble()
    }

    data class Quantity(val quantity: hu.nevermind.lib.Quantity, val type: NumberType) : Operand() {
        override fun asString(): String = this.quantity.toString()

        override fun toRawNumber(): Double = quantity.toNumber().toDouble()
    }
}