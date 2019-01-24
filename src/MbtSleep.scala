import modbat.dsl._
import modbat.mbt.MbtThread
import java.io._

class MbtSleep extends Model {
  var hoge = 0

  "init" -> "mid" := {
    val testThread = new TestThread()
    testThread.start
  } label "thread"

  "mid" -> "end" := {
    //assert(hoge == 50)
  } label "end"

  class Hoge extends Model {
    "init" -> "end" := {
      
    } label "hoge"
  }

  class TestThread() extends MbtThread {
    override def mbtRun() {
      //val writer = new OutputStreamWriter(new FileOutputStream(s"../log/MBTThreadTest/hello.dat"))
      //writer.write("Hello MBTThread!")
      mbtSleep(100)
      hoge = hoge+1
      //writer.close()
      //finish()
    }
  }
}

