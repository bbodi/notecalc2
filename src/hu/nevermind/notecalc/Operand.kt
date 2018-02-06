package hu.nevermind.notecalc

import hu.nevermind.lib.BigNumber
import hu.nevermind.lib.MathJs

sealed class Operand {

    abstract fun asString(): String
    abstract fun toRawNumber(): BigNumber

    data class Percentage(val num: BigNumber, val type: NumberType = NumberType.Float) : Operand() {
        constructor(num: kotlin.Number) : this(MathJs.bignumber(num))

        override fun asString(): String = this.num.toString()
        override fun toRawNumber(): BigNumber = MathJs.bignumber(num)
    }

    data class Number(val num: BigNumber, val type: NumberType = NumberType.Float) : Operand() {
        constructor(num: kotlin.Number) : this(MathJs.bignumber(num))

        override fun asString(): String = this.num.toString()
        override fun toRawNumber(): BigNumber = num
    }

    data class Quantity(val quantity: hu.nevermind.lib.Quantity, val type: NumberType) : Operand() {
        override fun asString(): String = this.quantity.toString()

        override fun toRawNumber(): BigNumber = MathJs.bignumber(quantity.toNumber())
    }
}