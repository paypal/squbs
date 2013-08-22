/*
 * Copyright (c) 2013 eBay, Inc.
 * All rights reserved.
 *
 * Contributors:
 * asucharitakul
 */
package org.squbs.pattern

import scala.collection._
import scala.concurrent.duration._
import scala.math.BigDecimal.int2bigDecimal

import akka.actor._

import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest.FunSuite
import org.scalatest.matchers.ShouldMatchers


/**
 * Sample and test code for the aggregator patter. This is based on Jamie Allen's tutorial at
 * http://jaxenter.com/tutorial-asynchronous-programming-with-akka-actors-46220.html
 */

object AccountType extends Enumeration {
  type AccountType = Value
  val CHECKING, SAVINGS, MONEY_MARKET = Value
}

case class GetCustomerAccountBalances(id: Long, accountTypes: Set[AccountType.Value])
case class GetAccountBalances(id: Long)

case class AccountBalances(accountType: AccountType.Value, balance: Option[List[(Long, BigDecimal)]])

case class CheckingAccountBalances(balances: Option[List[(Long, BigDecimal)]])
case class SavingsAccountBalances(balances: Option[List[(Long, BigDecimal)]])
case class MoneyMarketAccountBalances(balances: Option[List[(Long, BigDecimal)]])

case object TimedOut
case object CantUnderstand

class SavingsAccountProxy extends Actor {
  def receive = {
    case GetAccountBalances(id: Long) =>
      sender ! SavingsAccountBalances(Some(List((1, 150000), (2, 29000))))
  }
}
class CheckingAccountProxy extends Actor {
  def receive = {
    case GetAccountBalances(id: Long) =>
      sender ! CheckingAccountBalances(Some(List((3, 15000))))
  }
}
class MoneyMarketAccountProxy extends Actor {
  def receive = {
    case GetAccountBalances(id: Long) =>
      sender ! MoneyMarketAccountBalances(None)
  }
}

class AccountBalanceRetriever extends Actor with Aggregator {

  import AccountType._
  import context._

  expectOnce {
    case GetCustomerAccountBalances(id, types) =>
      new AccountAggregator(sender, id, types)
    case _ =>
      sender ! CantUnderstand
      context.stop(self)
  }

  class AccountAggregator(originalSender: ActorRef, id: Long, types: Set[AccountType.Value]) {

    val results = mutable.ArrayBuffer.empty[(AccountType.Value, Option[List[(Long, BigDecimal)]])]

    if (types.size > 0)
      types foreach {
        case CHECKING => fetchCheckingAccountsBalance()
        case SAVINGS => fetchSavingsAccountsBalance()
        case MONEY_MARKET => fetchMoneyMarketAccountsBalance()
      }
    else collectBalances() // Empty type list yields empty response

    context.system.scheduler.scheduleOnce(250 milliseconds) {
      self ! TimedOut
    }

    expect {
      case TimedOut => collectBalances(force = true)
    }

    def fetchCheckingAccountsBalance() {
      context.actorOf(Props[CheckingAccountProxy]) ! GetAccountBalances(id)
      expectOnce {
        case CheckingAccountBalances(balances) =>
          results += (CHECKING -> balances)
          collectBalances()
      }
    }

    def fetchSavingsAccountsBalance() {
      context.actorOf(Props[SavingsAccountProxy]) ! GetAccountBalances(id)
      expectOnce {
        case SavingsAccountBalances(balances) =>
          results += (SAVINGS -> balances)
          collectBalances()
      }
    }

    def fetchMoneyMarketAccountsBalance() {
      context.actorOf(Props[MoneyMarketAccountProxy]) ! GetAccountBalances(id)
      expectOnce {
        case MoneyMarketAccountBalances(balances) =>
          results += (MONEY_MARKET -> balances)
          collectBalances()
      }
    }

    def collectBalances(force: Boolean = false) {
      if (results.size == types.size || force) {
        originalSender ! results.toList // Make sure it becomes immutable
        context.stop(self)
      }
    }
  }
}

class AggregatorTest extends TestKit(ActorSystem("test")) with ImplicitSender with FunSuite with ShouldMatchers {

  import AccountType._

  test ("Test request 1 account type") {
    system.actorOf(Props[AccountBalanceRetriever]) ! GetCustomerAccountBalances(1, Set(SAVINGS))
    receiveOne(10 seconds) match {
      case result: List[_] =>
        result should have size 1
      case result =>
        assert(condition = false, s"Expect List, got ${result.getClass}")
    }
  }

  test ("Test request 3 account types") {
    system.actorOf(Props[AccountBalanceRetriever]) !
      GetCustomerAccountBalances(1, Set(CHECKING, SAVINGS, MONEY_MARKET))
    receiveOne(10 seconds) match {
      case result: List[_] =>
        result should have size 3
      case result =>
        assert(condition = false, s"Expect List, got ${result.getClass}")
    }
  }
}