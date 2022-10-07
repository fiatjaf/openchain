import scala.util.{Success, Failure}
import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits._
import org.scalajs.dom
import com.raquo.laminar.api.L._
import openchain._

object BlockView {
  val blocks: Signal[List[BmmTx]] = Main.info.changes
    .flatMap(info =>
      EventStream.fromFuture(
        Node.getBmmSince((info.latestBmmTx.bmmHeight) - 20 min 0)
      )
    )
    .toSignal(List.empty)

  val selectedBlock: Var[Option[Either[String, Block]]] = Var(None)

  def render(): HtmlElement =
    div(
      cls := "my-3",
      div(cls := "text-xl", "blocks"),
      children <-- blocks.map(_.reverse.map(renderBlock(_)))
    )

  final val cellClass =
    "px-3 py-1 border border-amber-600 text-ellipsis overflow-hidden"

  def renderBlock(bmm: BmmTx): HtmlElement =
    div(
      cls := "text-lg bg-amber-100 text-black w-fit max-w-full",
      div(
        cls := "flex",
        div(
          cls := cellClass,
          cls := "text-center",
          styleAttr := "width: 70px",
          bmm.bmmHeight
        ),
        div(
          cls := cellClass,
          cls := "cursor-cell hover:text-amber-500",
          styleAttr := "width: 735px",
          onClick.preventDefault --> { _ =>
            bmm.bmmHash.foreach { hash =>
              val selectedHash = selectedBlock
                .now()
                .map(_.map(_.hash.toHex).merge)
                .getOrElse("")

              if (hash == selectedHash)
                selectedBlock.set(None)
              else
                Node.getBlock(hash).onComplete {
                  case Success(block) =>
                    selectedBlock.set(Some(Right(block)))
                  case Failure(err) =>
                    println(s"failed to load block $hash: ${err.getMessage()}")
                    selectedBlock.set(Some(Left(hash)))
                }
            }
          },
          bmm.bmmHash.getOrElse("")
        ),
        div(
          cls := cellClass,
          styleAttr := "width: 735px",
          a(
            href := s"https://mempool.space/tx/${bmm.txid}",
            target := "_blank",
            cls := "text-sky-700 hover:text-sky-500",
            s"${bmm.txid}"
          )
        )
      ),
      child <-- selectedBlock.signal.map {
        case Some(Left(hash)) if Some(hash) == bmm.bmmHash =>
          div(
            cls := "block p-2 border",
            "block not available.",
            br(),
            br(),
            "either our node is missing it or it was never published by its creator and was skipped."
          )
        case Some(Right(block)) if Some(block.hash.toHex) == bmm.bmmHash =>
          div(
            cls := "block py-2 mx-3",
            div(
              cls := "overflow-hidden text-ellipsis",
              b("previous block: "),
              block.header.previous.toHex
            ),
            div(
              cls := "overflow-hidden text-ellipsis",
              b("merkle root: "),
              block.header.merkleRoot.toHex
            ),
            div(
              b(cls := "text-lg mt-2", "transactions"),
              block.txs.map(renderTransaction(_))
            )
          )
        case _ => div()
      }
    )

  def renderTransaction(tx: Tx): HtmlElement = {
    def attr(name: String, value: String, color: String) =
      div(
        cls := "w-full mx-2",
        s"$name: ",
        div(
          cls := s"text-$color-600 w-full text-ellipsis overflow-hidden",
          value
        )
      )

    div(
      cls := "my-2 py-3 px-1 border-2",
      div(
        cls := "flex w-full",
        styleAttr := "max-width: 48%",
        attr("id", tx.hash.toHex, "gray"),
        attr("asset", tx.asset.toHex, "indigo")
      ),
      div(
        cls := "flex w-full",
        styleAttr := "max-width: 48%",
        attr("from", tx.from.toHex, "yellow"),
        attr("to", tx.to.toHex, "green")
      )
    )
  }
}