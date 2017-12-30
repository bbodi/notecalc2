package hu.nevermind.notecalc

import hu.nevermind.lib.MathJs
import kotlin.math.pow
import kotlin.math.round

class CalcTests {


    private val tokenParser: TokenParser = TokenParser()
    private val tokenListSimplifier: TokenListSimplifier = TokenListSimplifier()

    fun runTests() {
        fun num(n: Int) = Token.NumberLiteral(n, "", NumberType.Int)
        fun num(n: Double) = Token.NumberLiteral(n, "", NumberType.Float)
        fun op(n: String) = Token.Operator(n)
        fun str(n: String) = Token.StringLiteral(n)
        fun unit(n: String) = Token.UnitOfMeasure(n)

        assertTokenListEq(tokenParser.parse("1+2.0"),
                num(1),
                op("+"),
                num(2.0))
        assertTokenListEq(tokenParser.parse("200kg alma + 300 kg banán"),
                num(200),
                unit("kg"),
                str("alma"),
                op("+"),
                num(300),
                unit("kg"),
                str("banán")
        )
        assertTokenListEq(tokenParser.parse("(1 alma + 4 körte) * 3 ember"),
                op("("), num(1), str("alma"), op("+"), num(4), str("körte"), op(")"), op("*"), num(3), str("ember")
        )
        assertTokenListEq(tokenParser.parse("1/2s"),
                num(1), op("/"), num(2), unit("s")
        )
        assertTokenListEq(shuntingYard("1/2s"),
                num(1), num(2), unit("s"), op("/")
        )
        assertTokenListEq(tokenParser.parse("0b00101 & 0xFF ^ 0xFF00 << 16 >> 16 ! 0xFF"),
                num(0b00101), op("&"), num(0xFF), op("^"), num(0xFF00), op("<<"), num(16), op(">>"), num(16), op("!"), num(0xFF)
        )
        assertTokenListEq(tokenParser.parse("10km/h * 45min in m"),
                num(10),
                unit("km"),
                op("/"),
                unit("h"),
                op("*"),
                num(45),
                unit("min"),
                op("in"),
                unit("m")
        )
        assertTokenListEq(tokenParser.parse("10(km/h)^2 * 45min in m"),
                num(10),
                op("("),
                unit("km"),
                op("/"),
                unit("h"),
                op(")"),
                op("^"),
                num(2),
                op("*"),
                num(45),
                unit("min"),
                op("in"),
                unit("m")
        )
        assertTokenListEq(tokenListSimplifier.mergeCompoundUnits(tokenParser.parse("12km/h")),
                num(12),
                unit("km/h")
        )
        assertTokenListEq(tokenListSimplifier.mergeCompoundUnits(tokenParser.parse("12km/h*3")),
                num(12),
                unit("km/h"),
                op("*"),
                num(3)
        )
        assertTokenListEq(tokenParser.parse("-3"), op("-"), num(3))
        assertTokenListEq(tokenParser.parse("-0xFF"), op("-"), num(255))
        assertTokenListEq(tokenParser.parse("-0b110011"), op("-"), num(51))
        assertTokenListEq(tokenListSimplifier.mergeCompoundUnits(tokenParser.parse("-3")), op("-"), num(3))
        assertTokenListEq(tokenParser.parse("-0xFF"), op("-"), num(255))
        assertTokenListEq(tokenParser.parse("-0b110011"), op("-"), num(51))

        assertEq("30 km", "(10+20)km")
        assertEq("7500 m", "10(km/h) * 45min in m")
        assertEq("500 kg", "200kg alma + 300 kg banán")
        assertEq(Operand.Number(15), "(1 alma + 4 körte) * 3 ember")
        assertEq(Operand.Percentage(5), "10 as a % of 200")
        assertEq(Operand.Percentage(30), "10% + 20%")
        assertEq(Operand.Percentage(20), "30% - 10%")
        assertEq(Operand.Number(220), "200 + 10%")
        assertEq(Operand.Number(180), "200 - 10%")
        assertEq(Operand.Number(20), "200 * 10%")
        assertEq(Operand.Number(20), "10% * 200")
        assertEq(Operand.Percentage(30), "(10 + 20)%")

        assertEq(Operand.Number(181.82, NumberType.Float), "10% on what is $200")
        assertEq(Operand.Number(2000), "10% of what is $200")
        assertEq(Operand.Number(222.22, NumberType.Float), "10% off what is $200")

        assertTokenListEq(shuntingYard("30% - 10%"),
                num(30),
                op("%"),
                num(10),
                op("%"),
                op("-")
        )

        assertTokenListEq(tokenParser.parse("I traveled with 45km/h for / 13km in min"),
                str("I"),
                str("traveled"),
                str("with"),
                num(45),
                unit("km"),
                op("/"),
                unit("h"),
                str("for"),
                op("/"),
                num(13),
                unit("km"),
                op("in"),
                unit("min")
        )
        assertEq("19.5 min", "I traveled 13km / at a rate 40km/h in min")
        assertEq("12 mile/h", "I traveled 24 miles and rode my bike  / 2 hours")
        assertEq("40 mile", "Now let's say you rode your bike at a rate of 10 miles/h for * 4 h")
        assertEq(Operand.Number(9), "12-3")
        assertEq(Operand.Number(1027), "2^10 + 3")
        assertEq(Operand.Number(163), "1+2*3^4")
        assertEq("0.5s", "1/2s")
        assertEq("0.5s", "1/(2s)")
        assertEq(Operand.Number(60), "15 EUR adómentes azaz 75-15 euróból kell adózni")
        assertEq("0.529 GB / seconds", "transfer of around 1.587GB in about / 3 seconds")
        assertEq("37.5 MB", "A is a unit but should not be handled here so... 37.5MB of DNA information in it.")
        assertEq(Operand.Number(1000), "3k - 2k")
        assertEq(Operand.Number(1000000), "3M - 2M")
        assertEq(Operand.Number(100), "1GB / 10MB")
        asd(Operand.Number(2), "2\n" +
                "\${lineId-0}")
        asd(Operand.Number(2 + 3), "2\n" +
                "3\n" +
                "\${lineId-0} + \${lineId-1}")

        test("The parser must find the longest variable name.") {
            val result = LineParser().parseProcessAndEvaulate(emptyList(), "ab", listOf("a", "ab"))!!
            assertEquals(result.parsedTokens.size, 1, "The parser must find the longest variable name 'ab' instead of 'a'")
            assertEquals(result.parsedTokens.first().asString(), "ab")
        }
        test("The parser must find the longest function name.") {
            val result = LineParser().parseProcessAndEvaulate(listOf("a", "ab"), "ab()", emptyList())!!
            assertEquals(result.parsedTokens.first().asString(), "ab")
        }
        assertTokenListEq(tokenParser.parse("9-3"),
                num(9),
                op("-"),
                num(3)
        )
        assertTokenListEq(tokenParser.parse("200 - 10%"),
                num(200),
                op("-"),
                num(10),
                op("%")
        )
        assertTokenListEq(shuntingYard("-1 + -2"),
                num(1),
                op(UNARY_MINUS_TOKEN_SYMBOL),
                num(2),
                op(UNARY_MINUS_TOKEN_SYMBOL),
                op("+")
        )
        assertTokenListEq(shuntingYard("-1 - -2"),
                num(1),
                op(UNARY_MINUS_TOKEN_SYMBOL),
                num(2),
                op(UNARY_MINUS_TOKEN_SYMBOL),
                op("-")
        )
        assertEq(Operand.Number(-3), "-3")
        assertEq(Operand.Percentage(-30), "-30%")
        assertEq(Operand.Number(-3), "-1 + -2")
        assertEq(Operand.Number(1), "(-1) - (-2)")
        assertEq(Operand.Number(1), "-1 - -(2)")
        assertEq(Operand.Number(1), "-1 - -2")
        assertEq(Operand.Number(3), "+3")
        assertEq(Operand.Number(6), "+3 + +3")
        assertEq(Operand.Number(1), "+3 - +2")
        assertEq(Operand.Number(5), "+3 - -2")
        assertEq(Operand.Number(-3), "+(-(+(3)))")
        assertEq(Operand.Number(3), "+-+-3")
        assertEq(Operand.Number(-3), "-+-+-3")

        assertEq(Operand.Number(1.03.pow(3.0)), "3%^3")
    }

