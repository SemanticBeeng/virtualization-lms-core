package scala.virtualization.lms
package epfl
package test7

import common._
import internal._
import original._
import original.Conversions._
import collection.mutable.HashSet


trait MDArrayTypingPrimitives {
  var unknownIndex = 0

  // Just to make the code look better :)
  val preReq: Boolean = true
  val postReq: Boolean = false

  // Not yet specific
  type Symbol
  type Expression
  def getId(s: Symbol): Int

  abstract class TypingVariable
  abstract class Var(name: String) extends TypingVariable { override def toString = name }
  case class ShapeVar(s: Symbol) extends Var("S" + getId(s))
  case class ValueVar(s: Symbol) extends Var("V" + getId(s))
  object ShapeVar { def apply(e: Expression)(implicit manifest: Manifest[Int]) = new ShapeVar(e.asInstanceOf[Symbol]) }
  object ValueVar { def apply(e: Expression)(implicit manifest: Manifest[Int]) = new ValueVar(e.asInstanceOf[Symbol]) }
  case class Lst(list: List[TypingElement]) extends TypingVariable { override def toString = list.map(elt => elt.toString).mkString("[", "  ", "]") }

  abstract class TypingElement
  case class Value(n: Int) extends TypingElement { override def toString = n.toString }
  case class Unknown(i: Int) extends TypingElement { override def toString = "U" + i.toString }
  case class LengthOf(v: Var) extends TypingElement { override def toString = "dim(" + v.toString + ")" }
  case class LessThan(i: Int, t: TypingElement) extends TypingElement { override def toString = "U" + i.toString + "(<" + t.toString + ")" }
  def getNewUnknown(): Unknown = Unknown({ unknownIndex += 1; unknownIndex })
  def getNewLessThan(t: TypingElement): LessThan = LessThan({ unknownIndex += 1; unknownIndex }, t)

  abstract class TypingConstraint(val prereq: Boolean, val node: Any) { override def toString = getConstraintString(this) }
  case class Equality(a: TypingVariable, b: TypingVariable, _prereq: Boolean, _node: Any) extends TypingConstraint(_prereq, _node)
  case class EqualityOrScalar(a: TypingVariable, b: TypingVariable, _prereq: Boolean, _node: Any) extends TypingConstraint(_prereq, _node)
  case class EqualityExceptFor(d: TypingVariable, a: TypingVariable, b: TypingVariable, _prereq: Boolean, _node: Any) extends TypingConstraint(_prereq, _node)
  case class LessThanConstraint(a: TypingVariable, b: TypingVariable, _prereq: Boolean, _node: Any) extends TypingConstraint(_prereq, _node)
  case class PrefixLt(main: TypingVariable, prefix: TypingVariable, suffix: TypingVariable, _prereq: Boolean, _node: Any) extends TypingConstraint(_prereq, _node)
  case class SuffixEq(main: TypingVariable, prefix: TypingVariable, suffix: TypingVariable, _prereq: Boolean, _node: Any) extends TypingConstraint(_prereq, _node)
  case class EqualityAeqBcatC(a: TypingVariable, b: TypingVariable, c: TypingVariable, _prereq: Boolean, _node: Any) extends TypingConstraint(_prereq, _node)
  case class LengthEqualityAeqB(a: TypingVariable, b: TypingVariable, _prereq: Boolean, _node: Any) extends TypingConstraint(_prereq, _node)
  case class CommonDenominator(a: TypingVariable, b: TypingVariable, c: TypingVariable, _prereq: Boolean, _node: Any) extends TypingConstraint(_prereq, _node)
  case class EqualProduct(a: TypingVariable, b: TypingVariable, _prereq: Boolean, _node: Any) extends TypingConstraint(_prereq, _node)
  case class ReconstructValueFromShape(value: TypingVariable, shape: TypingVariable, _prereq: Boolean, _node:Any) extends TypingConstraint(_prereq, _node)
  case class EqualityAeqDimTimesValue(a: TypingVariable, dim: TypingVariable, value: TypingVariable, _prereq: Boolean, _node:Any) extends TypingConstraint(_prereq, _node)
  case class EqualityShAeqShBplusShCalongD(a: TypingVariable, b: TypingVariable, c: TypingVariable, d: TypingVariable, _prereq: Boolean, _node:Any) extends TypingConstraint(_prereq, _node)

