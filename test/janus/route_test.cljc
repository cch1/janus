(ns janus.route-test
  (:require [janus.route :refer [router identify identifiers parameters generate path parent root recursive-route dispatch
                                 ->Route ->RecursiveRoute AsSegment]]
            [clojure.tools.reader.edn :as edn]
            #?(:clj  [clojure.test :refer :all]
               :cljs [cljs.test :refer-macros [deftest is testing run-tests]])
            [clojure.string :as string]))

(deftest router-construction
  (is (instance? janus.route.Router (router :r)))
  (is (instance? janus.route.Router (router [:root identity])))
  (is (instance? janus.route.Router (router [:root ["root" identity]])))
  (is (instance? janus.route.Router (router [:root {}])))
  (is (instance? janus.route.Router (router [:root "root"])))
  (is (instance? janus.route.Router (router [:R [nil :R {}]]))))

(deftest identify-patterns
  (testing "nil"
    (let [router (-> [:R [nil :R {}]] router (identify "/"))]
      (is (= [:R] (identifiers router)))
      (is (= () (parameters router)))))
  (testing "string"
    (let [router (-> [:R [nil :R {'a ["a" 'a {}]}]] router (identify "/a"))]
      (is (= [:R 'a] (identifiers router)))
      (is (= [['a "a"]] (parameters router)))))
  (testing "keyword"
    (let [router (-> [:R [nil :R {'a [:a 'a {}]}]] router (identify "/a"))]
      (is (= [:R 'a] (identifiers router)))
      (is (= [['a "a"]] (parameters router)))))
  (testing "boolean"
    (let [router (-> [:R [nil :R {'a [true 'a {}]}]] router (identify "/a"))]
      (is (= [:R 'a] (identifiers router)))
      (is (= [['a "a"]] (parameters router)))))
  (testing "regex without capture groups"
    (let [router (-> [:R [nil :R {'a [#"(?:\d{3})-(?:\d{4})" 'a {}]}]] router (identify "/867-5309"))]
      (is (= [:R 'a] (identifiers router)))
      (is (= [['a "867-5309"]] (parameters router)))))
  (testing "regex with capture groups"
    (let [router (-> [:R [nil :R {'a [#"(\d+).(\d+)" 'a {}]}]] router (identify "/54.40"))]
      (is (= [:R 'a] (identifiers router)))
      (is (= [['a ["54" "40"]]] (parameters router)))))
  (testing "vector"
    (let [router (-> [:R [nil :R {:s [["foo" identity] :s {}]}]] router (identify "/foo"))]
      (is (= [:R :s] (identifiers router)))
      (is (= [[:s "foo"]] (parameters router)))))
  (testing "function"
    (let [f (fn [x] (if (string? x) ; good for multimethod
                      (when-let [i (#?(:cljs js/parseInt :clj Integer/parseInt) x)]
                        (when (even? i) i))
                      (do (assert (even? x)) (str x))))
          router (-> [:R [nil :R {:even [f :even {}]}]] router (identify "/12"))]
      (is (= [:R :even ] (identifiers router)))
      (is (= [[:even 12]] (parameters router)))))
  (testing "no match"
    (let [router (-> [:R [nil :R {'a [#"(?:\d{3})-(?:\d{4})" 'a {}]}]] router (identify "/867-530"))]
      (is (nil? router)))))

(deftest generate-patterns
  (testing "nil"
    (let [router (-> [:R [nil :R {}]] router (generate []))]
      (is (= [:R] (identifiers router)))
      (is (= "/" (path router)))))
  (testing "string"
    (let [router (-> [:R [nil :R {'a ["a" 'a {}]}]] router (generate ['a]))]
      (is (= [:R 'a] (identifiers router)))
      (is (= "/a" (path router)))))
  (testing "keyword"
    (let [router (-> [:R [nil :R {'a [:a 'a {}]}]] router (generate ['a]))]
      (is (= [:R 'a] (identifiers router)))
      (is (= "/a" (path router)))))
  (testing "boolean"
    (let [router (-> [:R [nil :R {'a [true 'a {}]}]] router (generate [['a "a"]]))]
      (is (= [:R 'a] (identifiers router)))
      (is (= "/a" (path router)))))
  (testing "regex with string"
    (let [router (-> [:R [nil :R {'a [#"(?:\d{3})-(?:\d{4})" 'a {}]}]] router (generate [['a "867-5309"]]))]
      (is (= [:R 'a] (identifiers router)))
      (is (= "/867-5309" (path router)))))
  (testing "regex with vector of strings"
    (let [router (-> [:R [nil :R {'a [#"(\d{2})(\d{2})" 'a {}]}]] router (generate [['a ["54" "40"]]]))]
      (is (= [:R 'a] (identifiers router)))
      (is (= "/5440" (path router)))))
  (testing "vector"
    (let [router (-> [:R [nil :R {:s [["foo" identity] :s {}]}]] router (generate [[:s "foo"]]))]
      (is (= [:R :s] (identifiers router)))
      (is (= "/foo" (path router)))))
  (testing "function"
    (let [f (fn [x] (if (string? x) ; good for multimethod
                      (when-let [i #?(:cljs (js/parseInt x) :clj (Integer/parseInt x))] (when (even? i) i))
                      (do (assert (even? x)) (str x))))
          router (-> [:R [nil :R {:even [f :even {}]}]] router (generate [[:even 12]]))]
      (is (= [:R :even] (identifiers router)))
      (is (= "/12" (path router))))))

(def $rs (let [f (fn [x] (if (string? x) ; good for multimethod
                           ({"CA" :CA "CANADA" :CA "UNITED STATES" :US "USA" :US "US" :US} (string/upper-case x))
                           ({:US "United States" :CA "Canada"} x)))]
           [:root
            [nil :root
             {'a ["a" 'a
                  {:b "b"
                   'c ["c" {}]
                   :d [true
                       {:e ["e" {}]
                        :f ["f|F" :f
                            {:g [#"(\d{2})(\d{2})" :g
                                 {:h [[#"(\d{3})-(\d{4})" "%s-%s"] :h
                                      {:i [f :i
                                           {}]}]}]}]}]}]}]]))

(deftest identify-structure
  (testing "root"
    (let [r (router $rs)
          uri "/a/whatever/f%7CF/5440/867-5309/United%20States"
          params [['a "a"]
                  [:d "whatever"]
                  [:f "f|F"]
                  [:g ["54" "40"]]
                  [:h ["867" "5309"]]
                  [:i :CA]]]
      (is (= uri (path (generate r (parameters (identify r uri))))))
      (is (= params (parameters (identify r (path (generate r params)))))))))

(deftest url-encoded
  (let [r (-> [:R [nil :R {'top [true 'top {}]}]] router)]
    (testing "decode"
      (is (= [['top "a|b"]] (parameters (identify r "/a%7Cb"))))
      (is (= [['top "a b"]] (parameters (identify r "/a%20b"))))
      (is (= [['top "a+b"]] (parameters (identify r "/a+b"))))
      (is (= [['top "a/b"]] (parameters (identify r "/a%2fb")))))
    (testing "encode"
      (is (#{"/a%7cb" "/a%7Cb"} (path (generate r [['top "a|b"]]))))
      (is (= "/a%20b" (path (generate r [['top "a b"]]))))
      (is (#{"/a+b" "/a%2bb" "/a%2Bb"} (path (generate r [['top "a+b"]]))))
      (is (#{"/a%2fb" "/a%2Fb"} (path (generate r [['top "a/b"]])))))))

(deftest invertible
  (testing "string"
    (let [uri "/a"
          params [['a "a"]]
          router (-> [:R [nil :R {'a ["a" 'a {}]}]] router)]
      (is (= uri (-> router (generate params) path)))
      (is (= params (-> router (identify uri) parameters)))))
  (testing "keyword"
    (let [uri "/a"
          params [['a "a"]]
          router (-> [:R [nil :R {'a [:a 'a {}]}]] router)]
      (is (= uri (-> router (generate params) path)))
      (is (= params (-> router (identify uri) parameters)))))
  (testing "boolean"
    (let [uri "/a"
          params [['a "a"]]
          router (-> [:R [nil :R {'a [true 'a {}]}]] router)]
      (is (= uri (-> router (generate params) path)))
      (is (= params (-> router (identify uri) parameters)))))
  (testing "regex"
    (testing "without capture groups"
      (let [uri "/867-5309"
            params [['a "867-5309"]]
            router (-> [:R [nil :R {'a [#"(?:\d{3})-(?:\d{4})" 'a {}]}]] router)]
        (is (= uri (-> router (generate params) path)))
        (is (= params (-> router (identify uri) parameters)))))
    (testing "with covering capture groups"
      (let [uri "/ab"
            params [['a ["a" "b"]]]
            router (-> [:R [nil :R {'a [#"(a)(b)" 'a {}]}]] router)]
        (is (= uri (-> router (generate params) path)))
        (is (= params (-> router (identify uri) parameters)))))))

;;; https://en.wikipedia.org/wiki/Exoplanet
(def astronomy
  ['universe
   [nil 'universe
    {'galaxies
     {:galaxy [#"\w[\w\s]+"
               {'systems
                {:system [#"\w[\w\s]+"
                          {'planets
                           {:planet [#"\w[\w\s]+"
                                     {'moons
                                      {:moon [#"\w[\w\s]+" {}]}}]}
                           'comets
                           {:comet [#"\w[\w\s]+" {}]}}]}}]}}]])

(deftest navigation
  (let [universe (router astronomy)
        sol (generate universe ['galaxies [:galaxy "Milky Way"]
                                'systems [:system "Sol"]])
        earth (generate sol ['planets [:planet "Earth"]])
        venus (generate sol ['planets [:planet "Venus"]])]
    (is (= sol (-> earth parent parent)))
    (is (= universe (-> earth root)))
    (is (nil? (parent universe)))
    (is (= venus (-> earth parent (generate [[:planet "Venus"]]))))))

(deftest recursive-route-evaluates-all-descendants
  (let [uri "/a/b/c/leaf"
        router (-> [:R [nil :R {'a ["a" 'a [(recursive-route '* true identity)]]}]] router)
        params [['a "a"] ['* "b"] ['* "c"] ['* "leaf"]]]
    (is (= uri (-> router (generate params) path)))
    (is (= params (-> router (identify uri) parameters)))))

(deftest demonstrate-recursive-wildcard-route-as-fallback-after-explicit-routes
  (let [uri0 "/a/x"
        uri1 "/a/b/c/leaf"
        router (-> [:R [nil :R {'a ["a" 'a [['x ["x" identity {}]]
                                            (recursive-route '* true identity)]]}]] router)]
    (is (= [['a "a"] ['x "x"]] (-> router (identify uri0) parameters)))
    (is (= [['a "a"] ['* "b"] ['* "c"] ['* "leaf"]] (-> router (identify uri1) parameters)))))

(deftest generalized-path
  (let [universe (router astronomy)
        sol (generate universe ['galaxies [:galaxy "Milky Way"]
                                'systems [:system "Sol"]])
        earth (generate sol ['planets [:planet "Earth"]])]
    (is (= "/galaxies/:galaxy/systems/:system/planets/:planet" (path earth true)))))

(deftest equality
  (is (= (->Route :route "route" :route ())
         (->Route :route "route" :route ())))
  (is (not= (->Route :route1 "route" :route ())
            (->Route :route2 "route" :route ())))
  (is (= (->Route :route "route" :route (list (->Route :child "child" :child ())))
         (->Route :route "route" :route (list (->Route :child "child" :child ())))))
  (is (not= (->Route :route "route" :route (list (->Route :child0 "child" :child ())))
            (->Route :route "route" :route (list (->Route :child1 "child" :child ()))))))

(deftest tagged-literal-supported
  (let [routes (->Route :route "route" :route (list (->RecursiveRoute :recursive-route "recursive-route" :recursive-route)))]
    (is (= routes
           (edn/read-string {:readers {'janus.route/Route janus.route/read-route 'janus.route/RecursiveRoute janus.route/read-recursive-route}}
                            (pr-str routes))))))

(deftest custom-as-segment-proof-of-concept
  (let [geopoint-segment (reify AsSegment
                           (match [this segment]
                             (when-let [[_ lat lon radius :as els] (re-matches #"(-?\d+(?:\.\d+)?);(-?\d+(?:\.\d+))(?:;(\d+(?:\.\d+)))?" segment)]
                               (let [[lat lon radius] (sequence (comp (filter identity)
                                                                      (map (fn [el] (#?(:cljs js/parseFloat
                                                                                        :clj Float/parseFloat) el)))) (rest els))]
                                 [lat lon (or radius 10.0)])))
                           (build [this [lat lon radius]]
                             #?(:cljs (goog.string/format "%3.4f;%3.4f;%5.4f" lat lon radius) :clj (format "%3.4f;%3.4f;%5.4f" lat lon radius))))
        r (router [:R {:geopoint [geopoint-segment identity ()]}])
        uri-in "/61.45%3B-0.56"
        params (list [:geopoint [61.45 -0.56 10.0]])
        uri-out "/61.4500%3B-0.5600%3B10.0000"]
    ;; (is (= params (parameters (identify r uri-in)))) ; My kingdom for midje's `roughly` checker
    (is (= uri-out (path (generate r (parameters (identify r uri-in))))))))


(deftest dispatchable
  (let [dispatchable (reify janus.route/Dispatchable (dispatch* [this _] ::success))
        router (-> [:R [nil :R {'a [:a dispatchable {}]}]] router (identify "/a"))]
    (is (= ::success (dispatch router)))
    (is (= ::success (dispatch router {:request-method :get} {'a identity} :whatever3 "whatever4" 5 6 7 8 9 0))))
  (let [dispatchable (reify janus.route/Dispatchable (dispatch* [this args] args))
        router (-> [:R [nil :R {'a [:a dispatchable {}]}]] router (identify "/a"))]
    (is (= nil (dispatch router)))
    (is (= [::success] (dispatch router ::success)))
    (is (= [{:request-method :get} {'a identity} :whatever3 "whatever4" 5 6 7 8 9 0]
           (dispatch router {:request-method :get} {'a identity} :whatever3 "whatever4" 5 6 7 8 9 0)))))
