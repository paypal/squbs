/*
 * Copyright (c) 2014 eBay, Inc.
 * All rights reserved.
 *
 * Contributors:
 * asucharitakul
 */
package org.squbs.unicomplex

import org.scalatest.{Matchers, FunSpecLike}
import org.squbs.unicomplex.UnicomplexBoot._

class UnicomplexBootSpec extends FunSpecLike with Matchers {

  describe ("The UnicomplexBootstrap") {

    it ("Should handle non-duplication in cube short names") {
      val initInfoList = Seq(
        InitInfo("don't care", "com.foo.foobar.bar", "bar", "1.0.0", Seq.empty, StartupType.ACTORS),
        InitInfo("don't care", "com.foo.baz.foo", "foo", "1.0.0", Seq.empty, StartupType.SERVICES),
        InitInfo("don't care", "com.foo.baz.foobar", "foobar", "1.0.0", Seq.empty, StartupType.ACTORS)
      )

      val newList = resolveAliasConflicts(initInfoList)
      newList should be theSameInstanceAs initInfoList

    }

    it ("Should handle duplication in cube short names") {
      val initInfoList = Seq(
        InitInfo("don't care", "com.foo.foobar.bar", "bar", "1.0.0", Seq.empty, StartupType.ACTORS),
        InitInfo("don't care", "com.foo.baz.bar", "bar", "1.0.0", Seq.empty, StartupType.SERVICES),
        InitInfo("don't care", "com.foo.bar.bar", "bar", "1.0.0", Seq.empty, StartupType.ACTORS)
      )
      val newList = resolveAliasConflicts(initInfoList)
      newList should not be theSameInstanceAs (initInfoList)
      val newAliases = newList map (_.alias)
      val refAliases = Seq("foobar.bar", "baz.bar", "bar.bar")
      newAliases should be (refAliases)
    }

    it ("Should handle some duplication in cube names") {
      val initInfoList = Seq(
        InitInfo("don't care", "com.bar.baz.bar", "bar", "1.0.0", Seq.empty, StartupType.ACTORS),
        InitInfo("don't care", "com.foo.baz.bar", "bar", "1.0.0", Seq.empty, StartupType.SERVICES),
        InitInfo("don't care", "com.foo.bar.bar", "bar", "1.0.0", Seq.empty, StartupType.ACTORS)
      )
      val newList = resolveAliasConflicts(initInfoList)
      newList should not be theSameInstanceAs (initInfoList)
      val newAliases = newList map (_.alias)
      val refAliases = Seq("bar.baz.bar", "foo.baz.bar", "bar.bar")
      newAliases should be (refAliases)
    }
  }
}
