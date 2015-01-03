package io.simao.schiff

sealed trait SchiffMessage

trait Reply extends SchiffMessage
trait Request extends SchiffMessage
trait Internal extends SchiffMessage

case object AppendEntries extends Request
case object RequestVote extends Request

case object Ok extends Reply
case object NOk extends Reply
case object NoReply extends Reply

case object ElectionTimeout extends Internal
