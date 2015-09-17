(ns hyacinth.s3-test
  (:require [midje.sweet :refer :all]
            [hyacinth.s3 :refer :all]))

(facts "canonical-query-string"
  (fact "leaves A-Z a-z 0-9 '-' and '_'  unencoded (just like Java URLEncoder)"
    (canonical-query-string {:Some-HEAder "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"})
    => "Some-HEAder=ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_")

  (fact "leaves '.' and '~' unencoded (unlike Java URLEncoder)"
    (canonical-query-string {:Some-HEAder ".~"})
    => "Some-HEAder=.~")

  (fact "encodes space (unlike Java URLEncoder)"
    (canonical-query-string {:Some-HEAder " "})
    => "Some-HEAder=%20")

  (fact "encodes other characters (like Java URLEncoder)"
    (canonical-query-string {"Some-HEAder" "[]{}"})
    => "Some-HEAder=%5B%5D%7B%7D"))

(facts "trim-header-value"
  (fact "trims start and end space"
    (trim-header-value " a b  ") => "a b")

  (fact "trims space in the middle of value"
    (trim-header-value "a     b") => "a b")

  (fact "does not trim quoted strings"
    (trim-header-value "\"a     b\"") => "\"a     b\""))


(def example-request
  {:method  :post
   :url     "https://iam.amazonaws.com/"
   :headers {"Host"         " iam.amazonaws.com"
             "Content-Type" " application/x-www-form-urlencoded; charset=utf-8"
             "X-Amz-Date"   "20110909T233600Z"}
   :body    "Action=ListUsers&Version=2010-05-08"})


(fact "Hashed canonical request"
  ; See http://docs.aws.amazon.com/general/latest/gr/sigv4-create-canonical-request.html

  (fact "canonical request looks correct"
    (canonical-request (normalise-headers example-request))
    => (str "POST\n"
            "/\n"
            "\n"
            "content-type:application/x-www-form-urlencoded; charset=utf-8\n"
            "host:iam.amazonaws.com\n"
            "x-amz-date:20110909T233600Z\n"
            "\n"
            "content-type;host;x-amz-date\n"
            "b6359072c78d70ebee1e81adcbab4f01bf2c23245fa365ef83fe8f1f955085e2"))

  (fact "hash of canonical request is correct"
    (hex-sha256-hash (canonical-request (normalise-headers example-request)))
    => "3511de7e95d28ecd39e9513b642aee07e54f4941150d8df8bf94b328ef7e55e2"))

(fact "string to sign"
  ; See http://docs.aws.amazon.com/general/latest/gr/sigv4-create-string-to-sign.html
  (string-to-sign (normalise-headers example-request)
                  "us-east-1"
                  "iam")
  => (str "AWS4-HMAC-SHA256\n"
          "20110909T233600Z\n"
          "20110909/us-east-1/iam/aws4_request\n"
          "3511de7e95d28ecd39e9513b642aee07e54f4941150d8df8bf94b328ef7e55e2"))

(fact "signature-key"
  ; See http://docs.aws.amazon.com/general/latest/gr/signature-v4-examples.html#signature-v4-common-coding-mistakes

  (-> (signature-key "wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY"
                     "20120215"
                     "us-east-1"
                     "iam")
      (bytes->hex))
  => "f4780e2d9f65fa895f9c67b32ce1baf0b0d8a43505a000a1a9e090d414db404d")

(fact "signature"
  ; See http://docs.aws.amazon.com/general/latest/gr/sigv4-calculate-signature.html
  (signature (normalise-headers example-request) "wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY" "us-east-1" "iam")
  => "ced6826de92d2bdeed8f846f0bf508e8559e98e4b0199114b84c54174deb456c")

(fact "authorization-header"
  ; See http://docs.aws.amazon.com/general/latest/gr/sigv4-add-signature-to-request.html
  (authorization-header (normalise-headers example-request) "AKIDEXAMPLE" "wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY" "us-east-1" "iam")
  => (str "AWS4-HMAC-SHA256 "
          "Credential=AKIDEXAMPLE/20110909/us-east-1/iam/aws4_request, "
          "SignedHeaders=content-type;host;x-amz-date, "
          "Signature=ced6826de92d2bdeed8f846f0bf508e8559e98e4b0199114b84c54174deb456c"))

(fact "authorize"
  ; See http://docs.aws.amazon.com/general/latest/gr/sigv4-add-signature-to-request.html
  (-> (authorize example-request "AKIDEXAMPLE" "wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY" "us-east-1" "iam")
      (:headers)
      (get "Authorization"))
  => (str "AWS4-HMAC-SHA256 "
          "Credential=AKIDEXAMPLE/20110909/us-east-1/iam/aws4_request, "
          "SignedHeaders=content-type;host;x-amz-date, "
          "Signature=ced6826de92d2bdeed8f846f0bf508e8559e98e4b0199114b84c54174deb456c"))
