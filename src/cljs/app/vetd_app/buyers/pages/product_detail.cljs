(ns vetd-app.buyers.pages.product-detail
  (:require [vetd-app.buyers.components :as bc]
            [vetd-app.common.components :as cc]
            [vetd-app.ui :as ui]
            [vetd-app.util :as util]
            [vetd-app.docs :as docs]
            [reagent.core :as r]
            [reagent.format :as format]
            [re-frame.core :as rf]
            [clojure.string :as s]))

;; Events
(rf/reg-event-fx
 :b/nav-product-detail
 (fn [_ [_ product-idstr]]
   {:nav {:path (str "/b/products/" product-idstr)}}))

(rf/reg-event-fx
 :b/route-product-detail
 (fn [{:keys [db]} [_ product-idstr]]
   {:db (assoc db
               :page :b/product-detail
               :page-params {:product-idstr product-idstr})
    :analytics/page {:name "Buyers Product Detail"
                     :props {:product-idstr product-idstr}}}))

(rf/reg-event-fx
 :b/request-complete-profile
 (fn [{:keys [db]} [_ etype eid ename field-key]]
   {:ws-send {:payload {:cmd :b/request-complete-profile
                        :return {:handler :b/request-complete-profile-return}
                        :etype etype
                        :eid eid
                        :field-key field-key
                        :buyer-id (util/db->current-org-id db)}}
    :analytics/track {:event "Request"
                      :props {:category (str (s/capitalize (name etype)) " Profile")
                              :label (str ename " - " field-key)}}}))

(rf/reg-event-fx
 :b/request-complete-profile-return
 (constantly
  {:toast {:type "success"
           :title "Complete Profile Requested"
           :message "We'll let you know when the profile is completed."}}))

(rf/reg-event-fx
 :b/setup-call
 (fn [{:keys [db]} [_ product-id product-name]]
   {:ws-send {:payload {:cmd :b/setup-call
                        :return {:handler :b/setup-call-return}
                        :product-id product-id
                        :buyer-id (util/db->current-org-id db)}}
    :analytics/track {:event "Set Up Call"
                      :props {:category "Product"
                              :label product-name}}}))

(rf/reg-event-fx
 :b/setup-call-return
 (constantly
  {:toast {:type "success"
           :title "Set Up a Call"
           :message "We'll set up a call for you soon."}}))

(rf/reg-event-fx
 :b/create-preposal-req
 (fn [{:keys [db]} [_ product vendor]]
   {:ws-send {:payload {:cmd :b/create-preposal-req
                        :return {:handler :b/create-preposal-req-return
                                 :product product
                                 :vendor vendor}
                        :prep-req {:from-org-id (->> (:active-memb-id db)
                                                     (get (group-by :id (:memberships db)))
                                                     first
                                                     :org-id)
                                   :from-user-id (-> db :user :id)
                                   :prod-id (:id product)}}}}))

(rf/reg-event-fx
 :b/create-preposal-req-return
 (fn [_ [_ _ {{:keys [product vendor]} :return}]]
   {:toast {:type "success"
            :title "Pricing Estimate Requested"
            :message "We will be in touch with next steps."}
    :analytics/track {:event "Request"
                      :props {:category "Preposals"
                              :label (str (:pname product) " by " (:oname vendor))}}}))

(rf/reg-event-fx
 :b/buy
 (fn [{:keys [db]} [_ product-id product-name no-toast?]]
   {:ws-send {:payload {:cmd :b/buy
                        :return (when-not no-toast?
                                  {:handler :b/buy.return})
                        :product-id product-id
                        :buyer-id (util/db->current-org-id db)}}
    :analytics/track {:event "FRONTEND Buy"
                      :props {:category "Product"
                              :label product-name}}}))

(rf/reg-event-fx
 :b/buy.return
 (constantly
  {:toast {:type "success"
           :title "Buying Process Started!"
           :message "We'll be in touch via email with next steps shortly."}}))

(rf/reg-event-fx
 :b/ask-a-question
 (fn [{:keys [db]} [_ product-id product-name message]]
   {:ws-send {:payload {:cmd :b/ask-a-question
                        :return {:handler :b/ask-a-question-return}
                        :product-id product-id
                        :message message
                        :buyer-id (util/db->current-org-id db)}}
    :analytics/track {:event "Ask A Question"
                      :props {:category "Product"
                              :label product-name}}}))

