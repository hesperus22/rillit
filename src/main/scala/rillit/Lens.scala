package rillit

import language.experimental.macros

case class Lens[A,B](
  get: A => B,
  set: (A, B) => A
) {
  def modify(a:A, f: B => B) : A = set(a, f(get(a)))

  def compose[C](that: Lens[C,A]) = Lens[C,B](
    c => get(that.get(c)),
    (c, b) => that.modify(c, set(_, b))
  )

  def andThen[C](that: Lens[B,C]) = that compose this
}

case class Inner(value: Int)
case class Outer(inner: Inner)

object Main {

  def main(args: Array[String]) {
    val outer = Outer(Inner(3))
    val mod1 = outer.copy(inner = outer.inner.copy(value = outer.inner.value + 1))

    val valueLens = Lens[Outer, Int](
      get = _.inner.value,
      set = (o, v) => o.copy(inner = o.inner.copy(value = v))
    )

    println(valueLens.get(outer))
    println(valueLens.set(outer, 4))

    val value = Lens[Inner, Int](
      get = _.value,
      set = (i, v) => i.copy(value = v)
    )
    val inner = Lens[Outer, Inner](
      get = _.inner,
      set = (o, i) => o.copy(inner = i)
    )

    val composedLens = inner andThen value
    println(composedLens.get(outer))
    println(composedLens.set(outer, 4))

  }

}

