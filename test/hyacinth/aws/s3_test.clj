(ns hyacinth.aws.s3-test
  (:require [midje.sweet :refer :all]
            [vice.midje :refer [matches]]
            [vice.schemas :as vs]
            [hyacinth.aws.s3 :refer :all]
            [hyacinth.test-support :as test-support]
            [clojure.xml :as xml])
  (:import [java.util UUID]))

(def props (test-support/props "donotcheckin/s3.http.properties"))

(def http-aws (apply ->s3-http ((juxt :bucket-name :access-key :secret-key) @props)))

(defn stream->list-objects-keys [s]
  (-> s
      (xml/parse)
      (extract-list-object-keys "")))

(defn stream->list-objects-common-prefixes [s prefix]
  (-> s
      (xml/parse)
      (extract-list-object-common-prefixes prefix)))

(defmacro with-temporary-location [[var http-aws] & body]
  `(let [~var (str (UUID/randomUUID))]
     (try
       ~@body
       (finally
         (multi-object-delete! ~http-aws ~var)))))

(when @props
  (fact "get-region"
    (apply get-region ((juxt :bucket-name :access-key :secret-key) @props))
    => (:expected-bucket-region @props))


  (facts "put, get, delete"
    (let [body (str (UUID/randomUUID))]
      (with-temporary-location [location-key http-aws]
        (fact "put works"
          (put-object! http-aws location-key body)
          => (contains {:status 200}))

        (fact "get works"
          (slurp (:body (get-object http-aws location-key)))
          => body)

        (fact "delete works"
          (delete-object! http-aws location-key)
          => (contains {:status 204}))

        (fact "delete definitely works"
          (get-object http-aws location-key)
          => (contains {:status 404})))))

  (facts "list-objects"
    (let [body        (str (UUID/randomUUID))
          descendants ["/1/1.1/1.1.1"
                       "/1/1.1/1.1.2"
                       "/1/1.2/1.2.1"
                       "/2/2.1"]]
      (with-temporary-location [parent-key http-aws]
        (fact "putting test files works"
          (doseq [k descendants]
            (put-object! http-aws (str parent-key k) body)))

        (fact "list works for all descendants"
          (-> (list-objects http-aws parent-key)
              (update-in [:body] stream->list-objects-keys))
          => (matches {:status 200
                       :body   (vs/in-order (map #(str parent-key %) descendants))}))

        (fact "list works for leaf node children"
          (let [prefix (str parent-key "/1/1.1")]
            (-> (list-objects http-aws prefix "/")
                (update-in [:body] #(stream->list-objects-common-prefixes % prefix))))
          => (matches {:status 200
                       :body   (vs/in-order ["1.1.1" "1.1.2"])}))

        (fact "list works for children with trailing slash"
          (let [prefix (str parent-key "/1/")]
            (-> (list-objects http-aws prefix "/")
                (update-in [:body] #(stream->list-objects-common-prefixes % prefix))))
          => (matches {:status 200
                       :body   (vs/in-order ["1.1" "1.2"])}))

        (fact "list works without trailing slash"
          (let [prefix (str parent-key "/1")]
            (-> (list-objects http-aws prefix "/")
                (update-in [:body] #(stream->list-objects-common-prefixes % prefix))))
          => (matches {:status 200
                       :body   (vs/in-order ["1.1" "1.2"])}))

        (fact "list works with leading slash"
          (let [prefix (str "/" parent-key "/1")]
            (-> (list-objects http-aws prefix "/")
                (update-in [:body] #(stream->list-objects-common-prefixes % prefix))))
          => (matches {:status 200
                       :body   (vs/in-order ["1.1" "1.2"])}))))))
