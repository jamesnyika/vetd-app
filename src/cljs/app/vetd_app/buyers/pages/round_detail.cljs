(ns vetd-app.buyers.pages.round-detail
  (:require [vetd-app.buyers.components :as bc]
            [vetd-app.common.components :as cc]
            [vetd-app.ui :as ui]
            [vetd-app.util :as util]
            [vetd-app.docs :as docs]
            [reagent.core :as r]
            [reagent.format :as format]
            [re-frame.core :as rf]
            [markdown-to-hiccup.core :as md]
            [clojure.string :as s]))

(def last-query-id (atom 0))

(defn get-next-query-id []
  (swap! last-query-id inc))

;; Events
(rf/reg-event-fx
 :b/nav-round-detail
 (fn [_ [_ round-idstr]]
   {:nav {:path (str "/b/rounds/" round-idstr)}}))

(rf/reg-event-fx
 :b/route-round-detail
 (fn [{:keys [db]} [_ round-idstr]]
   {:db (assoc db
               :page :b/round-detail
               :page-params {:round-idstr round-idstr})
    :analytics/page {:name "Buyers Round Detail"
                     :props {:round-idstr round-idstr}}}))

;; Subscriptions
(rf/reg-sub
 :round-idstr
 :<- [:page-params] 
 (fn [{:keys [round-idstr]}] round-idstr))

