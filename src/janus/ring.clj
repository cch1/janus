(ns janus.ring
  (:require [janus.route :as route]))

(defprotocol Dispatchable
  (dispatch [this request args]))

(extend-protocol Dispatchable
  nil
  (dispatch [this request args] (-> args first (get this)))
  clojure.lang.Fn
  (dispatch [this request args]
    (this request))
  clojure.lang.Var
  (dispatch [this request args]
    ((deref this) request))
  clojure.lang.Keyword
  (dispatch [this request args]
    (let [f (-> args first (get this))]
      (assert f (format "No dispatch function found for keyword %s" this))
      (f request)))
  clojure.lang.Symbol
  (dispatch [this request args]
    (let [f (-> args first (get this))]
      (assert f (format "No dispatch function found for symbol %s" this))
      (f request)))
  janus.route.Router
  (dispatch [this request args]
    (let [[_ [_ dispatchable _]] (route/node this)]
      (dispatch dispatchable request args))))

(defn make-dispatcher
  [& args]
  (let [args (or args [{nil {:status 404 :body "Not Found"}}])]
    (fn dispatcher
      [{router ::router :as request}]
      (dispatch router request args))))

(defn make-identifier
  "Create Ring middleware to identify the route of a request based on `:path-info` or `:uri`"
  [handler router]
  {:pre [(instance? janus.route.Router router)]}
  (fn route-identifier
    [{:keys [uri path-info] :as req}]
    (let [r (route/identify router (or path-info uri))
          route-params (when r (into {} (filter (fn [[k v]] (keyword? k))) (route/parameters r)))]
      (handler (-> req
                  (update :params merge route-params)
                  (assoc :route-params route-params)
                  (assoc ::router r))))))
