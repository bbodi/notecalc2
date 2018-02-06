package hu.nevermind.notecalc

import hu.nevermind.lib.BigNumber
import hu.nevermind.lib.MathJs
import kotlin.math.pow

class CalcTests {


    private val tokenParser: TokenParser = TokenParser()
    private val tokenListSimplifier: TokenListSimplifier = TokenListSimplifier()

    fun runTests() {
        fun num(n: Int) = Token.NumberLiteral(MathJs.bignumber(n), "")
        fun num(n: Double) = Token.NumberLiteral(MathJs.bignumber(n), "")
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

        test("operations between Quantity and non-Quantity operands") {
            assertEq("60 km/h", "12 km/h * 5")
            assertEq("500 kg", "200kg alma + 300 kg banán")
            assertEvaulatingSingleLine(Operand.Number(15), "(1 alma + 4 körte) * 3 ember")

            assertEq("20 km/h", "200 km/h * 10%")
            assertEq("0 km/h", "200 km/h * 0%")
            assertEq("220 km/h", "200 km/h + 10%")
            assertEq("180 km/h", "200 km/h - 10%")
            assertEq("200 km/h", "200 km/h + 0%")
            assertEq("200 km/h", "200 km/h - 0%")

            assertEq("181.8181818181818 km/h", "10% on what is 200km/h")
            assertEq("200 km/h", "0% on what is 200km/h")
            assertEq("0 km/h", "10% on what is 0km/h")

            assertEq("2000 km/h", "10% of what is 200km/h")
//            assertEq("Infinity km/h", "0% of what is 200km/h")
            assertEq("0 km/h", "10% of what is 0km/h")

            assertEq("222.2222222222222 km/h", "10% off what is 200km/h")
            assertEq("200 km/h", "0% off what is 200km/h")
            assertEq("0 km/h", "10% off what is 0 km/h")
        }


        assertEvaulatingSingleLine(Operand.Percentage(5), "10 as a % of 200")
        assertEvaulatingSingleLine(Operand.Percentage(MathJs.Infinity), "10 as a % of 0")
        assertEvaulatingSingleLine(Operand.Percentage(0), "0 as a % of 200")
        assertEvaulatingSingleLine(Operand.Percentage(30), "10% + 20%")
        assertEvaulatingSingleLine(Operand.Percentage(0), "0% + 0%")
        assertEvaulatingSingleLine(Operand.Percentage(20), "30% - 10%")
        assertEvaulatingSingleLine(Operand.Percentage(0), "0% - 0%")
        assertEvaulatingSingleLine(Operand.Number(220), "200 + 10%")
        assertEvaulatingSingleLine(Operand.Number(200), "200 + 0%")
        assertEvaulatingSingleLine(Operand.Number(0), "0 + 10%")
        assertEvaulatingSingleLine(Operand.Number(180), "200 - 10%")
        assertEvaulatingSingleLine(Operand.Number(200), "200 - 0%")
        assertEvaulatingSingleLine(Operand.Number(0), "0 - 10%")
        assertEvaulatingSingleLine(Operand.Number(20), "200 * 10%")
        assertEvaulatingSingleLine(Operand.Number(0), "200 * 0%")
        assertEvaulatingSingleLine(Operand.Number(20), "10% * 200")
        assertEvaulatingSingleLine(Operand.Number(0), "0% * 200")
        assertEvaulatingSingleLine(Operand.Percentage(30), "(10 + 20)%")

        assertEvaulatingSingleLine(Operand.Number(MathJs.bignumber("181.8181818181818181818181818181818181818181818181818181818181818")), "10% on what is $200")
        assertEvaulatingSingleLine(Operand.Number(200), "0% on what is $200")
        assertEvaulatingSingleLine(Operand.Number(0), "10% on what is $0")

        assertEvaulatingSingleLine(Operand.Number(2000), "10% of what is $200")
        assertEvaulatingSingleLine(Operand.Number(MathJs.Infinity), "0% of what is $200")
        assertEvaulatingSingleLine(Operand.Number(0), "10% of what is $0")

        assertEvaulatingSingleLine(Operand.Number(MathJs.bignumber("222.2222222222222222222222222222222222222222222222222222222222222")), "10% off what is $200")
        assertEvaulatingSingleLine(Operand.Number(200), "0% off what is $200")
        assertEvaulatingSingleLine(Operand.Number(0), "10% off what is $0")


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
        assertEvaulatingSingleLine(Operand.Number(9), "12-3")
        assertEvaulatingSingleLine(Operand.Number(1027), "2^10 + 3")
        assertEvaulatingSingleLine(Operand.Number(163), "1+2*3^4")
        assertEq("0.5Hz", "1/2s")
        assertEq("0.5s", "1s/2")
        assertEq("0.5Hz", "1/(2s)")
        assertEvaulatingSingleLine(Operand.Number(60), "15 EUR adómentes azaz 75-15 euróból kell adózni")
        assertEq("0.529 GB / seconds", "transfer of around 1.587GB in about / 3 seconds")
        assertEq("37.5 MB", "A is a unit but should not be handled here so... 37.5MB of DNA information in it.")
        assertEvaulatingSingleLine(Operand.Number(1000), "3k - 2k")
        assertEvaulatingSingleLine(Operand.Number(1000000), "3M - 2M")
        assertEvaulatingSingleLine(Operand.Number(100), "1GB / 10MB")
        assertEvaulatingSingleLine(Operand.Number(1), "1.")
        assertEvaulatingSingleLine(Operand.Number(0.1), ".1")


        test("unit names like 'b' can be used as variable names") {
            assertEvaulatingFullNote(Operand.Number(4), """
            |b = 4
            |b""".trimMargin())
            // TODO think  through this scenario
//            assertEvaulatingFullNote(Operand.Number(12), """
//            |b = 4
//            |3b * b""".trimMargin())
        }

        assertEvaulatingFullNote(Operand.Number(15), """
            |fun function(a)
            |  12 + a
            |function(3)""".trimMargin())

        assertEvaulatingFullNote(Operand.Number(3), """
            |fun function(a)
            |  12 + a
            |unknown_function(3)""".trimMargin())

        assertEvaulatingFullNote(Operand.Number(19), """
            |fun function_with_locals(a)
            |  b = 4
            |  12 + b + a
            |function_with_locals(3)""".trimMargin())

        assertEvaulatingFullNote(Operand.Number(16), """
            |fun function_with_locals_overwritten(a)
            |  a = 4
            |  12 + a
            |function_with_locals_overwritten(3)""".trimMargin())


        // TODO: think it through, unit test is only for making it sure this kind of ()-less call will be possible in the future
        assertEvaulatingFullNote(Operand.Number(6), """
            |fun sin(a)
            |  a*2
            |sin3""".trimMargin())

        assertEvaulatingFullNote(Operand.Number(6), """
            |fun function(name with spaces)
            |  name with spaces * 2
            |function(3)""".trimMargin())

        assertEvaulatingFullNote(Operand.Number(3), """
            |fun function(name with spaces, ékezetes név)
            |  name with spaces + ékezetes név
            |function(1, 2)""".trimMargin())

        assertEvaulatingFullNote(null, """
            |fun infinite_recursion_protection()
            |  infinite_recursion_protection()""".trimMargin())

        assertEvaulatingFullNote(null, """
            |fun infinite_recursion_protection()
            |  infinite_recursion_protection()
            |infinite_recursion_protection()""".trimMargin())

        assertEvaulatingFullNote(Operand.Number(1.056482), """
            fun jelenlegi_ertek(vételi ár ABCben, mennyiség, jelenlegi eladási ár ABCban)
              ennyibe került = vételi ár ABCben * mennyiség
              ennyiért tudom most eladni = jelenlegi eladási ár ABCben * mennyiség
              ennyiért tudom most eladni - ennyibe került
            jelenlegi_ertek(0.035, 1.0905 + 0.0043, 1)""".trimIndent())



        assertEvaulatingFullNote(Operand.Number(2), "2\n" +
                "\${lineId-0}")
        assertEvaulatingFullNote(Operand.Number(2 + 3), "2\n" +
                "3\n" +
                "\${lineId-0} + \${lineId-1}")

        assertEvaulatingFullNote(Operand.Percentage(3), "3\n" +
                "\${lineId-0}%")

        test("function call should happen only if there are opening and closing brackets, the existence of function name as an individual string token is not enough!") {
            assertTokenListEq(tokenParser.parse("space separated numbers 10 000 000", functionNames = listOf("s")),
                    str("space"), str("separated"), str("numbers"), num(10000000)
            )

            assertTokenListEq(shuntingYard("space separated numbers 10 000 000", functionNames = listOf("s")),
                    str("space"), str("separated"), str("numbers"), num(10000000)
            )
        }


        test("The parser must find the longest variable name.") {
            val result = LineParser().parse(emptyList(), "ab", listOf("a", "ab"))
            assertEquals(result.parsedTokens.size, 1, "The parser must find the longest variable name 'ab' instead of 'a'")
            assertEquals(result.parsedTokens.first().asString(), "ab")
        }
        test("The parser must find the longest function name.") {
            val result = LineParser().parse(listOf("a", "ab"), "ab()", emptyList())
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
        assertEvaulatingSingleLine(Operand.Number(-3), "-3")
        assertEvaulatingSingleLine(Operand.Percentage(-30), "-30%")
        assertEvaulatingSingleLine(Operand.Number(-3), "-1 + -2")
        assertEvaulatingSingleLine(Operand.Number(1), "(-1) - (-2)")
        assertEvaulatingSingleLine(Operand.Number(1), "-1 - -(2)")
        assertEvaulatingSingleLine(Operand.Number(1), "-1 - -2")
        assertEvaulatingSingleLine(Operand.Number(3), "+3")
        assertEvaulatingSingleLine(Operand.Number(6), "+3 + +3")
        assertEvaulatingSingleLine(Operand.Number(1), "+3 - +2")
        assertEvaulatingSingleLine(Operand.Number(5), "+3 - -2")
        assertEvaulatingSingleLine(Operand.Number(-3), "+(-(+(3)))")
        assertEvaulatingSingleLine(Operand.Number(3), "+-+-3")
        assertEvaulatingSingleLine(Operand.Number(-3), "-+-+-3")

        assertEq("-60 km/h", "-12km/h * 5")

        assertEvaulatingSingleLine(Operand.Number(1.03.pow(3.0)), "3%^3")

        test("big numbers") {
            assertEvaulatingSingleLine(Operand.Number(MathJs.bignumber("2.29954912591911623810696812309991586309439171655591419683713981e+354031")),
                    "3.141592653589793 ^ 712 122")
        }
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
        console.info("Running Test: $desc")
        expect(true, desc) {
            func()
            true
        }
        console.info("------------ SUCCESS ----------")
    }


    private fun assertEq(expectedValue: String, actualInput: String) {
        test(actualInput) {
            val actual = TokenListEvaulator().processPostfixNotationStack(shuntingYard(actualInput), emptyMap(), emptyMap())!! as? Operand.Quantity
            assertTrue(MathJs.parseUnitName(expectedValue).equals(actual?.quantity),
                    "$expectedValue != ${actual?.quantity}")
        }
    }

    private fun assertEvaulatingSingleLine(expectedValue: Operand, actualInput: String) {
        val floatEq = { a: BigNumber, b: BigNumber -> (!a.isFinite() && !b.isFinite()) || MathJs.equal(a, b) }
        test(actualInput) {
            val actual = TokenListEvaulator().processPostfixNotationStack(shuntingYard(actualInput), emptyMap(), emptyMap())!!
            val ok = when (expectedValue) {
                is Operand.Number -> actual is Operand.Number && floatEq(actual.num, expectedValue.num)
                is Operand.Quantity -> actual is Operand.Quantity && actual.quantity.equals(expectedValue.quantity)
                is Operand.Percentage -> actual is Operand.Percentage && floatEq(actual.num, expectedValue.num)
            }
            assertTrue(ok, "expected(${expectedValue}) != actual(${actual})")
        }
    }

    private fun assertEvaulatingFullNote(expectedValue: Operand?, actualInput: String) {
        test(actualInput) {
            val evaulator = TextEvaulator()
            val actual = actualInput.lineSequence().mapIndexed { index, line ->
                evaulator.evaulate(line, index, evaulator.parseLine(index, line).postfixNotationTokens, "lineId-$index")
            }.lastOrNull()

            val ok = when (expectedValue) {
                is Operand.Number -> actual is Operand.Number && MathJs.equal(actual.num, expectedValue.num)
                is Operand.Quantity -> actual is Operand.Quantity && actual.quantity.equals(expectedValue.quantity)
                is Operand.Percentage -> actual is Operand.Percentage && MathJs.equal(actual.num, expectedValue.num)
                else -> expectedValue == null && actual == null
            }
            assertTrue(ok, "expected(${expectedValue?.asString()}) != actual(${actual?.asString()})")
        }
    }

    private fun assertTokenListEq(actualTokens: List<Token>, vararg expectedTokens: Token) {
        test(actualTokens.joinToString()) {
            assertEquals(expectedTokens.size, actualTokens.size, "token count: ${expectedTokens} != ${actualTokens}")
            expectedTokens.zip(actualTokens).forEach { (expected, actual) ->
                val ok = when (expected) {
                    is Token.NumberLiteral -> {
                        MathJs.equal(expected.num, (actual as Token.NumberLiteral).num)
                    }
                    is Token.UnitOfMeasure -> expected.unitName == (actual as Token.UnitOfMeasure).unitName.replace(" ", "")
                    else -> expected.equals(actual)
                }
                assertTrue(ok, "expected: $expected but was: $actual")
            }
        }
    }

    private fun shuntingYard(actualInput: String, functionNames: List<String> = emptyList()) = LineParser().shuntingYard(tokenListSimplifier.mergeCompoundUnits(tokenParser.parse(actualInput, functionNames = functionNames)), functionNames)
}