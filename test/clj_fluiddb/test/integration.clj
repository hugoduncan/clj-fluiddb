;; -*- coding: utf-8 -*-
(ns clj-fluiddb.test.integration
  (:use clj-fluiddb
	clojure.test))

(def *test-fdb* {:user "test" :password "test" :host *sandbox-host* :use-https false})

(defn- gen-uuid-string
  "Generate a unique namespace name, so that tests can be isolated."
  []
  (str (java.util.UUID/randomUUID)))

(defn- gen-namespace []
  (let [ns (gen-uuid-string)
	[values response] (create-namespace (*test-fdb* :user) ns "")]
    (is (= 201 response))
    (str (*test-fdb* :user) "/" ns)))

(deftest test-create-object
  (with-fluiddb *test-fdb*
    (let [ [values response-code] (create-object)]
      (is (= 201 response-code))
      (is (values "id"))
      (is (values "URI"))
      (is (= (str "http://" (*test-fdb* :host) "/objects/" (values "id")) (values "URI")))
      (let [ [values2 response-code content-type headers] (get-object (values "id") :about)]
	(is (= 200 response-code))
	(is (= (values2 "tagPaths") []))))))

(deftest test-create-object-about
  (with-fluiddb *test-fdb*
    (let [about (gen-uuid-string)
	  [values response-code] (create-object about)]
      (is (= 201 response-code))
      (is (values "id"))
      (is (values "URI"))
      (is (= (str "http://" (*test-fdb* :host) "/objects/" (values "id")) (values "URI")))
      (let [ [values2 response-code content-type headers] (get-object (values "id") :about)]
	(is (= 200 response-code))
	(is (= (values2 "tagPaths") ["fluiddb/about"]))
	(is (= about (values2 "about")))))))

(deftest test-create-namespace
  (with-fluiddb *test-fdb*
    (let [ns (gen-uuid-string)
	  description "A namespace to contain our test and prevent clashes"]
      (let [[values response rest] (create-namespace (*test-fdb* :user) ns description)]
	(is (= 201 response))
	(is (values "id"))
	(is (values "URI"))
	(is (= (str "http://" (*test-fdb* :host) "/namespaces/test/" ns) (values "URI"))))
      (let [ [values response rest] (get-namespace (str (*test-fdb* :user) "/" ns) :return-description)]
	(is (= 200 response))
	(is (values "id"))
	(is (= description (values "description")))))))

(deftest test-gen-namespace
  (with-fluiddb *test-fdb*
    (let [ns (gen-namespace)
	  [values response rest] (get-namespace ns)]
      (is (= 200 response))
      (is (values "id")))))

(deftest test-delete-namespace
  (with-fluiddb *test-fdb*
    (let [ns (gen-namespace)
	  [values response rest] (delete-namespace ns)]
      (is (= 204 response))
      (is (thrown? java.io.FileNotFoundException (get-namespace ns))))))

(deftest test-create-tag
  (with-fluiddb *test-fdb*
    (let [ns (gen-namespace)
	  name "atag"
	  description "a tag for testing"
	  [values response rest] (create-tag ns name description false)]
      (is (= 201 response))
      (is (values "id"))
      (is (values "URI"))
      (is (= (str "http://" (*test-fdb* :host) "/tags/" ns "/" name) (values "URI")))
      (delete-namespace ns))))


(deftest test-delete-tag
  (with-fluiddb *test-fdb*
    (let [ns (gen-namespace)
	  name "atag"
	  description "a tag for testing"
	  [values response rest] (create-tag ns name description false)]
      (is (= 201 response))
      (is (values "id"))
      (is (values "URI"))
      (is (= (str "http://" (*test-fdb* :host) "/tags/" ns "/" name) (values "URI")))
      (let [[values2 response2 rest] (delete-tag ns name)]
	(is (= 204 response2)))
      (delete-namespace ns))))

(deftest test-set-object-tag-value
  (with-fluiddb *test-fdb*
    (let [ns (gen-namespace)
	  tag "tag"
	  [object response rest] (create-object)
	  id (object "id")]
      (is (= 201 response))
      (is id)
      (is (= 201 (second (create-tag ns tag "tag-description" false))))
      (doseq [i ["hello" 1 1.2 true false]]
	(let [[values response content-type headers] (set-object-tag-value id (str ns "/" tag) i)]
	  (is (= 204 response)))
	(let [[values response content-type headers] (get-object-tag-value id (str ns "/" tag))]
	  (is (= 200 response))
	  (is (= "application/vnd.fluiddb.value+json" content-type))
	  (is (= i values)))
	(let [[values response content-type headers] (delete-object-tag id (str ns "/" tag))]
	  (is (= 204 response)))
	(is (thrown? java.io.FileNotFoundException (get-object-tag-value id (str ns "/" tag)))))
      (is (= 204 (second (delete-tag ns tag))))
      (is (= 204 (second (delete-namespace ns)))))))

(deftest test-set-object-tag-value-binary
  (with-fluiddb *test-fdb*
    (let [ns (gen-namespace)
	  tag "tag"
	  [object response rest] (create-object)
	  id (object "id")]
      (is (= 201 response))
      (is id)
      (is (= 201 (second (create-tag ns tag "tag-description" false))))
      (let [[values response content-type headers] (set-object-tag-value id (str ns "/" tag) (.getBytes "fred") "application/octet-stream")]
	(is (= 204 response)))
      (let [[values response content-type headers] (get-object-tag-value id (str ns "/" tag))]
	(is (= 200 response))
	(is (= "application/octet-stream" content-type))
	(is (= "fred" values)))
      (let [[values response content-type headers] (delete-object-tag id (str ns "/" tag))]
	(is (= 204 response)))
      (is (thrown? java.io.FileNotFoundException (get-object-tag-value id (str ns "/" tag))))
      (is (= 204 (second (delete-tag ns tag))))
      (is (= 204 (second (delete-namespace ns)))))))