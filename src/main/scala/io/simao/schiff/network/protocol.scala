package io.simao.schiff.network

import io.simao.schiff.{Reply, Request}

import scala.concurrent.Future

object ClientRequest {
  def apply(reqMsg: Request)(f: Reply => Future[Unit]) = {
    new ClientRequest(reqMsg)(f)
  }

  def unapply(creq: ClientRequest): Option[Request] = {
    Some(creq.message)
  }
}

class ClientRequest(reqMsg: Request)(f: Reply => Future[Unit]) {
  def message: Request = reqMsg

  // TODO: Implement general rule to handle failure of this future within so that
  // clients don't need to do it all the time
  def reply(replyMsg: Reply): Future[Unit] = f(replyMsg)
}
