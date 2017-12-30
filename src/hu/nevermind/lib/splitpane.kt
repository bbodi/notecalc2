package hu.nevermind.lib

import react.RClass
import react.RProps
import kotlinext.js.*


external interface SplitPaneProps : RProps {
    var split: String
    var defaultSize: Any
    var primary: String
    var maxSize: Int
    var minSize: Int
    var step: String
    var allowResize: Boolean
    var onChange: (size: Int)->Unit
}

val SplitPane: RClass<SplitPaneProps> = require("react-split-pane")