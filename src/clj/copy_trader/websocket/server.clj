(ns copy-trader.websocket.server
  (:require
   [cheshire.core :refer [parse-string generate-string]]
   [copy-trader.core :as core]
   [clojure.tools.logging :as log]
   [ring.adapter.jetty9 :as jetty]
   [copy-trader.exchange.traders :as traders]
   [copy-trader.websocket.event :as ws-event]
   [copy-trader.websocket.security :as security])
  (:import [java.util UUID]))

(defn- send-to-client!
  [ws {:keys [_message-code _payload] :as msg}]
  (if (jetty/connected? ws)
    (locking ws
      (jetty/send! ws (generate-string msg)))
    (swap! core/state update :ws-clients dissoc ws)))

(defn send-to-clients!
  [message & {:keys [except-client]}]
  (doseq [[ws attrs] (:ws-clients @core/state)]
    (when (and (:authenticated? attrs)
               (not= except-client ws))
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

(defmethod on-event :auth-challenge-ack
  [ws {:keys [_message-code payload]}]
  (let [auth-challenge (get-in @core/state [:ws-clients ws :auth-challenge])]
    (if (= auth-challenge (:auth-challenge payload))
      (do
        (log/info "WebSocket client authenticated.")
        (swap! core/state assoc-in [:ws-clients ws :authenticated?] true))
      (try
        (log/error "WebSocket failed auth challenge. Disconnecting.")
        (swap! core/state update :ws-clients dissoc ws)
        (jetty/close! ws)
        (catch Throwable t
          (log/error t))))))

;; re-firing a trade signal from our server
(defmethod ws-event/on-event :refire-trade-down
  [_uri {:keys [_message-code payload]}]
  {:pre [(string? payload)]}
  ;; payload is already signed
  ;; re-fire event to all downstream clients
  (send-to-clients! {:message-code :trade
                     :payload      payload}))

;; receiving a trade signal from our client
(defmethod on-event :trade
  [ws {:keys [_message-code payload] :as msg}]
  (try
    (let [trade-payload (-> (security/unsign-payload payload))]
      (log/info "Received trade from client" trade-payload)
      ;; re-fire event to other downstream clients
      (send-to-clients! (assoc msg :message-code :trade)
                        :except-client ws)
      ;; re-fire event to our server
      (ws-event/on-event "" {:message-code :refire-trade-up
                             :payload      payload})
      (traders/dispatch-trade! trade-payload))
    (catch Throwable t
      (log/error t))))

(defmethod on-event :ping
  [ws _msg]
  (send-to-client! ws {:message-code :pong
                       :payload      {}}))

(defn- on-connect
  [ws]
  (log/info "WebSocket client connected")
  (let [auth-challenge (str (UUID/randomUUID))]
    (swap! core/state update-in [:ws-clients ws]
           merge {:authenticated? false
                  :auth-challenge auth-challenge
                  :subscriptions  #{}})
    (send-to-client! ws {:message-code :auth-challenge
                         :payload      (security/sign-payload
                                        {:auth-challenge auth-challenge})})))

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
    (let [edn-msg (-> json-message
                      (parse-string true)
                      (update :message-code keyword))]
      (log/debug "Received message from client" edn-msg)
      (on-event ws edn-msg))
    (catch Throwable t
      (log/error t))))

(def handler
  ;; GOTCHA -- these need to be wrapped for hot reloading to work
  {:on-connect (fn [ws] (on-connect ws))
   :on-error   (fn [ws err] (on-error ws err))
   :on-close   (fn [ws status-code reason] (on-close ws status-code reason))
   :on-text    (fn [ws text] (on-receive ws text))})
