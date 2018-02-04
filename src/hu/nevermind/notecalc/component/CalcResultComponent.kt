package hu.nevermind.notecalc.component

import kotlinx.html.js.onClickFunction
import react.*
import react.dom.jsStyle
import react.dom.li
import react.dom.ul


interface CalcResultComponentProps : RProps {
    var name: String
    var onLineSelect: (LineSelection) -> Unit
    var cursorLineIndex: Int
    var insertReferencedLineVariable: (referencedLineIndex: Int) -> Unit
    var focusEditor: () -> Unit
}

private var positionWhereCtrlOrShiftWerePressed: Int? = null

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
                    attrs.onClickFunction = { e ->
                        if (e.asDynamic().altKey) {
                            props.insertReferencedLineVariable(currentIndex)
                            props.focusEditor()
                        } else if (e.asDynamic().shiftKey) {
                            props.onLineSelect(LineSelection.ByMouse.ShiftDown(props.cursorLineIndex, currentIndex))
                        } else {
                            props.onLineSelect(LineSelection.ByMouse.Simple(currentIndex, additive = e.asDynamic().ctrlKey))
                        }
                    }
                }
            }
        }
    }
}


fun RBuilder.calcResultComponent(handler: RHandler<CalcResultComponentProps>) = child(CalcResultComponent::class) {
    handler()
}