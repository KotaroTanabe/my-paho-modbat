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

class UnstableToStableForward extends Model {
  val brokerReceiver: String = "tcp://localhost:1883"
  val brokerSender:   String = "tcp://localhost:1885"
  val clientId:       String = "%x".format(getRandomSeed)
  val topic:          String = "%x/".format(getRandomSeed)
  val timeToWait: Long = -1
  val qos = 2

  var send: Int = 0
  var recv: Int = 0
  var subscribed: Boolean = false
  var end: Boolean = false

  class Sender extends Model {
    val client: MqttAsyncClient = new MqttAsyncClient(brokerSender, clientId + "_S", new MemoryPersistence)
    val connopts = new MqttConnectOptions()
    connopts.setCleanSession(false)
    val clientSSock = new ServerSocket(1885)
    var serverSock: Socket = _
    var clientSock: Socket = _
    var connected = true
    var dtokens: List[IMqttDeliveryToken] = Nil
    val host = "localhost"

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
    "connecting" -> "connecting" := skip nextIf({() => subscribed} -> "connected")
    "connected" -> "connected" := {
      val message = new MqttMessage(send.toString().getBytes())
      message.setQos(qos)
      message.setRetained(true)
      dtokens ::= client.publish(topic + send, message)
      println("publish: " + send)
      send += 1
    } label "publish" weight 5
    "connected" -> "lost" := {
      serverSock.close()
      clientSock.close()
    } label "lose"
    "lost" -> "connected" := {
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

  class Receiver extends Model {
    val client: MqttClient = new MqttClient(brokerReceiver, clientId + "_R", new MemoryPersistence)
    val connopts = new MqttConnectOptions()
    connopts.setCleanSession(false)

    object listener extends MqttCallback {
      def connectionLost(e: Throwable): Unit = {
        println("connection lost")
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
    "connected" -> "connected" := skip nextIf({() => end} -> "disconnecting") weight 3
    "disconnecting" -> "end" := {
      Thread.sleep(100)
      client.disconnect()
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

  "default" -> "end" := {
    launch(new Receiver)
    launch(new Sender)
  }

  @After
  def after(): Unit = {
    println(send, recv)
    assert(send == recv)
    Thread.sleep(200)
  }
}