;; :b/round.ask-a-question also points to this
(rf/reg-event-fx
 :b/ask-a-question-return
 (constantly
  {:toast {:type "success"
           :title "Question Sent!"
           :message "We'll get an answer for you soon."}}))

;; Subscriptions
(rf/reg-sub
 :product-idstr
 :<- [:page-params] 
 (fn [{:keys [product-idstr]}] product-idstr))

;; Components
(defn c-preposal-request-button
  [{:keys [vendor forms docs] :as product}]
  (when-not (seq docs) ;; does not have a completed preposal
    (if (not-empty forms) ;; has requested preposal
      [:> ui/Popup
       {:content "We will be in touch with next steps."
        :position "bottom left"
        :trigger (r/as-element
                  [:> ui/Label {:color "teal"
                                :size "large"
                                :basic true
                                :style {:display "block"
                                        :text-align "center"}}
                   "Estimate Requested"])}]
      [:> ui/Popup
       {:content (str "Get a personalized pricing estimate and pitch from "
                      (:oname vendor) ".")
        :header "Request Pricing Estimate"
        :position "bottom left"
        :trigger (r/as-element
                  [:> ui/Button {:onClick #(rf/dispatch [:b/create-preposal-req product vendor])
                                 :color "teal"
                                 :fluid true
                                 :icon true
                                 :labelPosition "left"}
                   "Request Estimate"
                   [:> ui/Icon {:name "wpforms"}]])}])))

(defn c-product-header-segment
  [{:keys [vendor rounds pname logo] :as product} v-fn discounts]
  [:> ui/Segment {:class "detail-container"}
   [:h1.product-title
    pname " " [:small " by " (:oname vendor)]]
   [:> ui/Image {:class "product-logo"
                 :src (str "https://s3.amazonaws.com/vetd-logos/" logo)}]
   (when (not-empty (:rounds product))
     [bc/c-round-in-progress {:round-idstr (-> rounds first :idstr)
                              :props {:ribbon "left"}}])
   [bc/c-tags product v-fn discounts true]
   [cc/c-grid {:style {:margin-top 4}}
    [[(or (util/parse-md (v-fn :product/description))
          [:p "No description available."]) 12]
     [[:<>
       (let [website-url (v-fn :product/website)]
         (when (bc/has-data? website-url)
           [:<>
            [bc/c-external-link website-url "Product Website"]
            [:br]
            [:br]]))
       (let [demo-url (v-fn :product/demo)]
         (when (bc/has-data? demo-url)
           [:<>
            [bc/c-external-link demo-url "Watch Demo Video"]
            [:br]
            [:br]]))] 4]]]])

(defn c-product
  "Component to display Product details."
  [{:keys [id pname vendor discounts
           form-docs
           forms ; requested preposals
           docs ; completed preposals
           agg-group-prod-rating agg-group-prod-price] :as product}]
  (let [v-fn (partial docs/get-value-by-term (-> form-docs first :response-prompts))
        preposal-v-fn (partial docs/get-value-by-term (-> docs first :response-prompts))
        c-display-field (bc/requestable
                         (partial bc/c-display-field* {:type :product
                                                       :id id
                                                       :name pname}))
        group-ids& (rf/subscribe [:group-ids])]
    [:<>
     [c-product-header-segment product v-fn discounts]
     (when (seq @group-ids&)
       [bc/c-community c-display-field id agg-group-prod-rating agg-group-prod-price])
     [bc/c-pricing c-display-field v-fn discounts
      (boolean (seq forms)) ;; has requested (and perhaps completed) a preposal
      (boolean (seq docs)) ;; has a completed preposal?
      preposal-v-fn
      (some-> docs first :updated)
      (some-> docs first :result)
      (some-> docs first :id)]
     [bc/c-vendor-profile (-> vendor :docs-out first) (:id vendor) (:oname vendor)]
     [bc/c-onboarding c-display-field v-fn]
     [bc/c-client-service c-display-field v-fn]
     [bc/c-reporting c-display-field v-fn]
     [bc/c-market-niche c-display-field v-fn]]))

