(ns copy-trader.exchange.alpaca.driver
  (:require
   [clj-http.conn-mgr :as conn-mgr]
   [clj-http.client :as client]))

(defonce ^:private cm (conn-mgr/make-reusable-conn-manager {:threads 4}))

(defn- endpoint-url
  [{trader-type :type :as _trader}]
  (if (= "paper" trader-type)
    "https://paper-api.alpaca.markets/v2/"
    "https://api.alpaca.markets/v2/"))

(defn- alpaca-request*
  [{:keys [credentials] :as trader-map} client-fn path & {:keys [params]}]
  {:pre [(map? credentials) (fn? client-fn) (or (nil? path) (string? path))]}
  (client-fn
   (str (endpoint-url trader-map) path)
   {:connection-manager cm
    :content-type       :json
    :as                 :json
    :headers            {"APCA-API-KEY-ID"     (:api_key credentials)
                         "APCA-API-SECRET-KEY" (:api_secret credentials)}
    :form-params        params
    :socket-timeout     5000
    :connection-timeout 5000
    :throw-exceptions   false}))

(defn alpaca-get
  [trader-map path]
  (some-> (alpaca-request* trader-map client/get path)
          :body))

(defn alpaca-post!
  [trader-map path params]
  (alpaca-request* trader-map client/post path :params params))

(defn alpaca-delete!
  [trader-map path]
  (alpaca-request* trader-map client/delete path))
