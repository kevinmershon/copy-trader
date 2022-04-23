(ns user
  (:require copy-trader.console))

(defn dev
  []
  (in-ns 'copy-trader.console)
  (eval '(go)))

(dev)
