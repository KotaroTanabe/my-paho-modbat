import modbat.dsl._

//SUT (System Under Test)
class Counter {
  private var n = 0
  def inc = n += 1
  def dec = if (n>3) n -= 2 else n -= 1 //BUG!
  def get = n
  def reset = n = 0
}

class Test extends Model {
  val counter = new Counter()
  var model : Int = 0
  "init" -> "init" := {
    counter.inc
    model += 1
  } label "inc"

  "init" -> "init" := {
    counter.dec
    model -= 1
  } label "dec"

  "init" -> "init" := {
    counter.reset
    model = 0
  } label "reset"

  "init" -> "init" := {
    assert (counter.get == model)
  } label "get"

  "init" -> "end" := {
    require (model > 5)
  } label "end"// weight 0.001
}

