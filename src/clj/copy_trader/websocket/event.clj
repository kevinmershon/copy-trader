(ns copy-trader.websocket.event)

(defmulti on-event
  (fn [{:keys [message-code _payload]}]
    message-code))

(defmethod on-event :default
  [_]
  :not-implemented)

;; TODO -- implement too-many-clients error handler
