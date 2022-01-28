package esmeta.lang

import scala.collection.mutable.{Map => MMap}
import scala.annotation.targetName

/** a kind counter for metalanguage */
class KindCounter extends UnitWalker {
  val stepMap: MMap[String, Int] = MMap()
  val exprMap: MMap[String, Int] = MMap()
  val condMap: MMap[String, Int] = MMap()
  override def walk(step: Step): Unit = {
    val name = step.getClass.getSimpleName
    val count = stepMap.getOrElseUpdate(name, 0)
    stepMap += name -> (count + 1)
    super.walk(step)
  }
  override def walk(expr: Expression): Unit = {
    val name = expr.getClass.getSimpleName
    val count = exprMap.getOrElseUpdate(name, 0)
    exprMap += name -> (count + 1)
    super.walk(expr)
  }
  override def walk(cond: Condition): Unit = {
    val name = cond.getClass.getSimpleName
    val count = condMap.getOrElseUpdate(name, 0)
    condMap += name -> (count + 1)
    super.walk(cond)
  }
}
object KindCounter {
  def apply(elem: LangElem): KindCounter =
    val counter = new KindCounter
    counter.walk(elem)
    counter
}
