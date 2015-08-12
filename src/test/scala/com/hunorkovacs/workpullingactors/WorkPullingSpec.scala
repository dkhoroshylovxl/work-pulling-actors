package com.hunorkovacs.workpullingactors

import java.util.concurrent.TimeoutException

import akka.actor._
import com.hunorkovacs.workpullingactors.PromiseWorker.Kept
import com.hunorkovacs.workpullingactors.Master.Result
import com.hunorkovacs.workpullingactors.Master.TooBusy
import com.hunorkovacs.workpullingactors.Worker.Work
import com.hunorkovacs.workpullingactors.collection.mutable.{BoundedRejectWorkQueue, WorkBuffer}
import org.specs2.mutable.Specification

import scala.collection.mutable
import scala.concurrent.{Promise, ExecutionContext}
import scala.util.{Failure, Success, Try}

import scala.concurrent.duration._

class WorkPullingSpec extends Specification {

  private val system = ActorSystem("test-actor-system")

  "Sending work and receiving results" should {
    "flow nicely with 1 worker." in {
      val n = 10
      val inbox = Inbox.create(system)
      val master = system.actorOf(PromiseKeeperMaster.props(1, BoundedRejectWorkQueue[Promise[Int]](n)), "master-1")
      val worksAndCompletions = (1 to n).toList.map(i => (Work(Promise[Int]()), Success(i)))

      worksAndCompletions.foreach(wc => inbox.send(master, wc._1))
      worksAndCompletions.foreach(wc => wc._1.work.complete(wc._2))

      val expectedResults = worksAndCompletions.map(wc => wc._1.resolveWith(wc._2))
      val actualResults = (1 to n).toList.map(_ => inbox.receive(2 seconds).asInstanceOf[Kept])
      actualResults must containTheSameElementsAs(expectedResults, (a: Kept, b: Kept) => Result.equal(a, b))
      actualResults.size must beEqualTo(n)
    }
    "flow nicely with n workers." in {
      val n = 10
      val inbox = Inbox.create(system)
      val master = system.actorOf(PromiseKeeperMaster.props(n, BoundedRejectWorkQueue[Promise[Int]](n)), "master-2")
      val worksAndCompletions = (1 to n).toList.map(i => (Work(Promise[Int]()), Success(i)))

      worksAndCompletions.foreach(wc => inbox.send(master, wc._1))
      worksAndCompletions.foreach(wc => wc._1.work.complete(wc._2))

      val expectedResults = worksAndCompletions.map(wc => wc._1.resolveWith(wc._2))
      val actualResults = (1 to n).toList.map(_ => inbox.receive(2 seconds).asInstanceOf[Kept])
      actualResults must containTheSameElementsAs(expectedResults, (a: Kept, b: Kept) => Result.equal(a, b))
      actualResults.size must beEqualTo(n)
    }
    "not flow with 0 workers." in {
      val n = 10
      val inbox = Inbox.create(system)
      val master = system.actorOf(PromiseKeeperMaster.props(0, BoundedRejectWorkQueue[Promise[Int]](n)), "master-3")

      val works = (1 to n).toList.map(w => Work(Promise.successful(w)))
      works.foreach(wc => inbox.send(master, wc))

      inbox.receive(500 millis).asInstanceOf[Set[Result[Promise[Int], Int]]] must throwA[TimeoutException]
    }
    "reject more work than buffer size. But compute the rest fine." in {
      val n = 10
      val inbox = Inbox.create(system)
      val master = system.actorOf(PromiseKeeperMaster.props(n, BoundedRejectWorkQueue[Promise[Int]](n)), "master-4")
      val worksAndCompletions = (1 to 2 * n).toList.map(i => (Work(Promise[Int]()), Success(i)))

      worksAndCompletions.foreach(wc => inbox.send(master, wc._1))

      worksAndCompletions.drop(n).foreach(wc => inbox.receive(1 second) must beEqualTo(TooBusy(wc._1)))

      worksAndCompletions.foreach(wc => wc._1.work.complete(wc._2))

      val expectedResults = worksAndCompletions.take(n).map(wc => wc._1.resolveWith(wc._2))
      val actualResults = (1 to n).toList.map(_ => inbox.receive(2 seconds).asInstanceOf[Kept])
      actualResults must containTheSameElementsAs(expectedResults, (a: Kept, b: Kept) => Result.equal(a, b))
      actualResults.size must beEqualTo(n)
    }
    "flow nicely with failures." in {
      val n = 10
      val inbox = Inbox.create(system)
      val master = system.actorOf(PromiseKeeperMaster.props(n, BoundedRejectWorkQueue[Promise[Int]](n)), "master-5")
      val worksAndCompletions = (1 to n).toList.map(i => (Work(Promise[Int]()), Failure[Int](new RuntimeException(i.toString))))

      worksAndCompletions.foreach(wc => inbox.send(master, wc._1))
      worksAndCompletions.foreach(wc => wc._1.work.complete(wc._2))

      val expectedResults = worksAndCompletions.map(wc => wc._1.resolveWith(wc._2))
      val actualResults = (1 to n).toList.map(_ => inbox.receive(2 seconds).asInstanceOf[Kept])
      actualResults must containTheSameElementsAs(expectedResults, (a: Kept, b: Kept) => Result.equal(a, b))
      actualResults.size must beEqualTo(n)
    }
    "flow nicely with continuous work coming in and going out." in {
      val queueSize = 8
      val nWorks = 100
      val nWorkers = 3
      val inbox = Inbox.create(system)
      val master = system.actorOf(PromiseKeeperMaster.props(nWorkers, BoundedRejectWorkQueue[Promise[Int]](queueSize)), "master-6")
      val worksAndCompletions = (1 to nWorks).toList.map { i =>
        val e: (Work[Promise[Int]], Try[Int]) = i % 2 match {
          case 0 => (Work(Promise[Int]()), Success(i))
          case 1 => (Work(Promise[Int]()), Failure[Int](new RuntimeException(i.toString)))
        }
        e
      }
      val senderQueue = mutable.Queue[(Work[Promise[Int]], Try[Int])]()
      worksAndCompletions.drop(queueSize / 2).foreach(senderQueue.enqueue(_))
      val completerQueue = mutable.Queue[(Work[Promise[Int]], Try[Int])]()
      worksAndCompletions.foreach(completerQueue.enqueue(_))

      // send in some, to pre-fill the queue but not fully
      worksAndCompletions.take(queueSize / 2).foreach(wc => inbox.send(master, wc._1))

      // uniformly produce and consume
      val actualResults = mutable.Set[Kept]()
      while (senderQueue.nonEmpty) {
        val toSend = senderQueue.dequeue()
        inbox.send(master, toSend._1)
        val toComplete = completerQueue.dequeue()
        toComplete._1.work.complete(toComplete._2)
        actualResults += inbox.receive(1 second).asInstanceOf[Kept]
      }

      // take out rest from the queue
      while (completerQueue.nonEmpty) {
        val r1 = completerQueue.dequeue()
        r1._1.work.complete(r1._2)
        actualResults += inbox.receive(1 second).asInstanceOf[Kept]
      }

      val expectedResults = worksAndCompletions.map(wc => wc._1.resolveWith(wc._2))
      actualResults must containTheSameElementsAs(expectedResults, (a: Kept, b: Kept) => Result.equal(a, b))
      actualResults.size must beEqualTo(nWorks)
    }
  }

