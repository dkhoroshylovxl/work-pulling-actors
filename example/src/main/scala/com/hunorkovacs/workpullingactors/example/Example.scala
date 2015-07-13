package com.hunorkovacs.workpullingactors.example

import akka.actor.{Inbox, ActorSystem, Props}
import com.hunorkovacs.workpullingactors.Master.Result
import com.hunorkovacs.workpullingactors.Worker.WorkFrom
import com.hunorkovacs.workpullingactors.{Worker, Master}
import com.hunorkovacs.workpullingactors.collection.mutable.{BoundedRejectWorkQueue, WorkBuffer}
import org.slf4j.LoggerFactory

import scala.concurrent.Future
import scala.concurrent.duration._

object Example extends App {

  private val logger = LoggerFactory.getLogger(getClass)
  private val sys = ActorSystem("example")

  private val n = 1000
  private val queue = BoundedRejectWorkQueue[Int](n)
  private val master = sys.actorOf(Props(classOf[MyMaster], 3, queue))
  private val inbox = Inbox.create(sys)

  (1 to n) foreach { i =>
    inbox.send(master, WorkFrom[String](i.toString))
  }

  private val sum = (1 to n).foldLeft(0) { (acc, _) =>
    acc + inbox.receive(2 seconds).asInstanceOf[Result[String, Int]].result.get
  }

  logger.info(s"Result is $sum.")

  sys.shutdown()
  sys.awaitTermination()
}

class MyMaster(nWorkers: Int, workBuffer: WorkBuffer[String]) extends Master[String, Int](nWorkers, workBuffer) {

  override protected def newWorkerProps = Props(classOf[MyWorker])
}

class MyWorker extends Worker[String, Int] {

  import context.dispatcher

  override protected def doWork(s: String) = Future(s.toInt)
}
