package hu.nevermind.notecalc

import hu.nevermind.lib.MathJs
import hu.nevermind.notecalc.component.appComponent
import kotlinext.js.requireAll
import kotlinext.js.*
import react.dom.render
import kotlin.browser.document
import kotlin.js.json

fun main(args: Array<String>) {
    requireAll(require.context("src", true, js("/\\.css$/")))
    MathJs.asDynamic().config(js {
        number = "BigNumber"
        precision = 64
    });
    if ((js("window").location.href as String).contains("?test")) {
        CalcTests().runTests()
        console.info("All tests ran successfully!")
    } else {
        render(document.getElementById("root")) {
            appComponent()
        }
    }
}
