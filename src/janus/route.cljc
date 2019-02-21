(ns janus.route
  (:import #?(:cljs goog.Uri
              :clj [java.net URI URLDecoder URLEncoder]))
  (:require [clojure.string :as string]
            [clojure.zip :as z]
            [clojure.spec.alpha :as s]
            [clojure.core.match :as m]))

(defprotocol AsSegment
  "An abstraction for concisely representing the construction and identification of route segments"
  (match [this segment] "If the given segment matches this, return the match context (if any), otherwise falsey")
  (build [this options] "Build the segment represented by this with the given options"))

(defprotocol Dispatchable
  (dispatch [this request args]))

(defprotocol Routable
  "An abstraction for an entity located in the route tree that can process move instructions by
  returning a new instance"
  (root [this] "Return a new routable located at the root")
  (parent [this] "Return a new routable located at the parent of this")
  (identify [this path] "Return a new routable based on the given path (URI)")
  (generate [this params] "Return a new routable based on the given path parameters"))

(defprotocol Routed
  "An abstraction for an entity located in the route tree that can describe its position"
  (path [this] [this generalized?] "Return the path of the route as a string, optionally generalized")
  (identifiers [this] "Return the route as a sequence of segment identifiers")
  (parameters [this]  "Return map of segment identifiers to route parameters"))

(extend-protocol AsSegment
  nil ; implicitly matched and generated placeholder -used by the root route.
  (match [this segment])
  (build [this _])
  String ; constant, invertible
  (match [this segment] (when (= this segment) segment))
  (build [this args] (if (sequential? args)
                       (apply format this args)
                       this))
  clojure.lang.Keyword ; constant, invertible
  (match [this segment] (when (= (name this) segment) segment))
  (build [this _] (name this))
  java.lang.Boolean ; invertible
  (match [this segment] (when this segment))
  (build [this args] args)
  java.util.regex.Pattern ; invertible
  (match [this segment] (when-let [m (re-matches this segment)]
                          (cond (string? m) m
                                (vector? m) (rest m))))
  (build [this args] (if (sequential? args) (apply str args) args))
  clojure.lang.PersistentVector ; invertible when elements are inverses of each other
  (match [this segment] (match (first this) segment))
  (build [this args] (build (second this) args))
  clojure.lang.Fn ; potentially invertible
  (match [this segment] (this segment))
  (build [this args] (this args)))

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

(defprotocol Zippable
  (branch? [route] "Is it possible for this node to have children?")
  (children [route] "Return children of this node.")
  (make-node [route children] "Makes new node from existing node and new children."))

(defprotocol Identifiable
  (ident [this]))

(defprotocol ConformableRoute
  (conform [route] "Return the conformed form of this route"))

(deftype Route [identifiable as-segment dispatchable children]
  Zippable
  (branch? [this] (seq children))
  (children [this] children)
  (make-node [this children] (Route. identifiable as-segment dispatchable children))
  ConformableRoute
  (conform [this] this)
  AsSegment
  (match [this segment] (match as-segment segment))
  (build [this options] (build as-segment options))
  Identifiable
  (ident [this] identifiable)
  Dispatchable
  (dispatch [this request dispatch-table] (dispatch dispatchable request dispatch-table)))

(deftype RecursiveRoute [identifiable as-segment dispatchable]
  Zippable
  (branch? [this] true)
  (children [this] [this])
  (make-node [this children] this)
  ConformableRoute
  (conform [this] this)
  AsSegment
  (match [this segment] (match as-segment segment))
  (build [this options] (build as-segment options))
  Identifiable
  (ident [this] identifiable)
  Dispatchable
  (dispatch [this request dispatch-table] (dispatch dispatchable request dispatch-table)))

(extend-protocol Dispatchable
  clojure.lang.Fn
  (dispatch [this request args] (this request))
  Object
  (dispatch [this request args] this))

(s/def ::segment (partial satisfies? AsSegment))
(s/def ::dispatchable (partial satisfies? Dispatchable))

