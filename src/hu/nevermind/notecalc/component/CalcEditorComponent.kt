package hu.nevermind.notecalc.component


import hu.nevermind.notecalc.TextEvaulator
import kotlinext.js.invoke
import kotlinext.js.js
import org.w3c.dom.Element
import react.*

private var hasInitialHighlighting = false
private val codeMirrorModule = kotlinext.js.require("codemirror")
private val plugins = {
    kotlinext.js.require("codemirror/addon/selection/active-line.js")
    kotlinext.js.require("codemirror/addon/edit/matchbrackets.js")
    kotlinext.js.require("codemirror/addon/scroll/annotatescrollbar.js")
    kotlinext.js.require("codemirror/addon/search/matchesonscrollbar.js")
    kotlinext.js.require("codemirror/addon/search/matchesonscrollbar.css")
    kotlinext.js.require("codemirror/addon/search/searchcursor.js")
}()

var reactCodeMirrorInstance: dynamic = null
var codeMirrorInstance: dynamic = null

fun getLineIdAt(cm: dynamic, zeroBasedLineIndex: Int): String? {
    val lineClasses = cm.lineInfo(zeroBasedLineIndex).bgClass as String?
    val lineId = if (lineClasses != null) lineClasses.indexOf("lineId-").let { index ->
        if (index == -1) null else lineClasses.drop(index).takeWhile { it != ' ' }
    } else null
    return lineId
}

class CalcEditorComponent(props: Props) : RComponent<CalcEditorComponent.Props, RState>(props) {

//    private lateinit var syntaxHiliterDecorator: SyntaxHiliterDecorator


    interface Props : RProps {
        var initialContent: String
        var currentlyEditingLineIndex: Int
        var lineChooserIndex: Int?
        var onChange: (String) -> Unit
        var evaulationResults: List<TextEvaulator.FinalEvaulationResult?>
        var tokenStyles: List<TextEvaulator.HighlightedText>
        var onCursorMove: (line: Int) -> Unit
        var onLineChooserIndexChange: (line: Int?) -> Unit
    }

    init {
        // just to avoid dead code elimination
        println("$plugins were initialized")
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
        defineTokenizer(codeMirrorModule, tokenizer)
    }

    override fun componentDidMount() {
        val cm = codeMirrorInstance
        cm.on("cursorActivity") { cm: dynamic ->
            if (cm.hasFocus()) {
                val cursor = cm.getCursor("head")
                props.onCursorMove(cursor.line)
//                resultsCodeMirrorInstance.setCursor(object {
//                    val line = cursor.line
//                    val ch = 0
//                })
            }
        }
        cm.on("dragstart") { editor, e ->
            console.log("dragstart")
        }
        cm.on("dragenter") { editor, e ->
            console.log("dragenter")
        }
        cm.on("dragover") { editor, e ->
            console.log("dragover")
        }
        cm.on("drop") { editor, e ->
            console.log("drop")
        }
        cm.on("keypress") { editor, e ->
            console.log("keypress")
        }
        cm.on("clear") { editor, e ->
            console.log("clear")
        }
        cm.on("keyup") { editor, e ->
            if (e.key == "Alt" && props.lineChooserIndex != null) {
                props.onLineChooserIndexChange(null)
            }
        }
        cm.on("keydown") { editor, e ->
            if (e.altKey == true) {
                if (e.key == "ArrowUp") {
                    props.onLineChooserIndexChange((props.lineChooserIndex ?: props.currentlyEditingLineIndex) - 1)
                } else if (e.key == "ArrowDown") {
                    props.onLineChooserIndexChange((props.lineChooserIndex ?: props.currentlyEditingLineIndex) + 1)
                }
            }
        }
        cm.on("inputRead") { editor, e ->
            console.log("inputRead")
        }
        cm.on("keyHandled") { editor, e ->
            console.log("keyHandled")
        }
        // line has been changed
        cm.on("change") { lineHandle, changeObj ->
        }
        cm.on("renderLine") { editor, line, element: Element ->
        }
    }

    override fun componentDidUpdate(prevProps: Props, prevState: RState) {
        val cm = codeMirrorInstance
        if (prevProps.currentlyEditingLineIndex != props.currentlyEditingLineIndex && !cm.hasFocus()) {
            val newLineIndex = props.currentlyEditingLineIndex
            val endOfLine = cm.getLine(newLineIndex).length
            cm.setCursor(js {
                line = newLineIndex
                ch = endOfLine
            })
            reactCodeMirrorInstance.focus()
        }
        val prevLineChooserIndex = prevProps.lineChooserIndex
        val newLineChooserIndex = props.lineChooserIndex
        if (prevLineChooserIndex != newLineChooserIndex) {
            if (prevLineChooserIndex != null) {
                cm.removeLineClass(prevLineChooserIndex, "background", "lineChooser")
            }
            if (newLineChooserIndex != null) {
                cm.addLineClass(newLineChooserIndex, "background", "lineChooser")
            } else { // selection happened
                val cursor = cm.getCursor();
                val chosedLineId = getLineIdAt(cm, prevLineChooserIndex!!)!!
                val variableStringForLineIdRef = "\${$chosedLineId}"
                cm.replaceRange(variableStringForLineIdRef, cursor); // +1 because of null-based indexing
            }
        }
        // ### Syntax highlighting and rendering of initial content ###
        // Since for highlighting and rendering both CodeMirror(it contains lineId-s for ${line references})
        // and the AppComponent(it contains the token names) are required, we have to wait until CM is mounted,
        // then evaulating it's initial content (filling up the highlighting infos with token names),
        // then forcing CM to redraw it's content.
        // But token names stored in the state of AppComponent, it must be propogated to here, which happens
        // asynchronously, that's why the code below must be in this method.
        if (!hasInitialHighlighting) {
            codeMirrorInstance.setOption("mode", "notecalc") // triggering redraw
            hasInitialHighlighting = true
        }
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
                    tokenizer(this@CalcEditorComponent.props.tokenStyles, stream, state)
                }
            }
        }
    }


//    syntaxHiliterDecorator = SyntaxHiliterDecorator()


    fun onChange(newCode: String) {
        props.onChange(newCode)
    }


    private val CodeMirrorReactComponent: RClass<RProps> = kotlinext.js.require("react-codemirror")
    override fun RBuilder.render() {
        CodeMirrorReactComponent {
            attrs {
                this.asDynamic().onChange = this@CalcEditorComponent::onChange
                this.asDynamic().value = props.initialContent
                this.asDynamic().options = js {
                    lineNumbers = true
                    mode = "notecalc"
                    styleActiveLine = true
                    extraKeys = js {
                        this["Ctrl-Space"] = "autocomplete"
                    }
                    highlightSelectionMatches = object {
                        val showToken = js("/\\w/")
                        val annotateScrollbar = true
                        val showMatchesOnScrollbar = true
                    }
                }
                ref = { element ->
                    if (element != null) {
                        reactCodeMirrorInstance = element
                        codeMirrorInstance = reactCodeMirrorInstance.getCodeMirror()
                    }
                }
            }
        }
    }
}


fun RBuilder.calcEditorComponent(handler: RHandler<CalcEditorComponent.Props>) = child(CalcEditorComponent::class) {
    handler()
}
