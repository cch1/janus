(ns janus.ring-test
  (:require [janus.ring :refer :all]
            [janus.route :as route]
            [ring.mock.request :as mock]
            [clojure.test :refer :all]))

(deftest add-route-identifier
  (let [handler identity
        request (mock/request :get "/foo")]
    (testing "Identified route is available in request and route params are available"
      (let [routes [:R {:a "foo"}]
            handler (wrap-identify handler (janus.route/router routes))
            response (handler request)]
        (is (instance? janus.route.Router (response :janus.ring/router)))
        (is (= {:a "foo"} (response :route-params)))))
    (testing "Route parameters are only available for keyword identifiers"
      (let [routes [:R {'a "foo"}]
            handler (wrap-identify handler (janus.route/router routes))
            response (handler request)]
        (is (instance? janus.route.Router (response :janus.ring/router)))
        (is (= {'a "foo"} (response :route-params)))))
    (testing "Unidentifiable route behaves gracefully"
      (let [routes [:R {:a "not-foo"}]
            handler (wrap-identify handler (janus.route/router routes))
            response (handler request)]
        (is (nil? (response :janus.ring/router)))
        (is (nil? (response :route-params)))))))

(def handler (fn [r] [(-> r :janus.ring/router route/path) (-> r :params)]))

(deftest dispatch-on-route
  (let [request (assoc (mock/request :get "/foo") :params {:x "!"})
        routes [:root [nil :R {:a [#"a(?:.*)" :a]
                               :b [#"b(?:.*)" 'b]
                               :c [#"c(?:.*)" #'handler]
                               :d [#"d(?:.*)" handler]}]]
        router (route/router routes)
        dispatch-table {:a handler 'b handler}]
    (testing "dispatch on keyword"
      (let [router (route/identify router "/aX")
            request (assoc request :janus.ring/router router :route-params (into {} (route/parameters router)))]
        (is (= ["/aX" {:x "!" :a "aX"}] ((make-dispatcher dispatch-table) request)))))
    (testing "dispatch on symbol"
      (let [router (route/identify router "/bX")
            request (assoc request :janus.ring/router router :route-params (into {} (route/parameters router)))]
        (is (= ["/bX" {:x "!" :b "bX"}] ((make-dispatcher dispatch-table) request)))))
    (testing "dispatch on var"
      (let [router (route/identify router "/cX")
            request (assoc request :janus.ring/router router :route-params (into {} (route/parameters router)))]
        (is (= ["/cX" {:x "!" :c "cX"}] ((make-dispatcher) request)))))
    (testing "dispatch on function"
      (let [router (route/identify router "/dX")
            request (assoc request :janus.ring/router router :route-params (into {} (route/parameters router)))]
        (is (= ["/dX" {:x "!" :d "dX"}] ((make-dispatcher) request)))))
    (testing "Unidentified route triggers not found response"
      (let [request (assoc request :janus.ring/router nil)]
        (is (= {:status 404 :body "Not Found" :headers {"Content-Type" "text/plain"}}
               ((make-dispatcher) request)))))))

(deftest combined-middleware
  (let [request (assoc (mock/request :get "/aX") :params {:x "!"})
        routes [:root [nil :R {:a [#"a(?:.*)" :a]
                               :b [#"b(?:.*)" 'b]
                               :c [#"c(?:.*)" #'handler]
                               :d [#"d(?:.*)" handler]}]]
        router (route/router routes)
        dispatch-table {:a handler 'b handler}
        handler (wrap-identify (make-dispatcher dispatch-table) router)
        response (handler request)]
    (is (= ["/aX" {:x "!" :a "aX"}] response))))
