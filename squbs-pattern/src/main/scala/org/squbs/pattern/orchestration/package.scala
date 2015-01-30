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

package object orchestration {

  implicit class OrchestrationStructure[A](val f: OFuture[A]) extends AnyVal {
    def >>[T](fn: A=>OFuture[T]): OFuture[T] = {
      f flatMap fn
    }
  }

  implicit class OrchestrationStructure2[A, B](val ft: (OFuture[A], OFuture[B])) extends AnyVal {

    def >>[T](fn: (A, B)=>OFuture[T]): OFuture[T] = {
      ft._1 flatMap (a => ft._2 flatMap (b => fn(a, b)))
    }
  }

  implicit class OrchestrationStructure3[A, B, C](val ft: (OFuture[A], OFuture[B], OFuture[C])) extends AnyVal {
    def >>[T](fn: (A, B, C) => OFuture[T]): OFuture[T] = {
      ft._1 flatMap (a => ft._2 flatMap (b => ft._3 flatMap (c => fn(a, b, c))))
    }
  }

  implicit class OrchestrationStructure4[A, B, C, D](val ft: (OFuture[A], OFuture[B], OFuture[C], OFuture[D]))
    extends AnyVal {
    def >>[T](fn: (A, B, C, D) => OFuture[T]): OFuture[T] = {
      ft._1 flatMap (a => ft._2 flatMap (b => ft._3 flatMap (c => ft._4 flatMap(d => fn(a, b, c, d)))))
    }
  }

  implicit class OrchestrationStructure5[A, B, C, D, E](val ft: (OFuture[A], OFuture[B], OFuture[C], OFuture[D], OFuture[E]))
    extends AnyVal {
    def >>[T](fn: (A, B, C, D, E) => OFuture[T]): OFuture[T] = {
      ft._1 flatMap (a => ft._2 flatMap (b => ft._3 flatMap (c => ft._4 flatMap(d => ft._5 flatMap(
        e => fn(a, b, c, d, e))))))
    }
  }

  implicit class OrchestrationStructure6[A, B, C, D, E, F](val ft: (OFuture[A], OFuture[B], OFuture[C], OFuture[D],
    OFuture[E], OFuture[F])) extends AnyVal {
    def >>[T](fn: (A, B, C, D, E, F) => OFuture[T]): OFuture[T] = {
      ft._1 flatMap (a => ft._2 flatMap (b => ft._3 flatMap (c => ft._4 flatMap(d => ft._5 flatMap(e => ft._6 flatMap(
        f => fn(a, b, c, d, e, f)))))))
    }
  }

  implicit class OrchestrationStructure7[A, B, C, D, E, F, G](val ft: (OFuture[A], OFuture[B], OFuture[C], OFuture[D],
    OFuture[E], OFuture[F], OFuture[G])) extends AnyVal {
    def >>[T](fn: (A, B, C, D, E, F, G) => OFuture[T]): OFuture[T] = {
      ft._1 flatMap (a => ft._2 flatMap (b => ft._3 flatMap (c => ft._4 flatMap(d => ft._5 flatMap(e => ft._6 flatMap(
        f => ft._7 flatMap (g => fn(a, b, c, d, e, f, g))))))))
    }
  }

  implicit class OrchestrationStructure8[A, B, C, D, E, F, G, H](val ft: (OFuture[A], OFuture[B], OFuture[C], OFuture[D],
    OFuture[E], OFuture[F], OFuture[G], OFuture[H])) extends AnyVal {
    def >>[T](fn: (A, B, C, D, E, F, G, H) => OFuture[T]): OFuture[T] = {
      ft._1 flatMap (a => ft._2 flatMap (b => ft._3 flatMap (c => ft._4 flatMap(d => ft._5 flatMap(e => ft._6 flatMap(
        f => ft._7 flatMap (g => ft._8 flatMap (h => fn(a, b, c, d, e, f, g, h)))))))))
    }
  }

  implicit class OrchestrationStructure9[A, B, C, D, E, F, G, H, I](val ft: (OFuture[A], OFuture[B], OFuture[C], OFuture[D],
    OFuture[E], OFuture[F], OFuture[G], OFuture[H], OFuture[I])) extends AnyVal {
    def >>[T](fn: (A, B, C, D, E, F, G, H, I) => OFuture[T]): OFuture[T] = {
      ft._1 flatMap (a => ft._2 flatMap (b => ft._3 flatMap (c => ft._4 flatMap(d => ft._5 flatMap(e => ft._6 flatMap(
        f => ft._7 flatMap (g => ft._8 flatMap (h => ft._9 flatMap (i => fn(a, b, c, d, e, f, g, h, i))))))))))
    }
  }

  implicit class OrchestrationStructure10[A, B, C, D, E, F, G, H, I, J](val ft: (OFuture[A], OFuture[B], OFuture[C],
    OFuture[D], OFuture[E], OFuture[F], OFuture[G], OFuture[H], OFuture[I], OFuture[J])) extends AnyVal {
    def >>[T](fn: (A, B, C, D, E, F, G, H, I, J) => OFuture[T]): OFuture[T] = {
      ft._1 flatMap (a => ft._2 flatMap (b => ft._3 flatMap (c => ft._4 flatMap(d => ft._5 flatMap(e => ft._6 flatMap(
        f => ft._7 flatMap (g => ft._8 flatMap (h => ft._9 flatMap (i => ft._10 flatMap (j =>
          fn(a, b, c, d, e, f, g, h, i, j)))))))))))
    }
  }

  implicit class OrchestrationStructure11[A, B, C, D, E, F, G, H, I, J, K](val ft: (OFuture[A], OFuture[B], OFuture[C],
    OFuture[D], OFuture[E], OFuture[F], OFuture[G], OFuture[H], OFuture[I], OFuture[J], OFuture[K])) extends AnyVal {
    def >>[T](fn: (A, B, C, D, E, F, G, H, I, J, K) => OFuture[T]): OFuture[T] = {
      ft._1 flatMap (a => ft._2 flatMap (b => ft._3 flatMap (c => ft._4 flatMap(d => ft._5 flatMap(e => ft._6 flatMap(
        f => ft._7 flatMap (g => ft._8 flatMap (h => ft._9 flatMap (i => ft._10 flatMap (j => ft._11 flatMap (k =>
          fn(a, b, c, d, e, f, g, h, i, j, k))))))))))))
    }
  }

  implicit class OrchestrationStructure12[A, B, C, D, E, F, G, H, I, J, K, L](val ft: (OFuture[A], OFuture[B], OFuture[C],
    OFuture[D], OFuture[E], OFuture[F], OFuture[G], OFuture[H], OFuture[I], OFuture[J], OFuture[K], OFuture[L]))
    extends AnyVal {
    def >>[T](fn: (A, B, C, D, E, F, G, H, I, J, K, L) => OFuture[T]): OFuture[T] = {
      ft._1 flatMap (a => ft._2 flatMap (b => ft._3 flatMap (c => ft._4 flatMap(d => ft._5 flatMap(e => ft._6 flatMap(
        f => ft._7 flatMap (g => ft._8 flatMap (h => ft._9 flatMap (i => ft._10 flatMap (j => ft._11 flatMap (k =>
          ft._12 flatMap (l => fn(a, b, c, d, e, f, g, h, i, j, k, l)))))))))))))
    }
  }

  implicit class OrchestrationStructure13[A, B, C, D, E, F, G, H, I, J, K, L, M](val ft: (OFuture[A], OFuture[B],
    OFuture[C], OFuture[D], OFuture[E], OFuture[F], OFuture[G], OFuture[H], OFuture[I], OFuture[J], OFuture[K], OFuture[L],
    OFuture[M])) extends AnyVal {
    def >>[T](fn: (A, B, C, D, E, F, G, H, I, J, K, L, M) => OFuture[T]): OFuture[T] = {
      ft._1 flatMap (a => ft._2 flatMap (b => ft._3 flatMap (c => ft._4 flatMap(d => ft._5 flatMap(e => ft._6 flatMap(
        f => ft._7 flatMap (g => ft._8 flatMap (h => ft._9 flatMap (i => ft._10 flatMap (j => ft._11 flatMap (k =>
          ft._12 flatMap (l => ft._13 flatMap (m => fn(a, b, c, d, e, f, g, h, i, j, k, l, m))))))))))))))
    }
  }

  implicit class OrchestrationStructure14[A, B, C, D, E, F, G, H, I, J, K, L, M, N](val ft: (OFuture[A], OFuture[B],
    OFuture[C], OFuture[D], OFuture[E], OFuture[F], OFuture[G], OFuture[H], OFuture[I], OFuture[J], OFuture[K], OFuture[L],
    OFuture[M], OFuture[N])) extends AnyVal {
    def >>[T](fn: (A, B, C, D, E, F, G, H, I, J, K, L, M, N) => OFuture[T]): OFuture[T] = {
      ft._1 flatMap (a => ft._2 flatMap (b => ft._3 flatMap (c => ft._4 flatMap(d => ft._5 flatMap(e => ft._6 flatMap(
        f => ft._7 flatMap (g => ft._8 flatMap (h => ft._9 flatMap (i => ft._10 flatMap (j => ft._11 flatMap (k =>
          ft._12 flatMap (l => ft._13 flatMap (m => ft._14 flatMap (n =>
            fn(a, b, c, d, e, f, g, h, i, j, k, l, m, n)))))))))))))))
    }
  }

  implicit class OrchestrationStructure15[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O](val ft: (OFuture[A], OFuture[B],
    OFuture[C], OFuture[D], OFuture[E], OFuture[F], OFuture[G], OFuture[H], OFuture[I], OFuture[J], OFuture[K], OFuture[L],
    OFuture[M], OFuture[N], OFuture[O])) extends AnyVal {
    def >>[T](fn: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O) => OFuture[T]): OFuture[T] = {
      ft._1 flatMap (a => ft._2 flatMap (b => ft._3 flatMap (c => ft._4 flatMap(d => ft._5 flatMap(e => ft._6 flatMap(
        f => ft._7 flatMap (g => ft._8 flatMap (h => ft._9 flatMap (i => ft._10 flatMap (j => ft._11 flatMap (k =>
          ft._12 flatMap (l => ft._13 flatMap (m => ft._14 flatMap (n => ft._15 flatMap (o =>
            fn(a, b, c, d, e, f, g, h, i, j, k, l, m, n, o))))))))))))))))
    }
  }

  implicit class OrchestrationStructure16[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P](val ft: (OFuture[A], OFuture[B],
    OFuture[C], OFuture[D], OFuture[E], OFuture[F], OFuture[G], OFuture[H], OFuture[I], OFuture[J], OFuture[K], OFuture[L],
    OFuture[M], OFuture[N], OFuture[O], OFuture[P])) extends AnyVal {
    def >>[T](fn: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P) => OFuture[T]): OFuture[T] = {
      ft._1 flatMap (a => ft._2 flatMap (b => ft._3 flatMap (c => ft._4 flatMap(d => ft._5 flatMap(e => ft._6 flatMap(
        f => ft._7 flatMap (g => ft._8 flatMap (h => ft._9 flatMap (i => ft._10 flatMap (j => ft._11 flatMap (k =>
          ft._12 flatMap (l => ft._13 flatMap (m => ft._14 flatMap (n => ft._15 flatMap (o => ft._16 flatMap (p =>
            fn(a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p)))))))))))))))))
    }
  }

  implicit class OrchestrationStructure17[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q](val ft: (OFuture[A],
    OFuture[B], OFuture[C], OFuture[D], OFuture[E], OFuture[F], OFuture[G], OFuture[H], OFuture[I], OFuture[J], OFuture[K],
    OFuture[L], OFuture[M], OFuture[N], OFuture[O], OFuture[P], OFuture[Q])) extends AnyVal {
    def >>[T](fn: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q) => OFuture[T]): OFuture[T] = {
      ft._1 flatMap (a => ft._2 flatMap (b => ft._3 flatMap (c => ft._4 flatMap(d => ft._5 flatMap(e => ft._6 flatMap(
        f => ft._7 flatMap (g => ft._8 flatMap (h => ft._9 flatMap (i => ft._10 flatMap (j => ft._11 flatMap (k =>
          ft._12 flatMap (l => ft._13 flatMap (m => ft._14 flatMap (n => ft._15 flatMap (o => ft._16 flatMap (p =>
            ft._17 flatMap (q => fn(a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q))))))))))))))))))
    }
  }

  implicit class OrchestrationStructure18[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R](val ft: (OFuture[A],
    OFuture[B], OFuture[C], OFuture[D], OFuture[E], OFuture[F], OFuture[G], OFuture[H], OFuture[I], OFuture[J], OFuture[K],
    OFuture[L], OFuture[M], OFuture[N], OFuture[O], OFuture[P], OFuture[Q], OFuture[R])) extends AnyVal {
    def >>[T](fn: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R) => OFuture[T]): OFuture[T] = {
      ft._1 flatMap (a => ft._2 flatMap (b => ft._3 flatMap (c => ft._4 flatMap(d => ft._5 flatMap(e => ft._6 flatMap(
        f => ft._7 flatMap (g => ft._8 flatMap (h => ft._9 flatMap (i => ft._10 flatMap (j => ft._11 flatMap (k =>
          ft._12 flatMap (l => ft._13 flatMap (m => ft._14 flatMap (n => ft._15 flatMap (o => ft._16 flatMap (p =>
            ft._17 flatMap (q => ft._18 flatMap (r =>
              fn(a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q, r)))))))))))))))))))
    }
  }

  implicit class OrchestrationStructure19[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S](val ft: (OFuture[A],
    OFuture[B], OFuture[C], OFuture[D], OFuture[E], OFuture[F], OFuture[G], OFuture[H], OFuture[I], OFuture[J], OFuture[K],
    OFuture[L], OFuture[M], OFuture[N], OFuture[O], OFuture[P], OFuture[Q], OFuture[R], OFuture[S])) extends AnyVal {
    def >>[T](fn: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S) => OFuture[T]): OFuture[T] = {
      ft._1 flatMap (a => ft._2 flatMap (b => ft._3 flatMap (c => ft._4 flatMap(d => ft._5 flatMap(e => ft._6 flatMap(
        f => ft._7 flatMap (g => ft._8 flatMap (h => ft._9 flatMap (i => ft._10 flatMap (j => ft._11 flatMap (k =>
          ft._12 flatMap (l => ft._13 flatMap (m => ft._14 flatMap (n => ft._15 flatMap (o => ft._16 flatMap (p =>
            ft._17 flatMap (q => ft._18 flatMap (r => ft._19 flatMap (s =>
              fn(a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q, r, s))))))))))))))))))))
    }
  }

  implicit class OrchestrationStructure20[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, U](val ft: (
    OFuture[A], OFuture[B], OFuture[C], OFuture[D], OFuture[E], OFuture[F], OFuture[G], OFuture[H], OFuture[I], OFuture[J],
      OFuture[K], OFuture[L], OFuture[M], OFuture[N], OFuture[O], OFuture[P], OFuture[Q], OFuture[R], OFuture[S], OFuture[U]))
    extends AnyVal {
    def >>[T](fn: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, U) => OFuture[T]): OFuture[T] = {
      ft._1 flatMap (a => ft._2 flatMap (b => ft._3 flatMap (c => ft._4 flatMap(d => ft._5 flatMap(e => ft._6 flatMap(
        f => ft._7 flatMap (g => ft._8 flatMap (h => ft._9 flatMap (i => ft._10 flatMap (j => ft._11 flatMap (k =>
          ft._12 flatMap (l => ft._13 flatMap (m => ft._14 flatMap (n => ft._15 flatMap (o => ft._16 flatMap (p =>
            ft._17 flatMap (q => ft._18 flatMap (r => ft._19 flatMap (s => ft._20 flatMap (u =>
              fn(a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q, r, s, u)))))))))))))))))))))
    }
  }

  implicit class OrchestrationStructure21[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, U, V](val ft: (
    OFuture[A], OFuture[B], OFuture[C], OFuture[D], OFuture[E], OFuture[F], OFuture[G], OFuture[H], OFuture[I], OFuture[J],
      OFuture[K], OFuture[L], OFuture[M], OFuture[N], OFuture[O], OFuture[P], OFuture[Q], OFuture[R], OFuture[S], OFuture[U],
      OFuture[V])) extends AnyVal {
    def >>[T](fn: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, U, V) => OFuture[T]): OFuture[T] = {
      ft._1 flatMap (a => ft._2 flatMap (b => ft._3 flatMap (c => ft._4 flatMap(d => ft._5 flatMap(e => ft._6 flatMap(
        f => ft._7 flatMap (g => ft._8 flatMap (h => ft._9 flatMap (i => ft._10 flatMap (j => ft._11 flatMap (k =>
          ft._12 flatMap (l => ft._13 flatMap (m => ft._14 flatMap (n => ft._15 flatMap (o => ft._16 flatMap (p =>
            ft._17 flatMap (q => ft._18 flatMap (r => ft._19 flatMap (s => ft._20 flatMap (u => ft._21 flatMap (v =>
              fn(a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q, r, s, u, v))))))))))))))))))))))
    }
  }

  implicit class OrchestrationStructure22[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, U, V, W](val ft: (
    OFuture[A], OFuture[B], OFuture[C], OFuture[D], OFuture[E], OFuture[F], OFuture[G], OFuture[H], OFuture[I], OFuture[J],
      OFuture[K], OFuture[L], OFuture[M], OFuture[N], OFuture[O], OFuture[P], OFuture[Q], OFuture[R], OFuture[S], OFuture[U],
      OFuture[V], OFuture[W])) extends AnyVal {
    def >>[T](fn: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, U, V, W) => OFuture[T]): OFuture[T] = {
      ft._1 flatMap (a => ft._2 flatMap (b => ft._3 flatMap (c => ft._4 flatMap(d => ft._5 flatMap(e => ft._6 flatMap(
        f => ft._7 flatMap (g => ft._8 flatMap (h => ft._9 flatMap (i => ft._10 flatMap (j => ft._11 flatMap (k =>
          ft._12 flatMap (l => ft._13 flatMap (m => ft._14 flatMap (n => ft._15 flatMap (o => ft._16 flatMap (p =>
            ft._17 flatMap (q => ft._18 flatMap (r => ft._19 flatMap (s => ft._20 flatMap (u => ft._21 flatMap (v =>
              ft._22 flatMap (w => fn(a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q, r, s, u, v, w)))))))))))))))))))))))
    }
  }
}
