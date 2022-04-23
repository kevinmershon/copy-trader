(ns copy-trader.websocket.server
  (:require
   [cheshire.core :refer [parse-string generate-string]]
   [copy-trader.core :as core]
   [clojure.tools.logging :as log]
   [ring.adapter.jetty9 :as jetty]
   [copy-trader.websocket.security :as security]
   [copy-trader.websocket.event :as ws-event]
   [copy-trader.exchange.trader :as trader]))

(defn- send-to-client!
  [ws {:keys [_message-code _payload] :as msg}]
  (if (jetty/connected? ws)
    (locking ws
      (jetty/send! ws (generate-string msg)))
    (swap! core/state update :ws-clients dissoc ws)))

(defn send-to-clients!
  [message & {:keys [except-client]}]
  (doseq [ws (keys (:ws-clients @core/state))]
    (when (not= except-client ws)
      (try
        (send-to-client! ws message)
        (catch Throwable t
          (log/error t))))))

(defmulti on-event
  (fn [_ws {:keys [message-code payload]}]
    {:pre [(keyword? message-code) (seqable? payload)]}
    message-code))

(defmethod on-event :default
  [_ws {:keys [_message-code payload]}]
  (log/error "Unrecognized websocket command:" payload))

;; re-firing a trade signal from our server
(defmethod ws-event/on-event :refire-trade-down
  [{:keys [_message-code payload]}]
  (let [msg {:message-code :trade
             :payload      (security/sign-payload payload)}]
    ;; re-fire event to all downstream clients
    (send-to-clients! :trade msg)))

;; receiving a trade signal from our client
(defmethod on-event :trade
  [ws {:keys [_message-code payload] :as msg}]
  (try
    (let [trade-payload (security/unsign-payload payload)]
      ;; re-fire event to other downstream clients
      (send-to-clients! msg :except-client ws)
      ;; re-fire event to our server
      (ws-event/on-event {:message-code :refire-trade-up
                          :payload      payload})
      ;; then take the trade ourselves
      (trader/on-trade trade-payload))
    (catch Throwable t
      (log/error t))))

(defmethod on-event :ping
  [ws _msg]
  (send-to-client! ws {:message-code :pong
                       :payload      "{}"}))

(defn- on-connect
  [ws]
  (log/info "WebSocket client connected")
  (swap! core/state assoc-in [:ws-clients ws :subscriptions] #{}))

(defn- on-error
  [_ws err]
  (log/error err "WebSocket error"))

(defn- on-close
  [ws status-code reason]
  (log/info "WebSocket client disconnected" status-code reason)
  (swap! core/state update :ws-clients dissoc ws))

(defn- on-receive
  [ws json-message]
  (try
    (on-event ws (parse-string json-message true))
    (catch Throwable t
      (log/error t))))

(def handler
  ;; GOTCHA -- these need to be wrapped for hot reloading to work
  {:on-connect (fn [ws] (on-connect ws))
   :on-error   (fn [ws err] (on-error ws err))
   :on-close   (fn [ws status-code reason] (on-close ws status-code reason))
   :on-text    (fn [ws text] (on-receive ws text))})
