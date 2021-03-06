// Copyright (c) Microsoft. All rights reserved.

package com.microsoft.azure.iot.iothubreact

import com.microsoft.azure.eventhubs.EventHubClient
import com.microsoft.azure.servicebus.ConnectionStringBuilder

private object IoTHubStorage extends Logger {

  private[this] val connString = new ConnectionStringBuilder(
    Configuration.iotHubNamespace,
    Configuration.iotHubName,
    Configuration.accessPolicy,
    Configuration.accessKey).toString

  // TODO: Manage transient errors e.g. timeouts
  // EventHubClient.createFromConnectionString(connString)
  //   .get(Configuration.receiverTimeout, TimeUnit.MILLISECONDS)
  def createClient(): EventHubClient = {
    log.info(s"Creating EventHub client to ${Configuration.iotHubName}")
    EventHubClient.createFromConnectionStringSync(connString)
  }
}
