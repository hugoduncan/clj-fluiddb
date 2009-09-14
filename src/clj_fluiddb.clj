(ns clj-fluiddb
  (:use [clojure.contrib.http.connection :as connection]
	[clojure.contrib.duck-streams :as duck]
	[clojure.contrib.fcase :as fcase])
  (:require clojure.contrib.base64 clojure.contrib.json.read clojure.contrib.json.write)
  (:import (java.net URL
		     URLEncoder
		     URLConnection
		     HttpURLConnection
		     UnknownHostException)
	   (java.io.PushbackReader)))

(def *host* "sandbox.fluidinfo.com")
(def *sandbox-host* "sandbox.fluidinfo.com")
(def *user-agent* "CLJ-FLUIDDB")
(def *content-type* "application/json")
(def *fdb* {:fdb nil})

(defn- credentials [user password]
  (str "Basic " (clojure.contrib.base64/encode-str (str user ":" password))))

(defn send-request
  "Send a request to FluidDB.
   We inspect the return data and convert it to a lisp data structure if it is json"
  ([method path] (send-request method path nil nil))
  ([method path body-data] (send-request method path body-data nil))
  ([method path body-data options]
     (println "PAYLOAD " body-data)
     (let [fdb (:fdb *fdb*)
	   host (or (fdb :host) *sandbox-host*)
	   use-https (fdb :use-https)
	   url (str (if (or (nil? use-https) use-https) "https://" "http://") host "/" path)
	   connection (connection/http-connection url)]
       (println url)
       (doto connection
	 (.setRequestMethod (name method))
	 (.setRequestProperty "Accept" (or (and options (options :accept)) (fdb :accept) *content-type*))
	 (.setRequestProperty "Content-type" (if body-data (or (and options (options :content-type)) *content-type*) "text/plain"))
	 (.setRequestProperty "User-agent" (or (fdb :user-agent) *user-agent*)))

       (println (.getRequestProperty connection "Content-type"))
       (if-let [timeout (fdb :timeout)]
	 (.setReadTimeout connection timeout))

       (if-let [user (fdb :user)]
	 (.setRequestProperty connection "Authorization"  (credentials user (fdb :password))))

       (connection/start-http-connection connection body-data)

       (let [response-code (.getResponseCode connection)
	     headers (.getHeaderFields connection)
	     content-types (.get headers "Content-Type")
	     content-type (if content-types (.get content-types 0))
	     content (if (= content-type "application/json")
		       (with-open [input (java.io.PushbackReader. (reader (.getInputStream connection)))]
			 (clojure.contrib.json.read/read-json input))
		       (with-open [input (.getInputStream connection)]
			 (slurp* input)))]
	 [content response-code content-type headers]))))

(defn- to-string [arg]
  (if (keyword? arg)
    (name arg)
    (str arg)))

(defn encode-options [options]
  (apply str "?"
	 (interpose "&"
		    (map #(str (name (first %)) "="
			       (URLEncoder/encode (to-string (second %)) "UTF-8"))
			 options))))

