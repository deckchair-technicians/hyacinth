(ns hyacinth.util-test
  (:require [hyacinth.util :refer :all]
            [midje.sweet :refer :all]))

(facts "Path joining with forward-slash"

  (join-paths "a") => "a"
  (join-paths "a" "b" "c") => "a/b/c"
  (join-paths "a/" "b/" "/c") => "a/b/c"
  (join-paths "a////" "b/" "/c") => "a/b/c")
