(ns copy-trader.exchange.alpaca.trader
  (:refer-clojure :exclude [symbol])
  (:require
   [clojure.tools.logging :as log]
   [copy-trader.exchange.alpaca.driver :as driver]
   [copy-trader.exchange.trader :refer [compute-volume on-trade with-balance]]
   [copy-trader.util :refer [to-precision]]))

(defmethod with-balance "alpaca"
  [trader-map]
  (let [balance-data (driver/alpaca-get trader-map "account")]
    (assoc trader-map :balance-usd (-> (or (-> balance-data
                                               :equity
                                               Double/parseDouble)
                                           0.0)
                                       (to-precision 2)))))

(defn- cancel-order!
  [{nickname :nickname :as trader-map} tx-id]
  (let [cancel-response (driver/alpaca-delete! trader-map (str "orders/" tx-id))]
    (if (<= 400 (:status cancel-response))
      (log/error (:body cancel-response))
      (do
        ;; FIXME -- remove order from order log
        (log/info (format "%s canceled order %s" nickname tx-id))))))

;; ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; ;;
;;                                   longs                                    ;;
;; ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; ;;

(defmethod on-trade ["alpaca" "long" "limit"]
  [{nickname :nickname :as trader-map} {:keys [symbol price stop-loss]}]
  ;; FIXME -- cancel existing long order if it exists
  (let [volume (compute-volume trader-map)
        order-data (driver/alpaca-post!
                    trader-map "orders"
                    {:symbol        symbol
                     :side          "buy"
                     :type          "limit"
                     :time_in_force "gtc"
                     :limit_price   price
                     :qty           (int volume)
                     :order_class   "oto"
                     :stop_loss     {:stop_price stop-loss}})]
    (if (<= 400 (:status order-data))
      (do
        (log/error (format "%s failed to limit long for %s" nickname symbol))
        (log/error (:body order-data)))
      (let [tx-id (get-in order-data [:body :id])]
        (log/info (format "%s: longing %.2f %s at price %.2f (stop-loss at %.2f)"
                          nickname volume symbol price stop-loss))
        ;; FIXME -- add order to order log
        (comment "record the order")))))

(defmethod on-trade ["alpaca" "long" "take-profit"]
  [{nickname :nickname :as trader-map} {:keys [symbol percentage] :as ev}]
  ;; FIXME -- cancel-existing long stop-loss
  (let [volume 0] ;; FIXME -- get existing volume and multiply by percentage
    (driver/alpaca-post!
     trader-map "orders"
     {:symbol        symbol
      :side          "sell"
      :type          "market"
      :time_in_force "gtc"
      :qty           (int volume)})
    (on-trade trader-map (assoc ev :order-type "stop-loss"))))

(defmethod on-trade ["alpaca" "long" "stop-loss"]
  [{nickname :nickname :as trader-map} {:keys [symbol stop-loss]}]
  ;; FIXME -- cancel-existing long stop-loss
  (let [volume 0 ;; get existing volume
        order-data (driver/alpaca-post!
                    trader-map "orders"
                    {:symbol        symbol
                     :side          "sell"
                     :type          "stop"
                     :time_in_force "gtc"
                     :stop_price    stop-loss
                     :qty           (int volume)})]
    (if (<= 400 (:status order-data))
      (do
        (log/error (format "%s: failed to open stop-loss order for %s" nickname symbol))
        (log/error (:body order-data)))
      (let [tx-id (get-in order-data [:body :id])]
        ;; FIXME -- add order to order log
        (log/info (format "%s: set %s long stop-loss to %.2f"
                          nickname symbol stop-loss))))))

;; ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; ;;
;;                                   shorts                                   ;;
;; ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; ;;

(defmethod on-trade ["alpaca" "short" "limit"]
  [trader-map {:keys [symbol price stop-loss]}]
  (comment
    "place a limit short"))

(defmethod on-trade ["alpaca" "short" "take-profit"]
  [trader-map {:keys [symbol price stop-loss percentage]}]
  (comment
    "cancel the current stop-loss, take profit, and open a new stop-loss"))

(defmethod on-trade ["alpaca" "short" "stop-loss"]
  [trader-map {:keys [symbol stop-loss]}]
  (comment
    "cancel the current stop-loss, and place a new one"))
