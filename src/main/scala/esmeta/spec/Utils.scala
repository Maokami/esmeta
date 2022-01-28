package esmeta.spec

import esmeta.lang.{Step, YetStep, StepCollector, KindCounter}
import esmeta.{EXTRACT_LOG_DIR, LINE_SEP}
import esmeta.util.BaseUtils.{cached, time}
import esmeta.util.HtmlUtils.*
import esmeta.util.SystemUtils.*
import org.jsoup.nodes.*
import scala.collection.mutable.{Map => MMap}

/** specification utilities */
object Utils {

  /** ordering of productions */
  given Ordering[Production] =
    Ordering.by(prod => (prod.kind.ordinal, prod.lhs.name))

  /** extensions for Elements */
  extension (elem: Element) {

    /** walker for ancestors */
    def walkAncestor[T](
      f: Element => T,
      base: T,
      join: (T, T) => T,
    ): T =
      val parent = elem.parent
      if (parent == null) base
      else join(f(parent), parent.walkAncestor(f, base, join))

    /** checks whether an element is in appendix */
    def isInAnnex: Boolean =
      elem.walkAncestor(_.tagName == "emu-annex", false, _ || _)

    /** checks whether an element is of Chapter 5. Notational Conventions */
    def isNotation: Boolean =
      elem.parent match {
        case null => false
        case parent =>
          if (parent.id == "sec-notational-conventions") true
          else parent.isNotation
      }

    /** get algos of the given elem */
    def getAlgos(spec: Spec) = spec.algorithms.filter(_.elem.id == elem.id)
  }

  // TODO optimize this by removing redundant computation
  /** extensions for specifications */
  extension (spec: Spec) {

    /** get incomplete algorithms */
    def incompleteAlgorithms: List[Algorithm] =
      spec.algorithms.filter(!_.complete)

    /** get complete algorithms */
    def completeAlgorithms: List[Algorithm] =
      spec.algorithms.filter(_.complete)

    /** get all algorithm steps */
    def allSteps: List[Step] = for {
      algo <- spec.algorithms
      step <- algo.steps
    } yield step

    /** get incomplete algorithm steps */
    def incompleteSteps: List[Step] =
      allSteps.filter(_.isInstanceOf[YetStep])

    /** get complete algorithm steps */
    def completeSteps: List[Step] =
      allSteps.filter(!_.isInstanceOf[YetStep])

    /** get stats */
    def stats: Stats = {
      val s = new Stats(spec)
      for { algo <- spec.algorithms } {
        val algoStat = Stat(
          if algo.complete then 1 else 0,
          1,
          algo.completeSteps.length,
          algo.steps.length,
        )
        s.addAlgo(algo, algoStat)
      }
      s
    }
  }

  /** extensions for algorithms */
  extension (algo: Algorithm) {

    /** check whether it is incomplete */
    def complete: Boolean = incompleteSteps.isEmpty

    /** get all steps */
    def steps: List[Step] = StepCollector(algo.body)

    /** get incomplete algorithm steps */
    def incompleteSteps: List[Step] =
      steps.filter(_.isInstanceOf[YetStep])

    /** get complete algorithm steps */
    def completeSteps: List[Step] =
      steps.filter(!_.isInstanceOf[YetStep])

    /** get all stats */
    def stats: KindCounter = KindCounter(algo.body)
  }

  /** extensions for grammars */
  extension (grammar: Grammar) {

    /** get the index mapping for grammars */
    def idxMap(forWeb: Boolean = false): Map[String, (Int, Int)] = (for {
      prod <- if (forWeb) grammar.prodsForWeb else grammar.prods
      pair <- prod.idxMap
    } yield pair).toMap
  }

  /** extensions for productions */
  extension (prod: Production) {

    /** get name */
    def name: String = prod.lhs.name

    /** get the index mapping for productions */
    def idxMap: Map[String, (Int, Int)] = (for {
      (rhs, i) <- prod.rhsList.zipWithIndex
      (name, j) <- rhs.allNames.zipWithIndex
    } yield prod.lhs.name + ":" + name -> (i, j)).toMap

    /** get non-terminals in RHSs */
    def getNts: List[Nonterminal] = for {
      rhs <- prod.rhsList
      nt <- rhs.getNts
    } yield nt
  }

  /** extensions for RHSs */
  extension (rhs: Rhs) {

    /** get RHS all names */
    def allNames: List[String] =
      rhs.symbols.foldLeft(List[String]("")) {
        case (names, Terminal(term)) => names.map(_ + term)
        case (names, Nonterminal(name, _, optional)) =>
          names.flatMap(x => {
            if (optional) List(x, x + name) else List(x + name)
          })
        case (names, ButNot(base, _)) =>
          names.map(_ + base.name)
        case (names, ButOnlyIf(base, _, _)) =>
          names.map(_ + base.name)
        case (names, _) => names
      }

    /** get non-terminals in an RHS */
    def getNts: List[Nonterminal] = rhs.symbols.flatMap(_.getNt)

    /** get parameters from RHSs */
    def getRhsParams: List[Param] = {
      import Param.Kind.*
      val names = rhs.getNts.map(_.name)
      val duplicated = names.filter(p => names.count(_ == p) > 1).toSet
      var counter = Map[String, Int]()
      val paramNames = names.map(name => {
        if (duplicated contains name) {
          val k = counter.getOrElse(name, 0)
          counter += name -> (k + 1)
          s"$name$k"
        } else name
      })
      paramNames.map(Param(_, Normal, "unknown"))
    }
  }

  /** extensions for symbols */
  extension (symbol: Symbol) {

    /** get an non-terminal or nothing from a symbol */
    def getNt: Option[Nonterminal] = symbol match {
      case (nt: Nonterminal)     => Some(nt)
      case ButNot(base, _)       => Some(base)
      case ButOnlyIf(base, _, _) => Some(base)
      case _                     => None
    }
  }

  /** extensions for stats */
  extension (stat: Stats) {

    /** dump */
    def dump(baseDir: String): Unit = {
      // log Statistics
      mkdir(baseDir)

      val algoStr = stat.getAllStr("Algo")
      dumpFile(
        name = "the summary of algorithms",
        data = algoStr,
        filename = s"$baseDir/algo-summary",
      )

      val stepStr = stat.getAllStr("Step")
      dumpFile(
        name = "the summary of algorithm steps",
        data = stepStr,
        filename = s"$baseDir/step-summary",
      )

      val (stepMap, exprMap, condMap) = stat.totalKind
      val stepStatStr = (for {
        (name, count) <- stepMap.toList.sortBy(_._2)
      } yield f"$count%-5d $name").mkString(LINE_SEP)
      dumpFile(
        name = "the summary of spec step-stat",
        data = stepStatStr,
        filename = s"$baseDir/step-stat-summary",
      )

      val exprStatStr = (for {
        (name, count) <- exprMap.toList.sortBy(_._2)
      } yield f"$count%-5d $name").mkString(LINE_SEP)
      dumpFile(
        name = "the summary of spec expr-stat",
        data = exprStatStr,
        filename = s"$baseDir/expr-stat-summary",
      )

      val condStatStr = (for {
        (name, count) <- condMap.toList.sortBy(_._2)
      } yield f"$count%-5d $name").mkString(LINE_SEP)
      dumpFile(
        name = "the summary of spec expr-stat",
        data = condStatStr,
        filename = s"$baseDir/cond-stat-summary",
      )
    }

  }
}