  "Crashing worker" should {
    "make a refresh action in master who will replace the worker." in {
      val queueSize = 100
      val nWorks = 100
      val nWorkers = 3
      val nCrashers = 5

      val it = ((1 to nCrashers).toList.map(_ => CrashingWorker.props) :::
        (1 to nWorkers).toList.map(_ => PromiseWorker.props)).iterator
      def props(): Props = it.next()

      val inbox = Inbox.create(system)
      val master = system.actorOf(PluggableMaster.props(nWorkers, BoundedRejectWorkQueue[Promise[Int]](queueSize),
        props), "master-7")
      val worksAndCompletions = (1 to nWorks).toList.map(i => (Work(Promise[Int]()), Success(i)))
      worksAndCompletions.foreach(wc => wc._1.work.complete(wc._2))

      worksAndCompletions.take(nCrashers).foreach(wc => inbox.send(master, wc._1))
      worksAndCompletions.drop(nCrashers).foreach(wc => inbox.send(master, wc._1))

      val actualResults = (1 to nWorks - nCrashers).map(_ => inbox.receive(1 second).asInstanceOf[Kept])
      val expectedResults = worksAndCompletions.drop(nCrashers).map(wc => wc._1.resolveWith(wc._2))
      actualResults must containTheSameElementsAs(expectedResults, (a: Kept, b: Kept) => Result.equal(a, b))
      actualResults.size must beEqualTo(nWorks - nCrashers)
    }
  }

  "Workers that get intermittent assignments" should {
    "resume flawlessly." in {
      val n = 10
      val inbox = Inbox.create(system)
      val master = system.actorOf(PromiseKeeperMaster.props(1, BoundedRejectWorkQueue[Promise[Int]](n)), "master-8")
      val worksAndCompletions = (1 to n).toList.map(i => (Work(Promise[Int]()), Success(i)))

      Thread.sleep(2000)

      worksAndCompletions.foreach(wc => inbox.send(master, wc._1))
      worksAndCompletions.foreach(wc => wc._1.work.complete(wc._2))

      val expectedResults = worksAndCompletions.map(wc => wc._1.resolveWith(wc._2))
      val actualResults = (1 to n).toList.map(_ => inbox.receive(2 seconds).asInstanceOf[Kept])
      actualResults must containTheSameElementsAs(expectedResults, (a: Kept, b: Kept) => Result.equal(a, b))
      actualResults.size must beEqualTo(n)
    }
  }
}

private class PromiseKeeperMaster(nWorkers: Int, workBuffer: WorkBuffer[Int])
  extends Master[Int, Int](nWorkers, workBuffer) {

  override protected def newWorkerProps = PromiseWorker.props
}

private object PromiseKeeperMaster {
  def props(nWorkers: Int, workBuffer: WorkBuffer[Promise[Int]]) =
    Props(classOf[PromiseKeeperMaster], nWorkers, workBuffer)
}

private class PromiseWorker extends Worker[Promise[Int], Int] {
  implicit private val ec = ExecutionContext.Implicits.global

  override def doWork(work: Promise[Int]) = work.future
}

private object PromiseWorker {
  def props = Props(classOf[PromiseWorker])

  type Kept = Result[Promise[Int], Int]
}

private class PluggableMaster(nWorkers: Int, workBuffer: WorkBuffer[Int], workerProps: () => Props)
  extends Master[Int, Int](nWorkers, workBuffer) {

  override protected def newWorkerProps = workerProps()
}

private object PluggableMaster {
  def props(nWorkers: Int, workBuffer: WorkBuffer[Promise[Int]], workerProps: () => Props) =
    Props(classOf[PluggableMaster], nWorkers, workBuffer, workerProps)
}

private class CrashingWorker extends PromiseWorker {
  override def doWork(work: Promise[Int]) =
    throw new RuntimeException("crashed")
}

private object CrashingWorker {
  def props = Props(classOf[CrashingWorker])
}
