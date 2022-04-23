(ns copy-trader.config
  (:require
   [cheshire.core :refer [parse-string]]
   [clojure.java.io :as io]))

(defn config
  []
  (-> (io/file "config.json")
      slurp
      (parse-string true)))
