(ns copy-trader.cache
  (:refer-clojure :exclude [key symbol])
  (:require
   [clojure.walk :refer [keywordize-keys]]
   [environ.core :refer [env]]
   [taoensso.carmine :as car]))

(defn redis-uri
  []
  (or (env :redis-endpoint)
      "redis://127.0.0.1:6379"))

(def ^:private redis-conn {:pool {} :spec {:uri (redis-uri)}})
(defmacro with-carmine [& body] `(car/wcar redis-conn ~@body))

(defn- save!
  []
  (with-carmine
    (car/save)))

(defn- del!
  [key]
  (with-carmine
    (car/del key)))

(defn- hset!
  [key field value]
  (with-carmine
    (car/hset key field value)))

(defn- hdel!
  [key field]
  (with-carmine
    (car/hdel key field)))

(defn- hget
  [key field]
  (with-carmine
    (car/hget key field)))

(defn- hgetall
  [key]
  (apply hash-map
         (with-carmine
           (car/hgetall key))))

(defn cache-orders!
  [{:keys [nickname orders] :as _trader-map}]
  (let [cache-key (str nickname "-orders")]
    (del! cache-key)
    (doseq [{:keys [status order-id] :as order} orders]
      (if (= :open status)
        (hset! cache-key order-id order)
        (hdel! cache-key order-id)))))

(defn get-orders
  [{:keys [nickname] :as _trader-map}]
  (let [cache-key (str nickname "-orders")]
    (or
     (some->> (hgetall cache-key)
              vals
              vec)
     [])))

(defn cache-positions!
  [{:keys [nickname open-positions] :as _trader-map}]
  (let [cache-key (str nickname "-positions")]
    (del! cache-key)
    (doseq [[symbol position] open-positions]
      (hset! cache-key symbol position))))

(defn get-positions
  [{:keys [nickname] :as _trader-map}]
  (let [cache-key (str nickname "-positions")]
    (keywordize-keys (hgetall cache-key))))

(defn with-orders-and-positions
  [trader-map]
  (merge trader-map
         {:orders         (get-orders trader-map)
          :open-positions (get-positions trader-map)}))

(defn with-cache
  [trader-map]
  (cache-orders! trader-map)
  (cache-positions! trader-map)
  (save!)

  ;; return
  trader-map)
