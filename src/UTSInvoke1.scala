import modbat.dsl._
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttAsyncClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttException
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.{IMqttDeliveryToken, MqttToken}
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.net.{ServerSocket, Socket, SocketException}
import java.io.{InputStream, OutputStream}

class UTSInvoke1 extends Model {
  val brokerSender:   String = "tcp://localhost:1884"
  val brokerReceiver: String = "tcp://localhost:1885"
  val clientId:       String = "%x".format(getRandomSeed)
  val topic:          String = "%x/".format(getRandomSeed)
  val timeToWait: Long = 500
  val qos = 1
  val senderStability = 0.8
  val receiverStability = 1.0

  var send: Int = 0
  var recv: Int = 0
  var subscribed: Boolean = false
  var end: Boolean = false
  var forwarderEnabled: Boolean = false

  var sender: Sender = _
  var receiver: Receiver = _

  class Client extends Model {
    var disconnected = false
  }

  class Sender extends Client {
    val client: MqttAsyncClient = new MqttAsyncClient(brokerSender, clientId + "_S", new MemoryPersistence)
    val connopts = new MqttConnectOptions()
    connopts.setCleanSession(false)
    var dtokens: List[IMqttDeliveryToken] = Nil
    val host = "localhost"
    var forwarder: Forwarder = _

    object listener extends MqttCallback {
      def connectionLost(e: Throwable): Unit = {
        println("connection lost")
        invokeTransition("lose")
      }
      def deliveryComplete(token: IMqttDeliveryToken): Unit = {
      }
      def messageArrived(topic: String, msg: MqttMessage): Unit = {
      }
    }

    forwarder = new Forwarder(this, 1883, 1884)
    launch(forwarder)
    forwarder.setRates(senderStability, 1.0 - senderStability)
    client.setCallback(listener)
    client.connect(connopts).waitForCompletion(timeToWait)

    "connected" -> "connected" := {
      require(client.isConnected)
      val message = new MqttMessage(send.toString().getBytes())
      message.setQos(qos)
      message.setRetained(true)
      try {
        val token = client.publish(topic + send, message)
        dtokens ::= token
        send += 1
      } catch {
        case e: MqttException => ()
      }
    } label "publish" weight 5 nextIf({() => !client.isConnected} -> "lost")
    "connected" -> "lost" := skip label "lose" weight 0
    "lost" -> "connected" := {
      try {
        client.connect(connopts).waitForCompletion(timeToWait)
      } catch {
        case e: MqttException => ()
      }
    } label "reconnect" nextIf({() => !client.isConnected} -> "lost")
    "connected" -> "stop" := {
      forwarder.setRates(1.0, 0.0)
      forwarder.invokeTransition("enable")
    } label "stop"
    "stop" -> "end" := {
      Thread.sleep(50)
      if(!client.isConnected()) {
        try {
          client.connect(connopts).waitForCompletion(timeToWait)
        } catch {
          case e: Exception => e.printStackTrace()
        }
      }
      for (token <- dtokens) {
        try {
          token.waitForCompletion(timeToWait)
        } catch {
          case e: Exception => {
            System.err.println(token.getMessage() + ": " + token.isComplete())
          }
        }
      }
      try {
        client.disconnect().waitForCompletion(timeToWait)
      } catch {
        case e: Exception => e.printStackTrace()
      }
      forwarder.invokeTransition("end")
      disconnected = true
    } label "end"
  }

  class Receiver extends Client {
    val client: MqttAsyncClient = new MqttAsyncClient(brokerReceiver, clientId + "_R", new MemoryPersistence)
    val connopts = new MqttConnectOptions()
    connopts.setCleanSession(false)
    val host = "localhost"
    var forwarder: Forwarder = _

    object listener extends MqttCallback {
      def connectionLost(e: Throwable): Unit = {
        println("connection lost")
        invokeTransition("lose")
      }
      def deliveryComplete(token: IMqttDeliveryToken): Unit = {
      }
      def messageArrived(topic: String, msg: MqttMessage): Unit = {
        println("receive: " + msg)
        recv += 1
      }
    }

