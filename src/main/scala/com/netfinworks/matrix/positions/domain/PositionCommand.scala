package com.netfinworks.matrix.positions.domain

import com.netfinworks.matrix.positions.Position

import scala.collection.immutable.HashMap

/**
 * Created by canzheng on 8/13/15.
 */
sealed trait PositionCommand {
  def requestId: String
}

case class PosQuery(
                  pred: Position => Boolean,
                  requestId: String = null
                  ) extends PositionCommand

case class Update(
                   requestId: String,
                   account: String,
                   productId: String,
                   positionType: PositionType,
                   dQuantity: BigDecimal = 0,
                   dAmount: BigDecimal = 0,
                   dAvailableQty: BigDecimal = 0,
                   dAvailableAmt: BigDecimal = 0,
                   dMiscPos: Map[String, BigDecimal] = new HashMap[String, BigDecimal],
                   deleted: Boolean = false
                   ) extends PositionCommand

case class Transfer(
                     requestId: String,
                     account: String,
                     productId: String,
                     fromType: PositionType,
                     toType: PositionType,
                     isCompleteTransfer: Boolean = false,
                     dQuantity: BigDecimal = 0,
                     dAmount: BigDecimal = 0
                     ) extends PositionCommand

// TODO: currently only support single platform

case class BatchUpdate(
                      requestId: String,
                      productId: String,
                      totalOrderQty: BigDecimal,
                      totalPosition: BigDecimal
                        ) extends PositionCommand

// Errors
case object FromPositionMissing

case object NotEnoughToTransfer

case object DuplicatedRequest