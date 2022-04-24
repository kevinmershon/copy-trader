(ns copy-trader.util
  (:import [clojure.lang Atom]))

(defn atom?
  [x]
  (instance? Atom x))

(defn to-precision
  [n precision]
  (let [multiplier (Math/pow 10 precision)]
    (/ (Math/floor (* n multiplier))
       multiplier)))
