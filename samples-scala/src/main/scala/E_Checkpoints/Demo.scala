// Copyright (c) Microsoft. All rights reserved.

package E_Checkpoints

import akka.stream.scaladsl.Sink
import com.microsoft.azure.iot.iothubreact.IoTMessage
import com.microsoft.azure.iot.iothubreact.ResumeOnError._
import com.microsoft.azure.iot.iothubreact.scaladsl._

/** Retrieve messages from IoT hub and save the current position
  * In case of restart the stream starts from where it left
  * (depending on the configuration)
  *
  * Note, the demo requires Cassandra, you can start an instance with Docker:
  * # docker run -ip 9042:9042 --rm cassandra
  */
object Demo extends App {

  val console = Sink.foreach[IoTMessage] {
    t ⇒ println(s"Message from ${t.deviceId} - Time: ${t.created}")
  }

  // Stream using checkpointing
  IoTHub().source(withCheckpoints = true)
    .to(console)
    .run()
}