(ns copy-trader.websocket.security
  (:require
   [buddy.core.keys :as buddy-keys]
   [buddy.sign.jwt :as jwt]))

(defonce ^:private ec-private-key (atom nil))
(def ^:private ec-public-key (buddy-keys/public-key "public-key.pem"))

(defn- load-private-key!
  []
  (when-not @ec-private-key
    (reset! ec-private-key (buddy-keys/private-key "private-key.pem"))))

(defn sign-payload
  [payload]
  (load-private-key!)
  (jwt/sign payload @ec-private-key {:alg :es512}))

(defn unsign-payload
  [signed-payload]
  (jwt/unsign signed-payload ec-public-key {:alg :es512}))
