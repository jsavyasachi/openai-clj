(ns openai.videos-test
  (:require [clojure.test :refer [deftest is]]
            [openai.videos :as videos])
  (:import (com.openai.core JsonValue)
           (com.openai.models.videos Video Video$Builder Video$Status VideoCreateError
                                     VideoCreateParams VideoCreateParams$InputReference
                                     VideoModel VideoSeconds VideoSize)))

(set! *warn-on-reflection* true)

(defn- opt [o]
  (when (.isPresent ^java.util.Optional o) (.get ^java.util.Optional o)))

(deftest translates-video-create
  (let [^VideoCreateParams p (#'videos/->create-params
                              {:prompt "A sunrise" :model "sora-2"
                               :size "1280x720" :seconds 8
                               :input-reference (.getBytes "png" "UTF-8")})]
    (is (= "A sunrise" (.prompt p)))
    (is (= "sora-2" (.asString ^VideoModel (opt (.model p)))))
    (is (= "1280x720" (.asString ^VideoSize (opt (.size p)))))
    (is (= "8" (.asString ^VideoSeconds (opt (.seconds p)))))
    (is (= [112 110 103]
           (vec (.readAllBytes
                 (.asStream ^VideoCreateParams$InputReference
                            (opt (.inputReference p)))))))))

(deftest converts-video-response-with-present-only-optionals
  (let [^java.util.Optional absent (java.util.Optional/empty)
        error (-> (VideoCreateError/builder) (.code "render_failed")
                  (.message "Could not render") (.build))
        ^Video$Builder base (doto (Video/builder)
                             (.id "video_1") (.createdAt 10) (.model "sora-2")
                             (.object_ (JsonValue/from "video")) (.progress 25)
                             (.seconds (VideoSeconds/of "8"))
                             (.size (VideoSize/of "1280x720"))
                             (.status (Video$Status/of "queued"))
                             (.completedAt absent) (.error absent)
                             (.expiresAt absent) (.prompt absent)
                             (.remixedFromVideoId absent))]
    (is (= {:id "video_1" :status :queued :model "sora-2" :progress 25
            :created-at 10 :size "1280x720" :seconds "8"}
           (#'videos/video->map (.build base))))
    (let [full (#'videos/video->map
                (.build (doto base (.completedAt 20) (.expiresAt 30) (.prompt "A sunrise")
                          (.error error))))]
      (is (= {:id "video_1" :status :queued :model "sora-2" :progress 25
              :created-at 10 :size "1280x720" :seconds "8"
              :completed-at 20 :expires-at 30 :prompt "A sunrise"}
             (dissoc full :error)))
      (is (map? (:error full))))))