    forwarder = new Forwarder(this, 1883, 1885)
    launch(forwarder)
    forwarder.setRates(receiverStability, 1.0 - receiverStability)
    client.connect(connopts).waitForCompletion(timeToWait)
    client.setCallback(listener)
    client.subscribe(topic + "+", qos).waitForCompletion(timeToWait)
    "connected" -> "lost" := skip label "lose" weight 0
    "lost" -> "connected" := {
      try {
        client.connect(connopts).waitForCompletion(timeToWait)
      } catch {
        case e: MqttException => ()
      }
    } label "reconnect" nextIf({() => !client.isConnected} -> "lost")
    "connected" -> "stop" := {
      require(sender.disconnected)
      forwarder.setRates(1.0, 0.0)
      forwarder.invokeTransition("enable")
    } label "stop"
    "stop" -> "end" := {
      Thread.sleep(50)
      if(!client.isConnected()) {
        try {
          client.connect(connopts).waitForCompletion(timeToWait)
        } catch {
          case e: Exception => e.printStackTrace()
        }
      }
      Thread.sleep(100)
      try {
        client.disconnect().waitForCompletion(timeToWait)
      } catch {
        case e: Exception => e.printStackTrace()
      }
      forwarder.invokeTransition("end")
      assert(send <= recv)
    } label "end"
  }

  class Redirector(val is: InputStream, val os: OutputStream) extends Thread {
    val bufSize = 2048
    val buf = new Array[Byte](bufSize)
    var delay: Long = 0

    override def run() = {
      var len = 0
      try {
        while(len >= 0) {
          len = is.read(buf)
          if(len >= 0) {
            Thread.sleep(delay)
            os.write(buf, 0, len)
          }
        }
      } catch {
        case e: SocketException => ()
      }
      os.close()
    }
    def setDelay(delay: Long) {
      this.delay = delay
    }
  }

  class Forwarder(val client: Client, val serverPort: Int, val clientPort: Int,
    val host: String = "localhost") extends Model {
    val clientSSock = new ServerSocket(clientPort)
    var serverSock: Socket = _
    var clientSock: Socket = _
    var c2s: Redirector = _
    var s2c: Redirector = _
    var alive: Boolean = false
    val disableRate: Double = 0.3
    val enableRate: Double = 0.7
    forwarderEnabled = true
    class Body extends Thread {
      override def run() = {
        serverSock = new Socket(host, serverPort)
        try {
          clientSock = clientSSock.accept()
          c2s = new Redirector(clientSock.getInputStream(), serverSock.getOutputStream())
          s2c = new Redirector(serverSock.getInputStream(), clientSock.getOutputStream())
          c2s.start()
          s2c.start()
          alive = true
          c2s.join()
          s2c.join()
        } catch {
          case _: Exception => ()
        }
      }
    }
    new Body().start()
    "enabled" -> "enabled" := skip label "skip_e" weight enableRate
    "enabled" -> "disabled" := {
      require(alive)
      alive = false
      serverSock.close()
      clientSock.close()
    } label "disable" weight disableRate
    "disabled" -> "disabled" := skip label "skip_d" weight disableRate
    "disabled" -> "enabled" := {
      forwarderEnabled = true
      new Body().start()
    } label "enable" weight enableRate
    "enabled" -> "end" := {
      clientSSock.close()
    } label "end" weight 0

    def setRates(enable: Double, disable: Double) {
      setWeight("skip_e", enable)
      setWeight("enable", enable)
      setWeight("skip_d", disable)
      setWeight("disable", disable)
    }
    def setDelay(delay: Long) {
      if(s2c != null) {
        s2c.setDelay(delay)
      }
      if(c2s != null) {
        c2s.setDelay(delay)
      }
    }
  }

  "init" -> "run" := {
    sender = new Sender
    launch(sender)
    receiver = new Receiver
    launch(receiver)
  } label "start"

  @After
  def after(): Unit = {
    println(send, recv)
    if(!sender.forwarder.clientSSock.isClosed()) {
      sender.forwarder.clientSSock.close()
    }
    Thread.sleep(200)
  }
}
