import java.net.ServerSocket
import java.net.Socket

object Server {
  val bufSize = 256
  def main(args: Array[String]): Unit = {
    val port = args(0).toInt
    val ssock = new ServerSocket(port)
    val sock = ssock.accept()
    val is = sock.getInputStream()
    val os = sock.getOutputStream()
    val buf = new Array[Byte](bufSize)
    var len = 0
    do {
      len = is.read(buf)
      if(len >= 0) {
        System.out.write(buf, 0, len)
      }
    } while(len >= 0)
    is.close()
    os.close()
  }
}
