import modbat.dsl._
import org.eclipse.paho.client.mqttv3._
import org.eclipse.paho.client.mqttv3.persist._
import java.net._
import java.io._

class MyHouse extends Model {
  val seed = "%x".format(getRandomSeed)
  val host = "tcp:://localhost:"

  // Field
  val height = 4
  val width = 4
  var temperatureManager: TemperatureManager = _
  val timeScale = 0.001
  val envs = Array(new Environment(s"${seed}-0"),
    new Environment(s"${seed}-1"))

  // Clients
  var controller: Controller = _
  val thermometer = Array.ofDim[Thermometer](height, width)
  val airConditioner = Array.ofDim[AirConditioner](height, width)

  //ドア
  val door = Array.ofDim[Door](height+1,width+1,2)
  var running = false
  var end = false
  val timeToWait = -1
  //方向
  val LR = 0//左右
  val UD = 1//上下

  "init" -> "standby" := {
    controller = new Controller()
    launch(controller)
    temperatureManager = new TemperatureManager()
    launch(temperatureManager)
    for(i <- 0 until height) {
      for(j <- 0 until width) {
        thermometer(i)(j) = new Thermometer(i, j)
        launch(thermometer(i)(j))
        airConditioner(i)(j) = new AirConditioner(i, j)
        launch(airConditioner(i)(j))
      }
    }
    //ドア
    for(i <- 0 until height + 1) {
      for(j <- 0 until width + 1) {
        for(d <- 0 until 1) {
          door(i)(j)(d) = new Door(i,j,d)
          launch(door(i)(j)(d))
        }
      }
    }
  } label "setup"
  "standby" -> "run" := {
    require(controller.ready && thermometer.forall(_.forall(_.ready)))
    running = true
  } label "run" stay scale(3000)
  "run" -> "end" := {
    end = true
  } label "end"

  // returns time in millisecond
  def scale(timeInSecond: Double): Int = {
    (timeInSecond * timeScale * 1000).asInstanceOf[Int]
  }
  
  object Environment {
    val conductionOuter = 0.0001
    val conductionInner = 0.0002
    //DoorがOpenのときの熱伝導率
    val conductionDoorOpen = 0.1
    val airConditionerRate = 0.001
    val dirs = Array((-1,0), (1,0), (0,-1), (0,1))
    var temperatureOut: Double = 20.0
    var temperatureRate: Double = 0.0
    val interval: Double = 60.0
  }

  class Environment(val prefix: String) {
    val temperatures = Array.fill[Double](height, width){Environment.temperatureOut}
    val acMode = Array.fill[Int](height, width){0}

    //ドアを追加
    val doorIsOpen = Array.fill[Boolean](height+1, width+1, 2){false}

    val writer = new OutputStreamWriter(new FileOutputStream(s"../log/MyHouse/${prefix}.dat"))
    var maxDiff = 0.0

    //(引数に方向の情報を追加)
    def getTemperatureAndConduction(ts: Array[Array[Double]], i: Int, j: Int, dir: (Int, Int)): (Double, Double) = {
      val (di,dj) = dir
      val si = i + di
      val sj = j + dj
      //conductionについて、ドアが開いているとconductionDoorOpenを返すようにした
      if(getDoorMode(i,j,(di,dj))) {
        (temperatures(si)(sj), Environment.conductionDoorOpen)
      } else if(si >= 0 && si < height && sj >= 0 && sj < width) {
        (temperatures(si)(sj), Environment.conductionInner)
      } else {
        (Environment.temperatureOut, Environment.conductionOuter)
      }
    }

    //door
    def getDoorMode(i: Int, j: Int, dir : (Int, Int)) = {
      val (di, dj) = dir
      if(dj == 0) {
        doorIsOpen(i+(1+di)/2)(j)(UD)
      } else {
        doorIsOpen(i)(j+(1+dj)/2)(LR)
      }
    }

    def updateTemperature() {
      val ts0 = temperatures.map(_.clone())
      for(i <- 0 until height) {
        for(j <- 0 until width) {
          val currentT = temperatures(i)(j)
          var diff = 0.0
          for((di, dj) <- Environment.dirs) {
            val (t, cond) = getTemperatureAndConduction(ts0, i, j, (di,dj))
            diff += (t - currentT) * cond
          }
          diff += acMode(i)(j) * Environment.airConditionerRate
          temperatures(i)(j) += diff * Environment.interval
        }
      }
      Environment.temperatureOut += Environment.temperatureRate * Environment.interval
    }

