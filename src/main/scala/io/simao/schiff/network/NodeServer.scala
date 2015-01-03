package io.simao.schiff

import com.typesafe.scalalogging.LazyLogging
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel._
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.serialization.{ClassResolvers, ObjectDecoder, ObjectEncoder}
import io.netty.util.concurrent.GenericFutureListener
import io.simao.schiff.network.ClientRequest
import rx.lang.scala.schedulers.ComputationScheduler
import rx.lang.scala.{Observable, Observer}

import scala.concurrent.{Future, Promise}

trait NodeServer {
  def startServer: Observable[ClientRequest]
}

// Understands commands received from the network for a node
class NettyNodeServer(val port: Int) extends NodeServer with LazyLogging {
  def startServer: Observable[ClientRequest] = {
    Observable(startServerLoop)
      .observeOn(ComputationScheduler())
  }

  private def startServerLoop(observer: Observer[ClientRequest]): Unit = {
    val bossGroup = new NioEventLoopGroup()
    val workerGroup = new NioEventLoopGroup()

    try {
      val b = new ServerBootstrap()
      b.group(bossGroup, workerGroup)
        .channel(classOf[NioServerSocketChannel])
        .childHandler(new NodeServerChannelInitializer(observer))

      logger.info("Binding on port {}", port.toString)
      b.bind(port).sync().channel().closeFuture().sync()

    } catch {
      case t: Throwable =>
        logger.error("Error on NodeServer", t)
        observer.onError(t)
    } finally {
      workerGroup.shutdownGracefully()
      bossGroup.shutdownGracefully()
    }
  }
}

class NodeServerChannelInitializer(observer: Observer[ClientRequest]) extends ChannelInitializer[SocketChannel] {
    override def initChannel(ch: SocketChannel) {
    ch.pipeline().addLast(
      new ObjectEncoder,
      new ObjectDecoder(ClassResolvers.cacheDisabled(null)),
      new NodeServerHandler(observer)
    )
  }
}

class NodeServerHandler(val subscriber: Observer[ClientRequest]) extends SimpleChannelInboundHandler[Request] with LazyLogging {
  override def channelRead0(ctx: ChannelHandlerContext, msg: Request): Unit = {
    val clientRequest = ClientRequest(msg)(replyToClient(ctx))
    subscriber.onNext(clientRequest)
  }

  override def exceptionCaught(ctx: ChannelHandlerContext , cause: Throwable ) {
    ctx.close()
    subscriber.onError(cause) // TODO: If this is not handled by the observer, shit will happen
    logger.error("Error: ", cause)
  }

  private def replyToClient(ctx: ChannelHandlerContext)(replyMsg: Reply): Future[Unit] = {
    val p = Promise[Unit]()

    ctx.writeAndFlush(replyMsg).addListener(new GenericFutureListener[ChannelFuture] {
      override def operationComplete(future: ChannelFuture): Unit = p.success(Unit)
    })

    p.future
  }
}
