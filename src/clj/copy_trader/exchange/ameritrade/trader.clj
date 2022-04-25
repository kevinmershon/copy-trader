(ns copy-trader.exchange.ameritrade.trader
  (:refer-clojure :exclude [symbol])
  (:require
   [clojure.tools.logging :as log]
   [copy-trader.exchange.ameritrade.driver :as driver :refer [->account-id]]
   [copy-trader.exchange.trader :refer [compute-position-size
                                        compute-volume
                                        on-trade
                                        with-balance with-positions with-orders]]
   [copy-trader.util :refer [to-precision]]))

(defn- account-for
  [account-id account-results]
  (some->> account-results
           (map :securitiesAccount)
           (filter #(= account-id (:accountId %)))
           first))

(defmethod with-balance "ameritrade"
  [trader-map]
  (try
    (let [account-results (driver/ameritrade-get trader-map "accounts")
          account-id      (->account-id trader-map)
          balance-data    (some->> account-results
                                   (account-for account-id))
          cash-balance    (get-in balance-data [:currentBalances :cashBalance])
          available-funds (get-in balance-data [:projectedBalances :availableFunds])
          buying-power    (get-in balance-data [:projectedBalances :buyingPower])
          balance-usd     (get-in balance-data [:currentBalances :liquidationValue])]
      (if (and (number? balance-usd) (pos? balance-usd))
        (do
          ;; copy the account_id down into position atoms
          (doseq [symbol-key (keys (:positions trader-map))]
            (swap! (get (:positions trader-map) symbol-key)
                   assoc :account_id (:accountId balance-data)))

          (merge trader-map {:account_id      (:accountId balance-data)
                             :available-funds available-funds
                             :buying-power    buying-power
                             :cash-balance    (to-precision cash-balance 2)
                             :balance-usd     (to-precision balance-usd 2)}))
        trader-map))
    (catch Throwable t
      (log/error t)
      trader-map)))

(defn- parse-position
  [trader-map position]
  ;; FIXME implement
  (comment
    {:symbol         (keyword (:symbol position))
     :balance-usd    symbol-balance
     :position-value position-value
     :direction      (keyword (:side position))
     :volume         position-size
     :entry          (Double/parseDouble (:avg_entry_price position))
     :type           :equity}))

(defn- order->status
  [order]
  (if (#{"ACCEPTED" "PENDING_ACTIVATION" "QUEUED" "WORKING"} (:status order))
    :open
    :closed))

(defn- parse-order
  [order]
  (when (#{"LIMIT" "STOP" "STOP_LIMIT"} (:orderType order))
    (let [is-stop-loss? (#{"STOP" "STOP_LIMIT"} (:orderType order))]
      {:symbol    (-> order :orderLegCollection first :instrument :symbol)
       :type      (if is-stop-loss? :stop-loss :limit)
       :direction (if (= "SELL" (-> order :orderLegCollection first :instruction))
                    :long
                    :short)
       :order-id  (:orderId order)
       :volume    (:quantity order)
       :price     (if is-stop-loss?
                    (:stopPrice order)
                    (:price order))
       :status    (order->status order)})))

(defn- gather-orders
  [orders]
  (reduce
   (fn [vv order]
     (if-let [child-order (some-> order :childOrderStrategies first)]
       (concat vv [order child-order])
       (conj vv order)))
   []
   orders))

(defmethod with-orders "ameritrade"
  [trader-map]
  (try
    (let [account-id (->account-id trader-map)]
      (if-let [orders (some->> (driver/ameritrade-get trader-map "accounts"
                                                      :params {"fields" "orders"})
                               (account-for account-id)
                               :orderStrategies
                               gather-orders
                               (map parse-order)
                               (filter identity))]
        (merge trader-map {:orders     orders
                           :all-orders orders})
        trader-map))
    (catch Throwable t
      (log/error t)
      trader-map)))

(defn- parse-position
  [trader-map position]
  (let [trades         (:latest-trades trader-map)
        symbol         (get-in position [:instrument :symbol])
        opening-trade  (->> trades
                            (filter #(= symbol (:symbol %)))
                            last)
        symbol-balance (compute-position-size trader-map)
        asset-type     (-> (get-in position [:instrument :assetType])
                           (.toLowerCase)
                           keyword)
        long-quantity  (:longQuantity position)
        is-long?       (some-> long-quantity pos?)
        short-quantity (:shortQuantity position)
        is-short?      (some-> short-quantity pos?)]
    (when (not= :cash_equivalent asset-type)
      (merge
       {:symbol         symbol
        :balance-usd    symbol-balance
        :position-value (* (if is-long? long-quantity short-quantity)
                           (or (:price opening-trade)
                               (:averagePrice position)))
        :direction      (cond
                          is-long?  :long
                          is-short? :short
                          :else     :none)
        :volume         (if is-long? long-quantity short-quantity)
        :entry          (or (:price opening-trade)
                            (:averagePrice position)
                            0.0)
        :type           asset-type}))))

(defmethod with-positions "ameritrade"
  [trader-map]
  (try
    (let [account-id (->account-id trader-map)]
      (if-let [open-positions  (some->> (driver/ameritrade-get trader-map "accounts"
                                                               :params {"fields" "positions"})
                                        (account-for account-id)
                                        :positions
                                        not-empty)]
        (let [reset-positions (reduce-kv
                               (fn [m symbol position]
                                 (assoc m symbol (merge position
                                                        {:direction :none
                                                         :volume    0.0
                                                         :entry     0.0})))
                               {}
                               (:open-positions trader-map))
              positions       (->> open-positions
                                   (map #(parse-position trader-map %))
                                   (filterv identity)
                                   (reduce
                                    (fn [m {:keys [symbol] :as position}]
                                      (assoc m symbol position))
                                    {})
                                   (merge reset-positions))]
          (assoc trader-map :open-positions positions))
        trader-map))
    (catch Throwable t
      (log/error t)
      trader-map)))

(defn- cancel-order!
  [{:keys [nickname orders] :as trader-map} {:keys [symbol type direction]}]
  (let [open-orders (filter #(and (= symbol (name (:symbol %)))
                                  (= type (:type %))
                                  (= direction (:direction %)))
                            orders)]
    (doseq [{tx-id :order-id} open-orders]
      (let [cancel-response (driver/ameritrade-delete!
                             trader-map
                             (format "accounts/%s/orders/%s"
                                     (->account-id trader-map)
                                     tx-id))]
        (if (<= 400 (:status cancel-response))
          (log/error (:body cancel-response))
          (log/info (format "%s: canceled %s order %s" nickname symbol tx-id)))))))

;; ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; ;;
;;                                   longs                                    ;;
;; ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; ;;

(defmethod on-trade ["ameritrade" "long" "limit"]
  [{nickname :nickname :as trader-map} {:keys [symbol price stop-loss]}]

  ;; cancel existing long order if it exists
  (cancel-order! trader-map {:symbol symbol :type :limit :direction :long})

  (let [volume     (compute-volume trader-map price price stop-loss)
        instrument {:symbol    symbol
                    :assetType "EQUITY"}
        order-data (driver/ameritrade-post!
                    trader-map
                    (format "accounts/%s/orders" (->account-id trader-map))
                    {:session              "NORMAL"
                     :orderType            "LIMIT"
                     :duration             "GOOD_TILL_CANCEL"
                     :price                price
                     :orderStrategyType    "TRIGGER"
                     :orderLegCollection   [{:instruction "BUY"
                                             :quantity    (int volume)
                                             :instrument  instrument}]
                     :childOrderStrategies [{:session            "NORMAL"
                                             :orderType          "STOP"
                                             :stopPrice          stop-loss
                                             :duration           "GOOD_TILL_CANCEL"
                                             :orderStrategyType  "SINGLE"
                                             :orderLegCollection [{:instruction "SELL"
                                                                   :quantity    (int volume)
                                                                   :instrument  instrument}]}]})]
    (if (<= 400 (:status order-data))
      (do
        (log/error (format "%s: failed to open long limit for %s" nickname symbol))
        (log/error (:body order-data)))
      (log/info (format "%s: longing %.2f %s at price %.2f (stop-loss at %.2f)"
                        nickname volume symbol price stop-loss)))))

(defmethod on-trade ["ameritrade" "long" "take-profit"]
  [trader-map {:keys [symbol percentage] :as ev}]

  ;; cancel existing stop-loss order if it exists
  (cancel-order! trader-map {:symbol symbol :type :stop-loss :direction :long})

  (when-let [volume (get-in trader-map [:open-positions symbol :volume])]
    (let [profit-volume    (int (* volume percentage))
          remaining-volume (- volume profit-volume)
          instrument       {:symbol    symbol
                            :assetType "EQUITY"}]
      (driver/ameritrade-post!
       trader-map
       (format "accounts/%s/orders" (->account-id trader-map))
       {:session            "NORMAL"
        :orderType          "MARKET"
        :duration           "DAY"
        :orderStrategyType  "SINGLE"
        :orderLegCollection [{:instruction "SELL"
                              :quantity    (int volume)
                              :instrument  instrument}]})
      (on-trade (assoc-in trader-map
                          [:open-positions (keyword symbol) :volume]
                          remaining-volume)
                (assoc ev :order-type "stop-loss")))))

(defmethod on-trade ["ameritrade" "long" "stop-loss"]
  [{nickname :nickname :as trader-map} {:keys [symbol stop-loss]}]

  ;; cancel existing stop-loss order if it exists
  (cancel-order! trader-map {:symbol symbol :type :stop-loss :direction :long})

  (let [volume     (get-in trader-map [:open-positions (keyword symbol) :volume])
        instrument {:symbol    symbol
                    :assetType "EQUITY"}
        order-data (driver/ameritrade-post!
                    trader-map
                    (format "accounts/%s/orders" (->account-id trader-map))
                    {:session            "NORMAL"
                     :orderType          "STOP"
                     :duration           "GOOD_TILL_CANCEL"
                     :stopPrice          stop-loss
                     :orderStrategyType  "SINGLE"
                     :orderLegCollection [{:instruction "SELL"
                                           :quantity    (int volume)
                                           :instrument  instrument}]})]
    (if (<= 400 (:status order-data))
      (do
        (log/error (format "%s: failed to open long stop-loss for %s" nickname symbol))
        (log/error (:body order-data)))
      (log/info (format "%s: set %s long stop-loss to %.2f"
                        nickname symbol stop-loss)))))

;; ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; ;;
;;                                   shorts                                   ;;
;; ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; ;;

(defmethod on-trade ["ameritrade" "short" "limit"]
  [{nickname :nickname :as trader-map} {:keys [symbol price stop-loss]}]

  ;; cancel existing short order if it exists
  (cancel-order! trader-map {:symbol symbol :type :limit :direction :short})

  (let [volume     (compute-volume trader-map price price stop-loss)
        instrument {:symbol    symbol
                    :assetType "EQUITY"}
        order-data (driver/ameritrade-post!
                    trader-map
                    (format "accounts/%s/orders" (->account-id trader-map))
                    {:session              "NORMAL"
                     :orderType            "LIMIT"
                     :duration             "GOOD_TILL_CANCEL"
                     :price                price
                     :orderStrategyType    "TRIGGER"
                     :orderLegCollection   [{:instruction "SELL_SHORT"
                                             :quantity    (int volume)
                                             :instrument  instrument}]
                     :childOrderStrategies [{:session            "NORMAL"
                                             :orderType          "STOP"
                                             :stopPrice          stop-loss
                                             :duration           "GOOD_TILL_CANCEL"
                                             :orderStrategyType  "SINGLE"
                                             :orderLegCollection [{:instruction "BUY_TO_COVER"
                                                                   :quantity    (int volume)
                                                                   :instrument  instrument}]}]})]
    (if (<= 400 (:status order-data))
      (do
        (log/error (format "%s: failed to open short limit for %s" nickname symbol))
        (log/error (:body order-data)))
      (log/info (format "%s: shorting %.2f %s at price %.2f (stop-loss at %.2f)"
                        nickname volume symbol price stop-loss)))))

(defmethod on-trade ["ameritrade" "short" "take-profit"]
  [trader-map {:keys [symbol percentage] :as ev}]

  ;; cancel existing stop-loss order if it exists
  (cancel-order! trader-map {:symbol symbol :type :stop-loss :direction :short})

  (when-let [volume (get-in trader-map [:open-positions symbol :volume])]
    (let [profit-volume    (int (* volume percentage))
          remaining-volume (- volume profit-volume)
          instrument       {:symbol    symbol
                            :assetType "EQUITY"}]
      (driver/ameritrade-post!
       trader-map
       (format "accounts/%s/orders" (->account-id trader-map))
       {:session            "NORMAL"
        :orderType          "MARKET"
        :duration           "DAY"
        :orderStrategyType  "SINGLE"
        :orderLegCollection [{:instruction "BUY_TO_COVER"
                              :quantity    (int volume)
                              :instrument  instrument}]})
      (on-trade (assoc-in trader-map
                          [:open-positions (keyword symbol) :volume]
                          remaining-volume)
                (assoc ev :order-type "stop-loss")))))

(defmethod on-trade ["ameritrade" "short" "stop-loss"]
  [{nickname :nickname :as trader-map} {:keys [symbol stop-loss]}]

  ;; cancel existing stop-loss order if it exists
  (cancel-order! trader-map {:symbol symbol :type :stop-loss :direction :short})

  (let [volume     (get-in trader-map [:open-positions (keyword symbol) :volume])
        instrument {:symbol    symbol
                    :assetType "EQUITY"}
        order-data (driver/ameritrade-post!
                    trader-map
                    (format "accounts/%s/orders" (->account-id trader-map))
                    {:session            "NORMAL"
                     :orderType          "STOP"
                     :duration           "GOOD_TILL_CANCEL"
                     :stopPrice          stop-loss
                     :orderStrategyType  "SINGLE"
                     :orderLegCollection [{:instruction "BUY_TO_COVER"
                                           :quantity    (int volume)
                                           :instrument  instrument}]})]
    (if (<= 400 (:status order-data))
      (do
        (log/error (format "%s: failed to open short stop-loss for %s" nickname symbol))
        (log/error (:body order-data)))
      (log/info (format "%s: set %s short stop-loss to %.2f"
                        nickname symbol stop-loss)))))
