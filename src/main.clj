(ns main
  (:require [io.pedestal.http :as http]
            [io.pedestal.http.route :as route]
            [io.pedestal.test :as test]))

(defn response [status body & {:as headers}]
  {:status status :body body :headers headers})

(def ok (partial response 200))
(def created (partial response 201))
(def accepted (partial response 202))

;; db interceptors
(defonce database (atom {}))

(def entity-render
  {:name :entity-render
   :leave (fn [context]
            (if-let [item (:result context)]
              (assoc context :response (ok item))
              context))})

(def db-interceptor
  {:name :database-interceptor
   :enter
   (fn [context]
     (update context :request assoc :database @database))
   :leave
   (fn [context]
     (if-let [[op & args] (:tx-data context)]
       (do
         (apply swap! database op args)
         (assoc-in context [:request :database] @database))
       context))})
;; end of db-interceptors


;;in memory db specific functions.
(defn make-list
  [nm]
  {:name nm
   :items {}})

(defn make-list-item
  [nm]
  {:name nm
   :done? false})

(defn find-list-by-id
  [db-val id]
  (get db-val id))

(defn find-list-item-by-ids
  [db-val list-id item-id]
  (get-in db-val [list-id :items item-id] nil))

(defn list-item-add
  [db-val list-id item-id new-item]
  (if (contains? db-val list-id)
    (assoc-in db-val [list-id :items item-id] new-item)
    db-val))
;; end of in memory db specific functions.

;; interceptors
(def list-create
  {:name :list-create
   :enter
   (fn [context]
     (let [nm (get-in context [:request :query-params :name] "Unnamed List")
           new-list (make-list nm)
           db-id (str (gensym "1"))
           url (route/url-for :list-view :params {:list-id db-id})]
       (assoc context
              :response (created new-list "Location" url)
              :tx-data [assoc db-id new-list])))})

(def list-view
  {:name :list-view
   :enter
   (fn [context]
     (if-let [list-id (get-in context [:request :path-params :list-id])]
       (if-let [this-list (find-list-by-id (get-in context [:request :database]) list-id)]
         (assoc context :result this-list)
         context)
       context))})

(def list-item-view
  {:name :list-item-view
   :leave (fn [context]
            (if-let [list-id (get-in context [:request :path-params :list-id])]
              (if-let [item-id (get-in context [:request :path-params :item-id])]
                (if-let [item (find-list-item-by-ids (get-in context [:request :database]) list-id item-id)]
                  (assoc context :result item)
                  context)
                context)
              context))})

(def list-item-create 
  {:name :list-item-create
   :enter (fn [context]
            (if-let [list-id (get-in context [:request :path-params :list-id])]
              (let [nm (get-in context [:request :queury-params :name] "Unnamed item")
                    new-item (make-list-item nm)
                    item-id (str (gensym "i"))]
                (-> context
                    (assoc :tx-data [list-item-add list-id item-id new-item])
                    (assoc-in [:request :path-params :item-id] item-id)))
              context))})

(def echo
  {:name :echo
   :enter
   (fn [context]
     (let [request (:request context)
           response (ok context)]
       (assoc context :response response)))})
;; end of interceptors


(def routes
  (route/expand-routes
   #{["/todo" :post [db-interceptor list-create]]
     ["/todo" :get echo :route-name :list-query-form]
     ["/todo/:list-id" :get [entity-render db-interceptor list-view]]
     ["/todo/:list-id" :post [entity-render list-item-view db-interceptor list-item-create]]
     ["/todo/:list-id/:item-id" :get [entity-render list-item-view db-interceptor]]
     ["/todo/:list-id/:item-id" :put echo :route-name :list-item-update]
     ["/todo/:list-id/:item-id" :delete echo :route-name :list-item-delete]}))


(def service-map
  {::http/routes routes
   ::http/type   :jetty
   ::http/port   8890})



;; code for hot reloading of server in develop environment
(defn start []
  (http/start (http/create-server service-map)))

(defonce server (atom nil))

(defn start-dev []
  (reset! server
          (http/start (http/create-server
                       (assoc service-map
                              ::http/join? false)))))
(defn stop-dev []
  (http/stop @server))

(defn restart []
  (stop-dev)
  (start-dev))
;; end of reloading of server

(defn test-request [verb url]
  (io.pedestal.test/response-for (::http/service-fn @server) verb url))