package org.squbs.httpclient.demo

import akka.actor.ActorSystem
import akka.pattern.{CircuitBreakerOpenException, CircuitBreaker}
import scala.concurrent.duration._
import scala.concurrent.Future
import scala.util.{Failure, Success}

/**
 * Created by hakuang on 8/13/2014.
 */
object CircuitBreakerTest extends App{

  private implicit val system = ActorSystem("CircuitBreakerMain")

  import scala.concurrent.ExecutionContext.Implicits.global

  val breaker = new CircuitBreaker(system.scheduler, maxFailures = 5, callTimeout = 10 seconds, resetTimeout = 5 seconds).onOpen{
    println("CircuitBreaker is now open and will not close for one minute.")
  }

  def failureCall: String = {
    Thread.sleep(2000)
    println("entering failure call")
    throw new RuntimeException("failure call")
  }

  def successCall: String = {
    "Hello World"
  }

  while (true){
    Thread.sleep(1000)
    val result = breaker.withCircuitBreaker[String](Future(failureCall))
    result onComplete {
      case Success(s) => println("successful call, result is:" + s)
      case Failure(e) => e match {
        case exception: CircuitBreakerOpenException => println("failure call, message is:" + exception.getMessage + ",duration is:" + exception.remainingDuration.toSeconds)
        case _ => println("failure call, exception message is:" + e.getMessage)
      }
    }
  }

}
