package com.hunorkovacs.collection.mutable

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

class BoundedRejectQueue[T](protected val limit: Int, protected val refreshPeriod: Int) {

  protected val queue = new ConcurrentLinkedQueue[T]()
  private val sizeCounter = new AtomicInteger(0)
  protected val operationCounter = new AtomicInteger(0)

  def add(w: T) = {
    refreshSize()
    if (sizeCounter.get() < limit) {
      sizeCounter.incrementAndGet()
      queue.add(w)
    } else
      false
  }

  def poll = {
    refreshSize()
    val tail = Option(queue.poll())
    if (tail.isDefined)
      sizeCounter.decrementAndGet()
    tail
  }

  def size = sizeCounter.get()

  def isEmpty = queue.isEmpty

  private def refreshSize() = {
    if (operationCounter.getAndIncrement() > refreshPeriod) {
      sizeCounter.set(queue.size)
      operationCounter.set(0)
    }
  }
}

object BoundedRejectQueue {

  def apply[T](limit: Int, refreshPeriod: Int = 100) =
    new BoundedRejectQueue[T](limit, refreshPeriod)
}
