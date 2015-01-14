/*
 * Licensed to Typesafe under one or more contributor license agreements.
 * See the AUTHORS file distributed with this work for
 * additional information regarding copyright ownership.
 * This file is licensed to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.squbs.pattern

import org.squbs.util.threadless.Future

object Orchestration {

  implicit class OrchestrationStructure[A](val f: Future[A]) extends AnyVal {
    def >>[T](fn: A=>Future[T]): Future[T] = {
      f flatMap fn
    }
  }

  implicit class OrchestrationStructure2[A, B](val ft: (Future[A], Future[B])) extends AnyVal {

    def >>[T](fn: (A, B)=>Future[T]): Future[T] = {
      ft._1 flatMap (a => ft._2 flatMap (b => fn(a, b)))
    }
  }

  implicit class OrchestrationStructure3[A, B, C](val ft: (Future[A], Future[B], Future[C])) extends AnyVal {
    def >>[T](fn: (A, B, C) => Future[T]): Future[T] = {
      ft._1 flatMap (a => ft._2 flatMap (b => ft._3 flatMap (c => fn(a, b, c))))
    }
  }

  implicit class OrchestrationStructure4[A, B, C, D](val ft: (Future[A], Future[B], Future[C], Future[D]))
      extends AnyVal {
    def >>[T](fn: (A, B, C, D) => Future[T]): Future[T] = {
      ft._1 flatMap (a => ft._2 flatMap (b => ft._3 flatMap (c => ft._4 flatMap(d => fn(a, b, c, d)))))
    }
  }

  implicit class OrchestrationStructure5[A, B, C, D, E](val ft: (Future[A], Future[B], Future[C], Future[D], Future[E]))
    extends AnyVal {
    def >>[T](fn: (A, B, C, D, E) => Future[T]): Future[T] = {
      ft._1 flatMap (a => ft._2 flatMap (b => ft._3 flatMap (c => ft._4 flatMap(d => ft._5 flatMap(
        e => fn(a, b, c, d, e))))))
    }
  }

  implicit class OrchestrationStructure6[A, B, C, D, E, F](val ft: (Future[A], Future[B], Future[C], Future[D],
      Future[E], Future[F])) extends AnyVal {
    def >>[T](fn: (A, B, C, D, E, F) => Future[T]): Future[T] = {
      ft._1 flatMap (a => ft._2 flatMap (b => ft._3 flatMap (c => ft._4 flatMap(d => ft._5 flatMap(e => ft._6 flatMap(
        f => fn(a, b, c, d, e, f)))))))
    }
  }

  implicit class OrchestrationStructure7[A, B, C, D, E, F, G](val ft: (Future[A], Future[B], Future[C], Future[D],
      Future[E], Future[F], Future[G])) extends AnyVal {
    def >>[T](fn: (A, B, C, D, E, F, G) => Future[T]): Future[T] = {
      ft._1 flatMap (a => ft._2 flatMap (b => ft._3 flatMap (c => ft._4 flatMap(d => ft._5 flatMap(e => ft._6 flatMap(
        f => ft._7 flatMap (g => fn(a, b, c, d, e, f, g))))))))
    }
  }

  implicit class OrchestrationStructure8[A, B, C, D, E, F, G, H](val ft: (Future[A], Future[B], Future[C], Future[D],
      Future[E], Future[F], Future[G], Future[H])) extends AnyVal {
    def >>[T](fn: (A, B, C, D, E, F, G, H) => Future[T]): Future[T] = {
      ft._1 flatMap (a => ft._2 flatMap (b => ft._3 flatMap (c => ft._4 flatMap(d => ft._5 flatMap(e => ft._6 flatMap(
        f => ft._7 flatMap (g => ft._8 flatMap (h => fn(a, b, c, d, e, f, g, h)))))))))
    }
  }

  implicit class OrchestrationStructure9[A, B, C, D, E, F, G, H, I](val ft: (Future[A], Future[B], Future[C], Future[D],
      Future[E], Future[F], Future[G], Future[H], Future[I])) extends AnyVal {
    def >>[T](fn: (A, B, C, D, E, F, G, H, I) => Future[T]): Future[T] = {
      ft._1 flatMap (a => ft._2 flatMap (b => ft._3 flatMap (c => ft._4 flatMap(d => ft._5 flatMap(e => ft._6 flatMap(
        f => ft._7 flatMap (g => ft._8 flatMap (h => ft._9 flatMap (i => fn(a, b, c, d, e, f, g, h, i))))))))))
    }
  }

  implicit class OrchestrationStructure10[A, B, C, D, E, F, G, H, I, J](val ft: (Future[A], Future[B], Future[C],
      Future[D], Future[E], Future[F], Future[G], Future[H], Future[I], Future[J])) extends AnyVal {
    def >>[T](fn: (A, B, C, D, E, F, G, H, I, J) => Future[T]): Future[T] = {
      ft._1 flatMap (a => ft._2 flatMap (b => ft._3 flatMap (c => ft._4 flatMap(d => ft._5 flatMap(e => ft._6 flatMap(
        f => ft._7 flatMap (g => ft._8 flatMap (h => ft._9 flatMap (i => ft._10 flatMap (j =>
        fn(a, b, c, d, e, f, g, h, i, j)))))))))))
    }
  }

  implicit class OrchestrationStructure11[A, B, C, D, E, F, G, H, I, J, K](val ft: (Future[A], Future[B], Future[C],
      Future[D], Future[E], Future[F], Future[G], Future[H], Future[I], Future[J], Future[K])) extends AnyVal {
    def >>[T](fn: (A, B, C, D, E, F, G, H, I, J, K) => Future[T]): Future[T] = {
      ft._1 flatMap (a => ft._2 flatMap (b => ft._3 flatMap (c => ft._4 flatMap(d => ft._5 flatMap(e => ft._6 flatMap(
        f => ft._7 flatMap (g => ft._8 flatMap (h => ft._9 flatMap (i => ft._10 flatMap (j => ft._11 flatMap (k =>
        fn(a, b, c, d, e, f, g, h, i, j, k))))))))))))
    }
  }

  implicit class OrchestrationStructure12[A, B, C, D, E, F, G, H, I, J, K, L](val ft: (Future[A], Future[B], Future[C],
      Future[D], Future[E], Future[F], Future[G], Future[H], Future[I], Future[J], Future[K], Future[L]))
      extends AnyVal {
    def >>[T](fn: (A, B, C, D, E, F, G, H, I, J, K, L) => Future[T]): Future[T] = {
      ft._1 flatMap (a => ft._2 flatMap (b => ft._3 flatMap (c => ft._4 flatMap(d => ft._5 flatMap(e => ft._6 flatMap(
        f => ft._7 flatMap (g => ft._8 flatMap (h => ft._9 flatMap (i => ft._10 flatMap (j => ft._11 flatMap (k =>
        ft._12 flatMap (l => fn(a, b, c, d, e, f, g, h, i, j, k, l)))))))))))))
    }
  }

  implicit class OrchestrationStructure13[A, B, C, D, E, F, G, H, I, J, K, L, M](val ft: (Future[A], Future[B],
      Future[C], Future[D], Future[E], Future[F], Future[G], Future[H], Future[I], Future[J], Future[K], Future[L],
      Future[M])) extends AnyVal {
    def >>[T](fn: (A, B, C, D, E, F, G, H, I, J, K, L, M) => Future[T]): Future[T] = {
      ft._1 flatMap (a => ft._2 flatMap (b => ft._3 flatMap (c => ft._4 flatMap(d => ft._5 flatMap(e => ft._6 flatMap(
        f => ft._7 flatMap (g => ft._8 flatMap (h => ft._9 flatMap (i => ft._10 flatMap (j => ft._11 flatMap (k =>
        ft._12 flatMap (l => ft._13 flatMap (m => fn(a, b, c, d, e, f, g, h, i, j, k, l, m))))))))))))))
    }
  }

  implicit class OrchestrationStructure14[A, B, C, D, E, F, G, H, I, J, K, L, M, N](val ft: (Future[A], Future[B],
      Future[C], Future[D], Future[E], Future[F], Future[G], Future[H], Future[I], Future[J], Future[K], Future[L],
      Future[M], Future[N])) extends AnyVal {
    def >>[T](fn: (A, B, C, D, E, F, G, H, I, J, K, L, M, N) => Future[T]): Future[T] = {
      ft._1 flatMap (a => ft._2 flatMap (b => ft._3 flatMap (c => ft._4 flatMap(d => ft._5 flatMap(e => ft._6 flatMap(
        f => ft._7 flatMap (g => ft._8 flatMap (h => ft._9 flatMap (i => ft._10 flatMap (j => ft._11 flatMap (k =>
        ft._12 flatMap (l => ft._13 flatMap (m => ft._14 flatMap (n =>
        fn(a, b, c, d, e, f, g, h, i, j, k, l, m, n)))))))))))))))
    }
  }

  implicit class OrchestrationStructure15[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O](val ft: (Future[A], Future[B],
      Future[C], Future[D], Future[E], Future[F], Future[G], Future[H], Future[I], Future[J], Future[K], Future[L],
      Future[M], Future[N], Future[O])) extends AnyVal {
    def >>[T](fn: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O) => Future[T]): Future[T] = {
      ft._1 flatMap (a => ft._2 flatMap (b => ft._3 flatMap (c => ft._4 flatMap(d => ft._5 flatMap(e => ft._6 flatMap(
        f => ft._7 flatMap (g => ft._8 flatMap (h => ft._9 flatMap (i => ft._10 flatMap (j => ft._11 flatMap (k =>
        ft._12 flatMap (l => ft._13 flatMap (m => ft._14 flatMap (n => ft._15 flatMap (o =>
        fn(a, b, c, d, e, f, g, h, i, j, k, l, m, n, o))))))))))))))))
    }
  }

  implicit class OrchestrationStructure16[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P](val ft: (Future[A], Future[B],
      Future[C], Future[D], Future[E], Future[F], Future[G], Future[H], Future[I], Future[J], Future[K], Future[L],
      Future[M], Future[N], Future[O], Future[P])) extends AnyVal {
    def >>[T](fn: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P) => Future[T]): Future[T] = {
      ft._1 flatMap (a => ft._2 flatMap (b => ft._3 flatMap (c => ft._4 flatMap(d => ft._5 flatMap(e => ft._6 flatMap(
        f => ft._7 flatMap (g => ft._8 flatMap (h => ft._9 flatMap (i => ft._10 flatMap (j => ft._11 flatMap (k =>
        ft._12 flatMap (l => ft._13 flatMap (m => ft._14 flatMap (n => ft._15 flatMap (o => ft._16 flatMap (p =>
        fn(a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p)))))))))))))))))
    }
  }

  implicit class OrchestrationStructure17[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q](val ft: (Future[A],
      Future[B], Future[C], Future[D], Future[E], Future[F], Future[G], Future[H], Future[I], Future[J], Future[K],
      Future[L], Future[M], Future[N], Future[O], Future[P], Future[Q])) extends AnyVal {
    def >>[T](fn: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q) => Future[T]): Future[T] = {
      ft._1 flatMap (a => ft._2 flatMap (b => ft._3 flatMap (c => ft._4 flatMap(d => ft._5 flatMap(e => ft._6 flatMap(
        f => ft._7 flatMap (g => ft._8 flatMap (h => ft._9 flatMap (i => ft._10 flatMap (j => ft._11 flatMap (k =>
        ft._12 flatMap (l => ft._13 flatMap (m => ft._14 flatMap (n => ft._15 flatMap (o => ft._16 flatMap (p =>
        ft._17 flatMap (q => fn(a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q))))))))))))))))))
    }
  }

  implicit class OrchestrationStructure18[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R](val ft: (Future[A],
      Future[B], Future[C], Future[D], Future[E], Future[F], Future[G], Future[H], Future[I], Future[J], Future[K],
      Future[L], Future[M], Future[N], Future[O], Future[P], Future[Q], Future[R])) extends AnyVal {
    def >>[T](fn: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R) => Future[T]): Future[T] = {
      ft._1 flatMap (a => ft._2 flatMap (b => ft._3 flatMap (c => ft._4 flatMap(d => ft._5 flatMap(e => ft._6 flatMap(
        f => ft._7 flatMap (g => ft._8 flatMap (h => ft._9 flatMap (i => ft._10 flatMap (j => ft._11 flatMap (k =>
        ft._12 flatMap (l => ft._13 flatMap (m => ft._14 flatMap (n => ft._15 flatMap (o => ft._16 flatMap (p =>
        ft._17 flatMap (q => ft._18 flatMap (r =>
        fn(a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q, r)))))))))))))))))))
    }
  }

  implicit class OrchestrationStructure19[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S](val ft: (Future[A],
      Future[B], Future[C], Future[D], Future[E], Future[F], Future[G], Future[H], Future[I], Future[J], Future[K],
      Future[L], Future[M], Future[N], Future[O], Future[P], Future[Q], Future[R], Future[S])) extends AnyVal {
    def >>[T](fn: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S) => Future[T]): Future[T] = {
      ft._1 flatMap (a => ft._2 flatMap (b => ft._3 flatMap (c => ft._4 flatMap(d => ft._5 flatMap(e => ft._6 flatMap(
        f => ft._7 flatMap (g => ft._8 flatMap (h => ft._9 flatMap (i => ft._10 flatMap (j => ft._11 flatMap (k =>
        ft._12 flatMap (l => ft._13 flatMap (m => ft._14 flatMap (n => ft._15 flatMap (o => ft._16 flatMap (p =>
        ft._17 flatMap (q => ft._18 flatMap (r => ft._19 flatMap (s =>
        fn(a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q, r, s))))))))))))))))))))
    }
  }

  implicit class OrchestrationStructure20[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, U](val ft: (
      Future[A], Future[B], Future[C], Future[D], Future[E], Future[F], Future[G], Future[H], Future[I], Future[J],
      Future[K], Future[L], Future[M], Future[N], Future[O], Future[P], Future[Q], Future[R], Future[S], Future[U]))
      extends AnyVal {
    def >>[T](fn: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, U) => Future[T]): Future[T] = {
      ft._1 flatMap (a => ft._2 flatMap (b => ft._3 flatMap (c => ft._4 flatMap(d => ft._5 flatMap(e => ft._6 flatMap(
        f => ft._7 flatMap (g => ft._8 flatMap (h => ft._9 flatMap (i => ft._10 flatMap (j => ft._11 flatMap (k =>
        ft._12 flatMap (l => ft._13 flatMap (m => ft._14 flatMap (n => ft._15 flatMap (o => ft._16 flatMap (p =>
        ft._17 flatMap (q => ft._18 flatMap (r => ft._19 flatMap (s => ft._20 flatMap (u =>
        fn(a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q, r, s, u)))))))))))))))))))))
    }
  }

  implicit class OrchestrationStructure21[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, U, V](val ft: (
      Future[A], Future[B], Future[C], Future[D], Future[E], Future[F], Future[G], Future[H], Future[I], Future[J],
      Future[K], Future[L], Future[M], Future[N], Future[O], Future[P], Future[Q], Future[R], Future[S], Future[U],
      Future[V])) extends AnyVal {
    def >>[T](fn: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, U, V) => Future[T]): Future[T] = {
      ft._1 flatMap (a => ft._2 flatMap (b => ft._3 flatMap (c => ft._4 flatMap(d => ft._5 flatMap(e => ft._6 flatMap(
        f => ft._7 flatMap (g => ft._8 flatMap (h => ft._9 flatMap (i => ft._10 flatMap (j => ft._11 flatMap (k =>
        ft._12 flatMap (l => ft._13 flatMap (m => ft._14 flatMap (n => ft._15 flatMap (o => ft._16 flatMap (p =>
        ft._17 flatMap (q => ft._18 flatMap (r => ft._19 flatMap (s => ft._20 flatMap (u => ft._21 flatMap (v =>
        fn(a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q, r, s, u, v))))))))))))))))))))))
    }
  }

  implicit class OrchestrationStructure22[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, U, V, W](val ft: (
      Future[A], Future[B], Future[C], Future[D], Future[E], Future[F], Future[G], Future[H], Future[I], Future[J],
      Future[K], Future[L], Future[M], Future[N], Future[O], Future[P], Future[Q], Future[R], Future[S], Future[U],
      Future[V], Future[W])) extends AnyVal {
    def >>[T](fn: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, U, V, W) => Future[T]): Future[T] = {
      ft._1 flatMap (a => ft._2 flatMap (b => ft._3 flatMap (c => ft._4 flatMap(d => ft._5 flatMap(e => ft._6 flatMap(
        f => ft._7 flatMap (g => ft._8 flatMap (h => ft._9 flatMap (i => ft._10 flatMap (j => ft._11 flatMap (k =>
        ft._12 flatMap (l => ft._13 flatMap (m => ft._14 flatMap (n => ft._15 flatMap (o => ft._16 flatMap (p =>
        ft._17 flatMap (q => ft._18 flatMap (r => ft._19 flatMap (s => ft._20 flatMap (u => ft._21 flatMap (v =>
        ft._22 flatMap (w => fn(a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q, r, s, u, v, w)))))))))))))))))))))))
    }
  }
}
