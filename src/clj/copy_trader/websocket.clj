(ns copy-trader.websocket
  (:require
   [cheshire.core :refer [generate-string parse-string]]
   [clojure.tools.logging :as log]
   [copy-trader.core :as core]
   [copy-trader.exchange.trader :as trader]
   [gniazdo.core :as ws]))

(defmulti on-event
  (fn [{:keys [message-code _payload]}]
    message-code))

(defmethod on-event :default
  [_]
  :not-implemented)

(defmethod on-event :trade
  [{:keys [payload]}]
  (trader/on-trade payload))

;; TODO -- implement subscription to symbols

;; TODO -- implement too-many-clients error handler

(defn- on-receive
  [message-json]
  (on-event (parse-string message-json true)))

(defn- on-error
  [error]
  (log/error error))

(defn- on-close
  [uri]
  (let [socket (get-in @core/state [:clients uri])]
    (try
      (ws/close socket)
      (catch Throwable t
        (log/error t)))
    (swap! core/state update-in [:clients] dissoc uri)
    :disconnected))

(defn connect!
  [uri auth-token]
  (when (:is-running? @core/state)
    (when-let [socket (ws/connect uri
                                  :on-receive on-receive
                                  :on-error on-error
                                  :on-close #(on-close uri))]
      (swap! core/state assoc-in :clients uri socket)
      (ws/send-msg
       socket
       (generate-string
        {:authenticate {:token auth-token}}))
      :connected)))

(defn disconnect!
  [uri]
  ;; TODO -- maybe we'll want to send unsubscribe events here
  (on-close uri))
