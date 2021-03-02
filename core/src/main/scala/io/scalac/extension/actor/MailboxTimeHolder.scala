package io.scalac.extension.actor

import java.lang.invoke.MethodHandles

import scala.collection.mutable
import scala.concurrent.duration.FiniteDuration

object MailboxTimeHolder {

  type MailboxTimesType = mutable.ArrayBuffer[MailboxTime]

  val MailboxTimesVar = "mailboxTimes"

  private lazy val lookup = MethodHandles.publicLookup()

  private lazy val (mailboxTimesGetterHandler, mailboxTimesSetterHandler) = {
    val field = Class.forName("akka.actor.ActorCell").getDeclaredField(MailboxTimesVar)
    field.setAccessible(true)
    (
      lookup.unreflectGetter(field),
      lookup.unreflectSetter(field)
    )
  }
  @inline def setTimes(actorCell: Object): Unit =
    mailboxTimesSetterHandler.invoke(actorCell, mutable.ArrayBuffer.empty[MailboxTime])

  @inline def addTime(actorCell: Object, time: FiniteDuration): Unit =
    mailboxTimes(actorCell).foreach(_ += MailboxTime(time))

  @inline def takeTimes(actorCell: Object): Option[Array[MailboxTime]] = {
    val times = getTimes(actorCell)
    clearTimes(actorCell)
    times
  }

  @inline def getTimes(actorCell: Object): Option[Array[MailboxTime]] =
    mailboxTimes(actorCell).map(_.toArray)

  @inline def clearTimes(actorCell: Object): Unit =
    mailboxTimes(actorCell).foreach(_.clear())

  @inline private def mailboxTimes(actorCell: Object): Option[MailboxTimesType] =
    Option(mailboxTimesGetterHandler.invoke(actorCell)).map(_.asInstanceOf[MailboxTimesType])

}
