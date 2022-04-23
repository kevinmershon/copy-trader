(ns copy-trader.websocket.client
  (:require
   [cheshire.core :refer [parse-string generate-string]]
   [clojure.tools.logging :as log]
   [copy-trader.config :refer [config]]
   [copy-trader.core :as core]
   [copy-trader.exchange.trader :as trader]
   [copy-trader.websocket.event :as ws-event]
   [copy-trader.websocket.security :as security]
   [gniazdo.core :as ws]))

;; receiving a trade signal from our server
(defmethod ws-event/on-event :trade
  [{:keys [payload]}]
  (let [trade-payload (security/unsign-payload payload)]
    ;; re-fire event to all downstream clients
    (ws-event/on-event {:message-code :refire-trade-down
                        :payload      payload})
    ;; then take the trade ourselves
    (trader/on-trade trade-payload)))

;; refiring a trade signal from our client
(defmethod ws-event/on-event :refire-trade-up
  [{:keys [_message-code _payload] :as msg}]
  (doseq [[_uri socket] (:clients @core/state)]
    (ws/send-msg socket (generate-string msg))))

(defn- on-receive
  [json-message]
  (ws-event/on-event (parse-string json-message true)))

(defn- on-error
  [error]
  (log/error error))

(defn- on-close
  [uri]
  (log/info (format "Disconnecting from %s" uri))
  (let [socket (get-in @core/state [:clients uri])]
    (try
      (ws/close socket)
      (catch Throwable t
        (log/error t)))
    (swap! core/state update-in [:clients] dissoc uri)
    :disconnected))

(defn connect!
  [uri]
  (log/info (format "Connecting to %s" uri))
  (when (:is-running? @core/state)
    (when-let [socket (ws/connect uri
                        :on-receive on-receive
                        :on-error on-error
                        :on-close #(on-close uri))]
      (swap! core/state assoc-in [:clients uri] socket)
      :connected)))

(defn disconnect!
  [uri]
  ;; TODO -- maybe we'll want to send unsubscribe events here
  (on-close uri))

(defn connect-clients!
  []
  (try
    (let [client-configs (:clients (config))]
      (doseq [{:keys [uri]} client-configs]
        (connect! uri)))
    :connected))

(defn disconnect-clients!
  []
  (let [connected-clients (:clients @core/state)]
    (doseq [[uri _socket] connected-clients]
      (disconnect! uri))))