    def dumpTemperature() {
      writer.write(s"# ${Environment.temperatureOut} ${controller.target} ${maxDiff}\n")
      for(i <- 0 until height) {
        for(j <- 0 until width) {
          writer.write(s"${j} ${i} ${temperatures(i)(j)} ${acMode(i)(j)}\n")
        }
        writer.write("\n")
      }
      
      for(i <- 0 until height + 1) {
        for(j <- 0 until width + 1) {
          writer.write(s"lr-door ${i} ${j} doorIsOpen = ${doorIsOpen(i)(j)(LR)}\n")
          writer.write(s"ud-door ${i} ${j} doorIsOpen = ${doorIsOpen(i)(j)(UD)}\n")
        }
      }
      writer.write("\n")
    }

    def checkTemperature() {
      for(row <- temperatures) {
        for(t <- row) {
          maxDiff = maxDiff max (t - controller.target).abs
        }
      }
    }
  }

  class Controller extends Model {
    val broker = host + 1883
    val id = seed + "-controller"
    val tempQos = 0
    val acQos = 2
    var target: Double = 20.0
    var ready = false
    val interval = 400.0
    val instance = Array(
      new Instance(envs(0)),
      new FragileInstance(envs(1)))
    val sensitivity = 1.0

    "init" -> "run" := {
      instance.foreach(_.connect())
      updater.start()
      ready = true	
    } label "connect"
    "run" -> "run" := {
      require(running)
      target += choose(-1, 1)
    } label "set temperature" stay (scale(300), scale(500))
    "run" -> "end" := {
      require(end)
      instance.foreach(_.disconnect())
    } label "end"

    object updater extends Thread {
      override def run() {
        while(!end) {
          instance.foreach(_.control())
          Thread.sleep(scale(interval))
        }
      }
    }

    class Instance(val env: Environment) {
      val prefix = env.prefix
      val id = s"${prefix}-controller"
      val client = new MqttAsyncClient(broker, id)
      val connopts = new MqttConnectOptions()
      connopts.setCleanSession(false)
      connopts.setMaxInflight(height * width)
      val measuredTemperatures = Array.ofDim[Double](height, width)
      //ドアの測定結果記録のために追加
      //ドアから連続で"Open"が来た回数を記録？
      val measuredDoorOpen = Array.ofDim[Int](height + 1, width + 1, 2)
      object listner extends MqttCallback {
        def connectionLost(e: Throwable) {
          println("connection lost: " + id)
          e.printStackTrace
        }
        def deliveryComplete(token: IMqttDeliveryToken) {}
        /*
         * 温度計からのみmessageを受け取る前提でparseしてるので
	 * ドアから受け取ることも考えると場合分けが必要
	 * 温度計のtopic = s"${prefix}/temperature/${i}/${j}"
	 * ドアのtopic = s"${prefix}/door/${i}/${j}/${d}"
 	 */
        def messageArrived(topic: String, msg: MqttMessage) {
          val topics = topic.split('/')
          if(topic(1).equals("temperature")) {
            val x = topics(2).toInt
            val y = topics(3).toInt
            measuredTemperatures(x)(y) = msg.toString.toDouble
          } else {
            val i = topics(2).toInt
            val j = topics(3).toInt
            val d = topics(4).toInt
            if(msg.toString.toBoolean) {
              measuredDoorOpen(i)(j)(d) = 1
            } else {
              measuredDoorOpen(i)(j)(d) = 0
            }
          }
        }
      }
      def connect() {
        client.setCallback(listner)
        client.connect(connopts).waitForCompletion(timeToWait)
        client.subscribe(s"${prefix}/temperature/+/+", tempQos).waitForCompletion(timeToWait)
        //doorのためのsubscription
        client.subscribe(s"${prefix}/door/+/+/+", tempQos).waitForCompletion(timeToWait)

      }
      def disconnect() {
        client.disconnect().waitForCompletion(timeToWait)
      }
      def setAc(i: Int, j: Int, value: Int): IMqttDeliveryToken = {
        val message = new MqttMessage(value.toString().getBytes())
        message.setQos(acQos)
        val topic = s"${prefix}/ac-control/${i}/${j}"
        client.publish(topic, message)
      }
      def control() {
        var dtokens: List[IMqttDeliveryToken] = Nil
        for(i <- 0 until height) {
          for(j <- 0 until width) {
            val t = measuredTemperatures(i)(j)
            if(t < target - sensitivity) {
              dtokens ::= setAc(i, j, 1)
            } else if(t > target + sensitivity) {
              dtokens ::= setAc(i, j, -1)
            } else {
              dtokens ::= setAc(i, j, 0)
            }
          }
        }
        dtokens.foreach(_.waitForCompletion(timeToWait))
      }
    //def doorClose を書く予定
    }

