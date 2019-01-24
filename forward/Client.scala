import java.net.{Socket, SocketException}
import java.io.InputStream

object Client {
  val bufSize = 256
  val buf = new Array[Byte](bufSize)
  val host = "localhost"
  def main(args: Array[String]): Unit = {
    val port = args(0).toInt
    val sock = new Socket(host, port)
    val os = sock.getOutputStream()
    var len = 0
    try {
      do {
        len = System.in.read(buf)
        if(len >= 0) {
          os.write(buf, 0, len)
        }
      } while (len >= 0)
    } catch {
      case e: SocketException => e.printStackTrace()
    }
    os.close()
  }
}
