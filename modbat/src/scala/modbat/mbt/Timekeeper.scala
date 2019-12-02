package modbat.mbt
import modbat.log.Log
import scala.collection.mutable.PriorityQueue
import java.util.concurrent.locks._
//import java.util.concurrent.PriorityBlockingQueue

/*
 * 
 * def reset //initialization
　* def register(model: MBT, stayTime: Int) //add element (staying)
 * def regSleep(model: MBT, sleepTime: Int) //add element (sleeping)
 * def update //update time & if something popped, true and otherwise false
 * def earliest: Option[Long] //return time until earliest staying model finishes staying
 * def skip //skip time until one element finishes staying
 */

object Timekeeper {
  var firstcall = true
  var previousTime = System.currentTimeMillis
  var tau = 0L

  //var timerLock: Lock = new ReentrantLock()

  //priorityQueueで扱う型の定義
  case class MbtTuple(model: MBT, time: Long)
  def mbtTupleOrder(s: MbtTuple) = s.time * -1 //PriorityQueueは降順でdequeueされるので、負に

  private var stayingModels: PriorityQueue[MbtTuple]
    = PriorityQueue()(Ordering.by(mbtTupleOrder))
  private var sleepingModels: PriorityQueue[MbtTuple]
    = PriorityQueue()(Ordering.by(mbtTupleOrder))

  def reset = {
    firstcall = true
    tau = 0L
    assert(stayingModels.isEmpty)
  }

  /*
   * (0) 与えられたモデルのstaying = trueにするのは呼び出す側の責任とする?
   *     (stayTimeとsleepTimeを両方管理することにしたため) 
   * (1) stayTimeと現在時刻tをもとに終了時刻を計算して <-この時updateする
   * (2) PriorityQueueに追加する
   */

  def register(model: MBT, stayTime: Long) = {
   this.synchronized {
    update
    model.setStay(true)
    val finTime = tau + stayTime
    stayingModels += (MbtTuple(model, finTime))
    if(Modbat.debug)
      Log.fine(model.name + " started staying (end in tau = " + finTime + ")")
   }
  }

  def regSleep(model:MBT, sleepTime: Long) = {
   this.synchronized {
    update
    model.setSleep(true)
    val finTime = tau + sleepTime
    sleepingModels += (MbtTuple(model, finTime))
    if(Modbat.debug)
      Log.fine(model.name + " started sleeping (end in tau = " + finTime + ")")
   }
  }

  /*
   * (1) 現在時刻tを更新(前回の更新からの経過時間をプラスする)
   * (2) PriorityQueueから、stayTimeがtより小さいモデルをすべて取り出し、
   *     それらのstaying = falseにする
   * (3) sleepingについても同様のことを行う(+通知)
   */
  def update: Boolean = {
   this.synchronized {
    var ret = false
    val currentTime = System.currentTimeMillis
    if(firstcall) {
      firstcall = false
    } else {
      val passedTime = currentTime - previousTime
      tau = tau + passedTime
      if(Modbat.debug)
        Log.fine("updated timekeeper(tau = " + tau + ", time passed from last update： " + passedTime + " ms)")
    }
    previousTime = currentTime
    var loop = !stayingModels.isEmpty
    while(loop) {
      if(stayingModels.head.time <= tau) {
          ret = true
          //if(Modbat.debug) Log.fine("dequeueing head of stayingModels = "+ stayingModels.head.model.name)
          val top = stayingModels.dequeue()
          //if(Modbat.debug) Log.fine("calling "+ top.model.name + ".setStay(false)")
          top.model.setStay(false)
          if(Modbat.debug) Log.fine(top.model.name + " finished staying")

        loop = !stayingModels.isEmpty
      } else {
        loop = false
      }
    }
    if(Modbat.debug) Log.fine("stayingModels = "+ elementsStr(stayingModels))

    loop = !sleepingModels.isEmpty
    while(loop) {
      if(sleepingModels.head.time <= tau) {
          ret = true
          //if(debug) Log.fine("dequeueing head of sleepingModels = "+ sleepingModels.head.model.name)
          val top = sleepingModels.dequeue()
          top.model.setSleep(false)
          if(Modbat.debug) Log.fine(top.model.name + " finished sleeping")
          top.model.sleepLock.synchronized {
            top.model.sleepLock.notify()
          }
          loop = !sleepingModels.isEmpty
      } else {
        loop = false
      }
    }

    if(Modbat.debug) Log.fine("sleepingModels = "+ elementsStr(sleepingModels))

    return ret
   }
  }

  def earliest : Option[Long] = {
   this.synchronized {
    if(update) return Some(0L)
    var stayE:Option[Long] = None
    if(!stayingModels.isEmpty) {
      val e = stayingModels.head.time - tau
      if(e > 0L) stayE = Some(e) else stayE = Some(0L)
    }

    var sleepE:Option[Long] = None
    if(!sleepingModels.isEmpty) {
      val e = sleepingModels.head.time - tau
      if(e > 0L) sleepE = Some(e) else sleepE = Some(0L)
    }

    (stayE, sleepE) match {
      case (None, None) => None
      case (Some(x), None) => Some(x)
      case (None, Some(y)) => Some(y)
      case (Some(x), Some(y)) => if(x<y) Some(x) else Some(y)
    }
   }
  }
  /*
   * (1) PriorityQueue内のstayTime最小のプロセスのstayTimeが終了するまで時間を進める
   * (2) 次回のupdateの時に齟齬が出ないように、PreviousTimeを更新
   * (3) updateの(2)と同じ
   */
  def skip = {
   this.synchronized {
    earliest match {
      case None => 
        {
          if(Modbat.debug) {
            Log.fine("queue empty: stayingModels.isEmpty = "+ stayingModels.isEmpty + ", sleepingModels.isEmpty = " + sleepingModels.isEmpty)
            sleepingModels.head
          }
        } 
      case Some(0) =>
        {
           if(Modbat.debug) Log.fine("do nothing")
        }
      case Some(x) => 
        {
          tau = tau + x
          if(Modbat.debug) Log.fine("skipped for " + x + "ms")
          previousTime = System.currentTimeMillis
        }
    }
    update
   }
  }

  //for debug
  def printHead = {
    if(stayingModels.isEmpty) {
      println("stayingModels is empty")
    } else {
      println("head element = " + stayingModels.head.model.name)
    }
  }
  def elementsStr(pq: PriorityQueue[MbtTuple]): String = {
    var str:String = "("
    for(elm <- pq.clone.dequeueAll) {
      str += "("
      str += elm.model.name
      str += ", "
      str += elm.time
      str += "), "
    }
    str += ")"
    str
  }
}
