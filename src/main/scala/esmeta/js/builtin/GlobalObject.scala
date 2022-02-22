package esmeta.js.builtin

import esmeta.cfg.CFG
import esmeta.interp.*
import esmeta.spec.*

/** model for global object */
case class GlobalObject(cfg: CFG) {

  /** shortcuts */
  private val T = true
  private val F = false
  private val U = Undef
  private val spec = cfg.program.spec
  given CFG = cfg

  /** get global object */
  def obj: MapObj = MapObj("Object")(Str(SUBMAP) -> submapAddr(GLOBAL_OBJECT))

  /** get map for heap */
  lazy val map: Map[Addr, Obj] = {
    var nmap = List(
      "print" -> DataProperty(intrAddr("print"), T, F, T),
      // TODO "globalThis" -> DataProperty(NamedAddr("GLOBAL"), T, F, T),
      "Infinity" -> DataProperty(Number(Double.PositiveInfinity), F, F, F),
      "NaN" -> DataProperty(Number(Double.NaN), F, F, F),
      "undefined" -> DataProperty(Undef, F, F, F),
    )
    for {
      row <- spec.tables(WELL_KNOWN_INTRINSICS).rows
      List(intrCell, globCell) = row.take(2).map(_.trim) if globCell != ""
      intrKey = intrCell.replace("%", "")
      globKey = globCell.replace("`", "")
    } { nmap ::= globKey -> DataProperty(intrAddr(intrKey), T, F, T) }

    getSubmapObjects(GLOBAL_OBJECT, nmap)
  }
}
