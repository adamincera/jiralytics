(ns jira-scraper.handler
  (:require [compojure.core :refer :all]
            [compojure.handler :as handler]
            [compojure.route :as route]
            [clojure.string :as string]
            [cheshire.core :refer :all]
            [jira-scraper.core :refer :all]
            [clojure.core.cache :refer :all]
            ))

(defn vec-contains? [v x] (boolean (some #(= x %) v)))

(def username (:username (load-string (slurp "./credentials"))))

(def password (:password (load-string (slurp "./credentials"))))

(def cache (atom {}))

(defn fetch 
  "now with cache!"
  [project]
  (if (and (= project (:project @cache)) (has? @cache (first (keys @cache))) (not (empty? @cache))) 
      (vec (vals (dissoc @cache :project)))
    (do
      (def issues (get-project project username password))
      (reset! cache (ttl-cache-factory 
        (assoc (zipmap (id-list issues) issues) :project project) :ttl 3600000))
      (vec (vals (dissoc @cache :project))))
    )
  )

(defn vec-string
  "takes a vector and concatenates all its elements into a string"
  ([x c]
   (vec-string (str (first x)) c (rest x)))
  ([current c x]
   (if (empty? x) current (vec-string (str current c (first x)) c (rest x)))))

(defn parse-expression
  "builds a clojure expression from a partial URI formatted as 'f/function1/param1/param2/.../f/function2/param1/param2/...'"
  [issues uri]
  (if (or (empty? uri) (= uri "/"))
    (str issues)
    (let [allowed (vec (map str (conj (keys (ns-publics 'jira-scraper.core)) "count")))] 
      (string/replace (str 
                        "("
                        (vec-string 
                          (reverse 
                            (map #(if (not (vec-contains? allowed %)) (throw (Exception. (str "Function not allowed: " %))) %) (vec (map first (rest (map #(string/split % #"/") (string/split uri #"/f/"))))))) " (")
                        " " issues " \""
                        (vec-string (vec (map #(vec-string (rest %) " ") (rest (map #(string/split % #"/") (string/split uri #"/f/"))))) "\") \"")
                        "\")"
                        ) 
                      #"\"\"" ""))
    )
  )

(defroutes app-routes 
  (GET "/" [] "Welcome to the JIRA Analytics Web Application!")
;  (GET "/cache/count" [] (generate-string (count @cache)))
  (GET "/project/:project*" request (generate-string 
                                      (binding [*ns* (find-ns 'jira-scraper.handler)] (try (load-string
                                                              (parse-expression (str "(fetch \"" (-> request :params :project) "\")") 
                                                                                (str (-> request :params :*))))
                                                            (catch Exception e (str "Something went wrong: " (.getMessage e)))))))
  (route/not-found "Not Found")
  )

(def app
  (handler/site app-routes))
