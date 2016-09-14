package peibin.wang.job.druid

import com.metamx.common.Granularity
import com.metamx.tranquility.beam.{Beam, ClusteredBeamTuning}
import com.metamx.tranquility.druid.{DruidBeams, DruidLocation, DruidRollup, SpecificDruidDimensions}
import com.metamx.tranquility.spark.BeamFactory
import com.metamx.tranquility.typeclass.Timestamper
import io.druid.data.input.impl.TimestampSpec
import io.druid.granularity.QueryGranularity
import io.druid.query.aggregation.LongSumAggregatorFactory
import org.apache.curator.framework.CuratorFrameworkFactory
import org.apache.curator.retry.BoundedExponentialBackoffRetry
import org.joda.time.{DateTime, DateTimeZone, Period}

import scala.collection.mutable

/**
  * Created by peibin on 2016/9/9.
  */
class MapBeamFactory(val zkConnect: String,
                     val dataSource: String,
                     val timstampField: String,
                     val dimensions: Seq[String],
                     val metrics: Seq[String],
                     val windowPeriod: Period,
                     val granularity: Granularity) extends BeamFactory[Map[String, Any]] with Serializable {
  def makeBeam: Beam[Map[String, Any]] = MapBeamFactory.mkBeam(zkConnect, dataSource, timstampField, dimensions, metrics, windowPeriod, granularity)
}

object MapBeamFactory {

  private val beams = mutable.HashMap[String, Beam[Map[String, Any]]]()

  def mkBeam(zkConnect: String,
             dataSource: String,
             timstampField: String,
             dimensions: Seq[String],
             metrics: Seq[String],
             windowPeriod: Period,
             granularity: Granularity): Beam[Map[String, Any]] = {
    beams.synchronized {
      beams.getOrElseUpdate(zkConnect + dataSource, instance(zkConnect, dataSource, timstampField, dimensions, metrics, windowPeriod, granularity))
    }
  }

  def instance(zkConnect: String,
               dataSource: String,
               timstampField: String,
               dimensions: Seq[String],
               metrics: Seq[String],
               windowPeriod: Period,
               granularity: Granularity): Beam[Map[String, Any]] = {
    val curator = CuratorFrameworkFactory.newClient(
      zkConnect,
      new BoundedExponentialBackoffRetry(100, 3000, 5)
    )
    curator.start()

    val indexService = "druid/overlord" // Your overlord's druid.service, with slashes replaced by colons.
    val discoveryPath = "/druid/discovery" // Your overlord's druid.discovery.curator.path

    val timestamper = new Timestamper[Map[String, Any]] {
      def timestamp(a: Map[String, Any]) = {

        val millisecond = a(timstampField).asInstanceOf[Long]
        val result = new DateTime(millisecond).withZone(DateTimeZone.UTC);
        result
      }
    }
    val timestampSpec = new TimestampSpec(timstampField, "auto", new DateTime().withZone(DateTimeZone.UTC))
    val aggregators = metrics.map(x => new LongSumAggregatorFactory(x, x))
    val rollup = DruidRollup(SpecificDruidDimensions(dimensions), aggregators, QueryGranularity.MINUTE)
    //val partitioner = new EventPartitioner(timestamper, timestampSpec, rollup)

    DruidBeams
      .builder[Map[String, Any]]()(timestamper)
      .curator(curator)
      .timestampSpec(timestampSpec)
      .discoveryPath(discoveryPath)
      //.partitioner(partitioner)
      .location(DruidLocation.create(indexService, dataSource))
      .rollup(rollup)
      .tuning(
        ClusteredBeamTuning(
          segmentGranularity = granularity,
          windowPeriod = windowPeriod,
          partitions = 1,
          replicants = 1
        )
      )
      .buildBeam()
  }
}