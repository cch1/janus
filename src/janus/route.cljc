(ns janus.route
  "Construct routing tree, identify route from URIs and generate route from parameters"
  (:import #?@(:cljs (goog.Uri)
               :clj ([java.net URI])))
  (:require [clojure.string :as string]
            [clojure.zip :as z]
            [clojure.spec.alpha :as s]
            [clojure.core.match :as m]
            [clojure.pprint]
            #?@(:cljs ([goog.string :as gstring]
                       goog.string.format)
                :clj ([ring.util.codec]))))

(defprotocol Identifiable
  (ident [this] "Identify this logical route segment"))

(defprotocol AsSegment
  "An abstraction for concisely representing the construction and identification of route segments"
  (match [this segment] "If the given segment matches this, return the match context (if any), otherwise falsey")
  (build [this options] "Build the segment represented by this with the given options"))

(defprotocol Dispatchable
  (dispatch* [this args]))

(extend-protocol AsSegment
  nil ; implicitly matched and generated placeholder -used by the root route.
  (match [this segment])
  (build [this _])
  #?(:cljs string :clj String) ; constant, invertible
  (match [this segment] (when (= this segment) segment))
  (build [this args] (if (sequential? args)
                       (apply #?(:cljs gstring/format :clj format) this args)
                       this))
  #?(:cljs cljs.core/Keyword :clj clojure.lang.Keyword) ; constant, invertible
  (match [this segment] (when (= (name this) segment) segment))
  (build [this _] (name this))
  #?(:cljs boolean :clj java.lang.Boolean) ; invertible
  (match [this segment] (when this segment))
  (build [this args] args)
  #?(:cljs js/RegExp :clj java.util.regex.Pattern) ; invertible
  (match [this segment] (when-let [m (re-matches this segment)]
                          (cond (string? m) m
                                (vector? m) (rest m))))
  (build [this args] (if (sequential? args) (apply str args) args))
  #?(:cljs cljs.core/PersistentVector :clj clojure.lang.PersistentVector) ; invertible when elements are inverses of each other
  (match [this segment] (match (first this) segment))
  (build [this args] (build (second this) args))
  #?(:cljs function :clj clojure.lang.Fn) ; potentially invertible
  (match [this segment] (this segment))
  (build [this args] (this args)))

(defprotocol Zippable
  (branch? [route] "Is it possible for this node to have children?")
  (children [route] "Return children of this node.")
  (make-node [route children] "Makes new node from existing node and new children."))

(defprotocol ConformableRoute
  (conform [route] "Return the conformed form of this route"))

(defn- equivalent-routes [r0 r1] (and (= (type r0) (type r1))
                                      (= (.-identifiable r0) (.-identifiable r1))
                                      (= (.-as-segment r0) (.-as-segment r1))
                                      (= (.-dispatchable r0) (.-dispatchable r1))
                                      (= (.-children r0) (.-children r1))))

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
  (dispatch* [this args] (dispatch* dispatchable args))
  #?@(:cljs (IEquiv
             (-equiv [this other] (equivalent-routes this other)))
      :clj (java.lang.Object
            (hashCode [this] (.hashCode [identifiable as-segment dispatchable children]))
            (equals [this other] (equivalent-routes this other)))))

(defn- equivalent-recursive-routes [r0 r1] (and (= (type r0) (type r1))
                                                (= (.-identifiable r0) (.-identifiable r1))
                                                (= (.-as-segment r0) (.-as-segment r1))
                                                (= (.-dispatchable r0) (.-dispatchable r1))))

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
  (dispatch* [this args] (dispatch* dispatchable args))
  #?@(:cljs (IEquiv
             (-equiv [this other] (equivalent-recursive-routes this other)))
      :clj (java.lang.Object
            (hashCode [this] (.hashCode [identifiable as-segment dispatchable]))
            (equals [this other] (equivalent-recursive-routes this other)))))

