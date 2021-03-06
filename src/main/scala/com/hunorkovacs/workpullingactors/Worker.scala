package com.hunorkovacs.workpullingactors

import akka.actor.{ActorRef, Actor}
import com.hunorkovacs.workpullingactors.Master._
import com.hunorkovacs.workpullingactors.Worker._
import org.slf4j.LoggerFactory

import scala.concurrent.{Promise, Future}
import scala.util.Try

object Worker {

  case object WorkAvailable

  class Work[W] private (val work: W, val assigners: List[ActorRef]) {

    def resolveWith[R](result: Try[R]) = Result[W, R](this, result)

    def assignedBy(assigner: ActorRef) = new Work(work, assigner :: assigners)
  }

  object Work {
    def apply[W](work: W) = new Work[W](work, Nil)
  }
}

abstract class Worker[T, R] extends Actor {
  private val logger = LoggerFactory.getLogger(getClass)
  implicit private val ec = context.dispatcher

  override def preStart() =
    askForWork(context.parent)

  override def receive = {
    case WorkAvailable =>
      if (logger.isDebugEnabled)
        logger.debug(s"${self.path} - Received notice that work is available.")
      askForWork(sender())

    case work: Work[T] =>
      if (logger.isDebugEnabled)
        logger.debug(s"${self.path} - Starting to work on work unit with hashcode ${work.work.hashCode}...")
      val forwardedWork = work.assignedBy(sender())
      doWorkAssociated(forwardedWork) onSuccess {
        case result: Result[T, R] =>
          val returnTo = result.assigners.head
          tellResult(returnTo, result)
          askForWork(returnTo)
      }
  }

  private def doWorkAssociated(work: Work[T]): Future[Result[T, R]] = {
    val p = Promise[Result[T, R]]()
    doWork(work.work).onComplete(r => p.success(work.resolveWith(r)))
    p.future
  }

  protected def doWork(work: T): Future[R]

  private def askForWork(master: ActorRef) = {
    if (logger.isDebugEnabled)
      logger.debug(s"${self.path} - Asking for work from ${master.path}...")
    master ! GiveMeWork
  }

  private def tellResult(returnTo: ActorRef, result: Result[T, R]) = {
    if (logger.isDebugEnabled) {
      val resultHash = result.result.getOrElse(result).hashCode
      logger.debug(s"${self.path} - Sending result with hashcode $resultHash of the work unit with hashcode ${result.work.hashCode}...")
    }
    returnTo ! result.popAssigner()
  }
}

