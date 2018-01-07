package hu.nevermind.notecalc.component

import hu.nevermind.lib.SplitPane
import hu.nevermind.notecalc.TextEvaulator
import hu.nevermind.notecalc.WELCOME_NOTE
import kotlinext.js.invoke
import kotlinext.js.js
import kotlinx.html.dom.create
import kotlinx.html.id
import kotlinx.html.js.onClickFunction
import kotlinx.html.span
import org.w3c.dom.Element
import org.w3c.dom.events.Event
import react.*
import react.dom.div
import react.dom.jsStyle
import kotlin.browser.document

private var saveContentToLocalStoreTimerId: Int? = null
private val timer = kotlinext.js.require("timers-browserify")
private val Store = kotlinext.js.require("store")

interface AppComponentProps : RProps {
    var renderingConfig: Any?
}

private data class InsertedMarker(val marker: TextMarker, val element: Element)

private data class LineData(
        var zeroBasedLineIndex: Int,
        var insertedMarkersForItsResult: List<InsertedMarker>,
        var evaulationResult: TextEvaulator.FinalEvaulationResult?
)

private val lineDataById: MutableMap<String, LineData> = hashMapOf()

private external interface TextMarker {
    fun changed()
    fun find(): Any?
    fun clear()
}

class AppComponent(props: AppComponentProps) : RComponent<AppComponentProps, AppComponent.State>(props) {

    private var lineIdGenerator = 0

    data class State(
            var evaulationResults: List<TextEvaulator.FinalEvaulationResult?>,
            var currentlyEditingLineIndex: Int,
            var lineChooserIndex: Int?,
            var scrollTop: Double
    ) : RState

    init {
        // highlighting tokens must be loaded before CM would be attached so that tokenizing can happen
//        val evaulationResults = evaulateText(loadInitialContent())
        state = State(
                evaulationResults = emptyList(),
                currentlyEditingLineIndex = 0,
                lineChooserIndex = null,
                scrollTop = 0.0
        )
    }


    data class EditorLine(val key: String, val nullBasedIndex: Int, val content: String)

    private fun onLineChooserChanged(lineIndex: Int?) {
        val previousIndex = state.lineChooserIndex
        if (lineIndex == null && previousIndex != null) {
            setState {
                lineChooserIndex = null
            }
        } else if (previousIndex != lineIndex) {
            setState {
                lineChooserIndex = lineIndex
            }
        }
    }

    private fun onChange(newContent: String) {
        val evaulationResults = evaulateText(newContent)

        if (saveContentToLocalStoreTimerId != null) {
            timer.clearTimeout(saveContentToLocalStoreTimerId)
        }
        saveContentToLocalStoreTimerId = timer.setTimeout({
            Store.set("notecalc.savedNote", js {
                content = newContent
            })
            saveContentToLocalStoreTimerId = null
            0
        }, 3000)
        // TODO: improve performance by
        setState {
            this.evaulationResults = evaulationResults.toList()
        }
    }

    private fun evaulateText(newContent: String): Sequence<TextEvaulator.FinalEvaulationResult?> {
        //        ezt valszeg át kell helyezni és nem itt meghivni egyáltalán az evaulatátot
        val textEvaulator = TextEvaulator({ zeroBasedLineIndex -> getLineIdAt(codeMirrorInstance, zeroBasedLineIndex)!! })
        val evaulationResults = newContent.lineSequence().mapIndexed { zeroBasedLineIndex, line ->
            val lineId = ensureLineDataForCurrentLine(zeroBasedLineIndex, lineDataById)
            val finalEvaulationResult = textEvaulator.evaulateLine(zeroBasedLineIndex, line)
            val lineData = lineDataById[lineId]!!
            lineData.zeroBasedLineIndex = zeroBasedLineIndex
            lineData.evaulationResult = finalEvaulationResult


            replaceLineReferencesToHtmlElement(0, line, zeroBasedLineIndex, lineDataById)

            finalEvaulationResult
        }
        return evaulationResults
    }

