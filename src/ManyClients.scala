import modbat.dsl._
import org.eclipse.paho.client.mqttv3.MqttAsyncClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttException
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

class ManyClients extends Model {
  val seed:     String      = "%x".format(getRandomSeed)
  class Client (val clientId: String) extends Model {
    val broker:   String      = "tcp://localhost:1883"
    val topic:    String      = clientId + "/"
    val timeToWait: Long      = -1
    val client:   MqttAsyncClient  = new MqttAsyncClient(broker, clientId, new MemoryPersistence)

    var dtokens: List[IMqttDeliveryToken] = Nil
    var send = 0
    var recv = 0

    object listner extends MqttCallback {
      def connectionLost(e: Throwable): Unit = {
        println("Connection Lost: " + clientId)
      }
      def deliveryComplete(token: IMqttDeliveryToken): Unit = {
      }
      def messageArrived(topic: String, msg: MqttMessage): Unit = {
        recv += 1
        println(clientId + " received: " + msg)
      }
    }
    // transitions
    "reset" -> "connected" := {
      client.connect().waitForCompletion(timeToWait)
      client.setCallback(listner)
      client.subscribe(topic + "+", 2).waitForCompletion(timeToWait)
    } label "connect"
    "connected" -> "connected" := {
      val message = new MqttMessage(send.toString().getBytes())
      message.setQos(2)
      message.setRetained(true)
      dtokens ::= client.publish(topic + send, message)
      println(clientId + " published: " + send)
      send += 1
    } label "publish" weight 3
    "connected" -> "end" := {
      dtokens.foreach(_.waitForCompletion(timeToWait))
      client.disconnect().waitForCompletion(timeToWait)
    } label "disconnect"
  }

  "default" -> "end" := {
    for (id <- 1 to 100) {
      launch(new Client(seed + "_" + id.toString))
    }
  } label "launch"

  @After
  def after(): Unit = {
    Thread.sleep(100)
  }
}
