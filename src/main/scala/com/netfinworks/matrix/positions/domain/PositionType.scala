package com.netfinworks.matrix.positions.domain

/**
 * Created by canzheng on 8/13/15.
 */

object PositionType {
  implicit def fromString(ptString: String): PositionType = {
    ptString.toLowerCase() match {
      case "long" => Long
      case "short" => Short
      case "collateral" => Collateral
      case "dividend" => Dividend
      case "reward" => Reward
      case "other" => Other
    }
  }
}

sealed trait PositionType

case object Long extends PositionType
case object Short extends PositionType
case object Collateral extends PositionType
case object Dividend extends PositionType
case object Reward extends PositionType
case object Other extends PositionType