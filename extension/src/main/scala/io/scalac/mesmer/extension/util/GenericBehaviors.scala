package io.scalac.mesmer.extension.util

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.receptionist.Receptionist.Listing
import akka.actor.typed.receptionist.ServiceKey
import akka.actor.typed.scaladsl.Behaviors

import scala.reflect.ClassTag
import scala.reflect.classTag

object GenericBehaviors {

  /**
   * Creates behavior that waits for service to be accessible on [[serviceKey]] After that it transition to specified
   * behavior using [[next]] function as factory
   * @param serviceKey
   * @param next
   *   factory function creating target behavior
   * @tparam T
   * @tparam I
   * @return
   */
  def waitForService[T, I: ClassTag](serviceKey: ServiceKey[T], bufferSize: Int = 1024)(
    next: ActorRef[T] => Behavior[I]
  ): Behavior[I] =
    Behaviors
      .setup[Any] { context => // use any to mimic union types

        import context._

        setLoggerName(classTag[I].runtimeClass)

        def start(): Behavior[Any] = {

          /*
            We can't use messageAdapter as there is no other option to unsubscribe from receptionist than stopping an actor
           */
          val subscriptionChild = context.spawnAnonymous(Behaviors.receiveMessage[Listing] { listing =>
            listing.allServiceInstances(serviceKey).headOption.fold[Behavior[Listing]](Behaviors.same) { ref =>
              context.self ! ref
              Behaviors.stopped
            }
          })

          system.receptionist ! Receptionist.Subscribe(serviceKey, subscriptionChild)
          waitingForService()
        }

        def waitingForService(): Behavior[Any] =
          Behaviors.withStash(bufferSize) { buffer =>
            Behaviors.receiveMessagePartial {
              case ref: ActorRef[T] @unchecked =>
                buffer.unstashAll(next(ref).asInstanceOf[Behavior[Any]])
              case message: I =>
                buffer.stash(message)
                Behaviors.same
              case _ => Behaviors.unhandled
            }
          }

        start()
      }
      .narrow[I]
}
