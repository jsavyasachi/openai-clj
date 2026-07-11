(ns openai.videos-test
  (:require [clojure.test :refer [deftest is]] [openai.videos :as videos])
  (:import (com.openai.models.videos VideoCreateParams VideoModel VideoSeconds VideoSize)))
(set! *warn-on-reflection* true)
(deftest translates-video-create
  (let [^VideoCreateParams p (#'videos/->create-params
                              {:prompt "A sunrise" :model "sora-2"
                               :size "1280x720" :seconds "8"})]
    (is (= "A sunrise" (.prompt p)))
    (is (= "sora-2" (.asString ^VideoModel (.get (.model p)))))
    (is (= "1280x720" (.asString ^VideoSize (.get (.size p)))))
    (is (= "8" (.asString ^VideoSeconds (.get (.seconds p)))))))
