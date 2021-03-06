(ns vetd-app.common.fx
  (:require [accountant.core :as acct]
            [vetd-app.util :as util]
            [re-frame.core :as rf]))

(defn validated-dispatch-fx
  [db event validator-fn]
  (let [[bad-input message] (validator-fn)]
    (if bad-input
      {:db (assoc-in db [:page-params :bad-input] bad-input)
       :toast {:type "error" 
               :title "Error"
               :message message}}
      {:db (assoc-in db [:page-params :bad-input] nil)
       :dispatch event})))

(rf/reg-fx
 :confetti
 (fn [_]
   (.startConfetti js/window)
   (js/setTimeout #(.stopConfetti js/window) 3000)))

(rf/reg-event-fx
 :maybe-show-extension-modal
 [(rf/inject-cofx :local-store [:has-shown-extension-modal?])]
 (fn [{:keys [local-store]}]
   (when-not (:has-shown-extension-modal? local-store)
     {:local-store {:has-shown-extension-modal? true} ;; only show this once
      :dispatch [:modal
                 {:header "Connected to Vetd \"Sales Email Gatekeeper\" Extension!"
                  :content [:<>
                            [:div#extension-intro-1
                             [:h3 "How To Forward Sales Outreach"]
                             [:p "Simply click on the Vetd logo in the Gmail toolbar when you receive a sales email. The email will be forwarded to Vetd and archived. We will then make sure that vendor doesn't email you again."]
                             [:img {:src "https://vetd-app-assets.s3.amazonaws.com/extension-intro-1.png"
                                    :style {:width "100%"}}]]]
                  :buttons [{:text "Skip"}
                            {:text "Next Page"
                             :event [:do-fx {:dispatch [:modal
                                                        {:header "Connected to Vetd \"Sales Email Gatekeeper\" Extension!"
                                                         :content [:<>
                                                                   [:div#extension-intro-2
                                                                    [:h3 "Review Weekly"]
                                                                    [:p "Each week we'll send you a recap of how many vendors reached out, and whether or not any of them are worth checking out."]
                                                                    [:img {:src "https://vetd-app-assets.s3.amazonaws.com/extension-intro-2.png"
                                                                           :style {:width "100%"}}]]]
                                                         :buttons [{:text "Skip"}
                                                                   {:text "Next Page"
                                                                    :event [:do-fx {:dispatch [:modal
                                                                                               {:header "Connected to Vetd \"Sales Email Gatekeeper\" Extension!"
                                                                                                :content [:<>
                                                                                                          [:div#extension-intro-2
                                                                                                           [:h3 "Learn More"]
                                                                                                           [:p "If any of the vendors look interesting, review them further on Vetd with a personalized pricing estimate, pitch, and additional details about the product or service."]
                                                                                                           [:img {:src "https://vetd-app-assets.s3.amazonaws.com/extension-intro-3.png"
                                                                                                                  :style {:width "100%"}}]]]
                                                                                                :buttons [{:text "Close"}
                                                                                                          {:text "Go to Gmail"
                                                                                                           :event [:do-fx {:nav {:external-url "https://gmail.com"
                                                                                                                                 :new-tab? true}}]
                                                                                                           :icon "external"
                                                                                                           :color "blue"}]
                                                                                                :size "small"}]}]
                                                                    :no-hide? true
                                                                    :icon "arrow right"
                                                                    :icon-on-right? true 
                                                                    :color "blue"}]
                                                         :size "small"}]}]
                             :no-hide? true
                             :icon "arrow right"
                             :icon-on-right? true 
                             :color "blue"}]
                  :size "small"}]})))

;; Tries to send a message to the Vetd Chrome Extension
(rf/reg-fx
 :chrome-extension
 (fn [{:keys [cmd args]}]
   (when js/chrome
     (js/chrome.runtime.sendMessage "gpmepfmejmnhphphkcabhlhfpccaabkj" ;; Chrome Web Store
                                    (clj->js {:command cmd
                                              :args args})
                                    (fn [success?]
                                      (when success?
                                        (rf/dispatch [:maybe-show-extension-modal])))))))

;; given a React component ref, scroll to it on the page
(rf/reg-fx
 :scroll-to
 (fn [ref]
   (when ref
     (.scrollIntoView ref (clj->js {:behavior "smooth"
                                    :block "start"})))))

(rf/reg-event-fx
 :scroll-to
 (fn [{:keys [db]} [_ ref-key]]
   {:scroll-to (-> db :scroll-to-refs ref-key)}))

(rf/reg-event-fx
 :reg-scroll-to-ref
 (fn [{:keys [db]} [_ ref-key ref]]
   {:db (assoc-in db [:scroll-to-refs ref-key] ref)}))

(rf/reg-event-fx
 :read-link
 (fn [_ [_ link-key]]
   {:ws-send {:payload {:cmd :read-link
                        :return {:handler :read-link-result
                                 :link-key link-key}
                        :key link-key}}}))

(rf/reg-event-fx
 :read-link-result
 [(rf/inject-cofx :local-store [:join-group-link-key])]
 (fn [{:keys [db local-store]} [_ {:keys [cmd output-data]} {{:keys [link-key]} :return}]]
   (case cmd ; make sure your case nav's the user somewhere (often :nav-home)
     :create-verified-account {:toast {:type "success"
                                       :title "Account Verified"
                                       :message "Thank you for verifying your email address."}
                               :local-store {:session-token (:session-token output-data)}
                               :dispatch-later [{:ms 100 :dispatch [:ws-get-session-user]}
                                                ;; first-time login, go to stack page
                                                {:ms 1000 :dispatch [:nav-home true]} ;; TODO hacky
                                                (when (:join-group-link-key local-store)
                                                  {:ms 1300 :dispatch [:read-link (:join-group-link-key local-store)]})
                                                {:ms 1500 :dispatch [:do-fx {:analytics/track
                                                                             {:event "FRONTEND Signup Complete"
                                                                              :props {:category "Accounts"
                                                                                      :label "Standard"}}}]}]}
     :password-reset {:toast {:type "success"
                              :title "Password Updated"
                              :message "Your password has been successfully updated."}
                      :local-store {:session-token (:session-token output-data)}
                      :dispatch-later [{:ms 100 :dispatch [:ws-get-session-user]}
                                       {:ms 200 :dispatch [:nav-home]}]}
     ;; this comes from an org invite email or org invite link
     :invite-user-to-org (if (:user-exists? output-data)
                           {:toast {:type "success"
                                    ;; this is a vague "Joined!" because it could
                                    ;; be an org or a community
                                    ;; also see fx :join-org-signup-return
                                    :title "Joined!"
                                    :message (str "You accepted an invitation to join " (:org-name output-data))}
                            :local-store {:session-token (:session-token output-data)}
                            :dispatch-later [{:ms 100 :dispatch [:ws-get-session-user]}
                                             {:ms 200 :dispatch [:nav-home]}]}
                           {:db (assoc db
                                       :signup-by-link-org-name (:org-name output-data)
                                       :signup-by-link-need-email? (:need-email? output-data))
                            :dispatch [:nav-join-org-signup link-key]})
     ;; this comes from the group invite link
     :g/join (if (:logged-in? db)
               {:dispatch-later [(when-not (= (:page db) :b/stack)
                                   {:ms 100 :dispatch [:nav-home]})
                                 {:ms 200
                                  :dispatch
                                  (if (:join-group-link-key local-store)
                                    ;; they don't need to visually consent because they were on a custom login or signup page
                                    ;; that already informed them that they will be join to the community
                                    [:g/accept-invite link-key]
                                    ;; they need to consent
                                    [:modal
                                     {:header (str "Join the " (:group-name output-data) " community")
                                      :content (str "Do you want to join the " (:group-name output-data) " community?")
                                      :buttons [{:text "Cancel"}
                                                {:text "Join"
                                                 :event [:g/accept-invite link-key]
                                                 :color "blue"}]
                                      :size "tiny"}])}]
                :local-store {:join-group-link-key nil
                              :join-group-name nil}}
               {:local-store {:join-group-link-key link-key
                              :join-group-name (:group-name output-data)}
                :dispatch-later [{:ms 100 :dispatch [:nav-signup :buyer]}]})
     :email-unsubscribe {:toast {:type "success"
                                 :title "Unsubscribed"
                                 :message "You will no longer receive emails like this."}
                         :dispatch [:nav-home]}
     {:toast {:type "error"
              :title "That link is expired or invalid."}
      :dispatch [:nav-home]})))

(rf/reg-sub
 :bad-input
 :<- [:page-params]
 (fn [{:keys [bad-input]}] bad-input))

(rf/reg-event-fx
 :bad-input.reset
 (fn [{:keys [db]}]
   {:db (assoc-in db [:page-params :bad-input] nil)}))

(rf/reg-event-fx
 :modal
 (fn [{:keys [db]} [_ {:keys [header content buttons size]}]]
   (-> db :modal :showing?& (reset! true)) ;; nastiness
   ;; not doing merge props because we want to nil out unused props
   {:db (update db :modal merge {:header header
                                 :content content
                                 :buttons buttons
                                 :size size})}))

(rf/reg-sub
 :modal
 (fn [{:keys [modal]}] modal))

(rf/reg-fx
 :nav
 (fn nav-fx [{:keys [path external-url new-tab?]}]
   (cond external-url
         (js/setTimeout #(if new-tab?
                           (.open js/window external-url)
                           (.assign (aget js/window "location") external-url))
                        1000)

         @util/force-refresh?&
         (.assign (aget js/window "location") path)

         :else
         (acct/navigate! path))))

;; think of every stash key->value as a separate stack of events that you can
;; push (store) and pop (dispatch)
(defn dispatch-stash-push
  [{:keys [db]} [_ k event]]
  {:pre [(keyword? k) (vector? event)]}
  {:db (update-in db
                  [:dispatch-stash k]
                  (fn [stash]
                    (if stash
                      (conj stash event)
                      [event])))})
(rf/reg-event-fx :dispatch-stash.push dispatch-stash-push)

(rf/reg-event-fx
 :dispatch-stash.pop
 (fn [{:keys [db]} [_ k]]
   {:dispatch (-> db :dispatch-stash k peek)
    :db (update-in db [:dispatch-stash k] pop)}))

(rf/reg-event-fx
 :dispatch-stash.pop-all
 (fn [{:keys [db]} [_ k]]
   (when-let [stash (-> db :dispatch-stash k)]
     {:dispatch-n stash
      :db (update db :dispatch-stash dissoc k)})))

;; Dark Mode
(rf/reg-sub
 :dark-mode?
 (fn [{:keys [dark-mode?]}] dark-mode?))

(rf/reg-event-fx
 :dark-mode.on
 (fn [{:keys [db]}]
   {:db (assoc db :dark-mode? true)
    :local-store {:dark-mode? "true"}
    :add-class {:node (util/first-node-by-tag "body")
                :class "dark-mode"}}))

(rf/reg-event-fx
 :dark-mode.off
 (fn [{:keys [db]}]
   {:db (assoc db :dark-mode? false)
    :local-store {:dark-mode? "false"}
    :remove-class {:node (util/first-node-by-tag "body")
                   :class "dark-mode"}}))

(rf/reg-event-fx
 :reify-modes
 (fn [{:keys [db]}]
   {:dispatch-n (cond-> []
                  (:dark-mode? db) (conj [:dark-mode.on]))}))

;; use sparingly (prefer reactive components)
(rf/reg-fx
 :add-class
 (fn [{:keys [node class]}]
   (util/add-class node class)))

(rf/reg-fx
 :remove-class
 (fn [{:keys [node class]}]
   (util/remove-class node class)))
