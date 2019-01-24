import org.eclipse.paho.client.mqttv3._

object ManualClient {
  val clientId = "ManualClient"
  val topic = "ManualClient"

  def main(args: Array[String]) {
    val broker = if (args.length < 1) {
      "tcp://localhost:1883"
    } else {
      "tcp://localhost:" + args(0)
    }
    val client = new MqttClient(broker, clientId)
    client.setCallback(listner)
    client.connect
    client.subscribe(topic)
    var line = io.StdIn.readLine
    while (line != null) {
      val message = new MqttMessage(line.getBytes)
      client.publish(topic, message)
      line = io.StdIn.readLine
    }
    client.disconnect
  }

  object listner extends MqttCallback {
    def connectionLost(e: Throwable) = {
      e.printStackTrace
    }
    def deliveryComplete(t: IMqttDeliveryToken) = {}
    def messageArrived(topic: String, message: MqttMessage) = {
      println("Recv: " + message)
    }
  }
}
