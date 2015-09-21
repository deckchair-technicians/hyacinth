(ns hyacinth.util-test
  (:require [hyacinth.util :refer :all]
            [midje.sweet :refer :all]))

(facts "Path joining with forward-slash"

  (join-path-forward-slash "a") => "a"
  (join-path-forward-slash "a" "b" "c") => "a/b/c"
  (join-path-forward-slash "a/" "b/" "/c") => "a/b/c"
  (join-path-forward-slash "a////" "b/" "/c") => "a/b/c")
