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

class ForwardSubmodelThread extends Model {
  val brokerReceiver: String = "tcp://localhost:1883"
  val brokerSender:   String = "tcp://localhost:1884"
  val clientId:       String = "%x".format(getRandomSeed)
  val topic:          String = "%x/".format(getRandomSeed)
  val timeToWait: Long = 500
  val qos = 2

  var send: Int = 0
  var recv: Int = 0
  var subscribed: Boolean = false
  var end: Boolean = false
  var forwarderEnabled: Boolean = false

  var sender: Sender = _

  class Sender extends Model {
    val client: MqttAsyncClient = new MqttAsyncClient(brokerSender, clientId + "_S", new MemoryPersistence)
    val connopts = new MqttConnectOptions()
    connopts.setCleanSession(false)
    var connected = false
    var dtokens: List[IMqttDeliveryToken] = Nil
    val host = "localhost"
    var forwarder: Forwarder = _

    object listener extends MqttCallback {
      def connectionLost(e: Throwable): Unit = {
        println("connection lost")
        connected = false
      }
      def deliveryComplete(token: IMqttDeliveryToken): Unit = {
      }
      def messageArrived(topic: String, msg: MqttMessage): Unit = {
      }
    }

    "reset" -> "connecting" := {
      // require(forwarderEnabled)
      forwarder = new Forwarder(1883, 1884)
      launch(forwarder)
      client.setCallback(listener)
      client.connect(connopts).waitForCompletion(timeToWait)
      println("connected")
    } label "connect"
    "connecting" -> "connected" := {
      require(subscribed)
      connected = true
    } label "connected"
    "connected" -> "connected" := {
      require(connected)
      val message = new MqttMessage(send.toString().getBytes())
      message.setQos(qos)
      message.setRetained(true)
      try {
        val token = client.publish(topic + send, message)
        dtokens ::= token
        println("publish: " + send + " , " + client.isConnected())
        send += 1
      } catch {
        case e: MqttException => ()
      }
    } label "publish" weight 5 nextIf({() => !connected} -> "lost")
    "connected" -> "lost" := {
      require(!connected)
    } label "lose"
    "lost" -> "connected" := {
      try {
        client.connect(connopts).waitForCompletion(timeToWait)
        connected = true
      } catch {
        case e: MqttException => ()
      }
    } label "reconnect" nextIf({() => !connected} -> "lost")
    "connected" -> "end" := {
      require(connected)
      for (token <- dtokens) {
        try {
          token.waitForCompletion(timeToWait)
        } catch {
          case e: Exception => {
            System.err.println(token.getMessage() + ": " + token.isComplete())
            // e.printStackTrace()
          }
        }
      }
      if(client.isConnected()) {
        try {
          client.disconnect().waitForCompletion(timeToWait)
        } catch {
          case e: Exception => e.printStackTrace()
        }
      }
      end = true
    } label "disconnect"
  }

  class Receiver extends Model {
    val client: MqttClient = new MqttClient(brokerReceiver, clientId + "_R", new MemoryPersistence)
    val connopts = new MqttConnectOptions()
    connopts.setCleanSession(false)
    val host = "localhost"
    var connected = true

    object listener extends MqttCallback {
      def connectionLost(e: Throwable): Unit = {
        println("connection lost")
        connected = false
      }
      def deliveryComplete(token: IMqttDeliveryToken): Unit = {
      }
      def messageArrived(topic: String, msg: MqttMessage): Unit = {
        println("receive: " + msg)
        recv += 1
      }
    }

    "reset" -> "connected" := {
      client.connect(connopts)
      client.setCallback(listener)
      client.subscribe(topic + "+", qos)
      subscribed = true
    } label "connect"
    "connected" -> "end" := {
      require(end)
      Thread.sleep(100)
      client.disconnect()
      assert(send == recv)
      // assert(forwarder.clientSSock.isClosed())
    } label "disconnect"
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

  class Forwarder(val serverPort: Int, val clientPort: Int,
    val host: String = "localhost") extends Model {
    val clientSSock = new ServerSocket(clientPort)
    var serverSock: Socket = _
    var clientSock: Socket = _
    var c2s: Redirector = _
    var s2c: Redirector = _
    var alive: Boolean = false
    val disableRate: Double = 0.0
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
      require(!end && alive)
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
      require(end)
      clientSSock.close()
    } label "end"

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

  "default" -> "stable" := {
    sender = new Sender
    launch(sender)
    launch(new Receiver)
  }
  "stable" -> "unstable" := {
    require(sender.forwarder != null)
    sender.forwarder.setRates(0.0, 0.7)
  }
  "unstable" -> "stable" := {
    sender.forwarder.setRates(0.7, 0.0)
  }
  "stable" -> "end" := skip

  @After
  def after(): Unit = {
    println(send, recv)
    // assert(send == recv)
    // assert(forwarder.clientSSock.isClosed())
    if(!sender.forwarder.clientSSock.isClosed()) {
      sender.forwarder.clientSSock.close()
    }
    Thread.sleep(200)
  }
}
