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
import org.w3c.dom.get
import react.*
import react.dom.div
import react.dom.jsStyle
import kotlin.browser.document
import kotlin.math.absoluteValue

private var saveContentToLocalStoreTimerId: Int? = null
private val timer = kotlinext.js.require("timers-browserify")
private val Store = kotlinext.js.require("store2")

interface AppComponentProps : RProps {
    var renderingConfig: Any?
}

private data class InsertedMarker(val marker: TextMarker, val element: Element)

private data class LineData(
        var insertedMarkersForItsResult: List<InsertedMarker>,
        var evaulationResult: TextEvaulator.EvaulationResult
)

private val lineDataById: MutableMap<String, LineData> = hashMapOf()

private external interface TextMarker {
    fun changed()
    fun find(): Any?
    fun clear()
}

class AppComponent(props: AppComponentProps) : RComponent<AppComponentProps, AppComponent.State>(props) {

    private var lineIdGenerator = 0
    private var textAreaContent: List<String> = listOf("hacky name for null")
    private var numberOfLinesWasChanged = false
    private val textEvaulator = TextEvaulator()

    data class State(
            var evaulationResults: List<TextEvaulator.EvaulationResult>,
            var currentlyEditingLineIndex: Int,
            var lineChooserIndex: Int?
    ) : RState

