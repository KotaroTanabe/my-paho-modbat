import modbat.dsl._

class Staytest extends Model {
  "init" -> "mid" := {
  } label "fst" stay 10000

  "mid" -> "end" := {
  } label "snd" stay 10000
}

