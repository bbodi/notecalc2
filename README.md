## NoteCalc

#### A handy calculator for the web.

![Readme](public/screen.png)

You can try this app from here: http://bbodi.github.io/notecalc2

Goals
--
- [X] Parsing (string literal, numbers(hex, dec, bin), variables, function invocations etc)
- [X] Referencing results (by Alt+Mouse or Alt+Up/Down)
- [X] Multiple line selection (Ctrl/Shift + Mouse/Cursors)
- [X] High precision calculations (math.bignumber)
- [ ] Builtin funcions (sin, sqrt, etc)
- [ ] Bitwise operations
- [ ] Autocompletion (operators, variables, parameters, functions)
- [ ] Result formatting (precision, format, notation)
- [ ] Dates (parsing, operations, conversions)
- [ ] Complex numbers

Build instructions
--
After cloning, the project can be built and tested with the following commands:
```
npm install
npm start
```
Then browse to http://localhost:3000/

Unit tests are skipped by default, they are exectued on http://localhost:3000/?test (results are on the console, if everything is fine, you will not see anything in the browser),

Building release version: 
``npm run build``
