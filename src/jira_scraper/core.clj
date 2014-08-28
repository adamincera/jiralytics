(ns jira-scraper.core
  (:require [clojure.set :as c-set]
            [clj-http.client :as client]
            [clj-time.core :as t]
            [clj-time.format :as f]
            [clojure.tools.trace :as trace]
            [incanter.core :as incanter]
            [incanter.stats :as stats]
            [incanter.charts :as charts]
            [clojure.string :as string]
            [compojure.core :refer :all]
            [compojure.handler :as handler]
            [clojure.string :as string]
            [cheshire.core :refer :all]
            ))

(def jira-url (str "jira.gilt.com"))

(def formatter (f/formatters :date-time))
(def simple-formatter (f/formatters :date))


(defn get-project
  "posts query for all issues within a given project, or a given number if specified"
  ([project username password]
   (try (((client/post (str "https://" jira-url "/rest/api/2/search") {:basic-auth [username password]
                                                                       :form-params {:jql (str "project=" project)
                                                                                     :startAt 0
                                                                                     :maxResults -1 
                                                                                     }
                                                                       :content-type :json
                                                                       :as :json
                                                                       }) :body) :issues)
        (catch Exception e (str "something went wrong! " (.getMessage e)))) 
   )

  ([project username password max-results]
   (try (((client/post (str "https://" jira-url "/rest/api/2/search") {
                                                                       :basic-auth [username password]
                                                                       :form-params {:jql (str "project=" project)
                                                                                     :startAt 0
                                                                                     :maxResults max-results 
                                                                                     }
                                                                       :content-type :json
                                                                       :as :json
                                                                       }) :body) :issues)
        (catch Exception e (str "ruh roh: "((.getMessage e) :status))))
   )

  ([project username password params max-results]
   (try (((client/post (str "https://" jira-url "/rest/api/2/search") {
                                                                       :basic-auth [username password]
                                                                       :form-params {:jql (str "project=" project)
                                                                                     :startAt 0
                                                                                     :maxResults max-results
                                                                                     :fields params
                                                                                     }
                                                                       :content-type :json
                                                                       :as :json
                                                                       }) :body) :issues)
        (catch Exception e (str "ruh roh: " ((.getMessage e) :status))))

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

(defn vec-elapse 
  "takes a vector of two times and returns the elapsed time" 
  [[x y]]
  (if (nil? x) nil
    (if (nil? y)
      (in-days (t/in-minutes (t/interval (f/parse formatter x) (t/now))))
      (in-days (t/in-minutes (t/interval (f/parse formatter x) (f/parse formatter y))))
      )
    )
  )

  (defn second-elapse
    "calls vec-elapse on the second element of a two-element vector"
    [[x y]]
    (vector x (vec-elapse y))
    )

  (defn elapsed-time
    "creates a map of the form {'id' age} containing all issues that are passed to it"
    [issues]
    (into {} (map second-elapse (seq (map-dates issues))))
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
    (if (map? issues)
      (assoc issues :age (vec-elapse [(-> issues :fields :created) (get (get issues :fields) :resolutiondate)]))
      (map #(assoc % :age (vec-elapse [(-> % :fields :created) (-> % :fields :resolutiondate)])) issues)
      )
    )

  (defn oldest
    "finds age of oldest issue, resolved or unresolved"
    [issues]
    (apply max (vals (elapsed-time issues)))
    )

  (defn count-age
    "finds the number of issues of a given age"
    [issues age]
    (count (filter #(= age (% :age)) issues)))



  (defn oldest-unresolved
    "finds oldest unresolved issue"
    [issues]
    (if (= 1 (count (filter #(= (oldest (all-unresolved issues)) (:age %)) (assoc-age issues))))
      (first (filter #(= (oldest (all-unresolved issues)) (:age %)) (assoc-age issues)))
      (vec (filter #(= (oldest (all-unresolved issues)) (:age %)) (assoc-age issues)))
      )
    )

  (defn recursive-map
    "recursively search through successive levels of a map of maps"
    ; case called by user
    ([issues params]
     (if (= 1 (count params))
       (issues (first params))
       (recursive-map issues (first params) (vec (rest params)))
       )
     )
    ; recursive case
    ([issues param1 params]
     (if (nil? issues) 
       (str "empty")
       (if (empty? params)
         (issues param1)
         (recur (get issues param1) (first params) (vec (rest params)))
         )
       )
     )
    )

  (defn unresolved-hist
    "creates a histogram displaying the ages of unresolved tickets"
    [issues]
    (incanter/view (charts/histogram (map in-days (vals (elapsed-time (all-unresolved issues))))))
    )

(defn resolved-hist
  "creates a histogram displaying the ages of resolved tickets"
  [issues]
  (incanter/view (charts/histogram (map in-days (vals (elapsed-time (all-resolved issues))))))
  )

(defn id-list
  "creates a list of all the id's from a collection of issues"
  [issues]
  (map #(:id %) issues)
  )

(defn avg-age
  "finds average age of issues in a set"
  [issues]
  (stats/mean (vals (elapsed-time issues))))

(defn avg-resolved
  "finds average resolution time for a set of issues"
  [issues]
  (int (stats/mean (vals (elapsed-time (all-resolved issues))))))

(defn avg-unresolved
  "finds average age of unresolved issues in a set"
  [issues]
  (int (stats/mean (vals (elapsed-time (all-unresolved issues))))))

(defn gen-filter
  "filters results according to one parameter. params is a vector containing the 'path' to the parameter in question"
  [issues term params]
  (vec (filter #(or (= (str term "@gilt.com") (recursive-map % (first params) (vec (rest params)))) (= term (recursive-map % (first params) (vec (rest params))))) issues)))

(defn get-id
  "returns the issue pertaining to an id number"
  [issues id]
  (first (filter #(= id (% :id)) issues)))

(defn event-horizon
  "determines the age after which an issue will likely go unresolved. default confidence level is 95%"
  ([issues]
   (event-horizon issues 0.95)
   )
  ([issues alpha]
   (+ 
     (avg-age (all-resolved issues)) 
     (/ 
       (* 
         (stats/quantile-normal alpha) 
         (stats/sd (vals (elapsed-time(all-resolved issues))))
         ) 
       (Math/sqrt (count (all-resolved issues)))
       )
     )
   )
  )

(defn same-day
  "determines whether two date-time objects correspond to the same date"
  [day1 day2]
  (and (= (t/year day1) (t/year day2)) (= (t/month day1) (t/month day2)) (= (t/day day1) (t/day day2)))
  )

(defn created-on
  "finds all issues created on a certain date"
  [issues data]
  (filter #(same-day ((% :fields) :created)) issues)
  )

(defn date?
  "determines whether a string is a properly formatted date of the form yyyy-mm-dd"
  [s]
  (if (string? s) (try (boolean (f/parse simple-formatter s))
                       (catch IllegalArgumentException e (boolean false))) (boolean false)))

(defn older-than
  "finds all issues created before a date, an issue, or an age"
  [issues cutoff]
  (vec (if (map? cutoff)
         (filter #(t/before? (f/parse formatter (-> % :fields :created)) (f/parse formatter (-> cutoff :fields :created))) issues) 
         (if (date? cutoff) 
           (filter #(t/before? (f/parse formatter (-> % :fields :created)) (f/parse simple-formatter cutoff)) issues)
           (filter #(> (get % :age) (if (string? cutoff) (read-string cutoff) cutoff)) (assoc-age issues))
           )
         ))
  )

(defn younger-than
  "finds all issues created after a date, an issue, or an age"
  [issues cutoff]
  (vec (if (map? cutoff)
         (filter #(t/after? (f/parse formatter (-> % :fields :created)) (f/parse formatter (-> cutoff :fields :created))) issues) 
         (if (date? cutoff) 
           (filter #(t/after? (f/parse formatter (-> % :fields :created)) (f/parse simple-formatter cutoff)) issues)
           (filter #(< (% :age) (if (string? cutoff) (read-string cutoff) cutoff)) (assoc-age issues))
           )
         ))
  )

(defn as-old
  "finds all issues of a given age, created on a certain day, or on the same day as another issue"
  [issues cutoff]
  (vec (if (map? cutoff)
         (filter #(same-day (f/parse formatter (-> % :fields :created)) (f/parse formatter (-> cutoff :fields :created))) issues) 
         (if (date? cutoff) 
           (filter #(same-day (f/parse formatter (-> % :fields :created)) (f/parse simple-formatter cutoff)) issues)
           (filter #(= (% :age) (if (string? cutoff) (read-string cutoff) cutoff)) (assoc-age issues))
           )
         ))
  )

(defn older-than-or-equal
  ""
  [issues cutoff]
  (vec (distinct (concat (older-than issues cutoff) (as-old issues cutoff))))
  )

(defn younger-than-or-equal
  ""
  [issues cutoff]
  (vec (distinct (concat (younger-than issues cutoff) (as-old issues cutoff))))
  )

(defn priority
  "finds issues matching a given set of priorities"
  ([issues x]
   (if (coll? x)
     (if (empty? (rest x))
       (priority issues (first x))
       (distinct (concat (priority issues (first x)) (priority issues (rest x)))))
     (vec (gen-filter issues (string/capitalize x) [:fields :priority :name]))))
  ([issues x & more]
   (distinct (concat (priority issues x) (priority issues more))))
  )

(defn user
  "finds all issues assigned to a given user"
  [issues user]
  (gen-filter issues user (if (= "unassigned" user) [:fields :assignee] [:fields :assignee :name]))
  )