    fun <T> assertEquals(expected: T, actual: T, msg: String = "") {
        if (expected != actual) {
            error("$msg, $expected != $actual")
        }
    }

    fun assertTrue(actual: Boolean, msg: String = "") {
        assertEquals(true, actual, msg)
    }

    fun <T> expect(expected: T, msg: String, body: () -> T) {
        if (expected != body()) {
            error(msg)
        }
    }

    fun test(desc: String, func: () -> Unit) {
        expect(true, desc) {
            func()
            true
        }
    }


    private fun assertEq(expectedValue: String, actualInput: String) {
        test(actualInput) {
            val actual = TokenListEvaulator().processPostfixNotationStack(shuntingYard(actualInput), emptyMap(), emptyMap())!! as Operand.Quantity
            assertTrue(MathJs.parseUnitName(expectedValue).equals(actual.quantity),
                    "$expectedValue != ${actual.quantity}")
        }
    }

    private fun assertEq(expectedValue: Operand, actualInput: String) {
        val floatEq = { a: Number, b: Number -> round(a.toDouble() * 100) == round(b.toDouble() * 100) }
        test(actualInput) {
            val actual = TokenListEvaulator().processPostfixNotationStack(shuntingYard(actualInput), emptyMap(), emptyMap())!!
            val ok = when (expectedValue) {
                is Operand.Number -> actual is Operand.Number && floatEq(actual.num, expectedValue.num)
                is Operand.Quantity -> actual is Operand.Quantity && actual.quantity.equals(expectedValue.quantity)
                is Operand.Percentage -> actual is Operand.Percentage && floatEq(actual.num, expectedValue.num)
            }
            assertTrue(ok, "expected(${expectedValue.asString()}) != actual(${actual.asString()})")
        }
    }

