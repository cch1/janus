(ns janus.ring
  "Identify routes from Ring-compatible requests and dispatch to Ring-compatible handlers"
  (:require [janus.route :as route]))

(extend-protocol janus.route/Dispatchable
  nil
  (dispatch [this request dispatch-table] (get dispatch-table this))
  clojure.lang.Var
  (dispatch [this request _]
    ((deref this) request))
  clojure.lang.Keyword
  (dispatch [this request dispatch-table]
    (let [f (get dispatch-table this)]
      (assert f (format "No dispatch function found for keyword %s" this))
      (f request)))
  clojure.lang.Symbol
  (dispatch [this request dispatch-table]
    (let [f (get dispatch-table this)]
      (assert f (format "No dispatch function found for symbol %s" this))
      (f request))))

(defn make-dispatcher
  ([] (make-dispatcher {}))
  ([dispatch-table]
   (let [dispatch-table (merge {nil {:status 404 :body "Not Found" :headers {"Content-Type" "text/plain"}}}
                               dispatch-table)]
     (fn dispatcher
       [{:keys [route-params params] router ::router :as request}]
       (let [request (if (and params route-params)
                       (update request :params merge (into {} (filter (fn [[k v]] (keyword? k)))
                                                           route-params))
                       request)]
         (route/dispatch router request dispatch-table))))))

(defmulti exception-handler "Handle exceptions that don't allow routing to execute" class)

(defmethod exception-handler java.net.URISyntaxException
  [e] {:status 400 :body "Invalid URI Syntax" :headers {"Content-Type" "text/plain"}})

(defmethod exception-handler :default
  [e] (throw e))

(defn wrap-identify
  "Create Ring middleware to identify the route of a request based on `:path-info` or `:uri`"
  ([handler router] (wrap-identify handler router exception-handler))
  ([handler router exception-handler]
   {:pre [(instance? janus.route.Router router)]}
   (fn identifier
     [{:keys [uri path-info] :as req}]
     (try (let [r (route/identify router (or path-info uri))
                route-params (when r (into {} (route/parameters r)))
                req (if route-params (assoc req :route-params route-params) req)]
            (handler (assoc req ::router r)))
          (catch Exception e (exception-handler e))))))
