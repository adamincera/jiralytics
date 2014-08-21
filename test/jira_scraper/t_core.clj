(ns jira-scraper.t-core
  (:use midje.sweet)
  (:use [jira-scraper.core]))

(defn fetch [project] (get-project project "aincera" "DummyPassword"))

(def all-issues (fetch "IOS"))

(facts "about `map-dates`"
       (fact "should return a map..."
             (type (map-dates all-issues)) => clojure.lang.PersistentHashMap
             )
       (fact "...of strings...."
             (every? string? (keys (map-dates all-issues))) => true
             )
       (fact "...which map to vectors."
             (every? vector? (vals (map-dates all-issues))) => true
             )
       )

(facts "about `elapsed-time`"
       (fact "should return a map..."
             (type (elapsed-time all-issues)) => clojure.lang.PersistentHashMap
             )
       (fact "...of strings..."
             (every? string? (keys (elapsed-time all-issues))) => true
             )
       (fact "...which map to longs."
             (every? #(= (type % ) java.lang.Long) (vals (elapsed-time all-issues))) => true
             )
       )

(facts "about `id-list`"
       (fact "should return a lazy sequence..."
             (type (id-list all-issues)) => clojure.lang.LazySeq
             )
       (fact "...of strings."
             (every? string? (id-list all-issues)) => true
             )
       )

(facts "about `all-resolved`"
      (fact "should contain no unresolved issues"
            (filter #(not (resolved? %)) (all-resolved all-issues)) => ()
            (every? resolved? (all-resolved all-issues)) => true
            )
      )
