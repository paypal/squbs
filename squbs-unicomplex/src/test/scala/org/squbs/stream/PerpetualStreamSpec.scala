
package org.squbs.stream

import akka.{NotUsed}
import akka.actor.ActorContext
import akka.stream.{ClosedShape}
import akka.stream.scaladsl.GraphDSL.Implicits._
import akka.stream.scaladsl._

import scala.concurrent.Future
import scala.language.postfixOps

/**
  * Created by pnarayanan on 4/20/2016.
  */

class PerpetualStreamSpec extends PerpetualStream[Future[Int]]{
    def streamGraph = RunnableGraph.fromGraph(GraphDSL.create(endSink) {implicit builder =>
    sink => startSource ~> sink
  ClosedShape
  })

  // IllegalStateException
  try {
    matValue
  }
  catch{
    case ex:Throwable => decider(ex)
    }

  private def startSource(implicit context: ActorContext): Source[Int, NotUsed] = Source(1 to 10).map(_ * 1)

  private def endSink(implicit context: ActorContext): Sink[Int, Future[Int]] = {
    val sink = Sink.fold[Int, Int](0)(_ + _)
    sink
   }
  override def shutdownHook() = { print ("Neo Stream Result " +  matValue.value.get.get + "\n\n") }

}