  private def getConstraintString(constraint: TypingConstraint): String = {
    val body = constraint match {
      case eq: Equality => eq.a.toString + " = " + eq.b.toString
      // TODO: Can we encode OR without hard coding it into a constraint?
      case es: EqualityOrScalar => es.a.toString + " = " + es.b.toString + " OR " + es.b.toString + " = []"
      case eq: EqualityExceptFor => eq.a.toString + "[i] = " + eq.b.toString + "[i] forall i != " + eq.d.toString
      case lt: LessThanConstraint => lt.a.toString + " < " + lt.b.toString
      case pl: PrefixLt => pl.main.toString + "(:length(" + pl.prefix.toString + ")) < " + pl.prefix.toString
      case se: SuffixEq => se.main.toString + "(length(" + se.prefix.toString + "):) = " + se.suffix.toString
      case eq: EqualityAeqBcatC => eq.a.toString + " = " + eq.b.toString + " ::: " + eq.c.toString
      case le: LengthEqualityAeqB => "length(" + le.a.toString + ") = length(" + le.b.toString + ")"
      case cd: CommonDenominator => cd.a.toString + " = common(" + cd.b.toString + ", " + cd.c.toString + ")"
      case ep: EqualProduct => "prod(" + ep.a.toString + ") = prod(" + ep.b.toString + ")"
      case rv: ReconstructValueFromShape => "reconstruct " + rv.value + " from shape " + rv.shape
      case eq: EqualityAeqDimTimesValue => eq.a.toString + " = " + eq.dim.toString + " x [" + eq.value.toString + "]"
      case eq: EqualityShAeqShBplusShCalongD => eq.a.toString + " = " + eq.b.toString + " <cat> " + eq.c.toString + " along dimension " + eq.d.toString
      case _ => "unknown constraint"
    }

    if (constraint.prereq)
      "PRE:    " + String.format("%-50s", body) + "     from " + constraint.node
    else
      "POST:   " + String.format("%-50s", body) + "     from " + constraint.node
  }

  protected def getSymbols(element: Any): List[Symbol] = element match {
    case ShapeVar(sym) => sym::Nil
    case ValueVar(sym) => sym::Nil
    case LengthOf(v) => getSymbols(v)
    case p: Product => p.productIterator.toList.flatMap(getSymbols(_))
    case _ => Nil
  }

  protected def makeUnknowns(size: Int): Lst = {
    var list:List[TypingElement] = Nil
    List.range(0, size).map(i => list = getNewUnknown :: list)
    Lst(list)
  }

  protected def makeLessThan(l: Lst): Lst =
    Lst(l.list.map(getNewLessThan(_)))

  protected def countUnknowns(l: Lst): Int = {
    var unks = 0
    l.list.map(elt => elt match { case v:Value => ; case _ => unks = unks + 1 })
    unks
  }

  abstract class Substitution(name: String) extends (TypingConstraint => TypingConstraint) {
    override def toString = name

    // we only have to override these functions to have a real substitution
    def updateVar(v: Var): TypingVariable
    def updateUnknown(uk: Unknown): TypingElement
    def updateLength(lt: LengthOf): TypingElement
    def updateLessThan(lt: LessThan): TypingElement

    // the rest is already done :)
    def updateElement(elt: TypingElement): TypingElement = elt match {
      case value: Value => value
      case unknown: Unknown => updateUnknown(unknown)
      case length: LengthOf => updateLength(length)
      case lessThan: LessThan => updateLessThan(lessThan)
    }
    def updateVariable(tv: TypingVariable): TypingVariable = tv match {
      case v: Var => updateVar(v)
      case l: Lst => Lst(l.list.map(elt => updateElement(elt)))
    }
    def apply(tcs: List[TypingConstraint]): List[TypingConstraint] = tcs.map(tc => apply(tc))
    def apply(tc: TypingConstraint): TypingConstraint = tc match {
      case eq: Equality => Equality(updateVariable(eq.a), updateVariable(eq.b), eq._prereq, eq._node)
      case es: EqualityOrScalar => EqualityOrScalar(updateVariable(es.a), updateVariable(es.b), es._prereq, es._node)
      case ef: EqualityExceptFor => EqualityExceptFor(updateVariable(ef.d), updateVariable(ef.a), updateVariable(ef.b), ef._prereq, ef._node)
      case lt: LessThanConstraint => LessThanConstraint(updateVariable(lt.a), updateVariable(lt.b), lt._prereq, lt._node)
      case pl: PrefixLt => PrefixLt(updateVariable(pl.main), updateVariable(pl.prefix), updateVariable(pl.suffix), pl._prereq, pl._node)
      case se: SuffixEq => SuffixEq(updateVariable(se.main), updateVariable(se.prefix), updateVariable(se.suffix), se._prereq, se._node)
      case eq: EqualityAeqBcatC => EqualityAeqBcatC(updateVariable(eq.a), updateVariable(eq.b), updateVariable(eq.c), eq._prereq, eq._node)
      case le: LengthEqualityAeqB => LengthEqualityAeqB(updateVariable(le.a), updateVariable(le.b), le._prereq, le._node)
      case cd: CommonDenominator => CommonDenominator(updateVariable(cd.a), updateVariable(cd.b), updateVariable(cd.c), cd._prereq, cd._node)
      case ep: EqualProduct => EqualProduct(updateVariable(ep.a), updateVariable(ep.b), ep._prereq, ep._node)
      case rv: ReconstructValueFromShape => ReconstructValueFromShape(updateVariable(rv.value), updateVariable(rv.shape), rv._prereq, rv._node)
      case eq: EqualityShAeqShBplusShCalongD => EqualityShAeqShBplusShCalongD(updateVariable(eq.a), updateVariable(eq.b), updateVariable(eq.c), updateVariable(eq.d), eq._prereq, eq._node)
      case eq: EqualityAeqDimTimesValue => EqualityAeqDimTimesValue(updateVariable(eq.a), updateVariable(eq.dim), updateVariable(eq.value), eq._prereq, eq._node)
      // XXX: No _ case here, if the substitution doesn't know the constraint it better fail as soon as possible!
    }
  }

