package com.netfinworks.matrix.positions

/**
 * Created by canzheng on 7/29/15.
 */

import scala.collection.mutable
import com.netfinworks.matrix.positions.domain._



case class Position (
                 positionId: String,
                 account: String,
                 productId: String,
                 positionType: PositionType,
                 quantity: BigDecimal = 0,
                 amount: BigDecimal = 0,
                 availableQuantity: BigDecimal = 0,
                 availableAmount: BigDecimal = 0,
                 miscPositionData: mutable.Map[String, BigDecimal] = new mutable.HashMap[String, BigDecimal],
                 deleted: Boolean = false
                 )
