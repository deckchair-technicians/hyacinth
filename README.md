# hyacinth

A common abstraction over S3 buckets, the file system, FTP and in-memory maps.

Supports a useful subset of the full S3 functionality

## Usage

```clj
(let [root (relative bucket (uuid-str))]
    (try
      (let [parent (relative root "1")
            intermediate (relative parent "1.1")
            empty-intermediate (relative parent "1.2")
            deleted-child (relative empty-intermediate "1.2.1")
            first-child (relative intermediate "1.1.1")
            first-data (uuid-str)

            second-child (relative intermediate "1.1.2")
            second-data (uuid-str)]

        (fact "put! does not throw exceptions"
          (put! first-child first-data) => first-child
          (put! second-child second-data) => second-child)

        (fact "reading data works"
          (slurp (get-stream first-child))
          => first-data)

        (fact "directory throws IOException if we ask for a stream"
          (get-stream intermediate)
          => (throws IOException))

        (fact "nonexistent key throws IOException if we ask for a stream"
          (get-stream (relative first-child (uuid-str)))
          => (throws IOException))

        (fact "has-data? identifies when keys exist"
          (has-data? first-child) => truthy)

        (fact "has-data? identifies when keys do not exist"
          (has-data? (relative parent (uuid-str))) => falsey)

        (fact "has-data? returns false for directory keys"
          (has-data? parent) => falsey)

        (fact "descendant-keys works"
          (descendant-keys parent)
          => (contains ["1.1/1.1.1" "1.1/1.1.2"] :in-any-order))

        (fact "descendant-keys returns nothing for data nodes"
          (descendant-keys first-child)
          => empty?)

        (fact "child-keys works"
          (child-keys intermediate)
          => (contains ["1.1.1" "1.1.2"] :in-any-order))

        (fact "child-keys returns nothing for data nodes"
          (child-keys first-child)
          => empty?)

        (fact "child-keys includes 'directories' and does not recurse"
          (child-keys parent)
          => (contains ["1.1"]))

        (put! deleted-child (uuid-str))
        (fact "delete! works"
          (delete! deleted-child) => nil
          (has-data? deleted-child) => falsey
          (get-stream deleted-child) => (throws IOException))

        (fact "delete! is idempotent"
          (delete! deleted-child) => falsey)

        (fact "behaviour of delete! on empty directories is undefined, but doesn't throw"
          (delete! empty-intermediate)))
      (finally
        (recursive-delete! root))))
```
