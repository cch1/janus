(ns janus.ring-test
  (:require [janus.ring :refer :all]
            [clojure.test :refer :all]
            [ring.mock.request :as mock]))

(deftest add-route-identifier
  (testing "Identified route is added to request and params are augmented"
    (let [request (mock/request :get "/foo")
          routes [:R {"foo" :a}]
          request' ((make-identifier identity routes) request)]
      (is (= (request' :janus.ring/route) [[:R nil] [:a "foo"]]))
      (is (= (request' :params) {:R nil :a "foo"})))))

(def handler (fn [r] (-> r :janus.ring/route last last)))

(deftest dispatch-on-route
  (let [request (mock/request :get "/foo")]
    (testing "dispatch on keyword"
      (let [request' (assoc request :janus.ring/route [[:R nil] [:a "foo"]])
            dispatch-table {:a (fn [r] (-> r :janus.ring/route last last))}]
        (is (= "foo" ((make-dispatcher dispatch-table) request')))))
    (testing "dispatch on string"
      (let [request' (assoc request :janus.ring/route [[:R nil] ["a" "foo"]])
            dispatch-table {"a" (fn [r] (-> r :janus.ring/route last last))}]
        (is (= "foo" ((make-dispatcher dispatch-table) request')))))
    (testing "dispatch on symbol"
      (let [request' (assoc request :janus.ring/route [[:R nil] ['a "foo"]])
            dispatch-table {'a (fn [r] (-> r :janus.ring/route last last))}]
        (is (= "foo" ((make-dispatcher dispatch-table) request')))))
    (testing "dispatch on var"
      (let [request' (assoc request :janus.ring/route [[:R nil] [#'handler "foo"]])]
        (is (= "foo" ((make-dispatcher) request')))))
    (testing "dispatch on function"
      (let [request' (assoc request :janus.ring/route [[:R nil] [handler "foo"]])]
        (is (= "foo" ((make-dispatcher) request')))))))
