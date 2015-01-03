package io.simao.schiff

import com.typesafe.scalalogging.{LazyLogging, StrictLogging}
import io.simao.schiff.network.{ClientRequest, NodeClient}
import rx.lang.scala.schedulers.ComputationScheduler
import rx.lang.scala.{Observable, Scheduler, Subscription}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, blocking}
import scala.language.postfixOps

// TODO: Instead of returning state, the handlers can return a function that knows
// how to mutate state, so the caller can just run the current state on that fn

abstract class NodeState(data: NodeStateData) extends LazyLogging {
  type StateTransition = Node => NodeState

  def defaultRequestHandler: PartialFunction[ClientRequest, StateTransition] = {
    case r =>
      logger.info(s"$r not handled")
      stay()
  }

  def handleMessage(creq: ClientRequest): StateTransition = {
    stateHandler.applyOrElse(creq, defaultRequestHandler)
  }

  def handleInternalEvent: PartialFunction[Internal, StateTransition] = {
    case i =>
      logger.warn(s"$i not handled")
      stay()
  }

  def stateHandler:  PartialFunction[ClientRequest, StateTransition]

  def stay(): StateTransition = _ => this
}

class Follower(data: NodeStateData) extends NodeState(data) {
  def stateHandler = {
    case cr @ ClientRequest(AppendEntries) =>
      logger.info("AppendEntries received")
      cr.reply(Ok)
      stay()
  }

  override def handleInternalEvent = {
    case ElectionTimeout => node => {
      logger.info("Start new election!")
      node.resetElectionTimeout()
      node.broadcast(RequestVote)
      val election = Election().voteFor(node.config.self)
      new Candidate(
        data.copy(currentTerm = data.currentTerm.map(_ + 1),
          election = Some(election)
      ))
    }
  }
}

object Election {
  def apply() = new Election(None, Map())
}

case class Election(votedFor: Option[NodeConfig], votes: Map[NodeConfig, Int]) {
  def voteFor(node: NodeConfig): Election = {
    new Election(Some(node), votes)
  }
}

class Candidate(val data: NodeStateData) extends NodeState(data) {
  def stateHandler = {
    case _ =>
      logger.info("Handled msg as Candidate ")
      stay()
  }
}

case class NodeStateData(currentTerm: Option[Long] = None,
                         votedFor: Option[NodeConfig] = None,
                          lastLeaderRpcAt: Option[Long] = None,
                          election: Option[Election] = None)

class Node(val config: Config) extends StrictLogging {
  implicit val ec = ExecutionContext.global

  lazy val client = new NodeClient

  lazy val timeIntervalScheduler: Scheduler = ComputationScheduler()

  lazy val server: NodeServer = new NettyNodeServer(config.self.port)

  var electionTimeout: Option[Subscription] = None

  var currentState: NodeState = new Follower(NodeStateData())

  for {
    n <- config.otherNodes
  } yield {
    logger.info(s"Using remote node at ${n.host}:${n.port}")
  }

  def broadcast(msg: Request): Unit = {
    config.otherNodes.map(n => client.send(n.host, n.port, msg))
  }

  def start() = {
    val serverInboundEvents = server.startServer
    resetElectionTimeout()
    blocking { serverInboundEvents.subscribe(handlerServerEvent _)  }
  }

  // TODO: Super stupid non thread safe method!
  // Maybe just use STM?
  def resetElectionTimeout(): Unit = {
    // TODO: Interval must be a random number
    this.electionTimeout.map(_.unsubscribe())
    this.electionTimeout = Some(Observable.interval(1 seconds, timeIntervalScheduler)
      .subscribe(_ =>
      changeState(currentState.handleInternalEvent(ElectionTimeout)(this))))
  }

  // TODO: Super stupid dangerous method, how to fix this?
  // Maybe we can use STM?
  private def changeState(newState: NodeState) = {
    this.currentState = newState
  }

  private def handlerServerEvent(req: ClientRequest): Unit = {
    val newStateTransition = currentState.handleMessage(req)
    changeState(newStateTransition(this))
  }
}
