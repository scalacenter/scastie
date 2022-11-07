package com.olegych.scastie.client.components.editor

import com.olegych.scastie.api
import com.olegych.scastie.api.EitherFormat.JsEither._
import com.olegych.scastie.client.HTMLFormatter
import com.olegych.scastie.client._
import japgolly.scalajs.react._
import netscape.javascript.JSObject
import org.scalajs.dom
import org.scalajs.dom.Element
import org.scalajs.dom.HTMLElement
import play.api.libs.json.{Json, Reads, Writes}
import typings.codemirrorAutocomplete.anon
import typings.codemirrorAutocomplete.mod._
import typings.codemirrorCommands.mod._
import typings.codemirrorLanguage.mod
import typings.codemirrorLanguage.mod._
import typings.codemirrorLint.codemirrorLintStrings
import typings.codemirrorLint.mod._
import typings.codemirrorSearch.mod._
import typings.codemirrorState
import typings.codemirrorState.mod._
import typings.codemirrorView
import typings.codemirrorView.mod._
import typings.highlightJs.mod.{HighlightOptions => HLJSOptions}
import typings.std.Node

import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import scalajs.js
import vdom.all._
import JsUtils._
import hooks.Hooks.UseStateF
import scalajs.concurrent.JSExecutionContext.Implicits.queue
import scalajs.js.Thenable.Implicits._
import js.JSConverters._

case class InteractiveProvider(props: CodeEditor) {
  private val scastieMetalsOptions = api.ScastieMetalsOptions(props.dependencies, props.target)
  private val isConfigurationSupported: Future[Boolean] = {
    if (props.metalsStatus == MetalsDisabled || props.isEmbedded) Future.successful(false)
      else {
      props.setMetalsStatus(MetalsLoading).runNow()
      val res = for {
        res <- makeRequest(scastieMetalsOptions, "isConfigurationSupported")
        text <- res.text()
      } yield  {
        parseMetalsResponse[Boolean](text, res.ok).getOrElse(false)
      }
      res.onComplete {
        case Success(true) => props.setMetalsStatus(MetalsReady).runNow()
        case Failure(exception) => props.setMetalsStatus(NetworkError(exception.getMessage)).runNow()
        case _ =>
      }
      res
    }
  }

  def extension: js.Array[Any] = js.Array[Any](autocompletion(autocompletionConfig), hovers)

  private def toLSPRequest(code: String, offset: Int): api.LSPRequestDTO = {
    val scastieOptions = api.ScastieMetalsOptions(props.dependencies, props.target)
    val offsetParams = api.ScastieOffsetParams(code, offset, props.isWorksheetMode)
    api.LSPRequestDTO(scastieOptions, offsetParams)
  }

  private def makeRequest[A](req: A, endpoint: String)(implicit writes: Writes[A]) =
    // We don't support metals in embedded so we don't need to map server url
    dom.fetch(s"/metals/$endpoint", js.Dynamic.literal(
      body = Json.toJson(req).toString,
      method = dom.HttpMethod.POST
    ).asInstanceOf[dom.RequestInit])

  private def parseMetalsResponse[B](jsonText: String, conditions: Boolean)(implicit readsB: Reads[B]): Option[B] = {
    if (conditions) {
      Json.parse(jsonText).asOpt[Either[api.FailureType, B]] match {
        case None =>
          println(s"Parsing error for text: $jsonText")
          None
        case Some(Left(api.PresentationCompilerFailure(msg))) =>
          props.setMetalsStatus(MetalsConfigurationError(msg)).runNow()
          None
        case Some(Left(api.NoResult(msg))) =>
          println(s"No hover for given position: $msg")
          None
        case Some(Right(value)) =>
          props.setMetalsStatus(MetalsReady)
          Some(value)
      }
    } else None
  }

  private def ifSupported[A](f: => Future[A]): js.Promise[Option[A]] = {
    isConfigurationSupported.flatMap(isSupported => {
      if (isSupported) {
        props.setMetalsStatus(MetalsLoading).runNow()
        val res = f.map(Option(_))
        res.onComplete(_ => props.setMetalsStatus(MetalsReady).runNow())
        res
      } else
        Future.successful(None)
    }).toJSPromise
  }

  private def createAdditionalTextEdits(insertInstructions: List[api.AdditionalInsertInstructions], view: EditorView): Seq[ChangeSpec] = {
    insertInstructions.map(textEdit => {
      val startPos = view.state.doc.line(textEdit.startLine).from.toInt + textEdit.startChar
      val endPos = view.state.doc.line(textEdit.endLine).from.toInt + textEdit.endChar
      js.Dynamic.literal(
        from = startPos,
        to = endPos,
        insert = textEdit.text
      ).asInstanceOf[ChangeSpec]
    })
  }

  private def createEditTransaction(view: EditorView, completion: api.CompletionItemDTO, from: Int, to: Int): TransactionSpec = {
    val cursorChange = from.toDouble +
      completion.additionalInsertInstructions.foldLeft(0)(_ + _.text.length) +
      completion.instructions.cursorMove

    TransactionSpec().setChangesVarargs(
      (js.Dynamic.literal(
        from = from.toDouble,
        to = to.toDouble,
        insert = completion.instructions.text
      ).asInstanceOf[ChangeSpec] +: createAdditionalTextEdits(completion.additionalInsertInstructions, view)):_*
    ).setSelection(codemirrorState.anon.Anchor(cursorChange))
  }

  private def getCompletionInfo(completionItemDTO: api.CompletionItemDTO) = {
    val scastieOptions = api.ScastieMetalsOptions(props.dependencies, props.target)
    val infoFunction: js.Function1[Completion, js.Promise[dom.Node]] = _ =>
        (for {
          res <- makeRequest(api.CompletionInfoRequest(scastieOptions, completionItemDTO), "completionItemResolve")
          text <- res.text()
        } yield {
          parseMetalsResponse[String](text, text.nonEmpty && res.ok).filter(_.nonEmpty).map { completionInfo =>
            val node = dom.document.createElement("div")
            node.innerHTML = InteractiveProvider.marked(completionInfo)
            node
          }.getOrElse(null)
        }
      ).toJSPromise
    infoFunction
  }

  private val completionsF: js.Function1[CompletionContext, js.Promise[CompletionResult]] = { ctx => ifSupported {
    val word: anon.Text = ctx.matchBefore(js.RegExp("\\.?\\w*")).asInstanceOf[anon.Text]

    val request = toLSPRequest(ctx.state.doc.toString(), ctx.pos.toInt)
    val from = if (word.text.headOption == Some('.')) word.from + 1 else word.from

    if (word == null || (word.from == word.to && !ctx.explicit)) {
      Future.successful(null)
    } else {
      (for {
        res <- makeRequest(request, "complete")
        text <- res.text()
      } yield {
          parseMetalsResponse[Set[api.CompletionItemDTO]](text, res.ok).map { completionList => {
            val completions = completionList.map {
              case cmp @ api.CompletionItemDTO(name, detail, tpe, boost, insertInstructions, additionalInsertInstructions, symbol) => {
                Completion(name)
                  .setDetail(detail)
                  .setInfo(getCompletionInfo(cmp))
                  .setType(tpe)
                  .setBoost(-boost.getOrElse(-99).toDouble)
                  .setApplyFunction4((view, _, from, to) => Callback(view.dispatch(createEditTransaction(view, cmp, from.toInt, to.toInt))))
              }
            }
            CompletionResult(from, completions.toJSArray).setValidFor(js.RegExp("^\\w*[!A-Z]?$"))
          }
        }.getOrElse(null)
      })
    }}.map(_.getOrElse(null)).toJSPromise
  }

  private val hovers = hoverTooltip((view, pos, side) => ifSupported {
    val request = toLSPRequest(view.state.doc.toString(), pos.toInt)
    (for {
      res <- makeRequest(request, "hover")
      text <- res.text()
    } yield {
      parseMetalsResponse[api.HoverDTO](text, res.ok).map { hover => {
        val hoverF: js.Function1[EditorView, TooltipView] = view => {
          val node = dom.document.createElement("div")
          node.innerHTML = InteractiveProvider.marked(hover.content)
          TooltipView(node.domToHtml.get)
        }
        Tooltip(hoverF, pos)
      }}.getOrElse(null)
    })
  }.map(_.getOrElse(null)).toJSPromise)

  private val autocompletionConfig = CompletionConfig()
    .setOverrideVarargs(completionsF)
    .setIcons(true)
    .setDefaultKeymap(true)
}

