(ns janus.route
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

(defn- normalize-uri
  [uri]
  #?(:clj (.getRawPath (.normalize (URI. uri)))
     :cljs (.getPath (goog.Uri. uri))))

(defn- normalize-routes
  "Yields `route => [as-segment handler (routes)])`"
  [route]
  (cond
    (vector? route) (let [[pattern children] route]
                      [pattern (map (fn [[pattern route]] [pattern (normalize-routes route)]) children)])
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
  [routes [segment & remaining-segments]]
  (if segment
    (some (fn [[pattern [handler subroutes]]]
            (when-let [route-params (match pattern (url-decode segment))]
              (when-let [children (match-segments subroutes remaining-segments)]
                (cons [handler route-params] children))))
          (seq routes))
    ()))

(defn identify
  "Given a route definition data structure and a URI as a string, return the
   segment sequence, if any, that completely matches the path."
  [routes uri-string]
  (let [path (normalize-uri uri-string)
        segments (if (= "/" path) [] (rest (clojure.string/split path #"/")))
        [root-handler routes] (normalize-routes routes)]
    (when-let [matched (match-segments routes segments)]
      (cons [root-handler nil] matched))))

(defn- build-segments
  [routes [[target params] & targets]]
  (if target
    (or (some (fn [[pattern [handler subroutes]]]
               (when (= handler target)
                 (let [segment (url-encode (build pattern params))]
                   (cons segment (build-segments subroutes targets)))))
             (seq routes))
       (throw (ex-info "Can't build route" {:target target :routes routes})))
    ()))

(defn- normalize-targets [targets] (map (fn [t] (if (vector? t) t [t nil])) targets))

(defn generate
  [routes targets]
  (let [[root-handler routes] (normalize-routes routes)
        [[target params] & targets] (normalize-targets targets)]
    (assert (= root-handler target) "Root target does not match")
    (let [segments (build-segments routes targets)]
      (str "/" (clojure.string/join "/" segments)))))
