(ns copy-trader.exchange.alpaca.trader
  (:refer-clojure :exclude [symbol])
  (:require
   [clojure.tools.logging :as log]
   [copy-trader.exchange.alpaca.driver :as driver]
   [copy-trader.exchange.trader :refer [compute-position-size
                                        compute-volume
                                        on-trade
                                        with-balance with-positions with-orders]]
   [copy-trader.util :refer [to-precision]]))

(defmethod with-balance "alpaca"
  [trader-map]
  (let [balance-data (driver/alpaca-get trader-map "account")
        balance      (-> (or (-> balance-data
                                 :equity
                                 Double/parseDouble)
                             0.0)
                         (to-precision 2))]
    (assoc trader-map :balance-usd balance)))

(defn- parse-position
  [trader-map position]
  (let [symbol-balance (compute-position-size trader-map)
        position-size  (Math/abs (Double/parseDouble (:qty position)))
        position-value (Math/abs (Double/parseDouble (:market_value position)))]
    {:symbol         (keyword (:symbol position))
     :balance-usd    symbol-balance
     :position-value position-value
     :direction      (keyword (:side position))
     :volume         position-size
     :entry          (Double/parseDouble (:avg_entry_price position))
     :type           :equity}))

(defmethod with-positions "alpaca"
  [trader-map]
  (let [open-positions  (driver/alpaca-get trader-map "positions")
        reset-positions (reduce-kv
                         (fn [m symbol position]
                           (assoc m symbol (merge position
                                                  {:direction :none
                                                   :volume    0.0
                                                   :entry     0.0})))
                         {}
                         (:open-positions trader-map))
        positions       (->> open-positions
                             (map #(parse-position trader-map %))
                             (filter identity)
                             (reduce
                              (fn [m {:keys [symbol] :as position}]
                                (assoc m symbol position))
                              reset-positions))]
    (assoc trader-map :open-positions positions)))

(defn- order->direction
  [order]
  (let [{:keys [side type]} order]
    (cond
      (and (= "buy" side) (= "limit" type))  :long
      (and (= "sell" side) (= "stop" type))  :long
      (and (= "sell" side) (= "limit" type)) :short
      (and (= "buy" side) (= "stop" type))   :short)))

(defn- order->status
  [{:keys [status] :as _order}]
  (if (#{"accepted" "new" "partially_filled"} status)
    :open
    :closed))

(defn- parse-order
  [order]
  {:symbol    (keyword (:symbol order))
   :type      (if (= "stop" (:type order))
                :stop-loss
                :limit)
   :direction (order->direction order)
   :order-id  (:id order)
   :volume    (Double/parseDouble (:qty order))
   :price     (Double/parseDouble (if (= "stop" (:type order))
                                    (:stop_price order)
                                    (:limit_price order)))
   :status    (order->status order)})

(defmethod with-orders "alpaca"
  [trader-map]
  (let [open-orders (driver/alpaca-get trader-map "orders")]
    (assoc trader-map :orders (->> open-orders
                                   (mapv parse-order)))))

(defn- cancel-order!
  [{:keys [nickname orders] :as trader-map} {:keys [symbol type direction]}]
  (let [open-orders (filter #(and (= symbol (name (:symbol %)))
                                  (= type (:type %))
                                  (= direction (:direction %)))
                            orders)]
    (doseq [{tx-id :order-id} open-orders]
      (let [cancel-response (driver/alpaca-delete! trader-map (str "orders/" tx-id))]
        (if (<= 400 (:status cancel-response))
          (log/error (:body cancel-response))
          (log/info (format "%s: canceled %s order %s" nickname symbol tx-id)))))))

;; ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; ;;
;;                                   longs                                    ;;
;; ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; ;;

(defmethod on-trade ["alpaca" "long" "limit"]
  [{nickname :nickname :as trader-map} {:keys [symbol price stop-loss]}]

  ;; cancel existing long order if it exists
  (cancel-order! trader-map {:symbol symbol :type :limit :direction :long})

  (let [volume     (compute-volume trader-map price price stop-loss)
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
        (log/error (format "%s: failed to open long limit for %s" nickname symbol))
        (log/error (:body order-data)))
      (log/info (format "%s: longing %.2f %s at price %.2f (stop-loss at %.2f)"
                        nickname volume symbol price stop-loss)))))

