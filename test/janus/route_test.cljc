(ns janus.route-test
  (:require [janus.route :refer :all]
            #?(:clj  [clojure.test :refer :all]
               :cljs [cljs.test :refer-macros [deftest is testing]])
            [clojure.string :as string]))

(deftest router-construction
  (is (instance? janus.route.Router (router :r))))

(deftest normalization
  (let [normalized [:root ["root" identity {}]]]
    (is (= [:root ["root" identity {}]] (node (router [:root identity]))))
    (is (= [:root ["root" identity {}]] (node (router [:root ["root" identity]]))))
    (is (= [:root ["root" :root {}]] (node (router [:root {}]))))
    (is (= [:root ["root" :root {}]] (node (router [:root "root"]))))))

(deftest identify-patterns
  (testing "nil"
    (let [router (-> [:R [nil :R {}]] router (identify "/"))]
      (is (= [:R] (identifiers router)))
      (is (= () (parameters router)))
      (is (= "/" (path router)))))
  (testing "string"
    (let [router (-> [:R [nil :R {'a ["a" 'a {}]}]] router (identify "/a"))]
      (is (= [:R 'a] (identifiers router)))
      (is (= [['a "a"]] (parameters router)))
      (is (= "/a" (path router)))))
  (testing "keyword"
    (let [router (-> [:R [nil :R {'a [:a 'a {}]}]] router (identify "/a"))]
      (is (= [:R 'a] (identifiers router)))
      (is (= [['a "a"]] (parameters router)))
      (is (= "/a" (path router)))))
  (testing "boolean"
    (let [router (-> [:R [nil :R {'a [true 'a {}]}]] router (identify "/a"))]
      (is (= [:R 'a] (identifiers router)))
      (is (= [['a "a"]] (parameters router)))
      (is (= "/a" (path router)))))
  (testing "regex without capture groups"
    (let [router (-> [:R [nil :R {'a [#"(?:\d{3})-(?:\d{4})" 'a {}]}]] router (identify "/867-5309"))]
      (is (= [:R 'a] (identifiers router)))
      (is (= [['a "867-5309"]] (parameters router)))
      (is (= "/867-5309" (path router)))))
  (testing "regex with capture groups"
    (let [router (-> [:R [nil :R {'a [#"(\d+).(\d+)" 'a {}]}]] router (identify "/54.40"))]
      (is (= [:R 'a] (identifiers router)))
      (is (= [['a ["54.40" "54" "40"]]] (parameters router)))
      (is (= "/54.40" (path router)))))
  (testing "regex partial match"
    (let [router (-> [:R [nil :R {'a [#"(?:\d{3})-(?:\d{4})" 'a {}]}]] router (identify "/867-530"))]
      (is (nil? router))))
  (testing "vector"
    (let [router (-> [:R [nil :R {:s [["foo" identity] :s {}]}]] router (identify "/foo"))]
      (is (= [:R :s] (identifiers router)))
      (is (= [[:s "foo"]] (parameters router)))
      (is (= "/foo" (path router)))))
  (testing "function"
    (let [f (fn [x] (if (string? x) ; good for multimethod
                     (when-let [i (Integer/parseInt x)] (when (even? i) i))
                     (do (assert (even? x)) (str x))))
          router (-> [:R [nil :R {:even [f :even {}]}]] router (identify "/12"))]
      (is (= [:R :even ] (identifiers router)))
      (is (= [[:even 12]] (parameters router)))
      (is (= "/12" (path router))))))

(deftest generate-patterns
  (testing "nil"
    (let [router (-> [:R [nil :R {}]] router (generate []))]
      (is (= [:R] (identifiers router)))
      (is (= [] (parameters router)))
      (is (= "/" (path router)))))
  (testing "string"
    (let [router (-> [:R [nil :R {'a ["a" 'a {}]}]] router (generate ['a]))]
      (is (= [:R 'a] (identifiers router)))
      (is (= [['a nil]] (parameters router)))
      (is (= "/a" (path router)))))
  (testing "keyword"
    (let [router (-> [:R [nil :R {'a [:a 'a {}]}]] router (generate ['a]))]
      (is (= [:R 'a] (identifiers router)))
      (is (= [['a nil]] (parameters router)))
      (is (= "/a" (path router)))))
  (testing "boolean"
    (let [router (-> [:R [nil :R {'a [true 'a {}]}]] router (generate [['a "a"]]))]
      (is (= [:R 'a] (identifiers router)))
      (is (= [['a "a"]] (parameters router)))
      (is (= "/a" (path router)))))
  (testing "regex without capture groups"
    (let [router (-> [:R [nil :R {'a [#"(?:\d{3})-(?:\d{4})" 'a {}]}]] router (generate [['a "867-5309"]]))]
      (is (= [:R 'a] (identifiers router)))
      (is (= [['a "867-5309"]] (parameters router)))
      (is (= "/867-5309" (path router)))))
  (testing "regex with capture groups"
    (let [router (-> [:R [nil :R {'a [#"(\d+).(\d+)" 'a {}]}]] router (generate [['a ["54.40" "54" "40"]]]))]
      (is (= [:R 'a] (identifiers router)))
      (is (= [['a ["54.40" "54" "40"]]] (parameters router)))
      (is (= "/54.40" (path router)))))
  (testing "vector"
    (let [router (-> [:R [nil :R {:s [["foo" identity] :s {}]}]] router (generate [[:s "foo"]]))]
      (is (= [:R :s] (identifiers router)))
      (is (= [[:s "foo"]] (parameters router)))
      (is (= "/foo" (path router)))))
  (testing "function"
    (let [f (fn [x] (if (string? x) ; good for multimethod
                     (when-let [i (Integer/parseInt x)] (when (even? i) i))
                     (do (assert (even? x)) (str x))))
          router (-> [:R [nil :R {:even [f :even {}]}]] router (generate [[:even 12]]))]
      (is (= [:R :even] (identifiers router)))
      (is (= [[:even 12]] (parameters router)))
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
                            {:g [#"(\d+).(\d+)" :g
                                 {:h [[#(when (= (seq %) (reverse %)) %)
                                       #(do (assert (= (seq %) (reverse %))) %)] :h
                                      {:i [f :i
                                           {}]}]}]}]}]}]}]]))

(deftest identify-structure
  (testing "root"
    (let [r (router $rs)
          uri "/a/whatever/f%7CF/54.40/868/United%20States"
          params [['a "a"] [:d "whatever"] [:f "f|F"] [:g ["54.40" "54" "40"]] [:h "868"] [:i :CA]]]
      (is (= uri (path (generate r (parameters (identify r uri))))))
      (is (= params (parameters (identify r (path (generate r params)))))))))

(deftest url-encoded
  (testing "decode"
    (let [router (-> [:R [nil :R {'top [true 'top {}]}]] router)]
      (is (= [['top "a|b"]] (parameters (identify router "/a%7Cb"))))
      (is (= [['top "a|b"]] (parameters (identify router "/a%7Cb"))))))
  (testing "encode"
    (let [router (-> [:R [nil :R {'top ["a|b" 'top {}]}]] router)]
      (is (#{"/a%7Cb" "/a%7cb"} (path (generate router ['top])))))))
