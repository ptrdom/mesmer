package io.scalac.extension

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }
import scala.jdk.DurationConverters.JavaDurationOps

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ ActorSystem, Behavior }
import akka.cluster.sharding.ShardRegion.{ GetShardRegionStats, ShardRegionStats }
import akka.cluster.sharding.{ ClusterSharding, ShardRegion }
import akka.pattern.ask
import akka.util.Timeout

import org.slf4j.LoggerFactory

import io.scalac.extension.config.ConfigurationUtils.ConfigOps
import io.scalac.extension.metric.ClusterMetricsMonitor
import io.scalac.extension.model.AkkaNodeOps
import io.scalac.extension.util.CachedQueryResult

class ClusterRegionsMonitorActor
object ClusterRegionsMonitorActor extends ClusterMonitorActor {

  private type RegionStats    = Map[ShardRegion.ShardId, Int]
  private type RegionStatsMap = Map[String, RegionStats]

  sealed trait Command

  private val logger = LoggerFactory.getLogger(classOf[ClusterRegionsMonitorActor])

  def apply(clusterMetricsMonitor: ClusterMetricsMonitor): Behavior[Command] =
    OnClusterStartUp { selfMember =>
      Behaviors.setup { ctx =>
        val system = ctx.system
        import system.executionContext

        val monitor = clusterMetricsMonitor.bind(selfMember.uniqueAddress.toNode)

        val regions = new Regions(system, onCreateEntry = (region, entry) => {

          monitor
            .entityPerRegion(region)
            .setUpdater(result =>
              entry.get.foreach { regionStats =>
                val entities = regionStats.values.sum
                result.observe(entities)
                logger.trace("Recorded amount of entities per region {}", entities)
              }
            )

          monitor
            .shardPerRegions(region)
            .setUpdater(result =>
              entry.get.foreach { regionStats =>
                val shards = regionStats.size
                result.observe(shards)
                logger.trace("Recorded amount of shards per region {}", shards)
              }
            )

        })

        monitor.entitiesOnNode.setUpdater { result =>
          regions.regionStats.map { regionsStats =>
            val entities = regionsStats.view.values.flatMap(_.values).sum
            result.observe(entities)
            logger.trace("Recorded amount of entities on node {}", entities)
          }
        }

        monitor.shardRegionsOnNode.setUpdater { result =>
          result.observe(regions.size)
          logger.trace("Recorded amount of regions on node {}", regions)
        }

        Behaviors.same
      }

    }

  private[extension] class Regions(
    system: ActorSystem[_],
    onCreateEntry: (String, CachedQueryResult[Future[RegionStats]]) => Unit
  )(
    implicit ec: ExecutionContext
  ) {

    implicit val queryRegionStatsTimeout: Timeout = Timeout(getQueryStatsTimeout)

    private val sharding = ClusterSharding(system.classicSystem)
    private val logger   = LoggerFactory.getLogger(getClass)
    private val cache    = collection.mutable.HashMap.empty[String, CachedQueryResult[Future[RegionStats]]]

    def size: Int = cache.size

    def regionStats: Future[RegionStatsMap] = {
      renewEntries()
      val regions = cache.keySet.toSeq
      Future
        .sequence(regions.map(cache(_).get))
        .map(regionStats => regions.zip(regionStats).toMap)
    }

    private def renewEntries(): Unit = {
      val current = sharding.shardTypeNames
      val cached  = cache.keySet
      val coming  = current.diff(cached)
      val leaving = cached.diff(current)
      leaving.foreach(cache.remove)
      coming.foreach(createEntry)
    }

    private def createEntry(region: String): Unit = {
      val entry = CachedQueryResult(runQuery(region))
      cache(region) = entry
      onCreateEntry(region, entry)
    }

    private def runQuery(region: String): Future[RegionStats] = {
      logger.debug(s"running query for region $region")
      (sharding.shardRegion(region) ? GetShardRegionStats)
        .mapTo[ShardRegionStats]
        .flatMap { regionStats =>
          if (regionStats.failed.isEmpty) {
            Future.successful(regionStats.stats)
          } else {
            val shardsFailed = regionStats.failed
            val msg          = s"region $region failed. Shards failed: ${shardsFailed.mkString("(", ",", ")")}"
            logger.warn(msg)
            Future.failed(new RuntimeException(msg))
          }
        }
    }

    private def getQueryStatsTimeout: FiniteDuration =
      system.settings.config
        .tryValue("io.scalac.scalac.akka-monitoring.timeouts.query-region-stats")(_.getDuration)
        .map(_.toScala)
        .getOrElse(2.second)

  }

}
