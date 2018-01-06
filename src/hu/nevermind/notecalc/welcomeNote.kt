package hu.nevermind.notecalc

val WELCOME_NOTE = """==========================================================
========================== Welcome =======================
==========================================================

Notecalc is a handy calculator trying to bring the advantages of Soulver
to the web.

You can use it as a combination of a calculator and a notepad, mixing calculations,
numbers, operators, units of measurement with meaningful, descriptive texts around them,
providing context for your calculations. Results on the right are automatically
updated when text changes.

Text is automatically saved in your local browser, nothing is sent to the server.

Some examples. Feel free to change them and play around.

Percentages
===========
100 + 10%
200 * 5%
200 - 20%

Numbers, Hex and binary digits
==============================
You don't have to count zeros
100k
10M
space separated numbers 10 000 000
Binary and Hex numbers
0xFF
0b1100 + 0b0011

Referencing lines
=================
You can reuse calculations by drag&dropping them both from the editor
or from the result window into the editor, or selecting them with the UP/DOWN keys
while holding ALT.
12 dollar bed * 3 beds
price is 3 people * """+"\${lineId-37}"+"""
In the above line, 36 is a referenced value from line 38. Change anything in line 38, and
it will be reflected in the referencing value.
You can even move the referenced lines around.


Variables
=========
Bank of America = 50 000 + 5.25%
Citibank = 50 000 + 6%
Difference of Citibank - Bank of America
${'$'}prev * 3 years
${'$'}prev holds the result of the previous calculation
--
12${'$'} for beer
2*13${'$'} for tickets
all spending = ${'$'}sum

${'$'}sum always holds the sum of the previous calculations
-- you can reset them with at least two dashes (--) or equal signs (==) at the beginning of a line
${'$'}sum is now zero

Units of measure
================
The road took 45minutes and the speed of the vehicle was * 12km/h
(This is an example that comments can be anywhere in an expressions.
The previous line works because it is basically a simple multiplication
between 45minutes and 12km/h, but there are words between the operands and
the operator, which, of course, are ignored when calculating the result)
Downloading a 1GB file with / 10Mb/s in min
or simply 1GB / 10Mb/s in min

Conversions
===========
11years in weeks
1 day in seconds
12 km/h in m/s
5m*m/s in km*km/h


Methods
=======
Methods are defined at the beginning of the line with a "fun" keyword and a method name.
Method name should not contain any space or special characters.
Every line starting with a whitespace character after the method name is the body of the method.

fun motion(time)
  a = (0 - 9.8)m/s^2
  v0 = 100 m/s
  x0 = 490 m
  1/2 * a * time^2 + v0 * time + x0

motion(1s)
motion(10s)
motion(20s)
motion(30s)"""