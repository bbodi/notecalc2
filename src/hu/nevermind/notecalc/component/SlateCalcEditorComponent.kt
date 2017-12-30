package hu.nevermind.notecalc.component


import hu.nevermind.notecalc.TextEvaulator
import kotlinext.js.invoke
import kotlinext.js.js
import kotlinx.html.contentEditable
import react.*
import react.dom.jsStyle
import react.dom.span

class SlateCalcEditorComponent(props: Props) : RComponent<SlateCalcEditorComponent.Props, SlateCalcEditorComponent.State>(props) {

//    private lateinit var syntaxHiliterDecorator: SyntaxHiliterDecorator

    private var SlateReact: dynamic = kotlinext.js.require("slate-react")
    private var Slate: dynamic = kotlinext.js.require("slate")

    data class State(var value: dynamic) : RState

    interface Props : RProps {
        var content: String
        var currentlyEditingLineIndex: Int
        var lineChooserIndex: Int?
        var onChange: (String) -> Unit
        var evaulationResults: List<TextEvaulator.FinalEvaulationResult?>
        var onCursorMove: (line: Int) -> Unit
        var onLineChooserIndexChange: (line: Int?) -> Unit
//        var evaulationResults: List<TextEvaulator.FinalEvaulationResult?>
    }


    init {
        state = State(Slate.Value.fromJSON(js {
            document = js {
                nodes = arrayOf(js {
                    kind = "block"
                    type = "paragraph"
                    nodes = arrayOf(js {
                        kind = "text"
                        leaves = arrayOf(
                                js {
                                    text = "Hi bali"
                                },
                                js {
                                    text = "123 456"
                                    marks = arrayOf(
                                            js {
                                                kind = "mark"
                                                type = "boldy"
                                                data = js {}
                                            }
                                    )
                                },
                                js {
                                    text = " asdlol a"
                                }
                        )
                    })
                })
            }
        }))
        val tokenizer: (List<TextEvaulator.HighlightedText>, dynamic, dynamic) -> String = { tokenStyles, stream, state ->
            val index: Int = state.index
            val tokenToHighlight = tokenStyles.getOrNull(index)
            if (tokenToHighlight == null) {
                stream.skipToEnd()
            } else {
                if (stream.peek() == js("' '") || stream.peek() == js("'\t'")) {
                    stream.eatSpace()
                    "space"
                } else {
                    val words = tokenToHighlight.text.split("\\s")
                    val ok = words.all { word ->
                        stream.eatSpace()
                        stream.match(word, true)
                    }
                    if (ok) {
                        state.index = index + 1
                        tokenToHighlight.cssClassName
                    } else {
                        stream.skipToEnd()
                        "error"
                    }
                }
            }
        }
        defineTokenizer(kotlinext.js.require("codemirror"), tokenizer)
    }

    override fun componentDidMount() {

    }

    override fun componentDidUpdate(prevProps: Props, prevState: State) {

    }

    private fun defineTokenizer(CodeMirror: dynamic, tokenizer: (List<TextEvaulator.HighlightedText>, dynamic, dynamic) -> String) {
        CodeMirror.defineMode("notecalc") { options: dynamic ->
            object {
                val startState = {
                    object {
                        val index = 0
                        val options = options
                    }
                }
                val token = { stream: dynamic, state: dynamic ->
                    val tokenStyles = this@SlateCalcEditorComponent.props.evaulationResults.map { it?.debugInfo?.highlightedTexts }
                            .filterNotNull()
                            .flatten()
//                    val tokenStyles = (options.noteCalcEditor as NoteCalcEditor).getHighlightedTexts()
                    tokenizer(tokenStyles, stream, state)
                }
            }
        }
    }


//    syntaxHiliterDecorator = SyntaxHiliterDecorator()


    fun onChange(newValue: dynamic) {
//        props.onChange(newCode)
        setState {
            value = newValue.value
        }
    }

    fun renderMark(props: dynamic): ReactElement? {
        if (props.mark.type == "boldy") {
            return buildElements {
                span {
                    attrs.contentEditable = false
                    attrs.jsStyle {
                        backgroundColor = "red"
                        width = "40px"
                        borderRadius = "5px"
                        color = "white"
                        cursor = "pointer"
                    }
                    +(props.children as String)
                }
            }
        } else {
            return null
        }
    }


    private val SlateReactComponent: RClass<RProps> = SlateReact.Editor
    override fun RBuilder.render() {
        SlateReactComponent {
            attrs {
                this.asDynamic().value = state.value
                this.asDynamic().renderMark = this@SlateCalcEditorComponent::renderMark
                this.asDynamic().onChange = this@SlateCalcEditorComponent::onChange

            }
        }
    }
}


fun RBuilder.slateCalcEditorComponent(handler: RHandler<SlateCalcEditorComponent.Props>) = child(SlateCalcEditorComponent::class) {
    handler()
}