    class FragileInstance(env: Environment)
        extends Instance(env) {
    }
  }

  class TemperatureManager extends Model {
    object updater extends Thread {
      override def run() {
        while(!end) {
          envs.foreach(_.updateTemperature())
          envs.foreach(e => {
            e.dumpTemperature()
            e.checkTemperature()
          })
          Thread.sleep(scale(Environment.interval))
        }
      }
    }

    "init" -> "0" := {
      updater.start()
    } label "setup"
    List("0", "+") -> "+" := {
      require(running)
      Environment.temperatureRate = 0.001
    } label "set +" stay (scale(200), scale(500))
    List("0", "+", "-") -> "0" := {
      require(running)
      Environment.temperatureRate = 0
    } label "set 0" stay (scale(200), scale(500))
    List("0", "-") -> "-" := {
      require(running)
      Environment.temperatureRate = -0.001
    } label "set -" stay (scale(200), scale(500))
    List("0", "+", "-") -> "end" := {
      require(end)
    } label "end"
  }

  class Thermometer(val i: Int, val j: Int, val port: Int = 1883) extends Model {
    val broker = host + port
    val qos = 0
    val interval = 300.0
    var ready = false
    var broken = false
    val instance = Array(
      new Instance(i, j, envs(0)),
      new FragileInstance(i, j, envs(1)))

    "init" -> "run" := {
      instance.foreach(_.connect())
      ready = true
      publisher.start()
    } label "connect"
    List("run", "broken") -> "broken" := {
      require(running)
      broken = true
    } label "break" weight 0.1 stay (scale(200), scale(500))
    List("run", "broken") -> "run" := {
      require(running)
      broken = false
    } label "restore" weight 0.9 stay (scale(200), scale(500))
    List("run", "broken") -> "end" := {
      require(end)
      instance.foreach(_.disconnect())
    } label "end"

    object publisher extends Thread {
      override def run() {
        while(!end) {
          instance.foreach(_.publish())
          Thread.sleep(scale(interval))
        }
      }
    }

    class Instance(val i: Int, val j: Int, val env: Environment) {
      val prefix = env.prefix
      val id = s"${prefix}-thermo-${i}-${j}"
      val topic = s"${prefix}/temperature/${i}/${j}"
      val client = new MqttAsyncClient(broker, id)
      val connopts = new MqttConnectOptions()
      connopts.setCleanSession(false)
      val qos = 0
      val normalRange = 1.0
      val brokenRange = 1.0

      def getTemperature(): Double = {
        val range = if(broken) brokenRange else normalRange
        env.temperatures(i)(j) + choose(-100, 100) / 100.0 * range
      }
      def connect() {
        client.setCallback(listner)
        client.connect(connopts).waitForCompletion(timeToWait)
      }
      def disconnect() {
        client.disconnect().waitForCompletion(timeToWait)
      }
      def publish() {
        if(!client.isConnected()) return
        val message = new MqttMessage(getTemperature().toString.getBytes())
        message.setQos(qos)
        try {
          client.publish(topic, message).waitForCompletion(timeToWait)
          Thread.sleep(scale(interval))
        } catch {
          case e: Exception => {
            System.err.println(id)
            e.printStackTrace
          }
        }
      }
      object listner extends MqttCallback {
        def connectionLost(e: Throwable) {
          println("connection lost: " + id)
        }
        def deliveryComplete(token: IMqttDeliveryToken) {}
        def messageArrived(topic: String, msg: MqttMessage) {}
      }
    }
    class FragileInstance(i: Int, j: Int, env: Environment)
        extends Instance(i, j, env) {
      override val brokenRange = 10.0
    }
  }

  class AirConditioner(val i: Int, val j: Int, val port: Int = 1883) extends Model {
    val broker = host + port
    val qos = 2
    var ready = false
    var broken = false
    val instance = Array(
      new Instance(i, j, envs(0)),
      new FragileInstance(i, j, envs(1)))

    "init" -> "run" := {
      instance.foreach(_.connect())
      ready = true
    } label "connect"
    List("run", "broken") -> "broken" := {
      require(running)
      broken = true
    } label "break" weight 0.1 stay (scale(200), scale(500))
    List("run", "broken") -> "run" := {
      require(running)
      broken = false
    } label "restore" weight 0.9 stay (scale(200), scale(500))
    List("run", "broken") -> "end" := {
      require(end)
      instance.foreach(_.disconnect())
    } label "end"

