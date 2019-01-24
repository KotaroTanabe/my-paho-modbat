import modbat.dsl._
import modbat.mbt.MbtThread
import java.io._

class MbtThreadTest extends Model {
  var hoge = 0

  "init" -> "mid" := {
    val helloThread = new TestThread()
    helloThread.start
  } label "thread"

  "mid" -> "end" := {
    assert(hoge == 1)
  } label "end"

  class TestThread() extends MbtThread {
    override def mbtRun() {
      val writer = new OutputStreamWriter(new FileOutputStream(s"../log/MBTThreadTest/hello.dat"))
      writer.write("Hello MBTThread!")
      hoge = 1
      writer.close()
      //finish()
    }
  }
}