(defn- conform-ipersistentvector
  [ipv]
  (let [[identifiable v] ipv
        s (name identifiable)
        as-segment? (partial s/valid? ::segment)
        dispatchable? (partial s/valid? ::dispatchable)]
    (cond
      (vector? v) (m/match [(count v) v]
                           [0 []]
                           , (conform [identifiable [s identifiable ()]])
                           [1 [(a :guard seqable?)]]
                           , (conform [identifiable [s identifiable a]])
                           [1 [(a :guard as-segment?)]]
                           , (conform [identifiable [a identifiable ()]])
                           [1 [a]]
                           , (conform [identifiable [s a ()]])
                           [2 [(a :guard as-segment?) (b :guard seqable?)]]
                           , (conform [identifiable [a identifiable b]])
                           [2 [(a :guard dispatchable?) (b :guard seqable?)]]
                           , (conform [identifiable [s a b]])
                           [2 [(a :guard as-segment?) b]]
                           , (conform [identifiable [a b ()]])
                           [3 [(a :guard as-segment?) b (c :guard seqable?)]]
                           , (->Route identifiable a b (map conform c)) ; terminus
                           :else (throw (ex-info "Unrecognized route format" {::route ipv})))
      (string? v) (conform [identifiable [v identifiable ()]])
      (seqable? v) (conform [identifiable [s identifiable v]])
      (or (var? v) (fn? v)) (conform [identifiable [s v ()]])
      :else (conform [identifiable [v identifiable ()]]))))

(extend-protocol ConformableRoute
  clojure.lang.PersistentVector
  (conform [this] (conform-ipersistentvector this))
  clojure.lang.MapEntry
  (conform [this] (conform-ipersistentvector this))
  clojure.lang.Keyword
  (conform [this] (conform [::root [nil this ()]])))

(defn- r-zip
  "Return a zipper for a normalized route data structure"
  [route]
  (z/zipper branch? children make-node route))

(defn- normalize-target [target] (if (vector? target) target [target nil]))

(defn- normalize-uri
  [uri]
  #?(:clj (.getRawPath (.normalize (URI. uri)))
     :cljs (.getPath (goog.Uri. uri))))

(defn- conform*
  "Yields `route => [identifiable [as-segment dispatchable routes]]`"
  ([identifiable dispatchable route] (conform* [identifiable [true dispatchable route]]))
  ([dispatchable route] (conform* [::root [nil dispatchable route]]))
  ([] (conform* [::root [nil ::root ()]])) ; degenerate route table
  ([route]
   {:pre [(satisfies? ConformableRoute route)] :post [(satisfies? Zippable %)]}
   (conform route)))

(defrecord Router [zipper params]
  Routable
  (root [this] (Router. (r-zip (z/root zipper)) []))
  (parent [this] (when-let [z (z/up zipper)] (Router. z (vec (butlast params)))))
  (identify [this uri]
    (if-let [segments (seq (map url-decode (rest (string/split (normalize-uri uri) #"/"))))]
      (loop [rz (z/down zipper) segments segments params params]
        (when rz
          (let [route (z/node rz)]
            (if-let [p (match route (first segments))]
              (if-let [remaining-segments (seq (rest segments))]
                (recur (z/down rz) remaining-segments (conj params p))
                (Router. rz (conj params p)))
              (recur (z/right rz) segments params)))))
      this))
  (generate [this targets]
    (if-let [ps (seq (map normalize-target targets))]
      (loop [rz (z/down zipper) [[i p] & remaining-ps :as ps] ps params params]
        (when rz
          (let [route (z/node rz)]
            (if-let [p' (when (= (.ident route) i) (build route p))]
              (if (seq remaining-ps)
                (recur (z/down rz) remaining-ps (conj params p))
                (Router. rz (conj params p)))
              (recur (z/right rz) ps params)))))
      this))
  Routed
  (path [this] (path this false))
  (path [this generalized?]
    (let [nodes (rest (concat (z/path zipper) (list (z/node zipper))))
          f (fn [route p] (if generalized? (ident route) (url-encode (build route p))))
          segments (map f nodes params)]
      (str "/" (string/join "/" segments))))
  (identifiers [this] (map ident (concat (z/path zipper) (list (z/node zipper)))))
  (parameters [this] (map vector (rest (identifiers this)) params))
  Dispatchable
  (dispatch [this request dispatch-table] (dispatch (z/node zipper) request dispatch-table)))

#?(:clj
   (remove-method clojure.core/print-method Router)
   (remove-method clojure.core/print-method Route)
   (defmethod clojure.core/print-method Router
     [router ^java.io.Writer writer]
     (.write writer (format "#<Router \"%s\">" (path router))))
   (defmethod clojure.core/print-method Route
     [route-node ^java.io.Writer writer]
     (doto writer
       (.write "#<Route ")
       (.write (str [(.identifiable route-node)
                     (.as-segment route-node)
                     (.dispatchable route-node)
                     (map (fn [n] (.identifiable n)) (.children route-node))]))
       (.write ">")))

   :cljs
   (extend-protocol IPrintWithWriter
     Router
     (-pr-writer [this writer opts]
       (-write writer (format "#<Router %s>" (path this))))))

(defn router
  [route]
  (Router. (-> route conform* r-zip) []))

(defn recursive-route [name as-segment dispatch]
  (->RecursiveRoute name as-segment dispatch))