    private fun asd(expectedValue: Operand, actualInput: String) {
        val floatEq = { a: Number, b: Number -> round(a.toDouble() * 100) == round(b.toDouble() * 100) }
        test(actualInput) {
            val evaulator = TextEvaulator({lineIndex -> "lineId-$lineIndex"})
            val actual = actualInput.lineSequence().mapIndexed { index, line ->
                evaulator.evaulateLine(index, line)
            }.last()!!.result!!
            val ok = when (expectedValue) {
                is Operand.Number -> actual is Operand.Number && floatEq(actual.num, expectedValue.num)
                is Operand.Quantity -> actual is Operand.Quantity && actual.quantity.equals(expectedValue.quantity)
                is Operand.Percentage -> actual is Operand.Percentage && floatEq(actual.num, expectedValue.num)
            }
            assertTrue(ok, "expected(${expectedValue.asString()}) != actual(${actual.asString()})")
        }
    }

    private fun assertTokenListEq(actualTokens: List<Token>, vararg expectedTokens: Token) {
        test(actualTokens.joinToString()) {
            assertEquals(actualTokens.size, expectedTokens.size, "token count")
            expectedTokens.zip(actualTokens).forEach { (expected, actual) ->
                val ok = when (expected) {
                    is Token.NumberLiteral -> {
                        when (expected.type) {
                            NumberType.Int -> expected.num.toInt() == (actual as Token.NumberLiteral).num.toInt()
                            NumberType.Float -> compareFloats(actual, expected, decimalCount = 2)
                        }
                    }
                    is Token.UnitOfMeasure -> expected.unitName == (actual as Token.UnitOfMeasure).unitName
                    else -> expected.equals(actual)
                }
                assertTrue(ok, "expected: $expected but was: $actual")
            }
        }
    }

    private fun shuntingYard(actualInput: String) = LineParser().shuntingYard(tokenListSimplifier.mergeCompoundUnits(tokenParser.parse(actualInput)), emptyList())

    private fun compareFloats(actual: Token, expected: Token.NumberLiteral, decimalCount: Int) = (expected.num.toFloat() * 10.0.pow(decimalCount.toDouble())).toInt() == ((actual as Token.NumberLiteral).num.toFloat() * 100).toInt()

}