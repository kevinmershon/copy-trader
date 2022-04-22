(ns user)

(defn dev
  []
  (println "Enter (go) to start"))

(defn go
  []
  (require 'copy-trader.console)
  (in-ns 'copy-trader.console)
  (eval '(go)))

(dev)
