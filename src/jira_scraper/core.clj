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
    (in-days (t/in-minutes (t/interval (f/parse formatter x) (t/now))))
    (in-days (t/in-minutes (t/interval (f/parse formatter x) (f/parse formatter y))))
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


(defn resolved?
  "returns true if an issue has been resolved and false if it has not"
  [issue]
  (not (nil? (:resolutiondate (:fields issue))))
  )

(defn all-unresolved
  "returns a mapping of all the unresolved issues"
  [issues]
  (filter #(not (resolved? %)) issues)
  )

(defn all-resolved
  "returns a map of all the resolved issues"
  [issues]
  (filter #(resolved? %) issues))

(defn assoc-resolved
  "adds a param :resolved to a vector of issues"
  [issues]
  (map #(assoc % :resolved (resolved? %)) issues)
 )

(defn assoc-age
  "associates a new param :age with each issue in the vector issues"
  [issues]
  (map #(assoc % :age (in-days (vec-elapse [((% :fields) :created) ((% :fields) :resolutiondate)]))) issues)
  )

(defn oldest
  "finds oldest issue, resolved or unresolved"
  [issues]
  (apply max (vals (elapsed-time (map-dates issues))))
)

(defn count-age
  "finds the number of issues of a given age"
  [issues age]
  (count (filter #(= age (% :age)) issues))
  
  )

(defn oldest-unresolved
  "finds oldest unresolved issue"
  [issues]
  (first (filter issues #(= (in-days (apply max (vals (elapsed-time (all-unresolved issues))))) (elapsed-time (second %)))))
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

(defn unresolved-hist
  "creates a histogram displaying the ages of unresolved tickets"
  [issues]
  (incanter/view (charts/histogram (map in-days (vals (elapsed-time (map-dates (all-unresolved issues)))))))
  )

(comment 
(def resolved-unresolved-hist
  "produces a line chart comparing durations of resolved and unresolved issues"
  [issues]
  (incanter/view (charts/line-chart (
)))))

(defn id-list
  "creates a list of all the id's from a collection of issues"
  [issues]
  (map #(:id %) issues)
  )

(defn avg-age
  "finds average age of issues in a set"
  [issues]
  (int (stats/mean (map in-days (vals (elapsed-time (map-dates issues)))))))
    
(defn gen-filter
  "filters results according to one parameteri. params is a vector containing the 'path' to the parameter in question"
  [issues term params]
  (vec (filter #(= term (recursive-map % (first params) (vec (rest params)))) issues)))

(defn event-horizon
  "determines the age after which an issue will likely go unresolved. default confidene level is 95%"
  ([issues]
   (event-horizon 0.95)
   )
  ([issues alpha]
   (+ 
;     (avg-age (resolved issues)) 
     (stats/mean issues)
      (/ 
       (* 
         (stats/quantile-normal alpha) 
         (stats/sd issues
;           (vals (elapsed-time (map-dates (all-resolved issues))))
                   )
         ) 
       (Math/sqrt (count issues))
       )
     )
   )
  )

