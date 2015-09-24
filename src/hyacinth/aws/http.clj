(ns hyacinth.aws.http
  (:require [hyacinth.aws.signing :as signing]
            [org.httpkit.client :as http])
  (:import [java.text SimpleDateFormat]
           [java.util TimeZone Date]))

(defn today []
  (.format (doto (SimpleDateFormat. "yyyyMMdd'T'HHmmss'Z'")
             (.setTimeZone (TimeZone/getTimeZone "UTC")))
           (Date.)))

(defn authorize-req
  [handler access-key secret-key region service]
  (fn [request]
    (-> request
        (assoc-in [:headers "x-amz-date"] (today))
        (signing/authorize
          access-key
          secret-key
          region
          service)
        handler)))

(defn add-http-kit-opts
  [handler]
  (fn [request]
    (-> request
        (assoc :as :stream
               :timeout 6000)
        handler)))

(defn add-x-amz-content-sha256 [handler]
  (fn [request]
    (-> request
        (assoc-in [:headers "x-amz-content-sha256"] (signing/hex-sha256-hash (:body request)))
        handler)))

(defn throw-exception
  [handler acceptable-response?]
  (fn [request]
    (let [{:keys [status body] :as response} (handler request)]
      (if (acceptable-response? response)
        response
        (let [response-body (when body (if (string? body) body (slurp body)))
              message       (if status
                              (str "Problem with AWS request: " status "\n" response-body)
                              "Problem with AWS request. No response received")]
          (throw (ex-info message
                          {:request  (assoc request :body request)
                           :response (assoc response :body response-body)})))))))

(defmacro either-exception [& body]
  `(try
     (let [r# (do ~@body)]
       [r# nil])
     (catch Exception e#
       [nil e#])))

(defmacro with-retries [n & body]
  `(loop [first-exception# nil
          retries-left#    ~n]
     (let [[r# e#] (either-exception ~@body)]
       (if e#
         (if (> retries-left# 0)
           (recur (or first-exception# e#) (dec retries-left#))
           (throw (or first-exception# e#)))
         r#))))

(defn retry
  [handler n]
  (fn [request]
    (with-retries n (handler request))))

(defn success-or-client-error [status]
  (or (<= 200 status 299) (<= 400 status 499)))

(defn deref-response [handler]
  (fn [request]
    @(handler request)))

(defn default-handler [access-key secret-key region service]
  (-> http/request
      (deref-response)
      (authorize-req access-key secret-key region service)
      (add-http-kit-opts)
      (add-x-amz-content-sha256)
      (throw-exception (fn [{:keys [status]}]
                         (and status (success-or-client-error status))))
      (retry 1)))



