(ns hyacinth.aws.s3
  (:require [hyacinth.aws.http :refer :all]
            [hyacinth.aws.xml :as aws-xml]
            [hyacinth.aws.signing :as signing]
            [hyacinth.util :as util]
            [clojure.xml :as xml]))

(defn normalise-key [k prefix]
  (-> k
      (.substring (count prefix))
      (util/remove-trailing-slash)
      (util/remove-leading-slash)))

(defn extract-list-object-keys [list-bucket-response prefix]
  (->>
    list-bucket-response
    (aws-xml/get-in-xml [:Contents :Key])
    (map #(normalise-key % prefix))))

(defn extract-list-object-common-prefixes [list-bucket-response prefix]
  (->>
    list-bucket-response
    (aws-xml/get-in-xml [:CommonPrefixes :Prefix])
    (map #(normalise-key % prefix))
    (concat (extract-list-object-keys list-bucket-response prefix))))

(defprotocol IS3Http
  (get-object [this location-key])
  (list-objects
    [this location-key]
    [this location-key delimiter])
  (put-object! [this location-key body])
  (delete-object! [this location-key])
  (multi-object-delete! [this location-key]))

(defn multi-object-delete-object [k]
  {:tag     :Object
   :content [{:tag     :Key
              :content [k]}]})

(defn multi-object-delete-body [keys]
  (with-out-str
    (aws-xml/emit {:tag     :Delete
                   :content (concat
                              [{:tag     :Quiet
                                :content ["false"]}]
                              (map multi-object-delete-object keys))})))

(defn basic-s3-request [bucket-name location-key method]
  {:method  method
   :url     (str "https://" bucket-name ".s3.amazonaws.com/" (-> location-key
                                                                 (util/remove-leading-slash)
                                                                 (util/remove-trailing-slash)))
   :headers {"Host" (str bucket-name ".s3.amazonaws.com")}})

(defn merge-requests [& requests]
  (apply merge-with merge requests))

(deftype S3Http [handler bucket-name]
  IS3Http
  (get-object [_ location-key]
    (-> (basic-s3-request bucket-name location-key :get)
        (handler)))

  (list-objects [_this location-key]
    (-> (basic-s3-request bucket-name "" :get)
        (merge-requests
          {:query-params {:prefix (-> location-key
                                      (util/remove-leading-slash))}})
        (handler)))

  (list-objects [_this location-key delimiter]
    (-> (basic-s3-request bucket-name "" :get)
        (merge-requests
          {:query-params {:prefix    (-> location-key
                                         (util/remove-leading-slash)
                                         (util/trailing-slash))
                          :delimiter delimiter}})
        (handler)))

  (put-object! [this location-key body]
    (-> (basic-s3-request bucket-name location-key :put)
        (merge-requests {:body body})
        (handler)))

  (delete-object! [this location-key]
    (-> (basic-s3-request bucket-name location-key :delete)
        (handler)))

  (multi-object-delete! [this location-key]
    (let [body (-> (list-objects this location-key)
                   :body
                   (xml/parse)
                   (extract-list-object-keys "")
                   (multi-object-delete-body))]
      (-> (basic-s3-request bucket-name "" :post)
          (merge-requests
            {:query-params {:delete ""}
             :body         body
             :headers      {"Content-MD5"    (signing/base64-md5-hash body)
                            "Content-Length" (str (count (.getBytes body "UTF8")))}})

          (handler)))))

(defn get-region [bucket-name access-key secret-key]
  (let [handler (default-handler access-key secret-key "us-east-1" "s3")]
    (-> {:method       :get
         :url          (str "https://s3.amazonaws.com/" bucket-name)
         :query-params {:location ""}
         :headers      {"Host" "s3.amazonaws.com"}}
        (handler)
        :body
        xml/parse
        :content
        first
        (or "us-east-1"))))

(defn ->s3-http
  [bucket-name access-key secret-key]
  (->S3Http (default-handler access-key secret-key (get-region bucket-name access-key secret-key) "s3") bucket-name))
