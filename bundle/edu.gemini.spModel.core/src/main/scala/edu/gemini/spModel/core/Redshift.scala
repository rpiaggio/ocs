package edu.gemini.spModel.core

import scalaz.{Monoid, Order}

/**
 * Specification of Radial velocity
 * @param redshift dimensionless measurement of redshift
 */
case class Redshift(redshift: Double)

object Redshift {
  /**
   * The `No redshift`
   * @group Constructors
   */
  val zero: Redshift = Redshift(0)

  /** @group Typeclass Instances */
  implicit val order: Order[Redshift] =
    Order.orderBy(_.redshift)

  /** @group Typeclass Instances */
  implicit val ordering: scala.Ordering[Redshift] =
    scala.Ordering.by(_.redshift)

  /**
   * Additive monoid for `Redshift`
   * @group Typeclass Instances
   */
  implicit val monoid: Monoid[Redshift] =
    new Monoid[Redshift] {
      val zero = Redshift.zero
      def append(a: Redshift, b: => Redshift): Redshift = Redshift(a.redshift + b.redshift)
    }

}