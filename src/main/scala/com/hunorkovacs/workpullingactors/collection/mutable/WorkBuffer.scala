package com.hunorkovacs.workpullingactors.collection.mutable

import com.hunorkovacs.workpullingactors.Worker.Work

trait WorkBuffer[T] {

  def add(w: Work[T]): Boolean

  def poll: Option[Work[T]]

  def size: Int

  def isEmpty: Boolean
}
