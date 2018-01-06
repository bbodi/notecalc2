package hu.nevermind.notecalc

import hu.nevermind.notecalc.component.appComponent
import kotlinext.js.requireAll
import kotlinext.js.*
import react.dom.render
import kotlin.browser.document

fun main(args: Array<String>) {
    requireAll(require.context("src", true, js("/\\.css$/")))
    if ((js("window").location.href as String).contains("?test")) {
        CalcTests().runTests()
    } else {
        render(document.getElementById("root")) {
            appComponent()
        }
    }
}