;; Components
(defn c-round-initiation-form
  [round-id]
  (let [requirements-options (r/atom [;; {:key "Subscription Billing"
                                      ;;  :text "Subscription Billing"
                                      ;;  :value "Subscription Billing"}
                                      ;; {:key "Free Trial"
                                      ;;  :text "Free Trial"
                                      ;;  :value "Free Trial"}
                                      ])
        ;; form data
        goal (r/atom "")
        start-using (r/atom "")
        num-users (r/atom "")
        budget (r/atom "")
        requirements (r/atom [])
        add-products-by-name (r/atom "")]
    (fn []
      [:<>
       [:h3 "Round Initiation Form"]
       [:p "Let us now a little more about who will be using this product and what features you are looking for. Then, we'll gather quotes for you to compare right away."]
       [:> ui/Form {:class "round-initiation-form"}
        [:> ui/FormTextArea
         {:label "What are you hoping to accomplish with the product?"
          :on-change (fn [_ this] (reset! goal (.-value this)))}]
        [:> ui/FormField
         [:label "When would you like to start using the product?"]
         [:> ui/Dropdown {:selection true
                          :options [{:key "Within 2 Weeks"
                                     :text "Within 2 Weeks"
                                     :value "Within 2 Weeks"}
                                    {:key "Within 3 Weeks"
                                     :text "Within 3 Weeks"
                                     :value "Within 3 Weeks"}
                                    {:key "Within 1 Month"
                                     :text "Within 1 Month"
                                     :value "Within 1 Month"}
                                    {:key "Within 2 Months"
                                     :text "Within 2 Months"
                                     :value "Within 2 Months"}
                                    {:key "Within 6 Months"
                                     :text "Within 6 Months"
                                     :value "Within 6 Months"}
                                    {:key "Within 12 Months"
                                     :text "Within 12 Months"
                                     :value "Within 12 Months"}]
                          :on-change (fn [_ this]
                                       (reset! start-using (.-value this)))}]]
        [:> ui/FormGroup {:widths "equal"}
         [:> ui/FormField
          [:label "What is your annual budget?"]
          [:> ui/Input {:labelPosition "right"}
           [:> ui/Label {:basic true} "$"]
           [:input {:type "number"
                    :on-change #(reset! budget (-> % .-target .-value))}]
           [:> ui/Label {:basic true} " per year"]]]
         [:> ui/FormField
          [:label "How many people will be using the product?"]
          [:> ui/Input {:labelPosition "right"}
           [:input {:type "number"
                    :on-change #(reset! num-users (-> % .-target .-value))}]
           [:> ui/Label {:basic true} "users"]]]]
        [:> ui/FormField
         [:label "What are your product requirements?"]
         [:> ui/Dropdown {:value @requirements
                          :options @requirements-options
                          :placeholder "Add your requirements..."
                          :search true
                          :selection true
                          :multiple true
                          :allowAdditions true
                          :additionLabel "Hit 'Enter' to Add "
                          :noResultsMessage "Type to add a new requirement..."
                          :onAddItem (fn [_ this]
                                       (let [value (.-value this)]
                                         (swap! requirements-options
                                                conj
                                                {:key value
                                                 :text value
                                                 :value value})))
                          :onChange (fn [_ this]
                                      (reset! requirements (.-value this)))}]]
        #_[:> ui/FormField
           [:label "Are there specific products you want to include?"]
           [:> ui/Dropdown {:multiple true
                            :search true
                            :selection true
                            ;; :on-change (fn [_ this]
                            ;;              (reset! add-products-by-name (.-value this)))
                            :on-change #(.log js/console %1 %2)}]]
        [:> ui/FormButton
         {:color "blue"
          :on-click
          #(rf/dispatch
            [:save-doc
             {:ftype "round-initiation"
              :round-id round-id}
             {:rounds/goal {:value @goal}
              :rounds/start-using {:value @start-using}
              :rounds/num-users {:value @num-users}
              :rounds/budget {:value @budget}
              :rounds/requirements {:value @requirements}
              :rounds/add-products-by-name {:value @add-products-by-name}}])}
         "Submit"]]])))

(defn c-round-initiation
  [{:keys [id status title products doc] :as round}]
  (if doc
    (println doc)
    [c-round-initiation-form id]))

(defn c-round-grid
  [{:keys [id status title products] :as round}]
  (let []
    (r/create-class
     {:component-did-mount
      (fn [this]
        (let [node (r/dom-node this)
              mousedown? (atom false)
              x-at-mousedown (atom nil)
              scrollleft-at-mousedown (atom nil)
              handle-mousedown
              (fn [e]
                (reset! x-at-mousedown (- (.-pageX e) (.-offsetLeft node)))
                (reset! scrollleft-at-mousedown (.-scrollLeft node))
                (reset! mousedown? true))
              handle-mouseup
              (fn [e]
                (reset! mousedown? false))
              handle-mousemove
              (fn [e]
                (when @mousedown?
                  (do (.preventDefault e)
                      (aset node
                            "scrollLeft"
                            (- @scrollleft-at-mousedown
                               (* 3 (- (- (.-pageX e) (.-offsetLeft node))
                                       @x-at-mousedown)))))))]
          (.addEventListener node "mousedown" handle-mousedown)
          (.addEventListener node "mouseup" handle-mouseup)
          (.addEventListener node "mouseleave" handle-mouseup)
          ;; add mouseleave?
          (.addEventListener node "mousemove" handle-mousemove)))
      
      :reagent-render
      (fn []
        (if (or true (seq products))
          [:div.round-grid
           {:style {:display "flex"
                    :flex-wrap "nowrap"     ; unnecessary?
                    :overflow-x "auto"
                    :box-sizing "border-box"
                    ;; :border "20px solid transparent"
                    :position "relative"
                    :cursor "move"
                    }}
           (for [i (range 40)]
             ^{:key i}
             [:div {:style {:width 250
                            
                            ;; :height 200

                            :flex "0 0 auto"
                            
                            ;; :float "left"
                            }}
              [:h4 "Subscription Billing"]
              [:p "Lorem ipsum lorem ispum lorem ispum lorem ispum lorem ispum lorem ispum lorem ispum lorem ispum lorem ispum lorem ispum lorem ispum lorem ispum lorem ispum."]
              [:p "Lorem ipsum lorem ispum lorem ispum lorem ispum lorem ispum lorem ispum lorem ispum lorem ispum lorem ispum lorem ispum lorem ispum lorem ispum lorem ispum."]
              [:p "Lorem ipsum lorem ispum lorem ispum lorem ispum lorem ispum lorem ispum lorem ispum lorem ispum lorem ispum lorem ispum lorem ispum lorem ispum lorem ispum."]
              [:p "Lorem ipsum lorem ispum lorem ispum lorem ispum lorem ispum lorem ispum lorem ispum lorem ispum lorem ispum lorem ispum lorem ispum lorem ispum lorem ispum."]
              [:p "Lorem ipsum lorem ispum lorem ispum lorem ispum lorem ispum lorem ispum lorem ispum lorem ispum lorem ispum lorem ispum lorem ispum lorem ispum lorem ispum."]])
           [:div {:style {:clear "both"}}]]
          [:<>
           [:p [:em "Your requirements have been submitted."]]
           [:p "We are gathering information for you to review from all relevant vendors. Check back soon for updates."]]))})))

(defn c-round
  "Component to display Round details."
  [{:keys [id status title products] :as round}]
  (let [status "in-progress"] ; DEV ONLY, REMOVE
    [:<>
     
     [:> ui/Segment {:class "detail-container"}
      [:h1 {:style {:margin-top 0}}
       title]]
     [:> ui/Segment {:class "detail-container"}
      [bc/c-round-status status]]
     [:> ui/Segment {:class "detail-container"}
      (case status
        "initiation" [c-round-initiation round]
        "in-progress" [c-round-grid round]
        "complete" [c-round-grid round])]]))

(defn c-page []
  (let [round-idstr& (rf/subscribe [:round-idstr])
        org-id& (rf/subscribe [:org-id])
        rounds& (rf/subscribe [:gql/sub
                               {:queries
                                [[:rounds {:idstr @round-idstr&}
                                  [:id :idstr :created :status :title
                                   [:products [:pname]]
                                   [:doc [:id
                                          [:response-prompts {:ref-deleted nil}
                                           [:id :prompt-id :prompt-prompt :prompt-term
                                            [:response-prompt-fields
                                             [:id :prompt-field-fname :idx
                                              :sval :nval :dval]]]]]]]]]}])]
    (fn []
      [:<>
       [:div.container-with-sidebar.round-details
        [:div.sidebar
         [:div {:style {:padding "0 15px"}}
          [bc/c-back-button {:on-click #(rf/dispatch [:b/nav-rounds])}
           "All VetdRounds"]]
         [:div {:style {:height 190}}] ; spacer
         (when-not (= :loading @rounds&)
           [:<>
            [:> ui/Segment
             [:h3 "Stripe Billing"]
             [:> ui/Popup
              {:content "what is does"
               :header "What is it Called?"
               :position "bottom left"
               :trigger
               (r/as-element
                [:> ui/Button {:onClick #(do (.stopPropagation %)
                                             #_(rf/dispatch [:b/do-something]))
                               :color "vetd-gradient"
                               :fluid true
                               :icon true
                               :labelPosition "left"}
                 "Declare Winner"
                 [:> ui/Icon {:name "checkmark"}]])}]
             [:> ui/Popup
              {:content "what is does"
               :header "What is it Called?"
               :position "bottom left"
               :trigger
               (r/as-element
                [:> ui/Button {:onClick #(do (.stopPropagation %)
                                             #_(rf/dispatch [:b/do-something]))
                               :color "grey"
                               :fluid true
                               :icon true
                               :labelPosition "left"}
                 "Disqualify"
                 [:> ui/Icon {:name "close"}]])}]
             [:> ui/Popup
              {:content (str "Let us setup a call for you with " 88888
                             " to discuss " 88888 ".")
               :header "Setup a Call"
               :position "bottom left"
               :trigger (r/as-element
                         [:> ui/Button { ;; :onClick #(rf/dispatch [:b/setup-call id pname])
                                        :color "grey"
                                        :fluid true
                                        :icon true
                                        :labelPosition "left"
                                        :style {:margin-right 15}}
                          "Setup a Call"
                          [:> ui/Icon {:name "left call"}]])}]
             ]
            [:> ui/Segment
             [:h3 "Authorize.Net"]
             [:> ui/Popup
              {:content "what is does"
               :header "What is it Called?"
               :position "bottom left"
               :trigger
               (r/as-element
                [:> ui/Button {:onClick #(do (.stopPropagation %)
                                             #_(rf/dispatch [:b/do-something]))
                               :color "vetd-gradient"
                               :fluid true
                               :icon true
                               :labelPosition "left"}
                 "Declare Winner"
                 [:> ui/Icon {:name "checkmark"}]])}]
             [:> ui/Popup
              {:content "what is does"
               :header "What is it Called?"
               :position "bottom left"
               :trigger
               (r/as-element
                [:> ui/Button {:onClick #(do (.stopPropagation %)
                                             #_(rf/dispatch [:b/do-something]))
                               :color "grey"
                               :fluid true
                               :icon true
                               :labelPosition "left"}
                 "Disqualify"
                 [:> ui/Icon {:name "close"}]])}]
             [:> ui/Popup
              {:content (str "Let us setup a call for you with " 88888
                             " to discuss " 88888 ".")
               :header "Setup a Call"
               :position "bottom left"
               :trigger (r/as-element
                         [:> ui/Button { ;; :onClick #(rf/dispatch [:b/setup-call id pname])
                                        :color "grey"
                                        :fluid true
                                        :icon true
                                        :labelPosition "left"
                                        :style {:margin-right 15}}
                          "Setup a Call"
                          [:> ui/Icon {:name "left call"}]])}]
             ]]
           #_(let [{:keys [vendor rounds] :as product} (-> @products& :products first)]
               (when (empty? (:rounds product))
                 [:> ui/Segment
                  [bc/c-start-round-button {:etype :product
                                            :eid (:id product)
                                            :ename (:pname product)
                                            :props {:fluid true}}]
                  [c-preposal-request-button product]
                  [bc/c-setup-call-button product vendor]
                  [bc/c-ask-a-question-button product vendor]])))]
        [:div.inner-container
         (if (= :loading @rounds&)
           [cc/c-loader]
           (let [round (-> @rounds& :rounds first)]
             [c-round round]))]]

       ])))
