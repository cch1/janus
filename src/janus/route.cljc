(ns janus.route
  (:import #?(:cljs goog.Uri
              :clj [java.net URI URLDecoder URLEncoder]))
  (:require [clojure.string]
            [clojure.core.match :as m]))

(defn- url-encode
  [s]
  {:pre [(string? s)] :post [(string? s)]}
  (-> s
      #?(:clj (URLEncoder/encode "UTF-8")
         :cljs (js/encodeURIComponent))
      (.replace "+" "%20")))

(defn- url-decode
  ([s]
   {:pre [(string? s)]}
   #?(:clj (url-decode s "UTF-8")
      :cljs (some-> s (js/decodeURIComponent))))
  #?(:clj ([s encoding]
           (URLDecoder/decode s encoding))))

(defn- normalize-uri
  [uri]
  #?(:clj (.getRawPath (.normalize (URI. uri)))
     :cljs (.getPath (goog.Uri. uri))))

(defprotocol AsSegment
     (match [this segment])
     (build [this options]))

(extend-protocol AsSegment
  nil ; constant, invertible ; Used by the root route
  (match [this segment] (when (= "" segment) nil))
  (build [this _] "")
  String ; constant, invertible
  (match [this segment] (when (= this segment) this))
  (build [this _] this)
  clojure.lang.Keyword ; constant, invertible
  (match [this segment] (when (= (name this) segment) this))
  (build [this _] (name this))
  java.lang.Boolean ; invertible
  (match [this segment] segment)
  (build [this args] args)
  java.util.regex.Pattern ; invertible
  (match [this segment] (re-matches this segment))
  (build [this args] (if (vector? args) (first args) args))
  clojure.lang.PersistentVector ; invertible when elements are inverses of each other
  (match [this segment] (match (first this) segment))
  (build [this args] (build (second this) args))
  clojure.lang.Fn ; potentially invertible
  (match [this segment] (this segment))
  (build [this args] (this args)))


(let [named? (partial instance? clojure.lang.Named)
      as-segment? (partial satisfies? AsSegment)
      dispatchable? (fn [x] (or (fn? x) (var? x) (named? x)))]

  (defn- valid-route?
    [[identifier [as-segment dispatchable routes]]]
    (and (named? identifier)
       (as-segment? as-segment)
       (dispatchable? dispatchable)
       (every? valid-route? routes)))

  (defn normalize
    "Yields `route => [identifiable [as-segment dispatchable routes]]`"
    ([identifiable dispatchable route] (normalize [identifiable [nil dispatchable route]]))
    ([dispatchable route] (normalize [::root [nil dispatchable route]]))
    ([] (normalize [::root [nil ::root {}]])) ; degenerate route table, implicit root
    ([route]
     {:post [(valid-route? %)]}
     (if-not (sequential? route)
       (normalize [::root [nil route {}]]) ; degenerate route table; explicit root
       (let [[identifiable v] route]
         (if-not (vector? v)
           (normalize [identifiable [v]])
           (let [s (name identifiable)]
             (m/match [(count v) v]
                      [0 []]
                      , (normalize [identifiable [s identifiable {}]])
                      [1 [(a :guard coll?)]]
                      , (normalize [identifiable [s identifiable a]])
                      [1 [(a :guard as-segment?)]]
                      , (normalize [identifiable [a identifiable {}]])
                      [1 [(a :guard dispatchable?)]]
                      , (normalize [identifiable [s a {}]])
                      [2 [(a :guard as-segment?) (b :guard dispatchable?)]]
                      , (normalize [identifiable [a b {}]])
                      [2 [(a :guard as-segment?) (b :guard coll?)]]
                      , (normalize [identifiable [a identifiable b]])
                      [2 [(a :guard dispatchable?) (b :guard coll?)]]
                      , (normalize [identifiable [s a b]])
                      [3 [(a :guard as-segment?) (b :guard dispatchable?) (c :guard coll?)]]
                      ,[identifiable [a b (into (empty c) (map normalize c))]]
                      :else (throw (ex-info "Unrecognized route format" {::route route}))))))))))

(defn- match-segments
  [routes [segment & remaining-segments]]
  (if segment
    (some (fn [[identifiable [as-segment _ subroutes]]]
            (when-let [route-params (match as-segment segment)]
              (when-let [children (match-segments subroutes remaining-segments)]
                (cons [identifiable route-params] children))))
          routes)
    ()))

(defn identify*
  "Given a route definition data structure and a URI as a string, return the
   abbreviated segment sequence, if any, that completely matches the path."
  [route uri-string]
  (let [path (normalize-uri uri-string)
        segments (if (= "/" path) [] (map url-decode (rest (clojure.string/split path #"/"))))
        [_ [_ _ child-routes]] route]
    (match-segments child-routes segments)))

(defn identify
  "Given a route definition data structure and a URI as a string, return the
   segment sequence, if any, that completely matches the path."
  [route uri-string]
  (when-let [matched (identify* route uri-string)]
    (cons [(first route) nil] matched)))

(defn- normalize-target [target] (if (vector? target) target [target nil]))

(defn- build-segments
  [routes [target & targets]]
  (if-let [[target params] (when target (normalize-target target))]
    (or (some (fn [[identifiable [as-segment _ subroutes]]]
               (when (= identifiable target)
                 (let [segment (url-encode (build as-segment params))]
                   (cons segment (build-segments subroutes targets)))))
             routes)
       (throw (ex-info "Can't build route" {:target target :routes routes})))
    ()))

(defn generate*
  [route targets]
  (let [[_ [_ _ routes]] route
        segments (build-segments routes targets)]
    (str "/" (clojure.string/join "/" segments))))

(defn generate
  [route targets]
  (let [[target & targets] targets]
    (assert (= (first route) (-> target normalize-target first)) "Root target does not match")
    (generate* route targets)))
