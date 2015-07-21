(ns hyacinth.contract
  (:require [midje.sweet :refer :all]
            [clojure.test :refer :all]
            [me.raynes.fs :as fs]
            [hyacinth.protocol :as h])

  (:import [java.util UUID]
           [java.io IOException]))

(defn uuid-str [] (.toString (UUID/randomUUID)))

(defn check-contract [bucket]
  (let [root-directory  (uuid-str)
        root (h/relative bucket root-directory)]
    (try
      (let [parent (h/relative root "1")
            intermediate (h/relative parent "1.1")
            empty-intermediate (h/relative parent "1.2")
            deleted-child (h/relative empty-intermediate "1.2.1")
            first-child (h/relative intermediate "1.1.1")
            first-data (uuid-str)

            second-child (h/relative intermediate "1.1.2")
            second-data (uuid-str)]

        (fact "key works as expected"
              (h/location-key second-child) => (str root-directory "/1/1.1/1.1.2"))

        (fact "put! does not throw exceptions"
              (h/put! first-child first-data) => first-child
              (h/put! second-child second-data) => second-child)

        (fact "reading data works"
          (slurp (h/get-stream first-child))
          => first-data)

        (fact "directory throws IOException if we ask for a stream"
          (h/get-stream intermediate)
          => (throws IOException))

        (fact "nonexistent key throws IOException if we ask for a stream"
          (h/get-stream (h/relative first-child (uuid-str)))
          => (throws IOException))

        (fact "has-data? identifies when keys exist"
          (h/has-data? first-child) => truthy)

        (fact "has-data? identifies when keys do not exist"
          (h/has-data? (h/relative parent (uuid-str))) => falsey)

        (fact "has-data? returns false for directory keys"
          (h/has-data? parent) => falsey)

        (fact "descendant-keys works"
          (h/descendant-keys parent)
          => (contains ["1.1/1.1.1" "1.1/1.1.2"] :in-any-order))

        (fact "descendant-keys returns nothing for data nodes"
          (h/descendant-keys first-child)
          => empty?)

        (fact "descendant-keys returns nothing for non-existent nodes"
          (h/descendant-keys (h/relative parent (uuid-str)))
          => empty?)

        (fact "child-keys works"
          (h/child-keys intermediate)
          => (contains ["1.1.1" "1.1.2"] :in-any-order))

        (fact "child-keys returns nothing for data nodes"
          (h/child-keys first-child)
          => empty?)

        (fact "child-keys includes 'directories' and does not recurse"
          (h/child-keys parent)
          => (contains ["1.1"]))

        (h/put! deleted-child (uuid-str))
        (fact "delete! works"
          (h/delete! deleted-child) => nil
          (h/has-data? deleted-child) => falsey
          (h/get-stream deleted-child) => (throws IOException))

        (fact "delete! is idempotent"
          (h/delete! deleted-child) => falsey)

        (fact "behaviour of delete! on empty directories is undefined, but doesn't throw"
          (h/delete! empty-intermediate)))
      (finally
        (h/recursive-delete! root)))))
