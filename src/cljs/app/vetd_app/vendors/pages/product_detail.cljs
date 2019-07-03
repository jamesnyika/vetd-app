(ns vetd-app.vendors.pages.product-detail
  (:require [vetd-app.ui :as ui]
            [vetd-app.util :as util]
            [vetd-app.docs :as docs]
            [reagent.core :as r]
            [re-frame.core :as rf]))

(rf/reg-event-fx
 :v/nav-product-detail
 (fn [{:keys [db]} [_ product-id]]
   (let [product-idstr (util/base31->str product-id)]
     {:nav {:path (str "/v/products/" product-idstr)}
      :analytics/track {:event "Navigate"
                        :props {:category "Navigation"
                                :label "Vendors Product Detail"
                                :product-idstr product-idstr}}})))

(rf/reg-event-fx
 :v/route-product-detail
 (fn [{:keys [db]} [_ product-idstr]]
   {:db (assoc db
               :page :v/product-detail
               :page-params {:product-idstr product-idstr})
    :analytics/page {:name "Vendors Product Detail"
                     :props {:product-idstr product-idstr}}}))

(rf/reg-event-fx
 :v/save-product
 (fn [{:keys [db]} [_ product]]
   {:ws-send {:payload {:cmd :v/save-product
                        :product product}}}))
(rf/reg-event-fx
 :v/delete-product
 (fn [{:keys [db]} [_ product-id]]
   {:ws-send {:payload {:cmd :v/delete-product
                        :product-id product-id}}}))

(defn auto-populate-categories [prompt]
  (let [product-idstr& (rf/subscribe [:product-idstr])
        cats& (rf/subscribe
               [:gql/q {:queries
                        [[:products {:idstr @product-idstr&
                                     :deleted nil
                                     :_order_by {:created :desc}
                                     :_limit 1}
                          [[:categories  {:ref-deleted nil}
                            [:id :cname]]]]]}])]
    (r/track! #(reset! (-> prompt
                           :fields
                           first
                           :response)
                       (->> @cats&
                            :products
                            first
                            :categories
                            (mapv (fn [{:keys [id cname]}]
                                    {:state
                                     (r/atom
                                      {:id id
                                       :text cname})})))))))

(defn c-product
  [{:keys [id pname form-doc created updated actions]}]
  (let [pname& (r/atom pname)
        save-doc-fn& (atom nil)]
    (fn [{:keys [id pname form-doc created updated]}]
      [:div {:style {:width "800px"}}
       [:> ui/Form {:as "div"
                    :style {:margin "10px"
                            :padding "10px"
                            :border "solid 1px #666666"}}
        [:> ui/FormField
         "Product Name"
         [ui/input {:value (or @pname& pname) ;; necessary for some reason??
                    :placeholder "Product Name"
                    :spellCheck false
                    :on-change (fn [this]
                                 (reset! pname& (-> this .-target .-value)))}]]
        [:div "created: " (.toString (js/Date. created))]
        [:div "updated: " (.toString (js/Date. updated))]       
        [docs/c-form-maybe-doc
         (docs/mk-form-doc-state form-doc
                                 actions)
         {:return-save-fn& save-doc-fn&
          :c-wrapper [:div]}]
        [:> ui/Button {:color "teal"
                       :fluid true
                       :on-click #(do
                                    (rf/dispatch [:v/save-product {:id id
                                                                   :pname @pname&}])
                                    (@save-doc-fn&))}
         "Save Product"]
        [:> ui/Button {:color "red"
                       :fluid true
                       :on-click #(rf/dispatch [:v/delete-product id])}
         "DELETE  Product"]]])))

(defn mk-actions
  [{prompts1 :prompts :as prod-prof-form} {prompts2 :prompts form-id :id :keys [doc-id] :as form-doc}]
  (let [missing-prompt-ids (clojure.set/difference (some->> prompts2 (mapv :id) set)
                                                   (->> prompts1 (mapv :id) set))
        remove-fn (fn [{:keys [id response]}]
                    (rf/dispatch [:remove-prompt&response id (:id response) form-id doc-id]))]
    (->> (for [{:keys [id prompt ref-id response]} (filter #(-> % :id missing-prompt-ids)
                                                           prompts2)]
           [id {"remove" remove-fn}])
         (into {"product/categories"
                {"auto-populate" auto-populate-categories}}))))

(defn c-page []
  (let [org-id& (rf/subscribe [:org-id])
        product-idstr& (rf/subscribe [:product-idstr])
        prods& (rf/subscribe [:gql/sub
                              {:queries
                               [[:products {:idstr @product-idstr&
                                            :deleted nil
                                            :_order_by {:created :desc}
                                            :_limit 1}
                                 [:id
                                  :pname
                                  :short-desc
                                  :long-desc
                                  :logo
                                  :url
                                  :created
                                  :updated
                                  [:categories {:ref-deleted nil} [:id]]
                                  [:form-docs {:ftype "product-profile"}
                                   [:id :title :ftype :fsubtype
                                    :doc-id :doc-title
                                    [:doc-product [:id]]
                                    [:prompts {:ref-deleted nil
                                               :_order_by {:sort :asc}}
                                     [:id :idstr :prompt :descr :sort :term
                                      [:fields {:deleted nil
                                                :_order_by {:sort :asc}}
                                       [:id :idstr :fname :ftype
                                        :fsubtype :list? :sort]]]]
                                    [:responses {:deleted nil
                                                 :ref-deleted nil}
                                     [:id :prompt-id :notes
                                      [:fields {:deleted nil
                                                :_order_by {:idx :asc}}
                                       [:id :pf-id :idx :sval :nval :dval :jval]]]]]]]]]}])
        prod-prof-form& (rf/subscribe [:gql/q
                                       {:queries
                                        [[:forms {:ftype "product-profile"
                                                  :_order_by {:created :desc}
                                                  :_limit 1
                                                  :deleted nil}
                                          [:id :title :ftype :fsubtype
                                           [:prompts {:_order_by {:sort :asc}
                                                      :deleted nil
                                                      :ref-deleted nil}
                                            [:id :idstr :prompt :descr :sort :ref-id
                                             [:fields {:_order_by {:sort :asc}
                                                       :deleted nil}
                                              [:id :idstr :fname :ftype
                                               :fsubtype :list? :sort]]]]]]]}])]
    (fn []
      (let [prod-prof-form (-> @prod-prof-form&
                               :forms
                               first )]
        [:div
         (let [{:keys [id form-docs] :as p} (-> @prods& :products first)
               {:keys [doc-product] :as form-doc} (first form-docs)
               form-doc' (when form-doc
                           (assoc form-doc
                                  :product
                                  doc-product))]
           (when id
             [:div
              [docs/c-missing-prompts prod-prof-form form-doc]
              [c-product (assoc p
                                :form-doc
                                (or form-doc'
                                    (assoc prod-prof-form
                                           :product {:id id}))
                                :actions (mk-actions prod-prof-form form-doc'))]]))]))))