    private fun ensureLineDataForCurrentLine(processedLineIndex: Int, indexToLineData: MutableMap<String, LineData>): String? {
        var lineId = getLineIdAt(codeMirrorInstance, processedLineIndex)
        if (lineId == null) {
            lineId = "lineId-${lineIdGenerator++}"
            codeMirrorInstance.addLineClass(processedLineIndex, "background", lineId)
            val newLineData = LineData(
                    zeroBasedLineIndex = processedLineIndex,
                    evaulationResult = null,
                    insertedMarkersForItsResult = emptyList()
            )
            indexToLineData[lineId] = newLineData
        }
        return lineId
    }


    private fun replaceLineReferencesToHtmlElement(startIndexOfRemainingText: Int, lineText: String, processedLineIndex: Int, lineDatas: MutableMap<String, LineData>): Boolean {
        val remainingText = lineText.drop(startIndexOfRemainingText)
        val indexOfVar = remainingText.indexOf("\${")
        if (indexOfVar != -1) {
            val referencedLineId = remainingText.drop(indexOfVar + 2).takeWhile { it != '}' }
            val referencedLine = lineDatas[referencedLineId]!!
            val endIndexOfLineReference = startIndexOfRemainingText + indexOfVar + referencedLineId.length + 3 // +3 -> ${}
            val markers: Array<TextMarker>? = codeMirrorInstance.findMarksAt(kotlinext.js.js {
                this.line = processedLineIndex
                ch = startIndexOfRemainingText + indexOfVar + 1
                // + 1 ==> for some reason, in the following example, CM returns the range at the 5th char
                // so I add +1 to the ch, because if there is a range at 0, then there must be at 1 also, since ranges
                // at least as long as this text: "${line-0}", but it will not return with the current range if the ch parameter is 5 (which means
                // I want to check if there is a range right after a previous range, like ${line-0}${line-1}).
                // text:  range
                // index: 012345
                //
            })
            if (markers == null || markers.isEmpty()) {
                val markerElement = document.create.span("referencedLineBox") {
                    +"content"
                }
                val marker: TextMarker = codeMirrorInstance.markText(
                        from = kotlinext.js.js {
                            this.line = processedLineIndex
                            ch = startIndexOfRemainingText + indexOfVar
                        },
                        to = kotlinext.js.js {
                            this.line = processedLineIndex
                            ch = endIndexOfLineReference
                        },
                        options = kotlinext.js.js {
                            atomic = true
                            replacedWith = markerElement
                        }
                )
                referencedLine.insertedMarkersForItsResult += InsertedMarker(marker, markerElement)
            }
            // TODO: free markers when it's not needed anymore
            val resultForReferencedLine = referencedLine.evaulationResult?.result
            val resultString = if (resultForReferencedLine != null) {
                createHumanizedResultString(resultForReferencedLine, 0, 0)
            } else {
                "\u00A0"
            }
            referencedLine.insertedMarkersForItsResult.forEach {
                it.element.innerHTML = resultString
                it.marker.changed()
            }
            return replaceLineReferencesToHtmlElement(endIndexOfLineReference, lineText, processedLineIndex, lineDatas)
        } else {
            return false
        }
    }

    private lateinit var resultParentElement: Element

    override fun componentDidMount() {
        codeMirrorInstance.setSize(null, 500);
        codeMirrorInstance.on("scroll") { cm ->
            setState {
                scrollTop = cm.getScrollInfo().top
            }
        }
        // check the comment for
        // ### Syntax highlighting and rendering of initial content ###
        onChange(loadInitialContent())
        codeMirrorInstance.focus()
    }

    override fun componentWillUnmount() {
        resultParentElement.removeEventListener("scroll", resultParentElementScrollListener)
    }

