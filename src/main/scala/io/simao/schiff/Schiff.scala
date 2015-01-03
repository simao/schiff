package io.simao.schiff

import com.typesafe.scalalogging.StrictLogging

import scala.io.Source

object Schiff extends App with StrictLogging {
  val config =
    args.toList.lift(0)
      .map(Source.fromFile)
      .map(Config(_))
      .getOrElse({
      logger.warn("Could not load schiff config, using default config")
      Config.default
    })

  val n = new Node(config)

  n.start()

}
