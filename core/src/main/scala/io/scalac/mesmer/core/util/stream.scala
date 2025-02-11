package io.scalac.mesmer.core.util
import akka.actor.ActorRef

import scala.annotation.tailrec

import io.scalac.mesmer.core.model.Tag._

object stream {

  // TODO FIX doc
  /**
   * Error prone way to get stream name from actor. Most of the time actors that take part in running a stream has path
   * following convention: {stream_name}-{stream_id}-{island_id}-{last-operator-name}
   * @param ref
   * @return
   */
  def subStreamNameFromActorRef(ref: ActorRef): SubStreamName = {
    @tailrec
    def findName(segments: List[String], offset: Int, actorName: String): List[String] =
      if (segments.size >= 3) {
        segments
      } else {
        var id = actorName.indexOf('-', offset)
        if (id < 0) {
          id = actorName.length
        }
        val segment = actorName.substring(offset, id)
        findName(segment :: segments, id + 1, actorName)
      }

    val islandId :: matId :: matName :: Nil = findName(Nil, 0, ref.path.name)
    SubStreamName(s"$matName-$matId", islandId)
  }
}
