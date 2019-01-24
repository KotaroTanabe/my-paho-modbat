# DEPENDENCIES #

* Make
* Scala 2.11.x
* Java 8
* Maven
* Ant
* Mosquitto
  * `$ sudo apt install mosquitto`

# USAGE #

1. Mosquitto runs automatically in Ubuntu.
   If it is not running, launch it in another shell window by `$ mosquitto`.
1. `$ make (MODEL NAME).run`
   For example, execute `$ make House.run` for `src/House.scala`.
1. Log files are stored in `log/(MODEL NAME)/`.

# MODELS #

* UTUInvoke(x).scala
    * An unstable Sender publishes messages to an unstable Receiver in QoS x.
* STUInvoke(x).scala
    * Stable Sender -> Unstable Receiver
* UTSInvoke(x).scala
    * Unstable Sender -> Stable Receiver
* House.scala
    * Perfect system vs With thermometer and air conditioner errors
* HouseThermoError.scala
    * Perfect system vs With thermometer errors
* HouseAcError.scala
    * Perfect system vs With air conditioner errors
* HousePrev.scala
    * Naive algorithm vs Comparing to previous room temperature
* HouseSurround.scala
    * Naive algorithm vs Comparing to adjacent room temperatures
