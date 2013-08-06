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
   * Singly linked list entry implementation for WorkList.
   * @param ref The item reference
   * @param permanent If the entry is to be kept after processing
   * @tparam T The type of the item
   */
  class Entry[T](val ref: T, val permanent: Boolean) {
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
  def +=(ref: T)(implicit permanent: Boolean = false) = {
    if (tail == null) {
      tail = new Entry[T](ref, permanent)
      head = tail
    } else {
      tail.next = new Entry[T](ref, permanent)
      tail = tail.next
    }
    this
  }

  def -=(ref: T): Boolean = {

    @tailrec
    def remove(parent: Entry[T], entry: Entry[T]): Boolean = {
      if (entry.ref == ref) {
        parent.next = entry.next // Remove entry
        if (tail == entry) tail = parent
        true
      }
      else if (entry.next != null) remove(entry, entry.next)
      else false
    }

    // Outer function body...
    val entry = head
    if (entry == null) false
    else if (entry.ref == ref) {
      head = entry.next // Remove entry at head
      if (tail == entry) tail = head
      true
    }
    else if (entry.next != null) remove(entry, entry.next)
    else false
  }

  /**
   * Tries to process each entry using the processing function. Stops at the first entry processing succeeds.
   * If the entry is not permanent, the entry is removed.
   * @param processFn The processing function, returns true if processing succeeds.
   * @return true if an entry has been processed, false if no entries are processed successfully.
   */
  def process(processFn: T => Boolean): Boolean = {

    @tailrec
    def process(parent: Entry[T], entry: Entry[T]): Boolean = {
      val processed = processFn(entry.ref)
      if (processed) {
        if (!entry.permanent) {
          parent.next = entry.next // Remove entry
          if (tail == entry) tail = parent
        }
        true // Handled
      }
      else if (entry.next != null) process(entry, entry.next)
      else false
    }

    // Outer function body...
    val entry = head
    if (entry == null) false
    else {
      val processed = processFn(entry.ref)
      if (processed) {
        if (!entry.permanent) {
          head = entry.next // Remove entry at head
          if (tail == entry) tail = head
        }
        true // Handled
      }
      else if (entry.next != null) process(entry, entry.next)
      else false
    }
  }
}