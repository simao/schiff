package io.simao.schiff

import io.simao.schiff.network.{ClientRequest, NodeClient}
import org.scalamock.scalatest.MockFactory
import org.scalatest.FunSuite
import rx.lang.scala.Observable
import rx.lang.scala.schedulers.TestScheduler
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.language.postfixOps

abstract class UnitSuite extends FunSuite with MockFactory

class NodeTest extends UnitSuite {
  implicit val ec = ExecutionContext.global

  val config = Config.default

  val testScheduler = TestScheduler()

  val nodeClient = mock[NodeClient]

  val nodeServer = mock[NodeServer]

  def node = new Node(config) {
    override lazy val timeIntervalScheduler = testScheduler
    override lazy val client = nodeClient
    override lazy val server = nodeServer
  }

  test("sends RequestVoteRpc to all nodes after an election timeout") {
    (nodeServer.startServer _)
      .expects()
      .returning(Observable.never)

    (nodeClient.send _)
      .expects("127.0.0.1", 7677, RequestVote)
      .returning(Future(Ok))
      .once()

    node.start()

    // check that all other servers received RequestVote RPC

    testScheduler.advanceTimeBy(1 second)
  }

  test("handles requests from clients") {
    val serverCallback = mockFunction[Reply, Future[Unit]]
    val request = ClientRequest(AppendEntries)(serverCallback)
    val requests = Observable.just(request, request)

    (nodeServer.startServer _)
      .expects()
      .returning(requests)

    serverCallback
      .expects(Ok)
      .twice()

    node.start()
  }
}
