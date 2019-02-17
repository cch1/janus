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
  (parameters [this]  "Return map of segment identifiers to route parameters")
  (node [this] "Return the resulting route leaf node"))

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
  (match [this segment] segment)
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

(defn- r-zip
  "Return a zipper for a normalized route data structure"
  [route]
  (z/zipper (constantly true)
            (fn [node] (-> node last last seq))
            (fn [node children] (assoc-in node [1 2] children))
            route))

(defn- normalize-target [target] (if (vector? target) target [target nil]))

(defn- normalize-uri
  [uri]
  #?(:clj (.getRawPath (.normalize (URI. uri)))
     :cljs (.getPath (goog.Uri. uri))))

(s/def ::name (partial instance? clojure.lang.Named))
(s/def ::segment (partial satisfies? AsSegment))
(s/def ::dispatchable (s/or :fn fn? :var var? :named ::name))
(s/def ::route (s/tuple ::name (s/tuple ::segment ::dispatchable (s/* ::route))))

(let [named? (partial s/valid? ::name)
      as-segment? (partial s/valid? ::segment)
      dispatchable? (partial s/valid? ::dispatchable)]

  (defn- normalize
    "Yields `route => [identifiable [as-segment dispatchable routes]]`"
    ([identifiable dispatchable route] (normalize [identifiable [true dispatchable route]]))
    ([dispatchable route] (normalize [::root [nil dispatchable route]]))
    ([] (normalize [::root [nil ::root ()]])) ; degenerate route table
    ([route]
     {:post [(s/valid? ::route %)]}
     (if-not (sequential? route)
       (normalize [::root [nil route ()]]) ; degenerate route table; explicit dispatchable
       (let [[identifiable v] route
             s (name identifiable)]
         (cond
           (vector? v) (m/match [(count v) v]
                                [0 []]
                                , (normalize [identifiable [s identifiable ()]])
                                [1 [(a :guard seqable?)]]
                                , (normalize [identifiable [s identifiable a]])
                                [1 [(a :guard as-segment?)]]
                                , (normalize [identifiable [a identifiable ()]])
                                [1 [(a :guard dispatchable?)]]
                                , (normalize [identifiable [s a ()]])
                                [2 [(a :guard as-segment?) (b :guard dispatchable?)]]
                                , (normalize [identifiable [a b ()]])
                                [2 [(a :guard as-segment?) (b :guard seqable?)]]
                                , (normalize [identifiable [a identifiable b]])
                                [2 [(a :guard dispatchable?) (b :guard seqable?)]]
                                , (normalize [identifiable [s a b]])
                                [3 [(a :guard as-segment?) (b :guard dispatchable?) (c :guard seqable?)]]
                                , [identifiable [a b (map normalize c)]]
                                :else (throw (ex-info "Unrecognized route format" {::route route})))
           (string? v) (normalize [identifiable [v identifiable ()]])
           (seqable? v) (normalize [identifiable [s identifiable v]])
           (or (var? v) (fn? v)) (normalize [identifiable [s v ()]])
           :else (normalize [identifiable [v identifiable ()]])))))))

(defrecord Router [zipper params]
  Routable
  (root [this] (Router. (r-zip (z/root zipper)) []))
  (parent [this] (when-let [z (z/up zipper)] (Router. z (vec (butlast params)))))
  (identify [this uri]
    (if-let [segments (seq (map url-decode (rest (string/split (normalize-uri uri) #"/"))))]
      (loop [rz (z/down zipper) segments segments params params]
        (when rz
          (let [[_ [as-segment _ _]] (z/node rz)]
            (if-let [p (match as-segment (first segments))]
              (if-let [remaining-segments (seq (rest segments))]
                (recur (z/down rz) remaining-segments (conj params p))
                (Router. rz (conj params p)))
              (recur (z/right rz) segments params)))))
      this))
  (generate [this targets]
    (if-let [ps (seq (map normalize-target targets))]
      (loop [rz (z/down zipper) [[i p] & remaining-ps :as ps] ps params params]
        (when rz
          (let [[identifier [as-segment _ _]] (z/node rz)]
            (if-let [p' (when (= identifier i) (build as-segment p))]
              (if (seq remaining-ps)
                (recur (z/down rz) remaining-ps (conj params p))
                (Router. rz (conj params p)))
              (recur (z/right rz) ps params)))))
      this))
  Routed
  (path [this] (path this false))
  (path [this generalized?]
    (let [nodes (rest (concat (z/path zipper) (list (z/node zipper))))
          f (if generalized?
              (comp first first vector)
              (fn [[identifiable [as-segment _ _]] p]
                (url-encode (build as-segment p))))
          segments (map f nodes params)]
      (str "/" (string/join "/" segments))))
  (identifiers [this] (map first (concat (z/path zipper) (list (z/node zipper)))))
  (parameters [this] (map vector (rest (identifiers this)) params))
  (node [this] (z/node zipper)))

#?(:clj
   (defmethod clojure.core/print-method Router
     [router ^java.io.Writer writer]
     (.write writer (format "#<Router \"%s\">" (path router))))
   :cljs
   (extend-protocol IPrintWithWriter
     Router
     (-pr-writer [this writer opts]
       (-write writer (format "#<Router %s>" (path this))))))

(defn router
  [route]
  (Router. (-> route normalize r-zip) []))
