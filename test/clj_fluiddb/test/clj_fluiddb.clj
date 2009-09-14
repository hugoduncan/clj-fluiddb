(ns clj-fluiddb.test.clj-fluiddb
  (:use clj-fluiddb
	clojure.test))

(deftest test-encode-options
  (is (= "?query=abc" (encode-options {:query "abc"})))
  (is (= "?query=abc&number=1" (encode-options {:query "abc" :number 1})))
  (is (= "?query=abc+d&number=1" (encode-options {:query "abc d" :number 1})))
  (is (= "?keyword=kw&opt=abc" (encode-options {:keyword :kw :opt "abc"}))))

(deftest test-option-value
  (is (= "True" (option-value [:fred :blogs] :fred)))
  (is (= "True" (option-value [:fred :blogs] :blogs)))
  (is (= "False" (option-value [:fred :blogs] :tom))))


(deftest test-with-fluiddb
  (let [fdb {:user "test" }]
    (with-fluiddb fdb
      (is (identical? fdb (:fdb clj-fluiddb/*fdb*))))))

(defmacro def-fdb-test [args values]
  `(deftest ~(symbol (str "test-" (first args) "-call"))
     (binding [send-request
	       (fn [& passed#]
		 (is (= passed# ~values)))]
       (~@args))))

(def-fdb-test [get-user "test"] [:GET "users/test"])
(def-fdb-test [get-object "id" :about] [:GET "objects/id?showAbout=True"])
(def-fdb-test [get-object "id"] [:GET "objects/id?showAbout=False"])
(def-fdb-test [query-objects "has fluiddb/about"] [:GET "objects?query=has+fluiddb%2Fabout"])
(def-fdb-test [create-object] [:POST "objects"])
(def-fdb-test [create-object "aboutme"] [:POST "objects" "{\"about\":\"aboutme\"}"])

;; TODO test the rest of the interface functions
;; TODO test send-request