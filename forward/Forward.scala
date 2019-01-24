import java.net.{ServerSocket, Socket, SocketException}
import java.io.{InputStream, OutputStream}

object Forward {
  val host = "localhost"
  def main(args: Array[String]) : Unit = {
    val serverPort = args(0).toInt
    val clientPort = args(1).toInt
    val server = new Socket(host, serverPort)
    val client = new ServerSocket(clientPort)
    val clientSock = client.accept()
    val c2s = new Redirector(clientSock.getInputStream(), server.getOutputStream())
    val s2c = new Redirector(server.getInputStream(), clientSock.getOutputStream())
    c2s.start()
    s2c.start()
    c2s.join()
    s2c.join()
    server.close()
    clientSock.close()
  }
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