object InteractiveProvider {
  val interactive = new Compartment()
  val interactiveExtension = new Compartment()

  val highlightJS = typings.highlightJs.mod.default
  val highlightF: (String, String, js.UndefOr[Any]) => String = (str, lang, x) => {
    if (lang != null && highlightJS.getLanguage(lang) != null && lang != "") {
      Try { highlightJS.highlight(str, HLJSOptions(lang)).value}.getOrElse(str)
    } else {
      str
    }
  }

  val marked = typings.marked.mod.marked.`package`
  marked.setOptions(typings.marked.mod.marked.MarkedOptions().setHighlight(highlightF))

  def reloadMetalsConfiguration(
    editorView: UseStateF[CallbackTo, EditorView],
    prevProps: Option[CodeEditor],
    props: CodeEditor
  ): Callback = {
    Callback {
      val extension = InteractiveProvider(props).extension
      val effects = interactive.reconfigure(extension)
      editorView.value.dispatch(TransactionSpec().setEffects(effects.asInstanceOf[StateEffect[Any]]))
    }.when_(props.visible && prevProps.isDefined && (
      (
        props.target != prevProps.get.target ||
        props.dependencies != prevProps.get.dependencies ||
        props.isWorksheetMode != prevProps.get.isWorksheetMode
      ) || (prevProps.get.visible != props.visible) ||
        (
          (prevProps.get.metalsStatus == MetalsDisabled && props.metalsStatus == MetalsLoading) ||
          (prevProps.get.metalsStatus != MetalsDisabled && props.metalsStatus == MetalsDisabled)
        )
      )
    )
  }

}

