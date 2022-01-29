/*
 * Copyright (C) 2012-2017 Typesafe Inc. <http://www.typesafe.com>
 */
package org.squbs.util

import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit
import java.time.{Duration => JavaDuration}

import scala.concurrent.duration.{FiniteDuration, Duration => ScalaDuration}


/**
  * This class contains static methods which convert between Java Durations
  * and the durations from the Scala concurrency package. This is useful when mediating between Scala and Java
  * libraries with asynchronous APIs where timeouts for example are often expressed as durations.
  */
object DurationConverters {

  /**
    * Transform a Java duration into a Scala duration. If the nanosecond part of the Java duration is zero the returned
    * duration will have a time unit of seconds and if nthere is a nanoseconds part the Scala duration will have a time
    * unit of nanoseconds.
    *
    * @throws IllegalArgumentException If the given Java Duration is out of bounds of what can be expressed with the
    *                                  Scala FiniteDuration.
    */
  final def toScala(duration: java.time.Duration): scala.concurrent.duration.FiniteDuration = {
    val originalSeconds = duration.getSeconds
    val originalNanos = duration.getNano
    if (originalNanos == 0) {
      if (originalSeconds == 0) ScalaDuration.Zero
      else FiniteDuration(originalSeconds, TimeUnit.SECONDS).toCoarsest.asInstanceOf[FiniteDuration]
    } else if (originalSeconds == 0) {
      FiniteDuration(originalNanos, TimeUnit.NANOSECONDS).toCoarsest.asInstanceOf[FiniteDuration]
    } else {
      try {
        val secondsAsNanos = Math.multiplyExact(originalSeconds, 1000000000)
        val totalNanos = secondsAsNanos + originalNanos
        if ((totalNanos < 0 && secondsAsNanos < 0) || (totalNanos > 0 && secondsAsNanos > 0))
          FiniteDuration(totalNanos, TimeUnit.NANOSECONDS).toCoarsest.asInstanceOf[FiniteDuration]
        else
          throw new ArithmeticException()
      } catch {
        case _: ArithmeticException =>
          throw new IllegalArgumentException(s"Java duration $duration cannot be expressed as a Scala duration")
      }
    }
  }

  /**
    * Transform a Scala FiniteDuration into a Java duration. Note that the Scala duration keeps the time unit it was created
    * with while a Java duration always is a pair of seconds and nanos, so the unit it lost.
    */
  final def toJava(duration: scala.concurrent.duration.FiniteDuration): java.time.Duration = {
    if (duration.length == 0) JavaDuration.ZERO
    else duration.unit match {
      case TimeUnit.NANOSECONDS => JavaDuration.ofNanos(duration.length)
      case TimeUnit.MICROSECONDS => JavaDuration.of(duration.length, ChronoUnit.MICROS)
      case TimeUnit.MILLISECONDS => JavaDuration.ofMillis(duration.length)
      case TimeUnit.SECONDS => JavaDuration.ofSeconds(duration.length)
      case TimeUnit.MINUTES => JavaDuration.ofMinutes(duration.length)
      case TimeUnit.HOURS => JavaDuration.ofHours(duration.length)
      case TimeUnit.DAYS => JavaDuration.ofDays(duration.length)
    }
  }

  implicit class Java2ScalaDurationConverter(val duration: java.time.Duration) extends AnyVal {
    def toScala: scala.concurrent.duration.FiniteDuration = DurationConverters.toScala(duration)
  }

  implicit class Scala2JavaDurationConverters(val duration: scala.concurrent.duration.FiniteDuration) extends AnyVal {
    def toJava: java.time.Duration = DurationConverters.toJava(duration)
  }
}
