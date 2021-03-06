// Copyright (c) Microsoft. All rights reserved.

package com.microsoft.azure.iot.iothubreact

import java.util.concurrent.TimeUnit

import com.microsoft.azure.eventhubs.EventHubClient
import com.typesafe.config.{Config, ConfigFactory}

import scala.concurrent.duration._
import scala.language.postfixOps

/** Hold IoT Hub configuration settings
  *
  * @see https://github.com/typesafehub/config for information about the configuration file formats
  */
private[iothubreact] object Configuration {

  // TODO: dependency injection

  private[this] val confConnPath      = "iothub-react.connection."
  private[this] val confStreamingPath = "iothub-react.streaming."

  // Maximum size supported by the client
  private[this] val MaxBatchSize = 999

  // Default IoThub client timeout
  private[this] val DefaultReceiverTimeout = 3 seconds

  private[this] val conf: Config = ConfigFactory.load()

  // IoT hub storage details
  val iotHubName      : String = conf.getString(confConnPath + "hubName")
  val iotHubNamespace : String = getNamespaceFromEndpoint(conf.getString(confConnPath + "hubEndpoint"))
  val iotHubPartitions: Int    = conf.getInt(confConnPath + "hubPartitions")
  val accessPolicy    : String = conf.getString(confConnPath + "accessPolicy")
  val accessKey       : String = conf.getString(confConnPath + "accessKey")
  val accessHostname  : String = conf.getString(confConnPath + "accessHostName")

  // Consumer group used to retrieve messages
  // @see https://azure.microsoft.com/en-us/documentation/articles/event-hubs-overview
  private[this] val tmpCG = conf.getString(confStreamingPath + "consumerGroup")
  val receiverConsumerGroup: String =
    tmpCG.toUpperCase match {
      case "$DEFAULT" ⇒ EventHubClient.DEFAULT_CONSUMER_GROUP_NAME
      case "DEFAULT"  ⇒ EventHubClient.DEFAULT_CONSUMER_GROUP_NAME
      case _          ⇒ tmpCG
    }

  // Message retrieval timeout in milliseconds
  private[this] val tmpRTO = conf.getDuration(confStreamingPath + "receiverTimeout").toMillis
  val receiverTimeout: FiniteDuration =
    if (tmpRTO > 0)
      FiniteDuration(tmpRTO, TimeUnit.MILLISECONDS)
    else
      DefaultReceiverTimeout

  // How many messages to retrieve on each call to the storage
  private[this] val tmpRBS = conf.getInt(confStreamingPath + "receiverBatchSize")
  val receiverBatchSize: Int =
    if (tmpRBS > 0 && tmpRBS <= MaxBatchSize)
      tmpRBS
    else
      MaxBatchSize

  private[this] def getNamespaceFromEndpoint(endpoint: String): String = {
    endpoint.replaceFirst(".*://", "").replaceFirst("\\..*", "")
  }
}
