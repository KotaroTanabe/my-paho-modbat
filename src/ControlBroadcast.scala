import modbat.dsl._
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttAsyncClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttException
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.net.{ServerSocket, Socket, SocketException}
import java.io.{InputStream, OutputStream}
import scala.actors.Actor._

class ControlBroadcast extends Model {
  val broker: String = "tcp://localhost:"
  val clientId:       String = "%x".format(getRandomSeed)
  val topic:          String = "%x/".format(getRandomSeed)
  val timeToWait: Long = -1
  val qos = 2

  var send: Int = 0
  var recv: Int = 0
  var subscribed: Boolean = false
  var end: Boolean = false

  var clients: List[Sender] = Nil

  class Sender(val clientId: String, val forwardPort: Int) extends Model {
    val client: MqttAsyncClient = new MqttAsyncClient(broker + forwardPort, clientId, new MemoryPersistence)
    val connopts = new MqttConnectOptions()
    connopts.setCleanSession(false)
    val clientSSock = new ServerSocket(forwardPort)
    var serverSock: Socket = _
    var clientSock: Socket = _
    var connected = true
    var dtokens: List[IMqttDeliveryToken] = Nil
    val host = "localhost"

    var alive: Boolean = true
    var start: Boolean = false

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
      val a = actor {
        serverSock = new Socket(host, 1883)
        clientSock = clientSSock.accept()
        val c2s = new Redirector(clientSock.getInputStream(), serverSock.getOutputStream())
        val s2c = new Redirector(serverSock.getInputStream(), clientSock.getOutputStream())
        c2s.start()
        s2c.start()
        c2s.join()
        s2c.join()
      }
      client.setCallback(listener)
      client.connect(connopts).waitForCompletion(timeToWait)
    } label "connect"
    "connecting" -> "connecting" := {
      start = true
    } nextIf({() => subscribed} -> "connected")
    "connected" -> "connected" := {
      val message = new MqttMessage(send.toString().getBytes())
      message.setQos(qos)
      message.setRetained(true)
      dtokens ::= client.publish(topic + send, message)
      println("publish: " + send)
      send += 1
    } label "publish" weight 5
    "connected" -> "lost" := {
      require(!alive)
      serverSock.close()
      clientSock.close()
    } label "lose"
    "lost" -> "connected" := {
      require(alive)
      val a = actor {
        serverSock = new Socket(host, 1883)
        println("a", serverSock.isClosed())
        clientSock = clientSSock.accept()
        println("b", serverSock.isClosed())
        val c2s = new Redirector(clientSock.getInputStream(), serverSock.getOutputStream())
        val s2c = new Redirector(serverSock.getInputStream(), clientSock.getOutputStream())
        c2s.start()
        s2c.start()
        c2s.join()
        s2c.join()
      }
      while(connected) {
        Thread.sleep(10)
      }
      client.connect(connopts)
      while(!client.isConnected()) {
        Thread.sleep(10)
      }
      connected = true
    } label "reconnect"
    "connected" -> "end" := {
      dtokens.foreach(_.waitForCompletion(timeToWait))
      client.disconnect().waitForCompletion(timeToWait)
      serverSock.close()
      clientSSock.close()
      end = true
    } label "disconnect"
  }

  class Redirector(val is: InputStream, val os: OutputStream) extends Thread {
    val bufSize = 1024
    val buf = new Array[Byte](bufSize)

    override def run() = {
      var len = 0
      try {
        while(len >= 0) {
          len = is.read(buf)
          if(len >= 0) {
            os.write(buf, 0, len)
          }
        }
      } catch {
        case e: SocketException => ()
      }
      os.close()
    }
  }

  "default" -> "running" := {
    for (i <- 0 to 10) {
      val c = new Sender(clientId + i, 1884 + i)
      clients ::= c
      launch(c)
    }
  }
  "running" -> "broken" := {
    require(clients.forall(_.start))
    for (c <- clients) {
      if(choose(0, 10) < 3) {
        c.alive = false
      }
    }
  }
  "broken" -> "running" := {
    for (c <- clients) {
      c.alive = true
    }
  }

  @After
  def after(): Unit = {
    println(send, recv)
    assert(send == recv)
    Thread.sleep(200)
  }
}
