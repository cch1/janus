(ns janus.ring-test
  (:require [janus.ring :refer :all]
            [janus.route :as route]
            [ring.mock.request :as mock]
            [clojure.test :refer :all]))

(deftest add-route-identifier
  (testing "Identified route is available in request and params are augmented"
    (let [request (mock/request :get "/foo")
          routes [:R {:a "foo"}]
          request' ((make-identifier identity (janus.route/router routes)) request)]
      (is (instance? janus.route.Router (request' :janus.ring/router)))))
  (testing "Unidentifiable route behaves gracefully"
    (let [request (mock/request :get "/not-a-route")
          routes [:R {:a "foo"}]
          request' ((make-identifier identity (janus.route/router routes)) request)]
      (is (nil? (request' :janus.ring/router))))))

(def handler (fn [r] [(-> r :janus.ring/router route/path) (:route-params r)]))

(deftest dispatch-on-route
  (let [request (mock/request :get "/foo?x=0")
        routes [:root [nil :R {:a [#"a(?:.*)" :a]
                               :b [#"b(?:.*)" 'b]
                               :c [#"c(?:.*)" #'handler]
                               :d [#"d(?:.*)" handler]}]]
        router (route/router routes)
        dispatch-table {:a handler 'b handler}]
    (testing "dispatch on keyword"
      (let [request (assoc request :janus.ring/router (route/identify router "/aX"))]
        (is (= ["/aX" {:a "aX"}] ((make-dispatcher dispatch-table) request)))))
    (testing "dispatch on symbol"
      (let [request (assoc request :janus.ring/router (route/identify router "/bX"))]
        (is (= ["/bX" {:b "bX"}] ((make-dispatcher dispatch-table) request)))))
    (testing "dispatch on var"
      (let [request (assoc request :janus.ring/router (route/identify router "/cX"))]
        (is (= ["/cX" {:c "cX"}] ((make-dispatcher) request)))))
    (testing "dispatch on function"
      (let [request (assoc request :janus.ring/router (route/identify router "/dX"))]
        (is (= ["/dX" {:d "dX"}] ((make-dispatcher) request)))))
    (testing "Unidentified route triggers not found response"
      (let [request (assoc request :janus.ring/router nil)]
        (is (= {:status 404 :body "Not Found"} ((make-dispatcher) request)))))))
