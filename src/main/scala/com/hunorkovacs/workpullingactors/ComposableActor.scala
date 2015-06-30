package com.hunorkovacs.workpullingactors

import akka.actor.{ActorRef, Actor}

trait ComposableActor extends Actor {

  private var receives: List[Receive] = List()

  protected def registerReceive(receive: Receive) {
    receives = receive :: receives
  }

  override def receive = receives reduce {_ orElse _}
}
