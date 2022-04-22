(ns copy-trader.core)

(defonce state (atom {:is-running? false}))

(defn is-running?
  []
  (:is-running? @state))
