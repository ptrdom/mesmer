package io.scalac.mesmer.agent.akka.actor.impl

import akka.actor.typed.TypedActorContext
import io.scalac.mesmer.core.actor.ActorCellDecorator
import net.bytebuddy.asm.Advice.{ Argument, OnMethodExit }

object SupervisorHandleReceiveExceptionInstrumentation {

  @OnMethodExit(onThrowable = classOf[Throwable])
  def onExit(@Argument(0) context: TypedActorContext[_]): Unit =
    ActorCellDecorator.get(ClassicActorContextProviderOps.classicActorContext(context)).foreach { metrics =>
      import metrics._
      if (failedMessages.isDefined && exceptionHandledMarker.isDefined) {

        failedMessages.get.inc()
        exceptionHandledMarker.get.mark()

      }
    }

}
