import modbat.dsl._
import org.eclipse.paho.client.mqttv3.MqttAsyncClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttException
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

class ManyClientsBroadcast extends Model {
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
        e.printStackTrace()
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
      client.subscribe(seed, 2).waitForCompletion(timeToWait)
    } label "connect"
    "connected" -> "connected" := {
      val message = new MqttMessage((clientId + " - " + send).getBytes())
      message.setQos(2)
      message.setRetained(true)
      dtokens ::= client.publish(seed, message)
      println(clientId + " published: " + send)
      send += 1
    } label "publish" weight 3
    "connected" -> "end" := {
      dtokens.foreach(_.waitForCompletion(timeToWait))
      println("Before Disconnection " + clientId)
      val tok = client.disconnect()
      println("Disconnecting " + clientId)
      tok.waitForCompletion(timeToWait)
      println("Disconnected " + clientId)
    } label "disconnect"
  }

  "default" -> "end" := {
    for (id <- 1 to 10) {
      launch(new Client(seed + "_" + id.toString))
    }
  } label "launch"

  @After
  def after(): Unit = {
    Thread.sleep(100)
  }
}
