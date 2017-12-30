package hu.nevermind.notecalc

import hu.nevermind.notecalc.component.appComponent
import react.dom.render
import kotlin.browser.document

fun main(args: Array<String>) {
    if ((js("window").location.href as String).contains("?test")) {
        CalcTests().runTests()
    } else {
        render(document.getElementById("root")) {
            appComponent()
        }
    }
}
