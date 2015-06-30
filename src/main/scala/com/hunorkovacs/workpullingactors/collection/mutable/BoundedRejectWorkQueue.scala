package com.hunorkovacs.workpullingactors.collection.mutable

import com.hunorkovacs.collection.mutable.BoundedRejectQueue
import com.hunorkovacs.workpullingactors.Worker.WorkFrom


class BoundedRejectWorkQueue[T](limit: Int, refreshPeriod: Int)
  extends BoundedRejectQueue[WorkFrom[T]](limit, refreshPeriod) with WorkBuffer[T] {
}

object BoundedRejectWorkQueue {

  def apply[T](limit: Int, refreshPeriod: Int = 100) =
    new BoundedRejectWorkQueue[T](limit, refreshPeriod)
}
