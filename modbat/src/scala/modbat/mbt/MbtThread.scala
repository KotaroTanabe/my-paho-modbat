package modbat.mbt
import modbat.log.Log

class MbtThread extends Thread {
  var parentModel: MBT = _
  def mbtStart() {
    parentModel = MBT.currentModel
    parentModel.runningThread = Some(this)
    super.start()
  }

  override def start() {
    mbtStart()
  }

  def finish() {
    parentModel.runningThread = None
    Modbat.waitLock.synchronized {
      if(Modbat.debug) Log.fine("notify finishing "+ parentModel.name + " to mainmethod")
      Modbat.waitLock.notify()
    }
  }

  final override def run() {
    mbtRun()
    finish()
  }

  def mbtRun() {
  }

  def mbtSleep(sleepTime: Long) {
    //Modbat.mainLock.lock()
    Timekeeper.regSleep(parentModel, sleepTime)
    //Modbat.mainLock.unlock()
    Modbat.waitLock.synchronized {
      if(Modbat.debug) Log.fine("notify sleeping of "+ parentModel.name + " to mainmethod")
      Modbat.waitLock.notify()
    }
    parentModel.sleepLock.synchronized {
      while(parentModel.sleeping) parentModel.sleepLock.wait(sleepTime)
    }
  }
}

