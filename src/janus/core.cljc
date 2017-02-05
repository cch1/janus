(ns janus.core
  (:import #?(:cljs goog.Uri
              :clj [java.net URI URLDecoder URLEncoder]))
  (:require clojure.string))

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

(defn- compile-route
  "Yields `route => [as-segment handler (routes)])`"
  [route]
  (cond
    (vector? route) (let [[pattern children] route]
                      [pattern (map (fn [[pattern route]] [pattern (compile-route route)]) children)])
    true [route ()]))

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
  clojure.lang.PersistentVector ; potentially invertible
  (match [this segment] (match (first this) segment))
  (build [this args] (build (second this) args))
  clojure.lang.Fn ; potentially invertible
  (match [this segment] (this segment))
  (build [this args] (this args)))

(defn- match-segments
  [[handler routes] [segment & remaining-segments] route-params]
  (when-let [children (if segment
                        (some (fn [[pattern subroutes]]
                                (when-let [route-params (match pattern (url-decode segment))]
                                  (match-segments subroutes remaining-segments route-params)))
                              (seq routes))
                        ())]
    (cons [handler route-params] children)))

(defn match-route
  "Given a route definition data structure and a URI as a string, return the
   segment sequence, if any, that completely matches the path."
  [routes uri-string]
  (let [path #?(:clj (.getRawPath (.normalize (URI. uri-string)))
                :cljs (.getPath (goog.Uri. uri-string))) ; protocol?
        segments (if (= "/" path) [] (rest (clojure.string/split path #"/")))
        routes (compile-route routes)]
    (match-segments routes segments nil)))

(defn- build-segments
  [routes [[target params] & targets] segments]
  (if target
    (or (some (fn [[pattern [handler subroutes]]]
               (when (= handler target)
                 (let [segment (url-encode (build pattern params))]
                   (build-segments subroutes targets
                                   (conj segments segment)))))
             (seq routes))
       (throw (ex-info "Can't build route"
                       {:target target :routes routes :segments segments})))
    segments))

(defn- normalize-targets [targets] (map (fn [t] (if (vector? t) t [t []])) targets))

(defn build-route
  [routes targets]
  (let [[root-handler routes] (compile-route routes)
        [[target params] & targets] (normalize-targets targets)]
    (assert (= root-handler target) "Root target does not match")
    (let [segments (build-segments routes targets [])]
      (str "/" (clojure.string/join "/" segments)))))