(defmethod on-trade ["alpaca" "long" "take-profit"]
  [trader-map {:keys [symbol percentage] :as ev}]

  ;; cancel existing stop-loss order if it exists
  (cancel-order! trader-map {:symbol symbol :type :stop-loss :direction :long})

  (when-let [volume (get-in trader-map [:open-positions (keyword symbol) :volume])]
    (let [profit-volume    (int (* volume percentage))
          remaining-volume (- volume profit-volume)]
      (driver/alpaca-post!
       trader-map "orders"
       {:symbol        symbol
        :side          "sell"
        :type          "market"
        :time_in_force "gtc"
        :qty           profit-volume})
      (on-trade (assoc-in trader-map
                          [:open-positions (keyword symbol) :volume]
                          remaining-volume)
                (assoc ev :order-type "stop-loss")))))

(defmethod on-trade ["alpaca" "long" "stop-loss"]
  [{nickname :nickname :as trader-map} {:keys [symbol stop-loss]}]

  ;; cancel existing stop-loss order if it exists
  (cancel-order! trader-map {:symbol symbol :type :stop-loss :direction :long})

  (let [volume (get-in trader-map [:open-positions (keyword symbol) :volume])
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
        (log/error (format "%s: failed to open long stop-loss for %s" nickname symbol))
        (log/error (:body order-data)))
      (log/info (format "%s: set %s long stop-loss to %.2f"
                        nickname symbol stop-loss)))))

;; ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; ;;
;;                                   shorts                                   ;;
;; ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; ;;

(defmethod on-trade ["alpaca" "short" "limit"]
  [{nickname :nickname :as trader-map} {:keys [symbol price stop-loss]}]

  ;; cancel existing short order if it exists
  (cancel-order! trader-map {:symbol symbol :type :limit :direction :short})

  (let [volume     (compute-volume trader-map price price stop-loss)
        order-data (driver/alpaca-post!
                    trader-map "orders"
                    {:symbol        symbol
                     :side          "sell"
                     :type          "limit"
                     :time_in_force "gtc"
                     :limit_price   price
                     :qty           (int volume)
                     :order_class   "oto"
                     :stop_loss     {:stop_price stop-loss}})]
    (if (<= 400 (:status order-data))
      (do
        (log/error (format "%s: failed to open short limit for %s" nickname symbol))
        (log/error (:body order-data)))
      (log/info (format "%s: shorting %.2f %s at price %.2f (stop-loss at %.2f)"
                        nickname volume symbol price stop-loss)))))

(defmethod on-trade ["alpaca" "short" "take-profit"]
  [trader-map {:keys [symbol percentage] :as ev}]

  ;; cancel existing stop-loss order if it exists
  (cancel-order! trader-map {:symbol symbol :type :stop-loss :direction :short})

  (when-let [volume (get-in trader-map [:open-positions (keyword symbol) :volume])]
    (let [profit-volume    (int (* volume percentage))
          remaining-volume (- volume profit-volume)]
      (driver/alpaca-post!
       trader-map "orders"
       {:symbol        symbol
        :side          "buy"
        :type          "market"
        :time_in_force "gtc"
        :qty           profit-volume})
      (on-trade (assoc-in trader-map
                          [:open-positions (keyword symbol) :volume]
                          remaining-volume)
                (assoc ev :order-type "stop-loss")))))

(defmethod on-trade ["alpaca" "short" "stop-loss"]
  [{nickname :nickname :as trader-map} {:keys [symbol stop-loss]}]

  ;; cancel existing stop-loss order if it exists
  (cancel-order! trader-map {:symbol symbol :type :stop-loss :direction :short})

  (let [volume (get-in trader-map [:open-positions (keyword symbol) :volume])
        order-data (driver/alpaca-post!
                    trader-map "orders"
                    {:symbol        symbol
                     :side          "buy"
                     :type          "stop"
                     :time_in_force "gtc"
                     :stop_price    stop-loss
                     :qty           (int volume)})]
    (if (<= 400 (:status order-data))
      (do
        (log/error (format "%s: failed to open short stop-loss for %s" nickname symbol))
        (log/error (:body order-data)))
      (log/info (format "%s: set %s short stop-loss to %.2f"
                        nickname symbol stop-loss)))))
