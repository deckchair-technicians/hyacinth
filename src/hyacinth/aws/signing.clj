(ns hyacinth.aws.signing
  (:require [midje.sweet :refer :all]
            [clojure.string :as string])

  (:import [hyacinth.aws URLEncoder URIEncoder]
           [java.security MessageDigest]
           [javax.crypto Mac]
           [javax.crypto.spec SecretKeySpec]
           [clojure.lang IPersistentMap]
           [java.net URI]
           [java.util Base64]))

(defn normalise-headers [request]
  (assoc request :headers (->> (:headers request)
                               (map (fn [[k v]]
                                      [(string/lower-case (name k)) v]))
                               (into {}))))

(defn canonical-query-string [query-params]
  (->> query-params
       (map (fn [[k v]]
              [(name k) v]))
       (sort-by first)
       (map (fn [[k v]]
              (str (URLEncoder/encode k)
                   "="
                   (URLEncoder/encode v))))
       (string/join \&)))

(defn trim-header-value [v]
  (let [trimmed (string/trim v)]
    (if (and (.startsWith trimmed "\"") (.endsWith trimmed "\""))
      trimmed
      (as-> trimmed $
            (string/split $ #"\s+")
            (string/join " " $)))))

(defn canonical-headers [headers]
  (->> headers
       (map (fn [[k v]]
              [k (trim-header-value v)]))
       (sort-by first)
       (map (fn [[k v]]
              (str k ":" v "\n")))
       (apply str)))

(defn signed-headers [headers]
  (->> headers
       (map key)
       (sort)
       (string/join ";")))

(defn positive-biginteger [#^bytes bytes]
  (BigInteger. 1 bytes))

(defn left-pad [^String s n p]
  (str (string/join "" (take (- n (.length s)) (repeat p)))
       s))

(defn digest [s algorithm]
  (-> (MessageDigest/getInstance algorithm)
      (doto (.update #^bytes (if s (.getBytes s "UTF8") (byte-array 0))))
      (.digest)))

(defn bytes->hex [#^bytes bytes]
  (-> bytes
      (positive-biginteger)
      (.toString 16)
      (left-pad 64 "0")))

(defn hex-sha256-hash [^String body]
  (-> (digest body "SHA-256")
      (bytes->hex)))

(defn bytes->base-64 [#^bytes bytes]
  (-> (Base64/getEncoder)
      (.encodeToString bytes)))

(defn base64-md5-hash [body]
  (-> (digest body "MD5")
      (bytes->base-64)))

(defn canonical-uri [url]
  (let [p (.getPath (URI. url))]
    (if (or (= p "/") (string/blank? p))
      "/"
      (->> (string/split p #"/")
           (map #(URIEncoder/encode %))
           (string/join "/")))))

(defn canonical-request [{:keys [method url query-params headers body]}]
  (str (string/upper-case (name method)) "\n"
       (canonical-uri url) "\n"
       (canonical-query-string query-params) "\n"
       (canonical-headers headers) "\n"
       (signed-headers headers) "\n"
       (hex-sha256-hash body)))

(defn request-date-stamp [^IPersistentMap request]
  (or (get-in request [:headers "x-amz-date"])
      (get-in request [:headers "date"])))

(defn credential-scope [date-stamp region service]
  {:pre [date-stamp region service]}
  (string/join "/" [(.substring date-stamp 0 8) region service "aws4_request"]))

(defn string-to-sign [request region service]
  (let [date-stamp (request-date-stamp request)]
    (str "AWS4-HMAC-SHA256\n"
         date-stamp "\n"
         (credential-scope date-stamp region service) "\n"
         (hex-sha256-hash (canonical-request request)))))

(def algorithm "HmacSHA256")
(defn hmac-256 [#^bytes k ^String data]
  (-> (Mac/getInstance algorithm)
      (doto (.init (SecretKeySpec. k algorithm)))
      (.doFinal (.getBytes data "UTF8"))))

(defn signature-key [^String secret-key
                     ^String date-stamp
                     ^String region
                     ^String service]
  {:pre [secret-key date-stamp region service]}
  (-> (.getBytes (str "AWS4" secret-key) "UTF8")
      (hmac-256 (.substring date-stamp 0 8))
      (hmac-256 region)
      (hmac-256 service)
      (hmac-256 "aws4_request")))


(defn signature [^IPersistentMap request
                 ^String secret-key
                 ^String region
                 ^String service]
  (-> (signature-key secret-key (request-date-stamp request) region service)
      (hmac-256 (string-to-sign request region service))
      (bytes->hex)))

(defn authorization-header [request access-key secret-key region service]
  (str "AWS4-HMAC-SHA256 Credential="
       access-key "/" (credential-scope (request-date-stamp request) region service) ", "
       "SignedHeaders=" (signed-headers (:headers request)) ", "
       "Signature=" (signature request secret-key region service)))

(defn authorize [request access-key secret-key region service]
  (assoc-in request [:headers "Authorization"]
            (authorization-header (normalise-headers request) access-key secret-key region service)))