(defn c-page []
  (let [product-idstr& (rf/subscribe [:product-idstr])
        org-id& (rf/subscribe [:org-id])
        group-ids& (rf/subscribe [:group-ids])
        products& (rf/subscribe [:gql/sub
                                 {:queries
                                  [[:products {:idstr @product-idstr&}
                                    [:id :pname :logo
                                     [:form-docs {:ftype "product-profile"
                                                  :_order_by {:created :desc}
                                                  :_limit 1
                                                  :doc-deleted nil}
                                      [:id 
                                       [:response-prompts {:deleted nil
                                                           :ref-deleted nil}
                                        [:id :prompt-id :prompt-prompt :prompt-term
                                         [:response-prompt-fields
                                          {:deleted nil
                                           :ref-deleted nil}
                                          [:id :prompt-field-fname :idx
                                           :sval :nval :dval]]]]]]
                                     [:discounts {:id @group-ids&
                                                  :ref-deleted nil}
                                      [:gname
                                       :group-discount-descr
                                       :group-discount-redemption-descr]]
                                     [:vendor
                                      [:id :oname :url
                                       [:docs-out {:dtype "vendor-profile"
                                                   :_order_by {:created :desc}
                                                   :_limit 1}
                                        [:id
                                         [:response-prompts {:ref-deleted nil}
                                          [:id :prompt-id :prompt-prompt :prompt-term
                                           [:response-prompt-fields
                                            [:id :prompt-field-fname :idx
                                             :sval :nval :dval]]]]]]]]
                                     [:forms {:ftype "preposal" ; preposal requests
                                              :from-org-id @org-id&}
                                      [:id]]
                                     [:docs {:dtype "preposal" ; completed preposals
                                             :to-org-id @org-id&
                                             :_order_by {:created :desc}
                                             :_limit 1
                                             :deleted nil}
                                      [:id :idstr :title :result :reason :updated
                                       [:from-org [:id :oname]]
                                       [:from-user [:id :uname]]
                                       [:to-org [:id :oname]]
                                       [:to-user [:id :uname]]
                                       [:response-prompts {:ref-deleted nil}
                                        [:id :prompt-id :prompt-prompt :prompt-term
                                         [:response-prompt-fields
                                          [:id :prompt-field-fname :idx :sval :nval :dval]]]]]]
                                     [:rounds {:buyer-id @org-id&
                                               :deleted nil}
                                      [:id :idstr :created :status]]
                                     [:categories {:ref-deleted nil}
                                      [:id :idstr :cname]]
                                     [:agg-group-prod-rating {:group-id @group-ids&}
                                      [:group-id :product-id
                                       :count-stack-items :rating]]
                                     [:agg-group-prod-price {:group-id @group-ids&}
                                      [:group-id :median-price]]]]]}])]
    (fn []
      [:div.container-with-sidebar
       [:div.sidebar
        [:div {:style {:padding "0 15px"}}
         [bc/c-back-button "Back"]]
        (when-not (= :loading @products&)
          (let [{:keys [vendor rounds] :as product} (-> @products& :products first)
                _ (rf/dispatch [:dispatch-stash.pop-all :product-detail-loaded])]
            [:<>
             [:> ui/Segment
              (when (empty? (:rounds product))
                [:<>
                 [bc/c-start-round-button {:etype :product
                                           :eid (:id product)
                                           :ename (:pname product)
                                           :props {:fluid true}}]
                 [c-preposal-request-button product]])
              [bc/c-buy-button product vendor]
              (when (empty? (:rounds product))
                [bc/c-ask-a-question-button product vendor])]
             [:> ui/Segment {:class "top-categories"}
              [:h4 "Jump To"]
              (util/augment-with-keys
               (for [[label k] (remove nil?
                                       [["Top Of Page" :top]
                                        ["Description" :top]
                                        (when (seq @group-ids&) ; is in a community?
                                          ["Your Communities" :product/community])
                                        ["Pricing" :product/pricing]
                                        ["Company Profile" :product/vendor-profile]
                                        ["Onboarding" :product/onboarding]
                                        ["Client Service" :product/client-service]
                                        ["Reporting & Measurement" :product/reporting]
                                        ["Industry Niche" :product/market-niche]])]
                 [:div
                  [:a.blue {:on-click #(rf/dispatch [:scroll-to k])}
                   label]]))]]))]
       [:div.inner-container
        (if (= :loading @products&)
          [cc/c-loader]
          [c-product (-> @products& :products first)])]])))
