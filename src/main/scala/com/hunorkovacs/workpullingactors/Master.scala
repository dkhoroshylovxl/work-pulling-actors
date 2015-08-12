package com.hunorkovacs.workpullingactors

import java.util.UUID.randomUUID

import akka.actor._
import com.hunorkovacs.workpullingactors.Master._
import com.hunorkovacs.workpullingactors.Worker._
import com.hunorkovacs.workpullingactors.collection.mutable.WorkBuffer
import org.slf4j.LoggerFactory

import scala.collection.mutable
import scala.util.Try

object Master {

  case object GiveMeWork

  class Result[W, R] private (val work: W, val assigners: List[ActorRef], val result: Try[R]) {
    
    def popAssigner() = new Result(work, assigners.tail, result)

    override def equals(that: Any) = that match {
      case that: Result[W, R] => Result.equal(this, that)
      case _ => false
    }
  }

  object Result {
    def apply[W, R](work: W, result: Try[R]) = new Result(work, Nil, result)

    def apply[W, R](workFrom: WorkFrom[W], result: Try[R]) = new Result(workFrom.work, workFrom.assigners, result)

    def equal[W, R](a: Result[W, R], b: Result[W, R]) =
      a.work == b.work &&
        a.assigners == b.assigners &&
        a.result == b.result
  }

  case class TooBusy[W](work: W)
}

abstract class Master[T, R](private val nWorkers: Int,
                            private val workBuffer: WorkBuffer[T]) extends Actor {

  private val logger = LoggerFactory.getLogger(getClass)
  private val workers = mutable.Set.empty[ActorRef]
  private val busy = mutable.Set.empty[ActorRef]
  private val idle = mutable.Set.empty[ActorRef]

  override def preStart() =
    refreshNrOfWorkers()

  override def receive = {
    case work: WorkFrom[T] =>
      if (workBuffer.add(work.assignedBy(sender()))) {
        if (logger.isDebugEnabled)
          logger.debug(s"${self.path} - Work unit with hashcode ${work.work.hashCode} added to queue.")
        if (workers.isEmpty)
          if (logger.isWarnEnabled)
            logger.warn(s"${self.path} - There are no workers registered but work is coming in.")
        if (idle.nonEmpty) {
          if (logger.isDebugEnabled)
            logger.debug(s"${self.path} - Sending notice that work is available to ${idle.head}...")
          idle.head ! WorkAvailable
        }
      } else {
        if (logger.isInfoEnabled)
          logger.info(s"${self.path} - Received work unit ${work.work.hashCode} but queue is full. TooBusy!")
        sender ! TooBusy(work)
      }

    case result: Result[T, R] =>
      val returnTo = result.assigners.head
      if (logger.isDebugEnabled) {
        val resultHash = result.result.getOrElse(result).hashCode
        logger.debug(s"${self.path} - Received result from ${sender().path} " +
          s"with result hashcode $resultHash " +
          s"of work unit hashcode ${result.work.hashCode}. Returning to ${returnTo.path}...")
      }
      makeIdle(sender())
      returnTo ! result.popAssigner()

    case GiveMeWork =>
      if (busy.contains(sender())) {
        if (logger.isDebugEnabled)
          logger.debug(s"${self.path} - Worker asked for work but that's an old message, it's busy currently: ${sender().path}")
      } else {
        workBuffer.poll match {
          case Some(work) =>
            if (logger.isDebugEnabled)
              logger.debug(s"${self.path} - Worker asked so sending work with hashcode ${work.work.hashCode} to idle ${sender().path}...")
            makeBusy(sender())
            sender() ! work
          case None =>
            if (logger.isDebugEnabled)
              logger.debug(s"${self.path} - Worker asked but no work to give to ${sender().path}.")
        }
      }

    case Terminated(worker) =>
      if (logger.isInfoEnabled)
        logger.info(s"${self.path} - Termination message got from and restarting worker ${worker.path}...")
      busy -= worker
      idle -= worker
      workers.remove(worker)
      context.unwatch(worker)
      refreshNrOfWorkers()
  }

  private def refreshNrOfWorkers() = {
    while (workers.size < nWorkers) {
      val newWorker = context.actorOf(newWorkerProps, "pullingworker-" + randomUUID)
      context.watch(newWorker)
      workers += newWorker
      idle += newWorker
      if (logger.isDebugEnabled)
        logger.debug(s"${self.path} - Created and watching new worker ${newWorker.path}...")
    }
  }

  protected def newWorkerProps: Props

  private def makeBusy(worker: ActorRef) = {
    busy += worker
    idle -= worker
  }

  private def makeIdle(worker: ActorRef) = {
    busy -= worker
    idle += worker
  }

  override val supervisorStrategy = SupervisorStrategy.stoppingStrategy
}
