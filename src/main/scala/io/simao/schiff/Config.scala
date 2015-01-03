package io.simao.schiff

import argonaut.Argonaut._
import argonaut._

import scala.io.Source
import scalaz.{-\/, \/-}

case class NodeConfig(host: String, port: Int)

object NodeConfig {
  implicit def nodeConfigCodecJson: CodecJson[NodeConfig] =
    casecodec2(NodeConfig.apply, NodeConfig.unapply)("host", "port")
}

class Config(serialized_config: JsonConfig) {
  def otherNodes: List[NodeConfig] = serialized_config.otherNodes.getOrElse(List(NodeConfig("127.0.0.1", 7677)))

  def self: NodeConfig = serialized_config.self.getOrElse(NodeConfig("127.0.0.1", 7676))
}

class DeserializeConfigException(msg: String) extends RuntimeException

case class JsonConfig(self: Option[NodeConfig], otherNodes: Option[List[NodeConfig]])

object JsonConfig {
  implicit def jsonConfigCodecJson: CodecJson[JsonConfig] =
    casecodec2(JsonConfig.apply, JsonConfig.unapply)("self", "other_nodes")
}

object Config {
  def default = Config("{}")

  def apply(s: Source): Config = {
    val content = s.mkString
    apply(content)
  }

  def apply(content: String): Config = {
    Parse.decodeEither[JsonConfig](content) match {
      case \/-(config) => new Config(config)
      case -\/(msg) =>
        throw new DeserializeConfigException("Error parsing config file: " + msg)
    }
  }
}
