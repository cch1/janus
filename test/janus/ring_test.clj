(ns janus.ring-test
  (:require [janus.ring :refer :all]
            [janus.route :as route]
            [ring.mock.request :as mock]
            [clojure.test :refer :all]))

(deftest handle-invalid-uri
  (let [router (janus.route/router [:R {:a "foo" 'a "bar"}])
        handler (-> (fn echo
                      ([request] request)
                      ([request respond raise] (respond request)))
                    (wrap-identify router))
        response (atom nil)
        respond #(reset! response %)
        exception (atom nil)
        raise #(reset! exception %)]
    (testing "Invalid URI returns response with status 400"
      (let [request (-> (mock/request :get "/")
                        (assoc :uri "/[")) ; have to force in an invalid URI ... mock will not tolerate it
            response (handler request)]
        (is (= 400 (response :status)))))
    (testing "Invalid URI returns async response with status 400"
      (let [request (-> (mock/request :get "/")
                        (assoc :uri "/["))]
        (handler request respond raise)
        (is (= 400 (@response :status)))))))

(deftest add-route-identifier
  (let [router (janus.route/router [:R {:a "foo" 'a "bar"}])
        handler (-> (fn echo
                      ([request] request)
                      ([request respond raise] (respond request)))
                    (wrap-identify router))
        response (atom nil)
        respond #(reset! response %)
        exception (atom nil)
        raise #(reset! exception %)]
    (testing "Identified route and route-params are available in sync request"
      (let [request (mock/request :get "/foo")
            response (handler request)]
        (is (instance? janus.route.Router (response :janus.ring/router)))
        (is (= {:a "foo"} (response :route-params)))))
    (testing "Identified route and route-params are available in async request"
      (let [request (mock/request :get "/foo")]
        (handler request respond raise)
        (is (instance? janus.route.Router (@response :janus.ring/router)))
        (is (= {:a "foo"} (@response :route-params)))))
    (testing "Route parameters are only available for keyword identifiers" ; hmmmm...
      (let [request (mock/request :get "/bar")
            response (handler request)]
        (is (instance? janus.route.Router (response :janus.ring/router)))
        (is (= {'a "bar"} (response :route-params)))))
    (testing "Unidentifiable route behaves gracefully"
      (let [request (mock/request :get "/qux")
            response (handler request)]
        (is (nil? (response :janus.ring/router)))
        (is (nil? (response :route-params)))))))

(def handler (fn ([r] [(-> r :janus.ring/router route/path) (-> r :params)])
               ([r respond raise] (respond [(-> r :janus.ring/router route/path) (-> r :params)]))))

(deftest dispatch-on-route
  (let [request (assoc (mock/request :get "/foo") :params {:x "!"})
        routes [:root [nil :R {:a [#"a(?:.*)" :a]
                               :b [#"b(?:.*)" 'b]
                               :c [#"c(?:.*)" #'handler]
                               :d [#"d(?:.*)" handler]}]]
        router (route/router routes)
        dispatch-table {:a handler 'b handler}
        response (atom nil)
        respond #(reset! response %)
        exception (atom nil)
        raise #(reset! exception %)]
    (testing "dispatch on keyword"
      (let [router (route/identify router "/aX")
            request (assoc request :janus.ring/router router :route-params (into {} (route/parameters router)))]
        (is (= ["/aX" {:x "!" :a "aX"}] ((make-dispatcher dispatch-table) request)))
        ((make-dispatcher dispatch-table) request respond raise)
        (is (= ["/aX" {:x "!" :a "aX"}] @response))))
    (testing "dispatch on symbol"
      (let [router (route/identify router "/bX")
            request (assoc request :janus.ring/router router :route-params (into {} (route/parameters router)))]
        (is (= ["/bX" {:x "!" :b "bX"}] ((make-dispatcher dispatch-table) request)))
        ((make-dispatcher dispatch-table) request respond raise)
        (is (= ["/bX" {:x "!" :b "bX"}] @response))))
    (testing "dispatch on var"
      (let [router (route/identify router "/cX")
            request (assoc request :janus.ring/router router :route-params (into {} (route/parameters router)))]
        (is (= ["/cX" {:x "!" :c "cX"}] ((make-dispatcher) request)))
        ((make-dispatcher dispatch-table) request respond raise)
        (is (= ["/cX" {:x "!" :c "cX"}] @response))))
    (testing "dispatch on function"
      (let [router (route/identify router "/dX")
            request (assoc request :janus.ring/router router :route-params (into {} (route/parameters router)))]
        (is (= ["/dX" {:x "!" :d "dX"}] ((make-dispatcher) request)))
        ((make-dispatcher dispatch-table) request respond raise)
        (is (= ["/dX" {:x "!" :d "dX"}] @response))))
    (testing "Unidentified route triggers not found response"
      (let [request (assoc request :janus.ring/router nil)]
        (is (= {:status 404 :body "Not Found" :headers {"Content-Type" "text/plain"}}
               ((make-dispatcher) request)))
        ((make-dispatcher dispatch-table) request respond raise)
        (is (= {:status 404 :body "Not Found" :headers {"Content-Type" "text/plain"}}
               @response))))
    (testing "Unimplemented handler triggers not implemented response"
      (let [router (route/identify router "/aX")
            request (assoc request :janus.ring/router router :route-params {})]
        (is (= {:status 501 :body "Not Implemented" :headers {"Content-Type" "text/plain"}}
               ((make-dispatcher) request)))
        ((make-dispatcher) request respond raise)
        (is (= {:status 501 :body "Not Implemented" :headers {"Content-Type" "text/plain"}}
               @response))))))

(deftest combined-middleware
  (let [request (assoc (mock/request :get "/aX") :params {:x "!"})
        routes [:root [nil :R {:a [#"a(?:.*)" :a]
                               :b [#"b(?:.*)" 'b]
                               :c [#"c(?:.*)" #'handler]
                               :d [#"d(?:.*)" handler]}]]
        router (route/router routes)
        dispatch-table {:a handler 'b handler}
        handler (wrap-identify (make-dispatcher dispatch-table) router)
        response (atom nil)
        respond #(reset! response %)
        exception (atom nil)
        raise #(reset! exception %)]
    (is (= ["/aX" {:x "!" :a "aX"}] (handler request)))
    (handler request respond raise)
    (is (= ["/aX" {:x "!" :a "aX"}] @response))))