    override fun componentDidUpdate(prevProps: AppComponentProps, prevState: State) {
        codeMirrorInstance.scrollTo(x = null, y = state.scrollTop)
        resultParentElement.scrollTop = state.scrollTop
    }

    private val resultParentElementScrollListener: (Event) -> Unit = {
        setState {
            scrollTop = resultParentElement.scrollTop
        }
    }


    override fun RBuilder.render() {
        SplitPane {
            attrs.split = "vertical"
            attrs.defaultSize = "50%"
            div {


                attrs.id = "editorParent"
                attrs.jsStyle = js {
                    height = "500px"
                    overflowY = "auto"
                }
                attrs.onClickFunction = {
                    var codeMirrorInstance: dynamic = null
//                    codeMirrorInstance.focus()
                }
                calcEditorComponent {
                    attrs {
                        currentlyEditingLineIndex = state.currentlyEditingLineIndex
                        lineChooserIndex = state.lineChooserIndex
                        onCursorMove = { lineIndex -> if (state.currentlyEditingLineIndex != lineIndex) setState { currentlyEditingLineIndex = lineIndex } }
                        onLineChooserIndexChange = this@AppComponent::onLineChooserChanged
                        initialContent = loadInitialContent()
                        onChange = this@AppComponent::onChange
                        evaulationResults = state.evaulationResults
                        tokenStyles = state.evaulationResults.map { it?.debugInfo?.highlightedTexts }
                                .filterNotNull()
                                .flatten()
                    }
                }
            }
            div("CodeMirror-lines") {
                attrs.id = "resultParent"
                ref { element ->
                    if (element != null) {
                        resultParentElement = element
                        resultParentElement.addEventListener("scroll", resultParentElementScrollListener)
                    } else {
                        resultParentElement.removeEventListener("scroll", resultParentElementScrollListener)
                    }
                }
                attrs.jsStyle = js {
                    height = "500px"
                    overflowY = "auto"
                }
                calcResultComponent {
                    attrs.onSelectLine = { setState { currentlyEditingLineIndex = it } }
                    state.evaulationResults.forEachIndexed { zeroBasedIndex, line ->
                        var classes = "resultLine"
                        if (zeroBasedIndex == state.currentlyEditingLineIndex) {
                            classes = " selectedResult"
                        }
                        if (zeroBasedIndex == state.lineChooserIndex) {
                            classes = " lineChooser"
                        }
                        calcResultLineComponent {
                            key = "calcResultLineComponent$zeroBasedIndex"
                            attrs {
                                zeroBasedLineNumber = zeroBasedIndex
                                this.classes = classes
                                result = line?.result
                                renderingConfig = null
                                postFixNotationTokens = line?.debugInfo?.postFixNotationTokens.orEmpty()
                                onLineClick = { clickedLineIndex: Int -> setState { currentlyEditingLineIndex = clickedLineIndex } }
                            }
                        }
                    }
                }
                if ((js("window").location.href as String).contains("debug=true")) {
                    div {
                        if (state.currentlyEditingLineIndex < state.evaulationResults.size) {
                            div {
                                +("ParsedTokens:" + state.evaulationResults[state.currentlyEditingLineIndex]?.debugInfo?.parsedTokens?.joinToString())
                            }
                            div {
                                +("postFixNotationTokens:" + state.evaulationResults[state.currentlyEditingLineIndex]?.debugInfo?.postFixNotationTokens?.joinToString())
                            }
                            div {
                                +("tokensWithMergedCompoundUnits:" + state.evaulationResults[state.currentlyEditingLineIndex]?.debugInfo?.tokensWithMergedCompoundUnits?.joinToString())
                            }
                        }
                    }
                }
            }
        }
    }

    private fun loadInitialContent(): String {
        val savedNote = Store.get("notecalc.savedNote")
        return savedNote?.content ?: WELCOME_NOTE
    }
}

fun RBuilder.appComponent() = child(AppComponent::class) {

}
