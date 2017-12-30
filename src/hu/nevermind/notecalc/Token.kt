package hu.nevermind.notecalc



sealed class Token {
    data class UnitOfMeasure(val unitName: String, val tokens: List<Token> = emptyList()) : Token() {
        override fun asString(): CharSequence = unitName
        override fun toString() = "Unit($unitName)"
    }

    data class StringLiteral(val str: String) : Token() {
        override fun asString(): CharSequence = str
        override fun toString() = "Str($str)"
    }

    data class Variable(val variableName: String) : Token() {
        override fun asString(): CharSequence = variableName
        override fun toString() = "Var($variableName)"
    }

    data class NumberLiteral(val num: Number, val originalStringRepresentation: String, val type: NumberType) : Token() {
        override fun asString(): CharSequence = num.toString()
        override fun toString(): String = "Num($num)"
    }

    data class Operator(val operator: String, val asStringValue: String = operator) : Token() {
        override fun asString(): CharSequence = asStringValue
        override fun toString(): String = "Op($operator)"
    }

    abstract fun asString(): CharSequence
}