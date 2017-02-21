(ns janus.ring-test
  (:require [janus.ring :refer :all]
            [janus.route :as route]
            [ring.mock.request :as mock]
            [clojure.test :refer :all]))

(deftest add-route-identifier
  (testing "Identified route is available in request and params are augmented"
    (let [request (mock/request :get "/foo")
          routes [:R {:a "foo"}]
          request' ((make-identifier identity routes) request)]
      (is (instance? janus.route.Router (request' :janus.ring/router)))
      (is (= (request' :params) {:a "foo"}))
      (is (= (request' :route-params) {:a "foo"})))))

(def handler (fn [r] (-> r :janus.ring/router route/path)))

(deftest dispatch-on-route
  (let [request (mock/request :get "/foo?x=0")]
    (testing "dispatch on keyword"
      (let [request (assoc request :janus.ring/router (route/router :r))]
        (is (= "/" ((make-dispatcher {:r handler}) request)))))
    (testing "dispatch on symbol"
      (let [request (assoc request :janus.ring/router (route/router 'r))]
        (is (= "/" ((make-dispatcher {'r handler}) request)))))
    (testing "dispatch on var"
      (let [request (assoc request :janus.ring/router (route/router [:root [nil #'handler]]))]
        (is (= "/" ((make-dispatcher) request)))))
    (testing "dispatch on function"
      (let [request (assoc request :janus.ring/router (route/router [:root [nil handler {}]]))]
        (is (= "/" ((make-dispatcher) request)))))))
