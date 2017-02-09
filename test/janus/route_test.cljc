(ns janus.route-test
  (:require
   [janus.route :refer :all]
   #?(:clj  [clojure.test :refer :all]
      :cljs [cljs.test :refer-macros [deftest is testing]])))

(deftest identify-patterns
  (testing "nil"
    (is (= [[:R nil]] (identify [:R] "/"))))
  (testing "string"
    (is (= [[:R nil] ['a "a"]] (identify [:R {"a" 'a}] "/a"))))
  (testing "keyword"
    (is (= [[:R nil] ['a :a]] (identify [:R {:a 'a}] "/a"))))
  (testing "boolean"
    (is (= [[:R nil] ['a "a"]] (identify [:R {true 'a}] "/a"))))
  (testing "regex without capture groups"
    (is (= [[:R nil] ['a "867-5309"]] (identify [:R {#"(?:\d{3})-(?:\d{4})" 'a}] "/867-5309"))))
  (testing "regex with capture groups"
    (is (= [[:R nil] ['a ["54.40" "54" "40"]]] (identify [:R {#"(\d+).(\d+)" 'a}] "/54.40"))))
  (testing "regex partial match"
    (is (nil? (identify [:R {#"(?:\d{3})-(?:\d{4})" 'a}] "/867-530"))))
  (testing "vector"
    (is (= [[:R nil] [:s "foo"]] (identify [:R {["foo" identity] :s}] "/foo"))))
  (testing "function"
    (is (= [[:R nil] [:even 12]] (identify [:R {#(when-let [i (Integer/parseInt %)] (when (even? i) i)) :even}] "/12")))))

(deftest generate-patterns
  (testing "nil"
    (is (= "/" (generate [:R {}] [:R]))))
  (testing "string"
    (is (= "/a" (generate [:R {"a" 'a}] [:R 'a]))))
  (testing "keyword"
    (is (= "/a" (generate [:R {:a 'a}] [:R 'a]))))
  (testing "boolean"
    (is (= "/foo" (generate [:R {true 'a}] [:R ['a "foo"]]))))
  (testing "regex"
    (is (= "/867-5309" (generate [:R {#"(?:\d{3})-(?:\d{4})" 'a}] [:R ['a "867-5309"]]))))
  (testing "vector"
    (is (= "/foo" (generate [:R {["foo" identity] :s}] [:R [:s "foo"]]))))
  (testing "function"
    (is (=  "/12" (generate [:R {#(str %) :even}] [:R [:even 12]])))))

(deftest invertible
  (testing "nil"
    (let [routes [:root {}]
          route "/"]
      (is (= route (generate routes (identify routes route))))))
  (testing "string"
    (let [routes [:root {"test" :s}]
          route "/test"]
      (is (= route (generate routes (identify routes route))))))
  (testing "keyword"
    (let [routes [:root {:test :test}]
          route "/test"]
      (is (= route (generate routes (identify routes route))))))
  (testing "boolean"
    (let [routes [:root {true :b}]
          route "/test"]
      (is (= route (generate routes (identify routes route))))))
  (testing "regex"
    (let [routes [:root {#"\d{3}-\d{4}" :test}]
          route "/867-5309"]
      (is (= route (generate routes (identify routes route))))))
  (testing "composite"
    (let [routes [:root {[#"(\d{3})-(\d{4})" (comp (partial apply format "%s-%s") rest)] :c}]
          route "/867-5309"]
      (is (= route (generate routes (identify routes route))))))
  (testing "multi-level"
    (let [routes [:root {"s" [:s {:k [:k {[#"(\d{3})-(\d{4})" (comp (partial apply format "%s-%s") rest )] :c}]}]}]
          route "/s/k/867-5309"]
      (is (= route (generate routes (identify routes route)))))))

(def $rs [:root
          {"a" ['a
                {"b" :b
                 "c" ['c {}]
                 "d" ["d"
                      {"e" [:e {}]
                       "f|F" :f}]}]}])

(deftest identify-structure
  (testing "root"
    (is (= [[:root nil]] (identify $rs "/"))))
  (testing "top"
    (is (= [[:root nil] ['a "a"]] (identify $rs "/a"))))
  (testing "interior"
    (is (= [[:root nil] ['a "a"] ["d" "d"]] (identify $rs "/a/d"))))
  (testing "leaf"
    (is (= [[:root nil] ['a "a"] [:b "b"]] (identify $rs "/a/b"))))
  (testing "trailing slashes"
    (is (= [[:root nil] ['a "a"]] (identify $rs "/a/"))))
  (testing "no route"
    (is (nil? (identify $rs "/a/nothinghere")))
    (is (nil? (identify $rs "/a/d/e/Z")))))

(deftest generate-structure
  (testing "root"
    (is (= "/" (generate $rs [[:root nil]]))))
  (testing "top"
    (is (= "/a" (generate $rs [[:root nil] ['a "a"]]))))
  (testing "interior"
    (is (= "/a/d" (generate $rs [[:root nil] ['a "a"] ["d" "d"]]))))
  (testing "leaf"
    (is (= "/a/b" (generate $rs [[:root nil] ['a "a"] [:b "b"]]))))
  (testing "no route"
    (is (thrown? java.lang.Exception (generate $rs [:root 'missing])))))

(deftest url-encoded
  (testing "decode"
    (is (= [[:R nil] ['top "a|b"]] (identify [:R {true 'top}] "/a%7Cb")))
    (is (= [[:R nil] ['top "a|b"]] (identify [:R {true 'top}] "/a%7cb"))))
  (testing "encode"
    (is (#{"/a%7Cb" "/a%7cb"} (generate [:R {true 'top}] [:R ['top "a|b"]])))))

(deftest abbreviated
  (testing "identify"
    (testing "nil"
      (is (= [] (identify* [:R] "/"))))
    (testing "string"
      (is (= [['a "a"]] (identify* [:R {"a" 'a}] "/a")))))
  (testing "generate"
    (testing "nil"
      (is (= "/" (generate* [:R {}] []))))
    (testing "string"
      (is (= "/a" (generate* [:R {"a" 'a}] ['a]))))))

(deftest abbreviated-invertible
  (testing "nil"
    (let [routes [:root {}]
          route "/"]
      (is (= route (generate* routes (identify* routes route))))))
  (testing "string"
    (let [routes [:root {"test" :s}]
          route "/test"]
      (is (= route (generate* routes (identify* routes route))))))
  (testing "keyword"
    (let [routes [:root {:test :test}]
          route "/test"]
      (is (= route (generate* routes (identify* routes route))))))
  (testing "boolean"
    (let [routes [:root {true :b}]
          route "/test"]
      (is (= route (generate* routes (identify* routes route))))))
  (testing "regex"
    (let [routes [:root {#"\d{3}-\d{4}" :test}]
          route "/867-5309"]
      (is (= route (generate* routes (identify* routes route))))))
  (testing "composite"
    (let [routes [:root {[#"(\d{3})-(\d{4})" (comp (partial apply format "%s-%s") rest)] :c}]
          route "/867-5309"]
      (is (= route (generate* routes (identify* routes route))))))
  (testing "multi-level"
    (let [routes [:root {"s" [:s {:k [:k {[#"(\d{3})-(\d{4})" (comp (partial apply format "%s-%s") rest )] :c}]}]}]
          route "/s/k/867-5309"]
      (is (= route (generate* routes (identify* routes route)))))))
