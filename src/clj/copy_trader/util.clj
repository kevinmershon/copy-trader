(ns copy-trader.util)

(defn to-precision
  [n precision]
  (let [multiplier (Math/pow 10 precision)]
    (/ (Math/floor (* n multiplier))
       multiplier)))
