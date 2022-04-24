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

(defn send-to-server!
  [socket {:keys [_message-code _payload] :as msg}]
  (ws/send-msg socket (generate-string msg)))

(defmethod ws-event/on-event :auth-challenge
  [uri {:keys [payload] :as _msg}]
  (let [socket                 (get-in @core/state [:ws-servers uri])
        auth-challenge-payload (security/unsign-payload payload)]
    (send-to-server! socket {:message-code :auth-challenge-ack
                             :payload      auth-challenge-payload})))

(defmethod ws-event/on-event :pong
  [_uri _msg]
  :pong)

;; receiving a trade signal from our server
(defmethod ws-event/on-event :trade
  [_uri {:keys [payload] :as _msg}]
  (let [trade-payload (security/unsign-payload payload)]
    ;; re-fire event to all downstream clients
    (ws-event/on-event {:message-code :refire-trade-down
                        :payload      payload})
    ;; then take the trade ourselves
    (trader/on-trade trade-payload)))

;; refiring a trade signal from our client
(defmethod ws-event/on-event :refire-trade-up
  [_uri {:keys [_message-code _payload] :as msg}]
  (doseq [[_uri socket] (:ws-servers @core/state)]
    (send-to-server! socket msg)))

(defn- ping!
  [socket]
  (send-to-server! socket {:message-code :ping
                           :payload      {}}))

(defn- on-receive
  [uri json-message]
  (let [edn-msg (-> json-message
                    (parse-string true)
                    (update :message-code keyword))]
    (log/debug (str "Received message from " uri) edn-msg)
    (ws-event/on-event uri edn-msg)))

(defn- on-error
  [error]
  (log/error error))

(defn- on-close
  [uri]
  (log/info (format "Disconnecting from %s" uri))
  (let [socket (get-in @core/state [:ws-servers uri])]
    (try
      (ws/close socket)
      (catch Throwable t
        (log/error t)))
    (swap! core/state update-in [:ws-servers] dissoc uri)
    :disconnected))

(defn connect!
  [uri]
  (log/info (format "Connecting to %s" uri))
  (when (:is-running? @core/state)
    (when-let [socket (ws/connect uri
                        :on-receive #(on-receive uri %)
                        :on-error on-error
                        :on-close #(on-close uri))]
      (swap! core/state assoc-in [:ws-servers uri] socket)
      :connected)))

(defn disconnect!
  [uri]
  ;; TODO -- maybe we'll want to send unsubscribe events here
  (on-close uri))

(defn connect-clients!
  []
  (try
    (let [server-configs (:servers (config))]
      (doseq [{:keys [uri]} server-configs]
        (connect! uri)))
    :connected))

(defn disconnect-clients!
  []
  (let [connected-clients (:ws-servers @core/state)]
    (doseq [[uri _socket] connected-clients]
      (disconnect! uri))))

(defn keepalive-clients!
  []
  (let [server-configs (:servers (config))]
    (doseq [{:keys [uri]} server-configs]
      (if-let [socket (get-in @core/state [:ws-servers uri])]
        (ping! socket)
        (connect! uri)))))
