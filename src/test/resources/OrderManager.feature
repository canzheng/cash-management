Feature: Order Manager

  Background:
    Given 系统初始化
    And 用户 account1 Invest 1000 份额
    And 用户 account2 Invest 1000 份额
    And 用户 account3 Invest 1000 份额
    And 完成轧差 3000
    And 基金确认订单总额 3000, 平台总份额 3000
    And 用户 account1 有 Completed Invest 订单 1000 份
    And 用户 account2 有 Completed Invest 订单 1000 份
    And 用户 account3 有 Completed Invest 订单 1000 份
    And 用户 account1 有 Long 头寸 1000 份
    And 用户 account2 有 Long 头寸 1000 份
    And 用户 account3 有 Long 头寸 1000 份

  Scenario: 普通申购
    Given 用户 account1 Invest 1000 份额
    And 完成轧差 1000
    When 基金确认订单总额 1000, 平台总份额 4003
    Then 用户 account1 有 Long 头寸 2000 份
    And 用户 account2 有 Long 头寸 1000 份
    And 用户 account3 有 Long 头寸 1000 份
    And 用户 account1 有 Dividend 头寸 1 份
    And 用户 account2 有 Dividend 头寸 1 份
    And 用户 account3 有 Dividend 头寸 1 份

  Scenario: 普通快赎
    Given 用户 account1 QRedeem 500 份额
    Then 用户 account1 有 Completed Sell 订单 500 份
    And 用户 00 有 Completed Buy 订单 500 份
    And 用户 00 有 Open Redeem 订单 500 份
    And 完成轧差 -500
    When 基金确认订单总额 -500, 平台总份额 2503
    And 用户 account1 有 Long 头寸 500 份
    And 用户 account2 有 Long 头寸 1000 份
    And 用户 account3 有 Long 头寸 1000 份
    And 用户 00 有 Long 头寸 0 份
    And 用户 account1 有 Dividend 头寸 0.5 份
    And 用户 account2 有 Dividend 头寸 1 份
    And 用户 account3 有 Dividend 头寸 1 份
    And 用户 00 有 Dividend 头寸 0.5 份

  Scenario: 申赎对冲，结果净申
    Given 用户 account1 QRedeem 500 份额
    And 用户 account2 Invest 300 份额
    And 用户 account3 Invest 600 份额
    Then 用户 account1 有 Completed Sell 订单 500 份
    And 用户 00 有 Completed Buy 订单 500 份
    And 用户 00 有 Open Redeem 订单 500 份
    And 用户 account2 有 Open Invest 订单 300 份
    And 用户 account3 有 Open Invest 订单 600 份
    And 完成轧差 400
    When 基金确认订单总额 400, 平台总份额 3403
    And 用户 account1 有 Long 头寸 500 份
    And 用户 account2 有 Long 头寸 1300 份
    And 用户 account3 有 Long 头寸 1600 份
    And 用户 00 有 Long 头寸 0 份
    And 用户 account1 有 Dividend 头寸 0.5 份
    And 用户 account2 有 Dividend 头寸 1 份
    And 用户 account3 有 Dividend 头寸 1 份
    And 用户 00 有 Dividend 头寸 0.5 份

  Scenario: 申赎对冲，结果净赎
    Given 用户 account1 QRedeem 500 份额
    And 用户 account2 QRedeem 300 份额
    And 用户 account3 Invest 600 份额
    Then 用户 account1 有 Completed Sell 订单 500 份
    And 用户 00 有 Completed Buy 订单 500 份
    And 用户 00 有 Open Redeem 订单 500 份
    And 用户 account2 有 Completed Sell 订单 300 份
    And 用户 00 有 Completed Buy 订单 300 份
    And 用户 00 有 Open Redeem 订单 300 份
    And 用户 account3 有 Open Invest 订单 600 份
    And 完成轧差 -200
    When 基金确认订单总额 -200, 平台总份额 2803
    And 用户 account1 有 Long 头寸 500 份
    And 用户 account2 有 Long 头寸 700 份
    And 用户 account3 有 Long 头寸 1600 份
    And 用户 00 有 Long 头寸 0 份
    And 用户 account1 有 Dividend 头寸 0.5 份
    And 用户 account2 有 Dividend 头寸 0.7 份
    And 用户 account3 有 Dividend 头寸 1 份
    And 用户 00 有 Dividend 头寸 0.8 份

  Scenario: 申赎对冲，结果无申赎
    Given 用户 account1 QRedeem 500 份额
    And 用户 account2 QRedeem 300 份额
    And 用户 account3 Invest 800 份额
    Then 用户 account1 有 Completed Sell 订单 500 份
    And 用户 00 有 Completed Buy 订单 500 份
    And 用户 00 有 Open Redeem 订单 500 份
    And 用户 account2 有 Completed Sell 订单 300 份
    And 用户 00 有 Completed Buy 订单 300 份
    And 用户 00 有 Open Redeem 订单 300 份
    And 用户 account3 有 Open Invest 订单 800 份
    And 完成轧差 0
    When 基金确认订单总额 0, 平台总份额 3003
    And 用户 account1 有 Long 头寸 500 份
    And 用户 account2 有 Long 头寸 700 份
    And 用户 account3 有 Long 头寸 1800 份
    And 用户 00 有 Long 头寸 0 份
    And 用户 account1 有 Dividend 头寸 0.5 份
    And 用户 account2 有 Dividend 头寸 0.7 份
    And 用户 account3 有 Dividend 头寸 1 份
    And 用户 00 有 Dividend 头寸 0.8 份

  Scenario: 机构确认净申，机构确认小于平台记录
    Given 用户 account1 QRedeem 500 份额
    And 用户 account2 Invest 300 份额
    And 用户 account3 Invest 600 份额
    Then 用户 account1 有 Completed Sell 订单 500 份
    And 用户 00 有 Completed Buy 订单 500 份
    And 用户 00 有 Open Redeem 订单 500 份
    And 用户 account2 有 Open Invest 订单 300 份
    And 用户 account3 有 Open Invest 订单 600 份
    And 完成轧差 400
    When 基金确认订单总额 300, 平台总份额 3303
    And 用户 account1 有 Long 头寸 500 份
    And 用户 account2 有 Long 头寸 1300 份
    And 用户 account3 有 Long 头寸 1600 份
    And 用户 00 有 Long 头寸 -100 份
    And 用户 account1 有 Dividend 头寸 0.5 份
    And 用户 account2 有 Dividend 头寸 1 份
    And 用户 account3 有 Dividend 头寸 1 份
    And 用户 00 有 Dividend 头寸 0.5 份
    And 用户 00 有 Open Invest 订单 100 份

  Scenario: 机构确认净申，机构确认大于平台记录
    Given 用户 account1 QRedeem 100 份额
    And 用户 account2 QRedeem 300 份额
    And 用户 account3 Invest 600 份额
    Then 用户 account1 有 Completed Sell 订单 100 份
    And 用户 00 有 Completed Buy 订单 100 份
    And 用户 00 有 Open Redeem 订单 100 份
    And 用户 account2 有 Completed Sell 订单 300 份
    And 用户 00 有 Completed Buy 订单 300 份
    And 用户 00 有 Open Redeem 订单 300 份
    And 用户 account3 有 Open Invest 订单 600 份
    And 完成轧差 200
    When 基金确认订单总额 300, 平台总份额 3303
    And 用户 account1 有 Long 头寸 900 份
    And 用户 account2 有 Long 头寸 700 份
    And 用户 account3 有 Long 头寸 1600 份
    And 用户 00 有 Long 头寸 100 份
    And 用户 account1 有 Dividend 头寸 0.9 份
    And 用户 account2 有 Dividend 头寸 0.7 份
    And 用户 account3 有 Dividend 头寸 1 份
    And 用户 00 有 Dividend 头寸 0.4 份

  Scenario: 机构确认净赎，机构确认小于平台记录（机构确认赎回更多）
    Given 用户 account1 QRedeem 700 份额
    And 用户 account2 Invest 300 份额
    And 用户 account3 Invest 200 份额
    Then 用户 account1 有 Completed Sell 订单 700 份
    And 用户 00 有 Completed Buy 订单 700 份
    And 用户 00 有 Open Redeem 订单 700 份
    And 用户 account2 有 Open Invest 订单 300 份
    And 用户 account3 有 Open Invest 订单 200 份
    And 完成轧差 -200
    When 基金确认订单总额 -300, 平台总份额 2703
    And 用户 account1 有 Long 头寸 300 份
    And 用户 account2 有 Long 头寸 1300 份
    And 用户 account3 有 Long 头寸 1200 份
    And 用户 00 有 Long 头寸 -100 份
    And 用户 account1 有 Dividend 头寸 0.3 份
    And 用户 account2 有 Dividend 头寸 1 份
    And 用户 account3 有 Dividend 头寸 1 份
    And 用户 00 有 Dividend 头寸 0.7 份
    And 用户 00 有 Open Invest 订单 100 份

  Scenario: 机构确认净赎，机构确认大于平台记录（机构确认赎回更少）
    Given 用户 account1 QRedeem 500 份额
    And 用户 account2 QRedeem 300 份额
    And 用户 account3 Invest 600 份额
    Then 用户 account1 有 Completed Sell 订单 500 份
    And 用户 00 有 Completed Buy 订单 500 份
    And 用户 00 有 Open Redeem 订单 500 份
    And 用户 account2 有 Completed Sell 订单 300 份
    And 用户 00 有 Completed Buy 订单 300 份
    And 用户 00 有 Open Redeem 订单 300 份
    And 用户 account3 有 Open Invest 订单 600 份
    And 完成轧差 -200
    When 基金确认订单总额 -100, 平台总份额 2903
    And 用户 account1 有 Long 头寸 500 份
    And 用户 account2 有 Long 头寸 700 份
    And 用户 account3 有 Long 头寸 1600 份
    And 用户 00 有 Long 头寸 100 份
    And 用户 account1 有 Dividend 头寸 0.5 份
    And 用户 account2 有 Dividend 头寸 0.7 份
    And 用户 account3 有 Dividend 头寸 1 份
    And 用户 00 有 Dividend 头寸 0.8 份

  Scenario: 机构确认净申，轧差值小于平台最小净申
    Given 用户 account1 QRedeem 100 份额
    And 用户 account2 QRedeem 300 份额
    And 用户 account3 Invest 450 份额
    Then 用户 account1 有 Completed Sell 订单 100 份
    And 用户 00 有 Completed Buy 订单 100 份
    And 用户 00 有 Open Redeem 订单 100 份
    And 用户 account2 有 Completed Sell 订单 300 份
    And 用户 00 有 Completed Buy 订单 300 份
    And 用户 00 有 Open Redeem 订单 300 份
    And 用户 account3 有 Open Invest 订单 450 份
    And 完成轧差 100
    And 用户 00 有 PendingRec Invest 订单 50 份
    When 基金确认订单总额 100, 平台总份额 3103
    And 用户 account1 有 Long 头寸 900 份
    And 用户 account2 有 Long 头寸 700 份
    And 用户 account3 有 Long 头寸 1450 份
    And 用户 00 有 Long 头寸 50 份
    And 用户 account1 有 Dividend 头寸 0.9 份
    And 用户 account2 有 Dividend 头寸 0.7 份
    And 用户 account3 有 Dividend 头寸 1 份
    And 用户 00 有 Dividend 头寸 0.4 份

  Scenario: 机构确认净赎，轧差值小于平台最小净赎
    Given 用户 account1 QRedeem 700 份额
    And 用户 account2 Invest 300 份额
    And 用户 account3 Invest 350 份额
    Then 用户 account1 有 Completed Sell 订单 700 份
    And 用户 00 有 Completed Buy 订单 700 份
    And 用户 00 有 Open Redeem 订单 700 份
    And 用户 account2 有 Open Invest 订单 300 份
    And 用户 account3 有 Open Invest 订单 350 份
    And 完成轧差 0
    And 用户 00 有 PendingRec Invest 订单 50 份
    When 基金确认订单总额 0, 平台总份额 3003
    And 用户 account1 有 Long 头寸 300 份
    And 用户 account2 有 Long 头寸 1300 份
    And 用户 account3 有 Long 头寸 1350 份
    And 用户 00 有 Long 头寸 50 份
    And 用户 account1 有 Dividend 头寸 0.3 份
    And 用户 account2 有 Dividend 头寸 1 份
    And 用户 account3 有 Dividend 头寸 1 份
    And 用户 00 有 Dividend 头寸 0.7 份

  Scenario: 商户赎回先冲抵之前净申购，不影响头寸
    Given 用户 account1 Invest 1300 份额
    When 用户 account1 QRedeem 300 份额
    Then 用户 account1 有 Open Redeem 订单 300 份
    And 用户 account1 有 Open Invest 订单 1300 份
    When 完成轧差 1000
    And 基金确认订单总额 1000, 平台总份额 4003
    Then 用户 account1 有 Long 头寸 2000 份
    And 用户 account2 有 Long 头寸 1000 份
    And 用户 account3 有 Long 头寸 1000 份
    And 用户 account1 有 Dividend 头寸 1 份
    And 用户 account2 有 Dividend 头寸 1 份
    And 用户 account3 有 Dividend 头寸 1 份

  Scenario: 商户赎回先冲抵之前净申购，再冲抵头寸，最后挂起
    Given 用户 account1 Invest 1300 份额
    # 剩余1000头寸 1300净申 0在途
    And 用户 account1 QRedeem 300 份额
    Then 用户 account1 有 Open Redeem 订单 300 份
    And 用户 account1 有 Open Invest 订单 1300 份
    # 剩余1000头寸 1000净申 0在途
    When 用户 account1 QRedeem 1400 份额
    Then 用户 account1 有 Open Redeem 订单 1000 份
    And 用户 account1 有 Completed Sell 订单 400 份
    And 用户 00 有 Completed Buy 订单 400 份
    And 用户 00 有 Open Redeem 订单 400 份
    # 剩余600头寸 0净申 0在途
    When 用户 account1 Invest 700 份额
    And 完成轧差 300
    # 剩余600头寸 0净申 700在途
    And 用户 account1 QRedeem 1100 份额
    Then 用户 account1 有 Completed Sell 订单 600 份
    And 用户 00 有 Completed Buy 订单 600 份
    And 用户 00 有 Open Redeem 订单 600 份
    And 用户 account1 有 PendingRedeem QRedeem 订单 500 份
    # 剩余0头寸 0净申 700在途，500挂起
    And 完成轧差 -600
    When 基金确认订单总额 -300, 平台总份额 2703
    Then 用户 account1 有 Long 头寸 700 份
    And 用户 account2 有 Long 头寸 1000 份
    And 用户 account3 有 Long 头寸 1000 份
    And 用户 00 有 Long 头寸 0 份
    And 用户 account1 有 Dividend 头寸 0 份
    And 用户 account2 有 Dividend 头寸 1 份
    And 用户 account3 有 Dividend 头寸 1 份
    And 用户 00 有 Dividend 头寸 1 份

  Scenario: 商户赎回超过头寸 + 未轧差净申 + 在途申购 + 挂起赎回时失败
    Given 用户 account1 Invest 1300 份额
    # 剩余1000头寸 1300净申 0在途
    And 用户 account1 QRedeem 300 份额
    Then 用户 account1 有 Open Redeem 订单 300 份
    And 用户 account1 有 Open Invest 订单 1300 份
    # 剩余1000头寸 1000净申 0在途
    And 用户 account1 无法赎回 2100 份
    When 用户 account1 QRedeem 1400 份额
    Then 用户 account1 有 Open Redeem 订单 1000 份
    And 用户 account1 有 Completed Sell 订单 400 份
    And 用户 00 有 Completed Buy 订单 400 份
    And 用户 00 有 Open Redeem 订单 400 份
    # 剩余600头寸 0净申 0在途
    When 用户 account1 Invest 700 份额
    And 完成轧差 300
    # 剩余600头寸 0净申 700在途
    And 用户 account1 QRedeem 1100 份额
    Then 用户 account1 有 Completed Sell 订单 600 份
    And 用户 00 有 Completed Buy 订单 600 份
    And 用户 00 有 Open Redeem 订单 600 份
    And 用户 account1 有 PendingRedeem QRedeem 订单 500 份
    # 剩余0头寸 0净申 700在途，500挂起
    And 完成轧差 -600
    When 基金确认订单总额 -300, 平台总份额 2703
    Then 用户 account1 有 Long 头寸 700 份
    And 用户 account2 有 Long 头寸 1000 份
    And 用户 account3 有 Long 头寸 1000 份
    And 用户 00 有 Long 头寸 0 份
    And 用户 account1 有 Dividend 头寸 0 份
    And 用户 account2 有 Dividend 头寸 1 份
    And 用户 account3 有 Dividend 头寸 1 份
    And 用户 00 有 Dividend 头寸 1 份
    # 检查挂起头寸影响赎回，此时500赎回挂起，700头寸
    And 用户 account1 无法赎回 300 份
    When 用户 account1 Invest 1700 份额
    # 500赎回挂起，700头寸，1700净申
    Then 用户 account1 无法赎回 2000 份
    When 用户 account1 QRedeem 1900 份额
    Then 用户 account1 有 Open Redeem 订单 1700 份
    And 用户 account1 有 Completed Sell 订单 200 份
    And 用户 00 有 Completed Buy 订单 200 份
    And 用户 00 有 Open Redeem 订单 200 份


