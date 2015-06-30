package com.hunorkovacs.workpullingactors

import com.hunorkovacs.workpullingactors.Worker.WorkFrom

trait WorkBuffer[T] {

  def add(w: WorkFrom[T]): Boolean

  def poll: Option[WorkFrom[T]]

  def size: Int

  def isEmpty: Boolean
}
