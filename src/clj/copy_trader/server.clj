(ns copy-trader.server
  (:require
   [copy-trader.core :as core]
   [copy-trader.exchange.trader :as trader]
   [copy-trader.websocket.server :as ws-server]
   [muuntaja.core :as m]
   [reitit.coercion.spec :as rcs]
   [reitit.ring :as ring]
   [reitit.ring.coercion :as rrc]
   [reitit.ring.middleware.muuntaja :as muuntaja]
   [reitit.ring.middleware.parameters :as parameters]
   [ring.adapter.jetty9 :as jetty])
  (:import
   [org.eclipse.jetty.server Server]))

(def ^:private default-route-data
  {:muuntaja   (m/create m/default-options)
   :coercion   rcs/coercion
   :middleware [parameters/parameters-middleware
                muuntaja/format-middleware
                rrc/coerce-exceptions-middleware
                rrc/coerce-request-middleware
                rrc/coerce-response-middleware]})

(defn- index [_]
  {:status  401
   :headers {"Content-Type" "text/html"}
   :body    "Access Denied"})

(defn authorize-trader-with-account-id!
  [{:keys [account-id code]}]
  (when-let [trader-state (->> @core/state
                               :traders
                               (filter (fn [trader-state]
                                         (= account-id (-> @trader-state
                                                           :credentials
                                                           :account_id)))))]
    (swap! trader-state #(trader/with-authorization % code)))
  {:status 202
   :headers {"Content-Type" "text/html"}
   :body "Accepted"})

(def http-handler
  (ring/ring-handler
   (ring/router
    [["/" {:get index}]
     ["/:account-id/authorize" {:any     {:parameters {:query {:code string?}}}
                                :handler (fn [req]
                                           (authorize-trader-with-account-id!
                                            {:account-id (-> req :path-params :account-id)
                                             :code       (-> req :parameters :query :code)}))}]]
    {:data      default-route-data
     :conflicts (constantly nil)})
   (ring/routes
    (ring/create-default-handler))))

(defn start!
  []
  (swap! core/state assoc :http-server
         (jetty/run-jetty http-handler
                          {:port 51585
                           :join? false
                           :websockets {"/ws" ws-server/handler}})))

(defn halt!
  []
  (when-let [server (:http-server @core/state)]
    (.stop ^Server server)
    (swap! core/state dissoc :http-server)))
