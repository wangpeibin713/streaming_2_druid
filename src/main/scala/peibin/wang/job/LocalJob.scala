package peibin.wang.job

import java.util.{Date, UUID}

import com.metamx.common.Granularity
import com.metamx.tranquility.spark.{BeamFactory, BeamRDD}
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.{DataFrame, Row, SQLContext}
import org.apache.spark.streaming.{Seconds, StreamingContext}
import org.apache.spark.{SparkConf, SparkContext}
import org.joda.time.{DateTime, DateTimeZone, Period}
import peibin.wang.job.druid.MapBeamFactory

import scala.collection.mutable
import scala.util.Random

/**
  * Created by peibin on 2016/9/6.
  */
object LocalJob extends Serializable{
  case class Test(val timestamp : Long, val ad : Int, val imp:Int, val clk : Int)
  def send2druid(frame: DataFrame) = {
    val rdd :RDD[Map[String, Any]] = frame.rdd.map(row=> {
      row.schema.map(
        x => {
          x.name -> row.getAs(x.name)
        }).toMap
    })

    val beamRDD: BeamRDD[Map[String, Any]] = rdd
    val zkConnect = "127.0.0.1:2181"
    val datasource = "hello_world"
    val windowPeriod = new Period("PT10M")
    val granularity =  Granularity.HOUR
    beamRDD.propagate(new MapBeamFactory(zkConnect, datasource, "timestamp", Seq("ad") , Seq("imp", "clk"), windowPeriod, granularity))

  }

  def main(args: Array[String]): Unit = {
    val sparkConf = new SparkConf().setAppName("test").setMaster("local[*]")
    val sc = new SparkContext(sparkConf)
    sc.setCheckpointDir("./checkpoint")
    val ssc = new StreamingContext(sc, Seconds(6))
    val lines = mutable.Queue[RDD[Test]]()
    val streams = ssc.queueStream(lines, false)


    streams.foreachRDD( rdd => {

      val sqlContext = SQLContext.getOrCreate(rdd.sparkContext)
      import sqlContext.implicits._
      val dataFrame = rdd.toDF()
      // generate unique table name, otherwise the data may be duplicated
      val tableName = s"all_log_${UUID.randomUUID()}".replaceAll("-","_") // table name should not have "-"
      dataFrame.registerTempTable(tableName)
      val sql = """select timestamp , vendor, sum(imp) as imp, sum(clk) as clk from test group by timestamp , vendor""".replaceAll(" test ", s" ${tableName} ")
      val result = sqlContext.sql(sql)
      // dataFrame.show()
      send2druid(result)
    })

    var flag =true
    sys.ShutdownHookThread {
      println("Gracefully stopping Spark Streaming Application at"+ new Date())
      flag = false
      ssc.stop(true, true)
      println("Application stopped at"+ new Date())
    }

    ssc.start()
    for(x <- 0 to 1000 if flag  ) yield  {
      val now = new DateTime(DateTimeZone.UTC)
      val ts = now.getMillis / 60000 * 60000 //  per minute
      lines += sc.makeRDD(Seq(Test(ts, 10, Random.nextInt(10000), Random.nextInt(10)), Test(ts, 20, Random.nextInt(10000), Random.nextInt(20))))
      Thread.sleep(1000)
    }
    ssc.awaitTermination()  }

}
