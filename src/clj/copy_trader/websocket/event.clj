(ns copy-trader.websocket.event)

(defmulti on-event
  (fn [_uri {:keys [message-code _payload]}]
    message-code))

(defmethod on-event :default
  [_uri _msg]
  :not-implemented)

;; TODO -- implement too-many-clients error handler
