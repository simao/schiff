package io.simao.schiff.network

import com.typesafe.scalalogging.{LazyLogging, StrictLogging}
import io.netty.bootstrap.Bootstrap
import io.netty.channel._
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.serialization.{ClassResolvers, ObjectDecoder, ObjectEncoder}
import io.simao.schiff.{Reply, Request}

import scala.concurrent.{Future, Promise}
import scala.language.postfixOps

// TODO: A new connection is open every time we contact a client


// Understands multiple connections to multiple clients
class NodeClient extends LazyLogging {
  val workerGroup = new NioEventLoopGroup

  def send(host: String, port: Int, msg: Request): Future[Reply] = {
    val sendPromise = Promise[Reply]()

    try {
      val b = new Bootstrap()
      b.group(workerGroup)
      b.channel(classOf[NioSocketChannel])
      b.handler(new NodeClientChannelInitializer(sendPromise))

      b.connect(host, port)
        .sync()
        .channel()
        .writeAndFlush(msg)

    } catch {
      case t: Throwable => sendPromise.failure(t)
    }

    // TODO: Reuse channels so we don't reconnect all the time
    sendPromise.future
  }
}

class NodeClientChannelInitializer(sendPromise: Promise[Reply]) extends ChannelInitializer[SocketChannel] {
  override def initChannel(ch: SocketChannel) = {
    ch.pipeline().addLast(
      new ObjectEncoder,
      new ObjectDecoder(ClassResolvers.cacheDisabled(null)),
      new NodeClientHandler(sendPromise))
  }
}

class NodeClientHandler(sendPromise: Promise[Reply]) extends SimpleChannelInboundHandler[Reply] with StrictLogging {
  override def channelRead0(ctx: ChannelHandlerContext, msg: Reply): Unit = {
    sendPromise.success(msg)
    ctx.close()
  }

  override def exceptionCaught(ctx: ChannelHandlerContext , cause: Throwable) {
    logger.error("Client error:", cause)
    sendPromise.failure(cause)
    ctx.close()
  }
}
