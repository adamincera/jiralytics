(ns jira-scraper.core
  (:require [clojure.set :as c-set]
            [clj-http.client :as client]
            [clj-time.core :as t]
            [clj-time.format :as f]
            [clojure.tools.trace :as trace]
            [incanter.core :as incanter]
            [incanter.stats :as stats]
            [incanter.charts :as charts]
            ))

(def jira-url (str "jira.gilt.com"))
(def formatter (f/formatters :date-time))

(defn get-project
  "posts query for all issues within a given project, or a given number if specified"
  ([project username password]
   (((client/post (str "https://" jira-url "/rest/api/2/search") {
                                                                  :basic-auth [username password]
                                                                  :form-params {:jql (str "project=" project)
                                                                                :startAt 0
                                                                                :maxResults -1 
                                                                                }
                                                                  :content-type :json
                                                                  :as :json
                                                                  }) :body) :issues)
   )

  ([project username password max-results]
   (((client/post (str "https://" jira-url "/rest/api/2/search") {
                                                                  :basic-auth [username password]
                                                                  :form-params {:jql (str "project=" project)
                                                                                :startAt 0
                                                                                :maxResults max-results 
                                                                                }
                                                                  :content-type :json
                                                                  :as :json
                                                                  }) :body) :issues)
   )

  ([project username password params max-results]
   (((client/post (str "https://" jira-url "/rest/api/2/search") {
                                                                  :basic-auth [username password]
                                                                  :form-params {:jql (str "project=" project)
                                                                                :startAt 0
                                                                                :maxResults max-results
                                                                                :fields params
                                                                                }
                                                                  :content-type :json
                                                                  :as :json
                                                                  }) :body) :issues)
   )

  )

(defn in-days
  "gives elapsed time in days, rounded down"
  ([start end]
   (quot (t/in-minutes start end) 1440))

  ([length]
   (quot length 1440)
   )
  )


(defn map-dates
  "creates a mapping of each issue and its start and end dates" 
  ; case that will be called by user
  ([issues]
   (map-dates issues nil))
  ; recursive case 
  ([issues current] 
   (if (empty? issues) ;(nil? issues)))
     current
     (map-dates (rest issues) 
                (assoc current 
                       ((first issues) :id) 
                       (vector 
                         (((first issues) :fields) :created) 
                         (((first issues) :fields) :resolutiondate)
                         )
                       )
                ) 
     )
   )
  )

(defn ordered-keys
  "returns the keys from a map in order"
  [issues]
  (sort (keys issues))
  )

(defn vec-elapse 
  "takes a vector of two times and returns the elapsed time" 
  [[x y]]
  (if (nil? y)
    (t/in-minutes (t/interval (f/parse formatter x) (t/now)))
    (t/in-minutes (t/interval (f/parse formatter x) (f/parse formatter y)))
    )
  )

(defn second-elapse
  "calls vec-elapse on the second element of a two-element vector"
  [[x y]]
  (vector x (vec-elapse y))
  )

(defn seq-to-map
  ([s]
   (seq-to-map s nil)
   )
  ([s current]
   (if (empty? s)
     current
     (seq-to-map (rest s)
                 (assoc current (first (first s)) (second (first s)))
                 )
     )
   )
  )


(defn elapsed-time
  "takes a mapping of the form returned by map-dates and calculates the elapsed time on each issue"
  [issues]
  (seq-to-map (map second-elapse (seq issues)))
  ) 


(defn unresolved
  "returns a mapping of all the unresolved issues"
  [issues]
  (filter #(nil? (:resolutiondate (:fields %))) issues)
  )

(defn resolved
  "returns a map of all the resolved issues"
  [issues]
  (filter #(not (nil? (:resolutiondate (:fields %)))) issues))

(defn oldest-unresolved
  "finds oldest unresolved issue"
  [issues]
  (in-days (apply max (vals (elapsed-time (unresolved issues)))))
  )

(defn recursive-map
  "recursively search through successive levels of a map of maps"
  ([issues params]
   (if (= 1 (count params))
     (issues (first params))
     (recursive-map issues (first params) (vec (rest params)))
     )
   )

  ([issues param1 params]
   (if (nil? issues) 
     (str "empty")
     (if (empty? params)
       (issues param1)
       (recur (issues param1) (first params) (vec (rest params)))
       )
     )
   )
  )

(defn id-list
  "creates a list of all the id's from a collection of issues"
  [issues]
  (map #(:id %) issues)
  )

(defn gen-filter
  "filters results according to one parameteri. params is a vector containing the 'path' to the parameter in question"
  [issues term params]
  ;    (println (vec (rest params)))
  (vec (filter #(= term (recursive-map % (first params) (vec (rest params)))) issues)))