    class Instance(val i: Int, val j: Int, val env: Environment) {
      val prefix = env.prefix
      val id = s"${prefix}-ac-${i}-${j}"
      val topic = s"${prefix}/ac-control/${i}/${j}"
      var storedMode: Int = 0
      val client = new MqttAsyncClient(broker, id)
      val connopts = new MqttConnectOptions()
      connopts.setCleanSession(false)
      def setMode(newMode: Int) {
        storedMode = newMode
        env.acMode(i)(j) = newMode
      }
      def connect() {
        client.setCallback(listner)
        client.connect(connopts).waitForCompletion(timeToWait)
        client.subscribe(topic, qos)
      }
      def disconnect() {
        client.disconnect().waitForCompletion(timeToWait)
      }
      object listner extends MqttCallback {
        def connectionLost(e: Throwable) {
          println("connection lost: " + id)
          e.printStackTrace
        }
        def deliveryComplete(token: IMqttDeliveryToken) {}
	//subscribeにより、Controllerからしかメッセージが来ない
        def messageArrived(topic: String, msg: MqttMessage) {
          println(s"$id recieved $msg")
          setMode(msg.toString.toInt)
        }
      }
    }
    class FragileInstance(i: Int, j: Int, env: Environment)
        extends Instance(i, j, env) {
      override def setMode(newMode: Int) {
        storedMode = newMode
        env.acMode(i)(j) = if(broken) choose(-1, 1) else newMode
      }
    }
  }

  //ドアのインスタンス
  class Door(val i: Int, val j: Int, d: Int, val port: Int = 1883) extends Model {
    val broker = host + port
    val qos = 0
    val interval = 300.0
    var ready = false
    var isOpen = false
    val instance = Array(
      new Instance(i, j, d, envs(0)),
      new FragileInstance(i, j, d, envs(1)))

    "init" -> "closed" := {
      instance.foreach(_.connect())
      ready = true
      publisher.start()
    } label "connect"
    List("open", "closed") -> "open" := {
      require(running)
      isOpen = true
      instance.foreach(_.setMode(isOpen))
    } label "open" weight 0.1 stay (scale(2), scale(5))
    List("open", "closed") -> "closed" := {
      require(running)
      isOpen = false
      instance.foreach(_.setMode(isOpen))
    } label "close" weight 0.9 stay (scale(2), scale(5))
    List("open", "closed") -> "end" := {
      require(end)
      instance.foreach(_.disconnect())
    } label "end"

    //Thermometerと同じもの 定期的にドアの状態を送信
    object publisher extends Thread {
      override def run() {
        while(!end) {
          instance.foreach(_.publish())
          Thread.sleep(scale(interval))
        }
      }
    }

    class Instance(val i: Int, val j: Int, val d: Int, val env: Environment) {
      val prefix = env.prefix
      val id = s"${prefix}-door-${i}-${j}-${d}"
      val topic = s"${prefix}/door/${i}/${j}/${d}"
      val client = new MqttAsyncClient(broker, id)
      val connopts = new MqttConnectOptions()
      connopts.setCleanSession(false)
      val qos = 0

      def setMode(b: Boolean) {
        env.doorIsOpen(i)(j)(d) = b
      }

      def connect() {
        client.setCallback(listner)
        client.subscribe(topic, qos)//このトピックのみ受け付け
        client.connect(connopts).waitForCompletion(timeToWait)
      }
      def disconnect() {
        client.disconnect().waitForCompletion(timeToWait)
      }
      //自分が管理するdoorが開いてるか(true/false)を、MQTTで送る
      def publish() {
        if(!client.isConnected()) return
        val message = new MqttMessage(env.doorIsOpen(i)(j)(d).toString.getBytes())
        message.setQos(qos)
        try {
          client.publish(topic, message).waitForCompletion(timeToWait)
          Thread.sleep(scale(interval))
        } catch {
          case e: Exception => {
            System.err.println(id)
            e.printStackTrace
          }
        }
      }
      
      object listner extends MqttCallback {
        def connectionLost(e: Throwable) {
          println("connection lost: " + id)
        }
        def deliveryComplete(token: IMqttDeliveryToken) {}
        //届いたメッセージはclientからのメッセージ？
        def messageArrived(topic: String, msg: MqttMessage) {
          println("$id recieved message")
          setMode(false)
        }
      }
    }
    class FragileInstance(i: Int, j: Int, d: Int, env: Environment)
        extends Instance(i, j, d, env) {
    }
  }
  



  @After
  def after() {
    envs.foreach(_.writer.close())
  }
}