  /** substitute a variable by another variable */
  case class SubstituteVarToVar(v1: Var, v2: Var) extends Substitution("Substitute " + v1.toString + " by " + v2.toString) {
    override def updateVar(v: Var): Var = if (v == v1) v2 else v
    override def updateUnknown(uk: Unknown): TypingElement = uk
    override def updateLength(lt: LengthOf): TypingElement = LengthOf(updateVar(lt.v))
    override def updateLessThan(lt: LessThan): TypingElement = LessThan(lt.i, updateElement(lt.t))
  }

  /** substitute a variable by a list */
  case class SubstituteVarToLst(vv: Var, l: Lst) extends Substitution("Substitute " + vv.toString + " by " + l.toString) {
    override def updateVar(v: Var): TypingVariable = if (v == vv) l else v
    override def updateUnknown(uk: Unknown): TypingElement = uk
    override def updateLength(lt: LengthOf): TypingElement = if (lt.v == vv) Value(l.list.length) else lt
    override def updateLessThan(lt: LessThan): TypingElement = LessThan(lt.i, updateElement(lt.t))
  }

  /** substitute a unknown value by something else */
  case class SubstituteUnknown(u: Unknown, elt: TypingElement) extends Substitution("Substitute unknown " + u.toString + " by " + elt.toString) {
    override def updateVar(v: Var): TypingVariable = v
    override def updateUnknown(uk: Unknown): TypingElement = if (uk == u) elt else uk
    override def updateLength(lt: LengthOf): TypingElement = lt
    override def updateLessThan(lt: LessThan): TypingElement = LessThan(lt.i, updateElement(lt.t))
  }

  /** substitute a less than value by something else */
  case class SubstituteLessThan(lt: LessThan, elt: TypingElement) extends Substitution("Substitute lessThan( " + lt.i.toString + ", " + lt.t.toString + ") by " + elt.toString) {
    override def updateVar(v: Var): TypingVariable = v
    override def updateUnknown(uk: Unknown): TypingElement = uk
    override def updateLength(lt: LengthOf): TypingElement = lt
    override def updateLessThan(lt2: LessThan): TypingElement = if (lt == lt2) elt else lt2
  }


  case class SubstitutionList(val substList: List[Substitution]) extends Substitution("SubstitutionList:\n" + substList.mkString(" ", "\n ", "\n")) {

    override def apply(tc: TypingConstraint): TypingConstraint = {
     var res = tc
     for(subst <- substList)
       res = subst.apply(res)
     res
    }

    def apply(tv: TypingVariable): TypingVariable = {
      var res = tv
      for(subst <- substList)
        res = subst.updateVariable(res)
      res
    }

    // we don't need these, but they have to be here to override the abstract class functions
    override def updateVar(v: Var): TypingVariable = v
    override def updateUnknown(uk: Unknown): TypingElement = uk
    override def updateLength(lt: LengthOf): TypingElement = lt
    override def updateLessThan(lt: LessThan): TypingElement = lt

    def length = substList.length
  }

  protected def getLength(v: TypingVariable): Option[Int] = v match {
    case l: Lst => Some(l.list.length)
    case _ => None
  }

  protected def getValue(v: TypingVariable): Option[List[Int]] = v match {
    case l: Lst if (countUnknowns(l) == 0) => Some(l.list.map(elt => elt.asInstanceOf[Value].n))
    case _ => None
  }
}