(ns janus.ring
  (:require [janus.route :refer [identify normalize]]))

(defprotocol Dispatchable
  (dispatch [this request args]))

(extend-protocol Dispatchable
  clojure.lang.Fn
  (dispatch [this request args]
    (this request))
  clojure.lang.Var
  (dispatch [this request args]
    ((deref this) request))
  clojure.lang.Keyword
  (dispatch [this request args]
    ((-> args first (get this)) request))
  clojure.lang.Symbol
  (dispatch [this request args]
    ((-> args first (get this)) request))
  java.lang.String
  (dispatch [this request args]
    ((-> args first (get this)) request)))

(defn make-dispatcher
  [& args]
  (fn dispatcher
    [{route ::route :as request}]
    (let [handler (-> route last first)]
      (dispatch handler request args))))

(defn make-identifier
  "Create Ring middleware to identify the route of a request based on `:path-info` or `:uri`"
  [handler routes]
  {:pre [routes]}
  (let [routes (normalize routes)]
    (fn route-identifier
      [{:keys [uri path-info] :as req}]
      (let [route (identify routes (or path-info uri))]
        (let [route-params (into {} (filter (fn [[k v]] (keyword? k))) route)]
          (handler (-> req
                      (update :params merge route-params)
                      (assoc :route-params route-params)
                      (assoc ::route route))))))))