(defn option-value [options option]
  (if (some #(= % option) options) "True" "False"))



(defmacro with-fluiddb
  "Specify connection parameters. Wrap all request methods with this."
  [fdb & body]
  `(binding [*fdb* (assoc *fdb* :fdb ~fdb)]
     ~@body))

;; Users

(defn get-user
  "Get information on the specified user"
  [name]
  (send-request :GET (str "users/" name)))

;; Objects

(defn get-object
  "Get the specified object"
  [id & options]
  (send-request :GET (str "objects/" id (encode-options {:showAbout (option-value options :about)}))))

(defn query-objects [query]
  (send-request :GET (str "objects" (encode-options {:query query}))))

(defn create-object
  ([] (create-object nil))
  ([about]
     (send-request :POST "objects"
		   (clojure.contrib.json.write/json-str
		    (if about  {"about" about})))))

(defn get-object-tag-value
  "Get a tag value for an object.  Defaults to json format for the reply."
  ([id tag] (get-object-tag-value id tag "json"))
  ([id tag format]
     (send-request :GET (str "objects/" id "/" tag (encode-options {:format format}))
		   nil
		   {:accept (fcase/case format
					"json" "application/json"
					"*/*")})))

(defn- flatten
  "Takes any nested combination of sequential things (lists, vectors, etc.) and
  returns their contents as a single, flat sequence. (flatten nil) returns nil."
  [x]
  (filter (complement sequential?)
	  (rest (tree-seq sequential? seq x))))

(defn set-object-tag-value
  "Set the value for a tag on an object."
  ([id tag content]
     (set-object-tag-value id tag content nil nil))
  ([id tag value value-type value-encoding]
     (send-request :PUT (str "objects/" id "/" tag (encode-options {:format "json"}))
		   (clojure.contrib.json.write/json-str
		    (apply hash-map (flatten
				     (filter #(second %)
					     {:value value
					      :valueType value-type
					      :valueEncoding value-encoding}))))
		   {:content-type "application/json"})))

(defn set-object-tag-value-base64
  "Set the value for a tag on an object. base64 encode the value"
  ([id tag content]
     (set-object-tag-value id tag (clojure.contrib.base64/encode-str content) (str (type content)) "base64")))


;; Tags
(defn create-tag [namespace tag description indexed]
  (send-request :POST (str "tags/" namespace)
		(clojure.contrib.json.write/json-str
		 {"name" tag
		  "description" description
		  "indexed" indexed})))

(defn get-tag
  ([namespace tag] (get-tag namespace tag true))
  ([namespace tag return-description]
     (send-request :GET (str "tags/" namespace "/" tag (encode-options {:returnDescription return-description})))))

(defn delete-tag [namespace tag]
  (send-request :DELETE (str "tags/" namespace "/" tag)))

;; Namespaces

(defn get-namespace
  "Return namespace information. Specify :return-description :return-namespace :return-tags as arguments"
  [ns & options] ;;; return-description return-namespace return-tags
  (send-request
   :GET
   (str "namespaces/"
	ns
	(encode-options
	 {:returnDescription (option-value options :return-description)
	  :returnNamespaces (option-value options :return-namespace)
	  :returnTags (option-value options :return-tags)}))))

(defn create-namespace [ns name description]
  (send-request :POST (str "namespaces/" ns)
                 (clojure.contrib.json.write/json-str
		  {"description" description
		   "name" name})))

(defn change-namespace [ns new-description]
  (send-request :PUT (str "namespaces/" ns)
                 (clojure.contrib.json.write/json-str
		  {"description" new-description})))

(defn delete-namespace [ns]
  (send-request :DELETE (str "namespaces/" ns)))


;; Permissions
(defn make-permission-object [policy exceptions]
  (clojure.contrib.json.write/json-str
   { "policy" policy
     "exceptions" exceptions }))


(defn get-namespace-permissions [namespace action]
  (send-request :GET (str "permissions/namespaces/" namespace (encode-options {:action action}))))

(defn set-namespace-permissions [namespace action policy exceptions]
  (send-request :PUT (str "permissions/namespaces/" namespace (encode-options {:action action}))
                (make-permission-object policy exceptions)))

(defn get-tag-permissions [tag action]
  (send-request :GET (str "permissions/tags/" tag (encode-options {:action action}))))

(defn set-tag-permissions [tag action policy exceptions]
  (send-request :PUT (str "permissions/tags/" tag (encode-options {:action action}))
                (make-permission-object policy exceptions)))

(defn get-tag-value-permissions [tag action]
  (send-request :GET (str "permissions/tag-values/" tag (encode-options {:action action}))))

(defn set-tag-value-permissions [tag action policy exceptions]
  (send-request :PUT (str "permissions/tag-values/" tag (encode-options {:action action}))
                (make-permission-object policy exceptions)))

;; Policies

(defn get-policy [user-name category action]
  (send-request :GET (str "policies/" user-name "/" (to-string category) "/" (to-string action))))

(defn set-policy [user-name category action policy exceptions]
  (send-request :PUT (str "policies/" user-name "/" (to-string category) "/" (to-string action))
                 (make-permission-object policy exceptions)))
