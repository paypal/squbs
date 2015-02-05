import scala.annotation.tailrec
import scala.collection.immutable.Stream.Empty

@tailrec
def process[T, A](s: Stream[T], g: T=>A, d: Stream[A] = Empty): Stream[A] = {
  s match {
    case Empty => d
    case head #:: tail => process(tail, g, d :+ g(head))
  }
}

val s = Stream(1, 2, 3)
def g(i: Int) = i.toString
process(s, {i: Int => i.toString}) foreach println

val l = List(1, 2, 3)
l.length