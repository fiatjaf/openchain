import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Failure
import scodec.bits.ByteVector
import com.github.lolgab.httpclient.{Request, Method}
import ujson._
import scoin._

object Node {
  import Main.logger

  var nodeUrl: String = ""

  private def call(
      method: String,
      params: ujson.Obj = ujson.Obj()
  ): Future[ujson.Value] =
    Request()
      .method(Method.POST)
      .url(nodeUrl)
      .body(
        ujson.write(ujson.Obj("method" -> method, "params" -> params))
      )
      .future()
      .andThen { case Failure(err) =>
        logger.err
          .item(err)
          .item("method", method)
          .item("params", params)
          .msg("failed to contact node")
        scala.sys.exit(1)
      }
      .map { r =>
        val b = ujson.read(r.body).obj
        if b.contains("result") then b("result")
        else throw new Exception(b("error").toString)
      }

  def getNextBlock(txs: Seq[ByteVector]): Future[ByteVector] =
    call(
      "makeblock",
      ujson.Obj(
        "txs" -> txs.map(_.toHex)
      )
    )
      .map(_("hex").str)
      .map(ByteVector.fromValidHex(_))

  def validateTx(tx: ByteVector): Future[Boolean] =
    call("validatetx", ujson.Obj("tx" -> tx.toHex))
      .map(_("ok").bool)

  def getBmmSince(bmmHeight: Int): Future[List[Bmm]] =
    call("getbmmsince", ujson.Obj("bmmheight" -> bmmHeight))
      .map(r =>
        r.arr.toList.map(bmm =>
          Bmm(
            bmm("txid").str,
            bmm("bmmheight").num.toInt,
            bmm("bmmhash").strOpt
              .map(h => ByteVector32(ByteVector.fromValidHex(h)))
          )
        )
      )

  def getBlock(bmmHash: ByteVector32): Future[Option[ujson.Obj]] =
    call("getblock", ujson.Obj("hash" -> bmmHash.toHex))
      .map(r => r.objOpt.map(ujson.Obj(_)))

  def registerBlock(block: ByteVector): Future[Unit] =
    call("registerblock", ujson.Obj("hex" -> block.toHex))
      .map(r => r.objOpt.map(ujson.Obj(_)))
}
