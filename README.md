[![Maven Central][maven-badge]][maven-url]
[![Bintray][bintray-badge]][bintray-url]
[![Build][build-badge]][build-url]
[![Issues][issues-badge]][issues-url]
[![Gitter][gitter-badge]][gitter-url]

# IoTHubReact

IoTHub React is an Akka Stream library that can be used to read data from 
[Azure IoT Hub](https://azure.microsoft.com/en-us/services/iot-hub/), via a **reactive stream** with 
**asynchronous back pressure**, and to send messages to connected devices. 
Azure IoT Hub is a service used to connect thousands to millions of devices to the Azure cloud.

The following is a simple example showing how to use the library in Scala. A stream of incoming 
telemetry data is read, parsed and converted to a `Temperature` object, and then filtered based on 
the temperature value:

```scala
IoTHub().source()
    .map(m => parse(m.contentAsString).extract[Temperature])
    .filter(_.value > 100)
    .to(console)
    .run()
```

and the equivalent code in Java:

```java
TypeReference<Temperature> type = new TypeReference<Temperature>() {};

new IoTHub().source()
    .map(m -> (Temperature) jsonParser.readValue(m.contentAsString(), type))
    .filter(x -> x.value > 100)
    .to(console())
    .run(streamMaterializer);
```

#### Streaming from IoT hub to _any_

A more interesting example is reading telemetry data from Azure IoT Hub, and sending it to a Kafka 
topic, so that it can be consumed by other services downstream:

```scala
... 
import org.apache.kafka.common.serialization.StringSerializer
import org.apache.kafka.common.serialization.ByteArraySerializer
import org.apache.kafka.clients.producer.ProducerRecord
import akka.kafka.ProducerSettings
import akka.kafka.scaladsl.Producer

case class KafkaProducer(bootstrapServer: String)(implicit val system: ActorSystem) {

  protected val producerSettings = ProducerSettings(system, new ByteArraySerializer, new StringSerializer)
    .withBootstrapServers(bootstrapServer)

  def getSink() = Producer.plainSink(producerSettings)

  def packageMessage(elem: String, topic: String): ProducerRecord[Array[Byte], String] = {
    new ProducerRecord[Array[Byte], String](topic, elem)
  }
}
```

```scala
val kafkaProducer = KafkaProducer(bootstrapServer)
 
IoTHub().source()
    .map(m => parse(m.contentAsString).extract[Temperature])
    .filter(_.value > 100)
    .runWith(kafkaProducer.getSink())
```

## Source options

### IoT hub partitions

The library supports also reading from a single 
[IoTHub partition](https://azure.microsoft.com/en-us/documentation/articles/event-hubs-overview), 
so a service that consumes IoTHub events can create multiple streams and process them independently.

```scala
val partitionNumber = 1

IoTHubPartition(partitionNumber).source()
    .map(m => parse(m.contentAsString).extract[Temperature])
    .filter(_.value > 100)
    .to(console)
    .run()
```

### Starting point

Unless specified, the stream starts from the beginning of the data present in each partition. 
It's possible to start the stream from a given date and time too:

```scala
val start = java.time.Instant.now()

IoTHub().source(start)
    .map(m => parse(m.contentAsString).extract[Temperature])
    .filter(_.value > 100)
    .to(console)
    .run()
```

### Stream processing restart - saving the current position

The library provides a mechanism to restart the stream from a recent *checkpoint*, to be resilient
to restarts and crashes. 
*Checkpoints* are saved automatically, with a configured frequency, on a storage provided.
For instance, the stream position can be saved every 15 seconds, in a table in Cassandra, or using 
Azure blobs, or a custom backend.

To store checkpoints in Azure blobs the configuration looks like this:

```
iothub-react{

  [... other settings ...]
  
  checkpointing {
    enabled = true
    frequency = 15s
    countThreshold = 1000
    timeThreshold = 30s
    
    storage {
      rwTimeout = 5s
      namespace = "iothub-react-checkpoints"
      
      backendType = "AzureBlob"
      azureblob {
        lease = 15s
        useEmulator = false
        protocol = "https"
        account = "..."
        key = "..."
      }
    }
  }
}
```

Similarly, to store checkpoints in Cassandra:

```
iothub-react{
  [...]
  checkpointing {
    [...]
    storage {
      [...]
      
      backendType = "cassandra"
      cassandra {
        cluster = "localhost:9042"
        replicationFactor = 3
      }
    }
  }
}
```

There are some [configuration settings](src/main/resources/reference.conf) to manage the 
checkpoint behavior, and in future it will also be possible to plug-in custom storage backends,
implementing a simple 
[interface](src/main/scala/com/microsoft/azure/iot/iothubreact/checkpointing/Backends/CheckpointBackend.scala)
to read and write the stream position.

There is also one API parameter to enabled/disable the checkpointing feature, for example:

```scala
val start = java.time.Instant.now()
val withCheckpoints = false

IoTHub().source(start, withCheckpoints)
    .map(m => parse(m.contentAsString).extract[Temperature])
    .filter(_.value > 100)
    .to(console)
    .run()
```

## Build configuration

IoTHubReact is available on Maven Central, you just need to add the following reference in 
your `build.sbt` file:

```scala
libraryDependencies ++= {
  val iothubReactV = "0.8.0"
  
  Seq(
    "com.microsoft.azure.iot" %% "iothub-react" % iothubReactV
  )
}
```

or this dependency in `pom.xml` file if working with Maven:

```xml
<dependency>
    <groupId>com.microsoft.azure.iot</groupId>
    <artifactId>iothub-react_2.12</artifactId>
    <version>0.8.0</version>
</dependency>
```

### IoTHub configuration

IoTHubReact uses a configuration file to fetch the parameters required to connect to Azure IoT Hub.
The exact values to use can be found in the [Azure Portal](https://portal.azure.com):

* **hubName**: see `Endpoints` ⇒ `Messaging` ⇒ `Events` ⇒ `Event Hub-compatible name`
* **hubEndpoint**: see `Endpoints` ⇒ `Messaging` ⇒ `Events` ⇒ `Event Hub-compatible endpoint`
* **hubPartitions**: see `Endpoints` ⇒ `Messaging` ⇒ `Events` ⇒ `Partitions`
* **accessPolicy**: usually `service`, see `Shared access policies`
* **accessKey**: see `Shared access policies` ⇒ `key name` ⇒ `Primary key` (it's a base64 encoded string)
* **accessHostName**: see `Shared access policies` ⇒ `key name` ⇒ `Connection string` ⇒ `HostName`

The values should be stored in your `application.conf` resource (or equivalent). Optionally you can 
reference environment settings if you prefer, for example to hide sensitive data.

```
iothub-react {

  connection {
    hubName        = "<Event Hub compatible name>"
    hubEndpoint    = "<Event Hub compatible endpoint>"
    hubPartitions  = <the number of partitions in your IoT Hub>
    accessPolicy   = "<access policy name>"
    accessKey      = "<access policy key>"
    accessHostName = "<access host name>"
  }
  
  [... other settings...]
}
````

Example using environment settings:

```
iothub-react {

  connection {
    hubName        = ${?IOTHUB_EVENTHUB_NAME}
    hubEndpoint    = ${?IOTHUB_EVENTHUB_ENDPOINT}
    hubPartitions  = ${?IOTHUB_EVENTHUB_PARTITIONS}
    accessPolicy   = ${?IOTHUB_ACCESS_POLICY}
    accessKey      = ${?IOTHUB_ACCESS_KEY}
    accessHostName = ${?IOTHUB_ACCESS_HOSTNAME}
  }
  
  [... other settings...]
}
````

The logging level can be managed via akka configuration, for example:

```
akka {
  # Options: OFF, ERROR, WARNING, INFO, DEBUG
  loglevel = "WARNING"
}
```

There are other settings, to tune performance and connection details:

* **streaming.consumerGroup**: the 
  [consumer group](https://azure.microsoft.com/en-us/documentation/articles/event-hubs-overview)
  used during the connection
* **streaming.receiverBatchSize**: the number of messages retrieved on each call to Azure IoT hub. The 
  default (and maximum) value is 999.
* **streaming.receiverTimeout**: timeout applied to calls while retrieving messages. The default value is 
  3 seconds.
* **checkpointing.enabled**: whether checkpointing is eanbled

The complete configuration reference is available in 
[reference.conf](src/main/resources/reference.conf).

## Samples

The project includes 4 demos, showing some of the use cases and how IoThub React API works. 
All the demos require an instance of Azure IoT hub, with some devices, and messages.

1. **DisplayMessages** [Java]: how to stream Azure IoT hub withing a Java application, filtering 
   temperature values greater than 60C
2. **OutputMessagesToConsole** [Scala]: stream all Temeprature events to console
3. **MessagesThroughput** [Scala]: stream all IoT hub messages, showing the current speed, and
   optionally throttling the speed to 200 msg/sec
4. **Checkpoints** [Scala]: demonstrate how the stream can be restarted without losing its position.
   The current position is stored in a Cassandra table (we suggest to run a docker container for
   the purpose of the demo, e.g. `docker run -ip 9042:9042 --rm cassandra`)

We provide a [device simulator](tools/devices-simulator/README.md) in the tools section, 
which will help setting up these requirements.

When ready, you should either edit the `application.conf` configuration files 
([scala](samples-scala/src/main/resources/application.conf) and
[java](samples-java/src/main/resources/application.conf)) 
with your credentials, or set the corresponding global variables.
Follow the instructions in the previous section on how to set the correct values.

* [`samples-scala`](samples-scala/src/main/scala):
  You can use `sbt run` to run the demos (or the `run_samples.*` scripts)
* [`samples-java`](samples-java/src/main/java): 
  You can use `mvn clean compile exec:java -Dexec.mainClass="DisplayMessages.Demo"` to run the 
  demo app (or the `run_samples.*` scripts)

## Future work

* improve asynchronicity by using EventHub SDK async APIs
* add Sink for Cloud2Device scenarios. `IoTHub.Sink` will allow cloud services to send messages 
  to devices (via Azure IoTHub)

# Contribute Code

If you want/plan to contribute, we ask you to sign a [CLA](https://cla.microsoft.com/) 
(Contribution license Agreement). A friendly bot will remind you about it when you submit 
a pull-request.

If you are sending a pull request, we kindly request to check the code style with IntelliJ IDEA, 
importing the settings from 
[`Codestyle.IntelliJ.xml`](https://github.com/Azure/toketi-iot-tools/blob/dev/Codestyle.IntelliJ.xml).


[maven-badge]: https://img.shields.io/maven-central/v/com.microsoft.azure.iot/iothub-react_2.11.svg
[maven-url]: http://search.maven.org/#search%7Cga%7C1%7Ca%3A%22iothub-react_2.11%22
[bintray-badge]: https://img.shields.io/bintray/v/microsoftazuretoketi/toketi-repo/iothub-react.svg
[bintray-url]: https://bintray.com/microsoftazuretoketi/toketi-repo/iothub-react
[build-badge]: https://img.shields.io/travis/Azure/toketi-iothubreact.svg
[build-url]: https://travis-ci.org/Azure/toketi-iothubreact
[issues-badge]: https://img.shields.io/github/issues/azure/toketi-iothubreact.svg?style=flat-square
[issues-url]: https://github.com/azure/toketi-iothubreact/issues
[gitter-badge]: https://img.shields.io/gitter/room/azure/toketi-repo.js.svg
[gitter-url]: https://gitter.im/azure-toketi/iothub-react
