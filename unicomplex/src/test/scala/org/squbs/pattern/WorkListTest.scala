/**
 * Copyright (c) 2013 eBay, Inc.
 * All rights reserved.
 *
 * Contributors:
 * asucharitakul
 */
package org.squbs.pattern

import org.scalatest.FunSuite

case class TestEntry(id: Int)

class WorkListTest extends FunSuite {

  val workList = WorkList.empty[TestEntry]
  var entry2: TestEntry = null
  var entry4: TestEntry = null

  test ("Processing empty WorkList") {
    // ProcessAndRemove something in the middle
    val processed = workList process {
      case TestEntry(9) => true
      case _ => false
    }
    assert(!processed)
  }

  test ("Insert temp entries") {
    assert(workList.head === null)
    assert(workList.tail === null)

    val entry0 = TestEntry(0)
    workList += entry0

    assert(workList.head != null)
    assert(workList.tail === workList.head)
    assert(workList.head.ref === entry0)

    val entry1 = TestEntry(1)
    workList += entry1

    assert(workList.head != workList.tail)
    assert(workList.head.ref === entry0)
    assert(workList.tail.ref === entry1)

    entry2 = TestEntry(2)
    workList += entry2

    assert(workList.tail.ref === entry2)

    val entry3 = TestEntry(3)
    workList += entry3

    assert(workList.tail.ref === entry3)
  }

  test ("Process temp entries") {

    // ProcessAndRemove something in the middle
    assert (workList process {
      case TestEntry(2) => true
      case _ => false
    })

    // ProcessAndRemove the head
    assert (workList process {
      case TestEntry(0) => true
      case _ => false
    })

    // ProcessAndRemove the tail
    assert (workList process {
      case TestEntry(3) => true
      case _ => false
    })
  }

  test ("Re-insert permanent entry") {
    implicit val permanent = true
    entry4 = TestEntry(4)
    workList += entry4

    assert(workList.tail.ref === entry4)
  }

  test ("Process permanent entry") {
    assert (workList process {
      case TestEntry(4) => true
      case _ => false
    })
  }

  test ("Remove permanent entry") {
    val removed = workList -= entry4
    assert(removed)
  }

  test ("Remove temp entry already processed") {
    val removed = workList -= entry2
    assert(!removed)
  }

  test ("Process non-matching entries") {

    val processed =
    workList process {
      case TestEntry(2) => true
      case _ => false
    }

    assert(!processed)

    val processed2 =
    workList process {
      case TestEntry(5) => true
      case _ => false
    }

    assert(!processed2)

  }
}
