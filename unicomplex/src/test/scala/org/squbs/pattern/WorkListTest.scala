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

  test ("Processing empty WorkList") {
    // ProcessAndRemove something in the middle
    val processed = workList processAndRemove {
      case TestEntry(9) => true
      case _ => false
    }
    assert(!processed)
  }

  test ("Insert entries") {
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

    val entry2 = TestEntry(2)
    workList += entry2

    assert(workList.tail.ref === entry2)

    val entry3 = TestEntry(3)
    workList += entry3

    assert(workList.tail.ref === entry3)
  }

  test ("Process entries") {

    // ProcessAndRemove something in the middle
    assert (workList processAndRemove {
      case TestEntry(2) => true
      case _ => false
    })

    // ProcessAndRemove the head
    assert (workList processAndRemove {
      case TestEntry(0) => true
      case _ => false
    })

    // ProcessAndRemove the tail
    assert (workList processAndRemove {
      case TestEntry(3) => true
      case _ => false
    })

  }

  test ("Re-insert entries") {
    val entry4 = TestEntry(4)
    workList += entry4

    assert(workList.tail.ref === entry4)
  }

  test ("Process non-matching entries") {

    val processed =
    workList processAndRemove {
      case TestEntry(2) => true
      case _ => false
    }

    assert(!processed)

    val processed2 =
    workList processAndRemove {
      case TestEntry(5) => true
      case _ => false
    }

    assert(!processed2)

  }
}
