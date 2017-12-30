package hu.nevermind.notecalc.component

import kotlinx.html.js.onClickFunction
import react.*
import react.dom.jsStyle
import react.dom.li
import react.dom.ul


interface CalcResultComponentProps : RProps {
    var name: String
    var onSelectLine: (Int) -> Unit
}

class CalcResultComponent(props: CalcResultComponentProps) : RComponent<CalcResultComponentProps, RState>(props) {


    override fun RBuilder.render() {
        ul {
            var index = 0

            attrs.jsStyle {
                listStyle = "none"
                paddingLeft = 0
                paddingTop = 0
                border = 0
                margin = 0
            }
            React.Children.forEach(props.children) { child ->
                val currentIndex = index++
                li {
                    key = "resultLine_${currentIndex}"
                    child(child.asDynamic())
                    attrs.onClickFunction = { props.onSelectLine(currentIndex) }
                }
            }
        }
    }
}


fun RBuilder.calcResultComponent(handler: RHandler<CalcResultComponentProps>) = child(CalcResultComponent::class) {
    handler()
}