    init {
        state = State(
                evaulationResults = emptyList(),
                currentlyEditingLineIndex = 0,
                lineChooserIndex = null
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

    // on change, this method is called first
    private fun onChangeFirstPhase(newContent: String) {
        val oldContent = textAreaContent
        textAreaContent = newContent.lines()
        numberOfLinesWasChanged = textAreaContent.size != oldContent.size
        if (oldContent.firstOrNull() == "hacky name for null") {
            // on initialization, "change" event is not triggered by CM, so it has to be done explicitly
            onChangeSecondPhase(0)
        }

        if (saveContentToLocalStoreTimerId != null) {
            timer.clearTimeout(saveContentToLocalStoreTimerId)
        }
        saveContentToLocalStoreTimerId = timer.setTimeout({
            Store.local.set("notecalc.savedNote", js {
                content = setLineReferencesToTheirRealLineNumber(newContent)
            })
            Store.session.set("notecalc.savedNote", js {
                content = setLineReferencesToTheirRealLineNumber(newContent)
            })
            saveContentToLocalStoreTimerId = null
            0
        }, 3000)
    }

    private fun setLineReferencesToTheirRealLineNumber(content: String): String {
        val oldIdsToNewIds = lineDataById.keys.map { lineId ->
            val realOneBasedIndex = document.getElementsByClassName(lineId)[0] // line element
                    ?.parentElement?.getElementsByClassName("CodeMirror-gutter-wrapper")?.get(0)?.textContent?.toInt()
            val oldId = lineId.drop("lineId-".length).toInt()
            val referencedLineWasMoved = realOneBasedIndex != null && oldId + 1 != realOneBasedIndex
            if (referencedLineWasMoved) {
                oldId to (realOneBasedIndex!! - 1)
            } else {
                null
            }
        }.filterNotNull().groupBy { it.first }.mapValues { it.value[0].second }

        fun replaceVariableIds(str: String, processedCharsCount: Int): String {
            if (processedCharsCount >= str.length) {
                return str
            }
            val indexOfVar = str.indexOf("\${", processedCharsCount)
            return if (indexOfVar == -1) {
                str
            } else {
                val preText = str.take(indexOfVar)
                val endOfOriginalVarIndex = str.indexOf('}', indexOfVar) + 1 /*}*/
                val varStr = str.drop(indexOfVar).take(endOfOriginalVarIndex - indexOfVar)
                val postText = str.drop(endOfOriginalVarIndex)
                val varId = varStr.drop("\${lineId-".length).takeWhile { it != '}' }.toInt()
                val newId = oldIdsToNewIds[varId]
                if (newId != null) {
                    replaceVariableIds("$preText\${lineId-$newId}$postText", endOfOriginalVarIndex)
                } else {
                    replaceVariableIds(str, endOfOriginalVarIndex)
                }
            }
        }
        return replaceVariableIds(content, 0)
    }

    // on change, after the "onChangeFirstPhase" method, this method is called with the index of modified line
    private fun onChangeSecondPhase(firstZeroBasedModifiedLineIndex: Int) {
        setState {
            this.evaulationResults = parseAndEvaulateModifiedLinesIfNecessary(firstZeroBasedModifiedLineIndex).toList()
        }
    }

    private fun parseAndEvaulateModifiedLinesIfNecessary(firstZeroBasedModifiedLineIndex: Int): List<TextEvaulator.EvaulationResult> {
        if (firstZeroBasedModifiedLineIndex >= state.evaulationResults.size) {
            return state.evaulationResults + textAreaContent.drop(state.evaulationResults.size).mapIndexed { i, line ->
                val zeroBasedLineIndex = firstZeroBasedModifiedLineIndex + i
                parseIfNecessaryAndEvaulate(zeroBasedLineIndex, line, true)
            }
        } else {
            val evaulationResults = state.evaulationResults.take(firstZeroBasedModifiedLineIndex).toMutableList()

            evaulationResults.apply {
                val modifiedLine = textAreaContent.drop(firstZeroBasedModifiedLineIndex).first()
                this += parseIfNecessaryAndEvaulate(firstZeroBasedModifiedLineIndex, modifiedLine, true)
            }

            evaulationResults += textAreaContent.drop(firstZeroBasedModifiedLineIndex + 1).mapIndexed { index, line ->
                val zeroBasedLineIndex = firstZeroBasedModifiedLineIndex + 1 + index
                parseIfNecessaryAndEvaulate(zeroBasedLineIndex, line, numberOfLinesWasChanged)
            }

            return evaulationResults
        }

    }

    fun parseIfNecessaryAndEvaulate(zeroBasedLineIndex: Int, line: String, needReparsing: Boolean): TextEvaulator.EvaulationResult {
        val parsingResult = if (needReparsing) {
            textEvaulator.parseLine(zeroBasedLineIndex, line)
        } else {
            state.evaulationResults.getOrElse(zeroBasedLineIndex) {
                // TODO, creation of EvaulationResult only for type matching is an ugly hack
                TextEvaulator.EvaulationResult(null, textEvaulator.parseLine(zeroBasedLineIndex, line))
            }.debugInfo
        }
        val lineId = ensureLineId(zeroBasedLineIndex)
        val resultOperand = textEvaulator.evaulate(line, zeroBasedLineIndex, parsingResult.postfixNotationTokens, lineId)
        val newLineData = LineData(
                evaulationResult = TextEvaulator.EvaulationResult(resultOperand, parsingResult),
                insertedMarkersForItsResult = emptyList()
        )
        lineDataById[lineId] = newLineData
        replaceLineReferencesToHtmlElement(0, line, zeroBasedLineIndex, lineDataById)
        return newLineData.evaulationResult
    }

    private fun ensureLineId(processedLineIndex: Int): String {
        var lineId = getLineIdAt(codeMirrorInstance, processedLineIndex)
        if (lineId == null) {
            lineId = "lineId-${lineIdGenerator++}"
            codeMirrorInstance.addLineClass(processedLineIndex, "background", lineId)
        }
        return lineId
    }

    private fun replaceLineReferencesToHtmlElement(startIndexOfRemainingText: Int, lineText: String, processedLineIndex: Int, lineDatas: MutableMap<String, LineData>): Boolean {
        val remainingText = lineText.drop(startIndexOfRemainingText)
        val indexOfVar = remainingText.indexOf("\${")
        if (indexOfVar == -1) {
            return false
        }
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
        val resultForReferencedLine = referencedLine.evaulationResult.result
        val resultString = if (resultForReferencedLine != null) {
            createHumanizedResultString(resultForReferencedLine).first.trim()
        } else {
            "\u00A0"
        }
        referencedLine.insertedMarkersForItsResult.forEach {
            it.element.innerHTML = resultString
            it.marker.changed()
        }
        return replaceLineReferencesToHtmlElement(endIndexOfLineReference, lineText, processedLineIndex, lineDatas)
    }

    private lateinit var resultParentElement: Element

    override fun componentDidMount() {
        codeMirrorInstance.setSize(null, 500)
        codeMirrorInstance.on("scroll") { cm ->
            resultParentElement.scrollTop = cm.getScrollInfo().top
            0
        }
        // check the comment for
        // ### Syntax highlighting and rendering of initial content ###
        onChangeFirstPhase(loadInitialContent())
        codeMirrorInstance.focus()
    }

    override fun componentWillUnmount() {
        resultParentElement.removeEventListener("scroll", resultParentElementScrollListener)
    }

    private val resultParentElementScrollListener: (Event) -> Unit = {
        codeMirrorInstance.scrollTo(x = null, y = resultParentElement.scrollTop)
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
                        onChange = this@AppComponent::onChangeFirstPhase
                        onLineChanged = this@AppComponent::onChangeSecondPhase
                        evaulationResults = state.evaulationResults
                        tokenStyles = state.evaulationResults.map { it.debugInfo.highlightedTexts }.flatten()
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
                    val decimalPlacesOflongestResult = state.evaulationResults.map { it.result?.toRawNumber()?.let { countOfDecimalPlaces(it) } ?: 0 }.max() ?: 0
                    val thousandGroupingCharactersCount = (decimalPlacesOflongestResult - 1) / 3
                    val requiredSpace = decimalPlacesOflongestResult + thousandGroupingCharactersCount
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
                                padStart = requiredSpace
                                zeroBasedLineNumber = zeroBasedIndex
                                this.classes = classes
                                result = line.result
                                renderingConfig = null
                                postFixNotationTokens = line.debugInfo.postfixNotationTokens
                                onLineClick = { clickedLineIndex: Int -> setState { currentlyEditingLineIndex = clickedLineIndex } }
                            }
                        }
                    }
                }
                if ((js("window").location.href as String).contains("debug=true")) {
                    div {
                        if (state.currentlyEditingLineIndex < state.evaulationResults.size) {
                            div {
                                +("ParsedTokens:" + state.evaulationResults[state.currentlyEditingLineIndex].debugInfo.parsedTokens.joinToString())
                            }
                            div {
                                +("postfixNotationTokens:" + state.evaulationResults[state.currentlyEditingLineIndex].debugInfo.postfixNotationTokens.joinToString())
                            }
                            div {
                                +("tokensWithMergedCompoundUnits:" + state.evaulationResults[state.currentlyEditingLineIndex].debugInfo.tokensWithMergedCompoundUnits.joinToString())
                            }
                        }
                    }
                }
            }
        }
    }

    private fun countOfDecimalPlaces(num: Double): Int {
        if (num.isInfinite()) {
            return 1 // '∞' symbol
        }
        var tmp = num.absoluteValue
        var i = 1
        while (tmp > 1) {
            tmp /= 10
            ++i
        }
        return i + if (num < 0) 1 else 0
    }

    private fun loadInitialContent(): String {
        val savedNote = Store.session.get("notecalc.savedNote") ?: Store.local.get("notecalc.savedNote")
        return savedNote?.content ?: WELCOME_NOTE
    }
}

fun RBuilder.appComponent() = child(AppComponent::class) {

}
