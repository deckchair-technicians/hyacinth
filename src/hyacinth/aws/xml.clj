(ns hyacinth.aws.xml
  "clojure.xml adds all kinds of whitespace, which AWS doesn't like")

(defn emit-element [e]
  (if (instance? String e)
    (print e)
    (do
      (print (str "<" (name (:tag e))))
      (when (:attrs e)
        (doseq [attr (:attrs e)]
          (print (str " " (name (key attr)) "='" (val attr)"'"))))
      (if (:content e)
        (do
          (print ">")
          (doseq [c (:content e)]
            (emit-element c))
          (println (str "</" (name (:tag e)) ">")))
        (println "/>")))))

(defn emit [x]
  (println "<?xml version='1.0' encoding='UTF-8'?>")
  (emit-element x))

(defn get-children [elements tag]
  (->> elements
       (filter #(= (:tag %) tag))
       (mapcat :content)))

(defn get-in-xml [tags xml]
  (reduce get-children (:content xml) tags))