(s/def ::segment #(satisfies? AsSegment %))
(s/def ::dispatchable #(satisfies? Dispatchable %))

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
  #?(:cljs cljs.core/PersistentVector :clj clojure.lang.PersistentVector)
  (conform [this] (conform-ipersistentvector this))
  #?(:cljs cljs.core/MapEntry :clj clojure.lang.MapEntry)
  (conform [this] (conform-ipersistentvector this))
  #?(:cljs cljs.core/Keyword :clj clojure.lang.Keyword)
  (conform [this] (conform [::root [nil this ()]])))

(defn- r-zip
  "Return a zipper for a normalized route data structure"
  [route]
  (z/zipper branch? children make-node route))

(s/def ::conformable-route #(satisfies? ConformableRoute %))
(s/def ::zippable #(satisfies? Zippable %))

(defn- conform*
  "Yields `route => [identifiable [as-segment dispatchable routes]]`"
  ([identifiable dispatchable route] (conform* [identifiable [true dispatchable route]]))
  ([dispatchable route] (conform* [::root [nil dispatchable route]]))
  ([] (conform* [::root [nil ::root ()]])) ; degenerate route table
  ([route]
   {:pre [(s/valid? ::conformable-route route)] :post [(s/valid? ::zippable %)]}
   (conform route)))

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

(defn- normalize-target [target] (if (vector? target) target [target nil]))

(defn- normalize-uri
  [uri]
  #?(:clj (.getRawPath (.normalize (URI. uri)))
     :cljs (.getPath (goog.Uri. uri))))

(def url-encode #?(:clj ring.util.codec/url-encode :cljs js/encodeURIComponent))
(def url-decode #?(:clj ring.util.codec/url-decode :cljs js/decodeURIComponent))

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
            (if-let [p' (when (= (ident route) i) (build route p))]
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
  (dispatch* [this args] (dispatch* (z/node zipper) args)))

#?(:clj
   (do (defmethod clojure.core/print-method Router
         [router ^java.io.Writer writer]
         (.write writer (format "#<Router \"%s\">" (path router))))
       (defmethod clojure.core/print-method Route
         [route ^java.io.Writer writer]
         (.write writer "#janus.route/Route ")
         (print-method [(.identifiable route)
                        (.as-segment route)
                        (.dispatchable route)
                        (.children route)] writer))
       (defmethod clojure.pprint/simple-dispatch Router
         [router]
         (print-method router *out*))
       (defmethod clojure.core/print-method RecursiveRoute
         [route ^java.io.Writer writer]
         (.write writer "#janus.route/RecursiveRoute ")
         (print-method [(.identifiable route)
                        (.as-segment route)
                        (.dispatchable route)] writer))
       (defmethod clojure.pprint/simple-dispatch Router
         [router]
         (print-method router *out*)))
   :cljs
   (extend-protocol IPrintWithWriter
     Router
     (-pr-writer [this writer opts]
       (-write writer (goog.string/format "#<Router %s>" (path this))))
     Route
     (-pr-writer [this writer opts]
       (-write writer "#janus.route/Route ")
       (-pr-writer [(.-identifiable this)
                    (.-as-segment this)
                    (.-dispatchable this)
                    (.-children this)] writer opts))
     RecursiveRoute
     (-pr-writer [this writer opts]
       (-write writer "#janus.route/RecursiveRoute ")
       (-pr-writer [(.-identifiable this)
                    (.-as-segment this)
                    (.-dispatchable this)] writer opts))))

(def read-route (partial apply ->Route))
(def read-recursive-route (partial apply ->RecursiveRoute))

(defn router
  [route]
  (->Router (-> route conform* r-zip) []))

(defn recursive-route [name as-segment dispatch]
  (->RecursiveRoute name as-segment dispatch))

(defn dispatch
  "Dispatch to the Dispatchable.dispatch method while collecting varargs"
  [dispatchable & args]
  (dispatch* dispatchable args))
