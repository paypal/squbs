/**
 * Copyright (c) 2013 eBay, Inc.
 * All rights reserved.
 *
 * Contributors:
 * asucharitakul
 */
package org.squbs.pattern

import scala.annotation.tailrec

/**
 * Provides the utility methods and constructors to the WorkList class.
 */
object WorkList {

  def empty[T] = new WorkList[T]

  /**
   * Singly linked list entry implementation.
   * @param ref The item reference
   * @tparam T The type of the item
   */
  class Entry[T](val ref: T) {
    var next: Entry[T] = null
  }
}

/**
 * Fast, small, and dirty implementation of a linked list that removes work entries once they are processed.
 * @tparam T The type of the item
 */
class WorkList[T] {

  import WorkList._

  var head: Entry[T] = null
  var tail: Entry[T] = null

  /**
   * Appends an entry to the work list.
   * @param ref The entry.
   * @return The updated work list.
   */
  def +=(ref: T) = {
    if (tail == null) {
      tail = new Entry[T](ref)
      head = tail
    } else {
      tail.next = new Entry[T](ref)
      tail = tail.next
    }
    this
  }

  /**
   * Tries to process each entry using the processing function. Stops and removes the
   * first entry processing succeeds.
   * @param processFn The processing function, returns true if processing succeeds.
   * @return true if an entry has been processed, false if no entries are processed successfully.
   */
  def processAndRemove(processFn: T => Boolean): Boolean = {
    val entry = head
    if (entry == null) false
    else {
      val processed = processFn(entry.ref)
      if (processed) {
        head = entry.next // Remove entry at head
        if (tail == entry) tail = head
        true // Handled
      }
      else if (entry.next != null) processAndRemove(entry, entry.next, processFn)
      else false
    }
  }

  @tailrec
  private def processAndRemove(parent: Entry[T], entry: Entry[T], processFn: T => Boolean): Boolean = {
    val processed = processFn(entry.ref)
    if (processed) {
      parent.next = entry.next // Remove entry
      if (tail == entry) tail = parent
      true // Handled
    }
    else if (entry.next != null) processAndRemove(entry, entry.next, processFn)
    else false
  }
}