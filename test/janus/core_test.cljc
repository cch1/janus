(ns janus.core-test
  (:require
   [janus.core :refer :all]
   #?(:clj  [clojure.test :refer :all]
      :cljs [cljs.test :refer-macros [deftest is testing]])))

(deftest match-patterns
  (testing "nil"
    (is (= [[:R nil]] (match-route [:R] "/"))))
  (testing "string"
    (is (= [[:R nil] ['a "a"]] (match-route [:R {"a" 'a}] "/a"))))
  (testing "keyword"
    (is (= [[:R nil] ['a :a]] (match-route [:R {:a 'a}] "/a"))))
  (testing "boolean"
    (is (= [[:R nil] ['a "a"]] (match-route [:R {true 'a}] "/a"))))
  (testing "regex without capture groups"
    (is (= [[:R nil] ['a "867-5309"]] (match-route [:R {#"(?:\d{3})-(?:\d{4})" 'a}] "/867-5309"))))
  (testing "regex with capture groups"
    (is (= [[:R nil] ['a ["54.40" "54" "40"]]] (match-route [:R {#"(\d+).(\d+)" 'a}] "/54.40"))))
  (testing "regex partial match"
    (is (nil? (match-route [:R {#"(?:\d{3})-(?:\d{4})" 'a}] "/867-530"))))
  (testing "vector"
    (is (= [[:R nil] [:s "foo"]] (match-route [:R {["foo" identity] :s}] "/foo"))))
  (testing "function"
    (is (= [[:R nil] [:even 12]] (match-route [:R {#(when-let [i (Integer/parseInt %)] (when (even? i) i)) :even}] "/12")))))

(deftest build-patterns
  (testing "nil"
    (is (= "/" (build-route [:R {}] [:R]))))
  (testing "string"
    (is (= "/a" (build-route [:R {"a" 'a}] [:R 'a]))))
  (testing "keyword"
    (is (= "/a" (build-route [:R {:a 'a}] [:R 'a]))))
  (testing "boolean"
    (is (= "/foo" (build-route [:R {true 'a}] [:R ['a "foo"]]))))
  (testing "regex"
    (is (= "/867-5309" (build-route [:R {#"(?:\d{3})-(?:\d{4})" 'a}] [:R ['a "867-5309"]]))))
  (testing "vector"
    (is (= "/foo" (build-route [:R {["foo" identity] :s}] [:R [:s "foo"]]))))
  (testing "function"
    (is (=  "/12" (build-route [:R {#(str %) :even}] [:R [:even 12]])))))

(deftest invertible
  (testing "nil"
    (let [routes [:root {}]
          route "/"]
      (is (= route (build-route routes (match-route routes route))))))
  (testing "string"
    (let [routes [:root {"test" :s}]
          route "/test"]
      (is (= route (build-route routes (match-route routes route))))))
  (testing "keyword"
    (let [routes [:root {:test :test}]
          route "/test"]
      (is (= route (build-route routes (match-route routes route))))))
  (testing "boolean"
    (let [routes [:root {true :b}]
          route "/test"]
      (is (= route (build-route routes (match-route routes route))))))
  (testing "regex"
    (let [routes [:root {#"\d{3}-\d{4}" :test}]
          route "/867-5309"]
      (is (= route (build-route routes (match-route routes route))))))
  (testing "composite"
    (let [routes [:root {[#"(\d{3})-(\d{4})" (comp (partial apply format "%s-%s") rest)] :c}]
          route "/867-5309"]
      (is (= route (build-route routes (match-route routes route))))))
  (testing "multi-level"
    (let [routes [:root {"s" [:s {:k [:k {[#"(\d{3})-(\d{4})" (comp (partial apply format "%s-%s") rest )] :c}]}]}]
          route "/s/k/867-5309"]
      (is (= route (build-route routes (match-route routes route)))))))

(def $rs [:root
          {"a" ['a
                {"b" :b
                 "c" ['c {}]
                 "d" ["d"
                      {"e" [:e {}]
                       "f|F" :f}]}]}])

(deftest match-structure
  (testing "root"
    (is (= [[:root nil]] (match-route $rs "/"))))
  (testing "top"
    (is (= [[:root nil] ['a "a"]] (match-route $rs "/a"))))
  (testing "interior"
    (is (= [[:root nil] ['a "a"] ["d" "d"]] (match-route $rs "/a/d"))))
  (testing "leaf"
    (is (= [[:root nil] ['a "a"] [:b "b"]] (match-route $rs "/a/b"))))
  (testing "trailing slashes"
    (is (= [[:root nil] ['a "a"]] (match-route $rs "/a/"))))
  (testing "no route"
    (is (nil? (match-route $rs "/a/nothinghere")))
    (is (nil? (match-route $rs "/a/d/e/Z")))))

(deftest build-structure
  (testing "root"
    (is (= "/" (build-route $rs [[:root nil]]))))
  (testing "top"
    (is (= "/a" (build-route $rs [[:root nil] ['a "a"]]))))
  (testing "interior"
    (is (= "/a/d" (build-route $rs [[:root nil] ['a "a"] ["d" "d"]]))))
  (testing "leaf"
    (is (= "/a/b" (build-route $rs [[:root nil] ['a "a"] [:b "b"]]))))
  (testing "no route"
    (is (thrown? java.lang.Exception (build-route $rs [:root 'missing])))))

(deftest url-encoded
  (testing "decode"
    (is (= [[:R nil] ['top "a|b"]] (match-route [:R {true 'top}] "/a%7Cb")))
    (is (= [[:R nil] ['top "a|b"]] (match-route [:R {true 'top}] "/a%7cb"))))
  (testing "encode"
    (is (#{"/a%7Cb" "/a%7cb"} (build-route [:R {true 'top}] [:R ['top "a|b"]])))))
