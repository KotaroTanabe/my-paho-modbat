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

class StableToStableForward extends Model {
  val brokerSender: String  = "tcp://localhost:1884"
  val broker:   String      = "tcp://localhost:1883"
  val clientId: String      = "%x".format(getRandomSeed)
  val topic:    String      = "%x/".format(getRandomSeed)
  val timeToWait: Long = -1

  var send: Int = 0
  var recv: Int = 0
  var subscribed: Boolean = false
  var end: Boolean = false

  var dtokens: List[IMqttDeliveryToken] = Nil

  class Sender extends Model {
    val client: MqttAsyncClient = new MqttAsyncClient(brokerSender, clientId + "_S", new MemoryPersistence)
    "reset" -> "connecting" := {
      client.connect().waitForCompletion(timeToWait)
    } label "connect"
    "connecting" -> "connecting" := skip nextIf({() => subscribed} -> "connected")
    "connected" -> "connected" := {
      val message = new MqttMessage(send.toString().getBytes())
      message.setQos(2)
      message.setRetained(true)
      dtokens ::= client.publish(topic + send, message)
      println("publish: " + send)
      send += 1
    } label "publish" weight 3
    "connected" -> "end" := {
      dtokens.foreach(_.waitForCompletion(timeToWait))
      client.disconnect().waitForCompletion(timeToWait)
      end = true
    } label "disconnect"
  }

  class Receiver extends Model {
    val client: MqttClient = new MqttClient(broker, clientId + "_R", new MemoryPersistence)
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
      client.connect()
      client.setCallback(listener)
      client.subscribe(topic + "+", 2)
      subscribed = true
    } label "connect"
    "connected" -> "connected" := skip nextIf({() => end} -> "disconnecting")
    "disconnecting" -> "end" := {
      Thread.sleep(100)
      client.disconnect()
    } label "disconnect"
  }

  class Redirector(val is: InputStream, val os: OutputStream) extends Thread {
    val bufSize = 256
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
    val host = "localhost"
    val a = actor {
      val serverSock = new Socket(host, 1883)
      val clientSSock = new ServerSocket(1884)
      val clientSock = clientSSock.accept()
      val c2s = new Redirector(clientSock.getInputStream(), serverSock.getOutputStream())
      val s2c = new Redirector(serverSock.getInputStream(), clientSock.getOutputStream())
      c2s.start()
      s2c.start()
      c2s.join()
      s2c.join()
      serverSock.close()
      clientSSock.close()
    }
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
