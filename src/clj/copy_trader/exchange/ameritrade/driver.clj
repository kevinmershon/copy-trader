(ns copy-trader.exchange.ameritrade.driver
  (:require
   [clj-http.client   :as client]
   [clj-http.conn-mgr :as conn-mgr]
   [clj-time.coerce   :as ctc]
   [clj-time.core     :as ctcore]
   [clj-time.format   :as ctf]
   [copy-trader.util :refer [atom?]]))

(def ^:private cm (clj-http.conn-mgr/make-reusable-conn-manager {:threads 4}))

(defn ameritrade-request*
  [trader-map client-fn path & {:keys [query-params params no-authorization?]
                                :or {query-params {}
                                     params {}
                                     no-authorization? false}}]
  {:pre [(map? trader-map) (fn? client-fn) (or (nil? path) (string? path))]}
  (client-fn
   (str "https://api.tdameritrade.com/v1/" path)
   {:connection-manager cm
    :content-type       (if no-authorization?
                          :x-www-form-urlencoded
                          :json)
    :as                 :json
    :headers            (merge
                         {}
                         (when-not no-authorization?
                           {"Authorization"
                            (str "Bearer " (get-in trader-map [:credentials :access_token]))}))
    :query-params       query-params
    :form-params        params
    :socket-timeout     5000
    :connection-timeout 5000
    :throw-exceptions   false}))

(defn ameritrade-get
  [trader-map path & {:keys [params]
                      :or {params {}}}]
  (some-> (ameritrade-request* trader-map client/get path :query-params params)
          :body))

(defn ameritrade-post!
  [trader-map path params & {:keys [no-authorization?]
                             :or {no-authorization? false}}]
  (ameritrade-request* trader-map client/post path
                       :params params
                       :no-authorization? no-authorization?))

(defn ameritrade-delete!
  [trader-map path]
  (ameritrade-request* trader-map client/delete path))

(defn- handle-authorize-response!
  [trader-state response]
  {:pre [(atom? trader-state) (map? response)]}
  (let [updated-credentials
        (merge
         (when (:refresh_token_expires_in response)
           (let [refresh-token-expires (->> (ctcore/seconds (:refresh_token_expires_in response))
                                            (ctcore/plus (ctcore/now))
                                            ctc/to-sql-time)]
             {:refresh_token         (:refresh_token response)
              :refresh_token_expires refresh-token-expires}))
         (when (:expires_in response)
           (let [access-token-expires (->> (ctcore/seconds (:expires_in response))
                                           (ctcore/plus (ctcore/now))
                                           ctc/to-sql-time)]
             {:access_token         (:access_token response)
              :access_token_expires access-token-expires})))]
    ;; FIXME -- persist refresh and access token to Redis?
    (swap! trader-state update-in [:credentials] merge updated-credentials))
  :ok)

(defn refresh-token!
  [trader-state & {:keys [full-refresh?]
                   :or   {full-refresh? false}}]
  {:pre [(atom? trader-state)]}
  (let [trader-map    @trader-state
        refresh-token (-> trader-map :credentials :refresh_token)
        app-id        (-> trader-map :credentials :app_id)
        response      (-> (ameritrade-post! trader-map "oauth2/token"
                                            (merge
                                             (when full-refresh?
                                               {:access_type "offline"})
                                             {"grant_type"    "refresh_token"
                                              "refresh_token" refresh-token
                                              "client_id"     app-id})
                                            :no-authorization? true)
                          :body)]
    (handle-authorize-response! trader-state response)))

(defn maybe-refresh-tokens!
  [trader-state]
  {:pre [(atom? trader-state)]}
  (let [{:keys [refresh_token_expires access_token_expires]}
        (:credentials @trader-state)]
    (when (and (not (nil? refresh_token_expires))
               (not (nil? access_token_expires)))
      (cond
        (ctcore/before? (if (string? refresh_token_expires)
                          (ctf/parse
                           (ctf/formatters :date-time-no-ms)
                           refresh_token_expires)
                          (ctc/from-sql-time refresh_token_expires))
                        (ctcore/plus (ctcore/now)
                                     (ctcore/days 1)))
        (refresh-token! trader-state :full-refresh? true)

        (ctcore/before? (if (string? access_token_expires)
                          (ctf/parse
                           (ctf/formatters :date-time-no-ms)
                           access_token_expires)
                          (ctc/from-sql-time access_token_expires))
                        (ctcore/plus (ctcore/now)
                                     (ctcore/minutes 5)))
        (refresh-token! trader-state)))))
