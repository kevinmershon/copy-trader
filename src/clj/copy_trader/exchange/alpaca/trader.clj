(ns copy-trader.exchange.alpaca.trader
  (:refer-clojure :exclude [symbol])
  (:require
   [copy-trader.exchange.trader :refer [on-trade]]))

;; ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; ;;
;;                                   longs                                    ;;
;; ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; ;;

(defmethod on-trade ["alpaca" "long" "limit"]
  [{:keys [symbol price stop-loss]}]
  (comment
    "place a limit long"))

(defmethod on-trade ["alpaca" "long" "take-profit"]
  [{:keys [symbol price stop-loss]}]
  (comment
    "cancel the current stop-loss, take profit, and open a new stop-loss"))

(defmethod on-trade ["alpaca" "long" "stop-loss"]
  [{:keys [symbol stop-loss]}]
  (comment
    "cancel the current stop-loss, and place a new one"))

;; ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; ;;
;;                                   shorts                                   ;;
;; ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; ;;

(defmethod on-trade ["alpaca" "short" "limit"]
  [{:keys [symbol price stop-loss]}]
  (comment
    "place a limit short"))

(defmethod on-trade ["alpaca" "short" "take-profit"]
  [{:keys [symbol price stop-loss]}]
  (comment
    "cancel the current stop-loss, take profit, and open a new stop-loss"))

(defmethod on-trade ["alpaca" "short" "stop-loss"]
  [{:keys [symbol stop-loss]}]
  (comment
    "cancel the current stop-loss, and place a new one"))
