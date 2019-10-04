(ns janus.ring
  "Identify routes from Ring-compatible requests and dispatch to Ring-compatible handlers"
  (:require [janus.route :as route]))

(defn not-found
  ([_] {:status 404 :body "Not Found" :headers {"Content-Type" "text/plain"}})
  ([_ respond _] (respond {:status 404 :body "Not Found" :headers {"Content-Type" "text/plain"}})))

(defn not-implemented
  ([_] {:status 501 :body "Not Implemented" :headers {"Content-Type" "text/plain"}})
  ([_ respond _] (respond {:status 501 :body "Not Implemented" :headers {"Content-Type" "text/plain"}})))

(extend-protocol janus.route/Dispatchable
  nil
  (dispatch* [this args] (let [dispatch-table (first args)]
                           (route/dispatch* (get dispatch-table this not-found) args)))
  clojure.lang.Fn
  (dispatch* [this args] (apply this (rest args))) ; this is the Ring protocol for sync and async
  clojure.lang.Var
  (dispatch* [this args] (route/dispatch* (deref this) args))
  Object
  (dispatch* [this args] (let [dispatch-table (first args)]
                           (route/dispatch* (get dispatch-table this not-implemented) args))))

(defn- dispatch-request
  [{:keys [route-params params] router ::router :as request}]
  (if (and params route-params)
    (update request :params merge (into {} (filter (fn [[k v]] (keyword? k)))
                                        route-params))
    request))

(defn make-dispatcher
  ([] (make-dispatcher {}))
  ([dispatch-table]
   (fn dispatch
     ([{router ::router :as request}]
      (let [request (dispatch-request request)]
        (route/dispatch* router [dispatch-table request])))
     ([{router ::router :as request} respond raise]
      (let [request (dispatch-request request)]
        (route/dispatch* router [dispatch-table request respond raise]))))))

(defmulti exception-handler "Handle exceptions that don't allow routing to execute" class)

(defmethod exception-handler java.net.URISyntaxException
  [e] {:status 400 :body "Invalid URI Syntax" :headers {"Content-Type" "text/plain"}})

(defmethod exception-handler :default
  [e] (throw e))

(defn- identify-request
  [router {:keys [uri path-info] :as request}]
  (let [r (route/identify router (or path-info uri))
        route-params (when r (into {} (route/parameters r)))
        request (if route-params (assoc request :route-params route-params) request)]
    (assoc request ::router r)))

(defn wrap-identify
  "Create Ring middleware to identify the route of a request based on `:path-info` or `:uri`"
  ([handler router] (wrap-identify handler router exception-handler))
  ([handler router exception-handler]
   {:pre [(instance? janus.route.Router router)]}
   (fn identifier
     ([request]
      (try (handler (identify-request router request))
           (catch Exception e (exception-handler e))))
     ([request respond raise]
      (try (handler (identify-request router request) respond raise)
           (catch Exception e (respond (exception-handler e))))))))
