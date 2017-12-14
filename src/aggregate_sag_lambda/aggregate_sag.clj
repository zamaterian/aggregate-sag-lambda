(ns aggregate-sag-lambda.aggregate-sag
  (:gen-class
   :implements [com.amazonaws.services.lambda.runtime.RequestStreamHandler])
  (:require [amazonica.aws.s3 :refer [put-object]]
            [amazonica.aws.sns :refer [publish]]
            [amazonica.aws.dynamodbv2 :refer :all]
            [cheshire.core :refer :all]
            [aggregate-sag-lambda.ecommon :refer :all])
  (:import (java.io ByteArrayInputStream)))


(defn opret-sag [e]
  (let [sags-id (e :sags-id)
        p (e :payload)]
    {:id sags-id
     :ejendoms-id (p :ejendoms-id)
     :journalplan (p :journalplan)
     :sagsbehandler (p :sagsbehandler)
     :titel (p :titel)
     :vurderings-terminer  (p :vurderings-terminer)
     :ansvarlig (p :ansvarlig)
     :type (p :type)
     :journalposter []
     :parter (if (p :parter) (p :parter) [])}))

(defn opdater-sag [s e]
  (let [p (e :payload)]
    (if (= (:id s) (p :sags-id))
      (dissoc (merge s p) :sags-id)
      nil)))

(defn opret-jp [s p]
  (let [jp {:jp-id (p :jp-id)
            :myndighed (p :myndighed)
            :type (p :type)
            :sagsbehandler (p :sagsbehandler)
            :titel (p :titel)
            :journalnotater []
            :dokumenter []}]
    (assoc s :journalposter (vec (conj (s :journalposter) jp)))))

(defn opdater-jp [s p]
  (let [jp (first (filter #(= (:jp-id %) (:jp-id p)) (s :journalposter)))]
    (if (and jp (= (:id s) (p :sags-id)))
      (assoc s :journalposter (vec (conj (filter #(not= (:jp-id p) (:jp-id %)) (s :journalposter)) (dissoc (merge jp p) :sags-id))))
      nil)))

(defn opret-jn [s p]
  (let [jn-id (p :jn-id)
        jp-id (p :jp-id)
        jps (s :journalposter)
        jp (first (filter #(= (:jp-id %) jp-id) jps))
        jns (jp :journalnotater)
        jn {:jn-id jn-id
            :myndighed (p :myndighed)
            :sagsbehandler (p :sagsbehandler)
            :journal-notat (p :journal-notat)}
        jp (assoc jp :journalnotater (conj jns jn))
        jps (vec (conj (filter #(not= (:jp-id %) jp-id) jps) jp))]
    (assoc s :journalposter jps)))

(defn opdater-jn [s p]
  (let [jp (first (filter #(= (:jp-id %) (:jp-id p)) (s :journalposter)))
        jn (first (filter #(= (:jn-id %) (:jn-id p)) (jp :journalnotater)))
        jn (dissoc (merge jn p) :sags-id :jp-id)
        jns (vec (conj (filter #(not= (:jn-id %) (:jn-id p)) (jp :journalnotater)) jn))
        jp-new (assoc jp :journalnotater jns)
        jps (vec (conj (filter #(not= (:jp-id %) (:jp-id p)) (s :journalposter)) jp-new))]
     (assoc s :journalposter jps)))

(defn opret-dokument [s p]
  (let[dok-id (p :dok-id)
       jp-id (p :jp-id)
       jps (s :journalposter)
       jp (first (filter #(= (:jp-id %) jp-id) jps))
       doks (jp :dokumenter)
       dok {:dok-id dok-id
            :titel (p :titel)
            :type (p :type)
            :journaliseret (p :journaliseret)
            :link (p :link)
            :tags (p :tags)
            :mime-type (p :mime-type)}
       jp (assoc jp :dokumenter (conj doks dok))
       jps (vec (conj (filter #(not= (:jp-id %) jp-id) jps) jp))]
    (assoc s :journalposter jps)))


(defn opdater-dokument [s p]
  (let [jp (first (filter #(= (:jp-id %) (:jp-id p)) (s :journalposter)))
        dok (first (filter #(= (:dok-id %) (:dok-id p)) (jp :dokumenter)))
        dok (dissoc (merge dok p) :sags-id :jp-id)
        doks (vec (conj (filter #(not= (:dok-id %) (:dok-id p)) (jp :dokumenter)) dok))
        jp-new (assoc jp :dokumenter doks)
        jps (vec (conj (filter #(not= (:jp-id %) (:jp-id p)) (s :journalposter)) jp-new))]
     (assoc s :journalposter jps)))

(defn opret-part [s p]
  (let [part {:part-id (p :part-id)
              :type (p :type)
              :oprettet-af (p :oprettet-af)
              :navn (p :navn)
              :cpr (p :cpr)}]
    (assoc s :parter (vec (conj (s :parter) part)))))

(defn opdater-part [s p]
  (let [part (first (filter #(= (:part-id %) (:part-id p)) (s :parter)))]
    (if (and part (= (:id s) (p :sags-id)))
      (assoc s :parter (vec (conj (filter #(not= (:part-id p) (:part-id %)) (s :parter)) (dissoc (merge part p) :sags-id :jp-id))))
      nil)))

(defn handle-event [s e]
  (cond
    (= "sag-oprettet" (:type e)) (opret-sag e)
    (= "jp-oprettet" (:type e)) (opret-jp s (e :payload))
    (= "jn-oprettet" (:type e)) (opret-jn s (e :payload))
    (= "dokument-oprettet" (:type e)) (opret-dokument s (e :payload))
    (= "part-oprettet" (:type e)) (opret-part s (e :payload))
    (= "sag-opdateret" (:type e)) (opdater-sag s e)
    (= "jp-opdateret" (:type e)) (opdater-jp s (e :payload))
    (= "jn-opdateret" (:type e)) (opdater-jn s (e :payload))
    (= "dokument-opdateret" (:type e)) (opdater-dokument s (e :payload))
    (= "part-opdateret" (:type e)) (opdater-part s (e :payload))))

(defn aggregate [events]
  (prn "E" events)
  (reduce handle-event {} events))

(def -handleRequest (mk-req-handler aggregate))
