package hu.nevermind.notecalc.component


import hu.nevermind.notecalc.TextEvaulator
import kotlinext.js.invoke
import kotlinext.js.js
import org.w3c.dom.DataTransfer
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
    val lineClasses = cm.lineInfo(zeroBasedLineIndex)?.bgClass as String?
    val lineId = if (lineClasses != null) lineClasses.indexOf("lineId-").let { index ->
        if (index == -1) null else lineClasses.drop(index).takeWhile { it != ' ' }
    } else null
    return lineId
}

private interface CodeMirrorEvent {
    val ctrlKey: Boolean
    val shiftKey: Boolean
    val altKey: Boolean
    val x: Int
    val y: Int
    val key: String
    val clipboardData: DataTransfer

    fun preventDefault()
}

class CalcEditorComponent(props: Props) : RComponent<CalcEditorComponent.Props, RState>(props) {

    interface Props : RProps {
        var initialContent: String
        var cursorLineIndex: Int
        var selectedLineIndices: Collection<Int>
        var lineChooserIndex: Int?
        var onChange: (String) -> Unit
        var onLineChanged: (Int) -> Unit
        var evaulationResults: Collection<TextEvaulator.EvaulationResult?>
        var tokenStyles: List<TextEvaulator.HighlightedText>
        var onLineSelect: (LineSelection) -> Unit
        var onCursorYPositionChanged: (line: Int) -> Unit
        var clearLineSelection: () -> Unit
        var onLineChooserIndexChange: (line: Int?) -> Unit
        var insertReferencedLineVariable: (referencedLineIndex: Int) -> Unit
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
                        state.index = index + 1
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
                props.onCursorYPositionChanged(cursor.line)
            }
        }
        cm.on("dragstart") { editor, e: CodeMirrorEvent ->
            console.log("dragstart")
        }
        cm.on("copy") { editor, e: CodeMirrorEvent ->
            console.log("copy")
        }
        cm.on("dragenter") { editor, e: CodeMirrorEvent ->
            console.log("dragenter")
        }
        cm.on("dragover") { editor, e: CodeMirrorEvent ->
            console.log("dragover")
        }
        cm.on("drop") { editor, e: CodeMirrorEvent ->
            console.log("drop")
        }
        cm.on("keypress") { editor, e: CodeMirrorEvent ->
            console.log("keypress")
        }
        cm.on("clear") { editor, e: CodeMirrorEvent ->
            console.log("clear")
        }
        cm.on("mousedown", ::handleMouseDown)
        cm.on("keyup") { editor, e: CodeMirrorEvent ->
            if (e.key == "Alt" && props.lineChooserIndex != null) {
                props.onLineChooserIndexChange(null)
            } else if ((e.key == "Control" || e.key == "Shift") && positionWhereCtrlOrShiftWerePressed != null) {
                positionWhereCtrlOrShiftWerePressed = null
            }
        }
        cm.on("keydown", ::handleKeyDown)
        cm.on("inputRead") { editor, e: CodeMirrorEvent ->
            console.log("inputRead")
        }
        cm.on("keyHandled") { editor, e: CodeMirrorEvent ->
            console.log("keyHandled")
        }
        // line has been changed
        cm.on("change") { lineHandle, changeObj ->
            props.onLineChanged(changeObj.from.line)
        }
        cm.on("renderLine") { editor, line, element: Element ->
        }
    }

    private var positionWhereCtrlOrShiftWerePressed: Int? = null

    private fun handleKeyDown(editor: dynamic, e: CodeMirrorEvent): Boolean {
        if (e.key != "ArrowUp" && e.key != "ArrowDown") {
            return true
        }
        val currentlyEditingLineIndex = props.cursorLineIndex
        val onlyOneLineIsSelectedAndAltPressed = currentlyEditingLineIndex != null && e.altKey == true
        if (onlyOneLineIsSelectedAndAltPressed) {
            if (e.key == "ArrowUp") {
                props.onLineChooserIndexChange((props.lineChooserIndex ?: currentlyEditingLineIndex!!) - 1)
            } else if (e.key == "ArrowDown") {
                props.onLineChooserIndexChange((props.lineChooserIndex ?: currentlyEditingLineIndex!!) + 1)
            }
        } else {
            val lastlySelectedLineIndex: Int = editor.getCursor().line
            if (e.ctrlKey || e.shiftKey) {
                val dir = if (e.key == "ArrowUp") -1 else 1
                val targetLine = lastlySelectedLineIndex + dir
                val ctrlWasReleasedSoFar = positionWhereCtrlOrShiftWerePressed == null
                if (ctrlWasReleasedSoFar) {
                    positionWhereCtrlOrShiftWerePressed = props.cursorLineIndex
                }
                if (e.shiftKey) {
                    props.onLineSelect(LineSelection.ByCursorKeys(positionWhereCtrlOrShiftWerePressed!!,
                            targetLine,
                            additive = false,
                            inclusive = true)
                    )
                } else { // e.ctrlKey
                    props.onLineSelect(LineSelection.ByCursorKeys(positionWhereCtrlOrShiftWerePressed!!,
                            targetLine,
                            additive = true,
                            inclusive = false)
                    )
                }
            } else {
                props.clearLineSelection()
            }
        }
        return true
    }

    private fun handleMouseDown(editor: dynamic, e: CodeMirrorEvent): Boolean {
        val lineCh = editor.coordsChar(js {
            left = e.x
            top = e.y
        })
        val clickedLineIdex: Int = lineCh.line
        if (e.altKey) {
            props.insertReferencedLineVariable(clickedLineIdex)
        } else if (e.shiftKey) {
            props.onLineSelect(LineSelection.ByMouse.ShiftDown(props.cursorLineIndex, clickedLineIdex))
        } else {
            props.onLineSelect(LineSelection.ByMouse.Simple(clickedLineIdex, additive = e.ctrlKey))
        }
        if (!e.altKey) {
            editor.setCursor(object {
                val line = clickedLineIdex
                val ch = lineCh.ch
            })
        }
        e.preventDefault()
        return false
    }

    override fun componentDidUpdate(prevProps: Props, prevState: RState) {
        val cm = codeMirrorInstance
        val singleLineSelectionFromResultWindow = props.cursorLineIndex != null && prevProps.cursorLineIndex != props.cursorLineIndex && !cm.hasFocus()
        if (singleLineSelectionFromResultWindow) {
            val newLineIndex = props.cursorLineIndex
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
                props.insertReferencedLineVariable(prevLineChooserIndex!!)
            }
        }

        updateHighlights(prevProps, cm)

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

    private fun updateHighlights(prevProps: Props, cm: dynamic) {
        prevProps.selectedLineIndices.forEach {
            cm.removeLineClass(it, "background", "selectedEditorLine")
        }
        props.selectedLineIndices.forEach {
            cm.addLineClass(it, "background", "selectedEditorLine")
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
